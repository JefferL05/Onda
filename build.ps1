param([switch]$Install, [switch]$LiveTests)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

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

New-Item -ItemType Directory -Force 'build\policy-test' | Out-Null
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d build/policy-test app/src/main/java/app/onda/mobile/UrlPolicy.java app/src/main/java/app/onda/mobile/LibraryQuery.java app/src/main/java/app/onda/mobile/QueueNavigator.java tests/UrlPolicyTest.java tests/LibraryRulesTest.java
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilacao dos testes.' }
& "$env:JAVA_HOME\bin\java.exe" -cp build/policy-test UrlPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes de URLs.' }
& "$env:JAVA_HOME\bin\java.exe" -cp build/policy-test LibraryRulesTest
if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes da biblioteca/fila.' }

$gradle = if (Test-Path '.\gradlew.bat') { '.\gradlew.bat' } else { "$tools\gradle-8.11.1\bin\gradle.bat" }
$gradleArgs = @('--no-daemon', 'assembleDebug', 'lintDebug', 'testDebugUnitTest')
if ($LiveTests) { $gradleArgs += '-PliveTests' }
& $gradle @gradleArgs
if ($LASTEXITCODE -ne 0) { throw 'Falha no build/lint.' }

$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'APK com assinatura invalida.' }
New-Item -ItemType Directory -Force 'dist' | Out-Null
$metadata = Get-Content 'app\build\outputs\apk\debug\output-metadata.json' -Raw | ConvertFrom-Json
$version = $metadata.elements[0].versionName
$filename = "Onda-$version-debug.apk"
Copy-Item $apk "dist\$filename" -Force
$hash = (Get-FileHash "dist\$filename" -Algorithm SHA256).Hash.ToLower()
"$hash  $filename" | Set-Content "dist\$filename.sha256" -Encoding ascii
Write-Output "APK: $PSScriptRoot\dist\$filename"

if ($Install) {
    & "$env:ANDROID_HOME\platform-tools\adb.exe" install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar no dispositivo. Verifique adb devices.' }
}
