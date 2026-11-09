param([switch]$Install, [switch]$LiveTests, [switch]$Push, [string]$CommitMessage)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# 1. Limpeza automatica de arquivos duplicados (*_2.*) na raiz
Get-ChildItem -Path $PSScriptRoot -File | Where-Object { $_.Name -match '_2(\.|$)' } | ForEach-Object {
    Write-Output "Removendo arquivo duplicado: $($_.Name)"
    Remove-Item $_.FullName -Force
}

$tools = Join-Path $env:LOCALAPPDATA 'AndroidBuild'
if (!(Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $jdk = Get-ChildItem "$tools\jdk" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if (!(Test-Path "$env:JAVA_HOME\bin\javac.exe")) { throw 'Configure JAVA_HOME para um JDK 17.' }
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
if (!$env:ANDROID_HOME) {
    if (Test-Path "$tools\sdk") { $env:ANDROID_HOME = "$tools\sdk" }
    elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk") { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
}
if (!(Test-Path "$env:ANDROID_HOME\platforms\android-35\android.jar")) { throw 'Android SDK 35 nao encontrado.' }

# Garantir local.properties configurado com sdk.dir
$localProps = Join-Path $PSScriptRoot 'local.properties'
if (!(Test-Path $localProps)) {
    $escapedSdk = $env:ANDROID_HOME -replace '\\', '\\' -replace ':', '\:'
    "sdk.dir=$escapedSdk" | Set-Content $localProps -Encoding ascii
}

New-Item -ItemType Directory -Force 'build\policy-test' | Out-Null
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d build/policy-test app/src/main/java/app/onda/mobile/UrlPolicy.java app/src/main/java/app/onda/mobile/LibraryQuery.java app/src/main/java/app/onda/mobile/QueueNavigator.java tests/UrlPolicyTest.java tests/LibraryRulesTest.java
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilacao dos testes.' }
& "$env:JAVA_HOME\bin\java.exe" -cp build/policy-test UrlPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes de URLs.' }
& "$env:JAVA_HOME\bin\java.exe" -cp build/policy-test LibraryRulesTest
if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes da biblioteca/fila.' }

$origRoot = $PSScriptRoot
$mappedDrive = $null
if ($origRoot -match '[^\u0000-\u007F]') {
    $letters = 79..90 | ForEach-Object { [char]$_ + ':' } | Where-Object { !(Get-PSDrive -Name $_[0] -ErrorAction SilentlyContinue) }
    $mappedDrive = $letters | Select-Object -First 1
    if ($mappedDrive) {
        subst $mappedDrive $origRoot
        Set-Location "$mappedDrive\"
    }
}

try {
    $gradle = if (Test-Path '.\gradlew.bat') { '.\gradlew.bat' } else { "$tools\gradle-8.11.1\bin\gradle.bat" }
    $gradleArgs = @('--no-daemon', 'assembleDebug', 'lintDebug', 'testDebugUnitTest')
    if ($LiveTests) { $gradleArgs += '-PliveTests' }
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Falha no build/lint.' }

    $currentWorkDir = (Get-Location).Path
    $apk = Join-Path $currentWorkDir 'app\build\outputs\apk\debug\app-debug.apk'
    & "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK com assinatura invalida.' }

    $distDir = Join-Path $origRoot 'dist'
    New-Item -ItemType Directory -Force $distDir | Out-Null
    $metadata = Get-Content (Join-Path $currentWorkDir 'app\build\outputs\apk\debug\output-metadata.json') -Raw | ConvertFrom-Json
    $version = $metadata.elements[0].versionName
    $filename = "Onda-$version-debug.apk"
    $destApk = Join-Path $distDir $filename
    Copy-Item $apk $destApk -Force
    $hash = (Get-FileHash $destApk -Algorithm SHA256).Hash.ToLower()
    "$hash  $filename" | Set-Content (Join-Path $distDir "$filename.sha256") -Encoding ascii
    Write-Output "APK: $destApk"

    if ($Install) {
        & "$env:ANDROID_HOME\platform-tools\adb.exe" install -r $destApk
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar no dispositivo. Verifique adb devices.' }
    }

    if ($Push) {
        Set-Location $origRoot
        $msg = if ($CommitMessage) { $CommitMessage } else { "Release $version - build e APK gerado" }
        git add .
        $status = git status --porcelain
        if ($status) {
            git commit -m $msg
            git push origin main
            $commitHash = (git rev-parse --short HEAD)
            Write-Output "Alterações enviadas para o GitHub com sucesso: $msg (Commit: $commitHash)"
        } else {
            Write-Output "Nenhuma alteração pendente para commit no Git."
        }
    }
}
finally {
    if ($mappedDrive) {
        Set-Location $origRoot
        subst $mappedDrive /d
    }
}
