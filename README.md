# Onda — Android

Versão 0.9.0 de um aplicativo musical Android com identidade própria. Busca nativa no YouTube com capas, player unificado online/offline, fila de downloads, biblioteca local e letras locais. O player usa Media3/ExoPlayer com fallback para MediaPlayer.

## Redesenho 0.9.0 — Oceanic Luminescence

Nova identidade visual: canvas grafite `#0B0E14`, ciano bioluminescente `#00E5FF`, ultravioleta `#7C4DFF`, cards `#121721`, mini-player de vidro e navegação inferior de 4 abas (Início, Buscar, Biblioteca, Tocando). A funcionalidade e os dados locais foram preservados.

Instale `dist/Onda-0.9.0-debug.apk` por cima da versão anterior, sem desinstalar ou limpar dados. Relatório: [validação 0.9.0](docs/VALIDACAO-0.9.0.md).

## Correção de reprodução 0.7.1

A migração tinha dois controladores de foco de áudio e disparava a preparação novamente após seek/rebuffer. O serviço também ignorava o Media3 ao manter a notificação ativa. Esses caminhos foram corrigidos, junto com o histórico nulo para arquivos do celular e a retomada após Parar. Os testes não desativam mais Media3 por detecção de Robolectric: há testes separados do adaptador, do fallback e do ExoPlayer real carregando um WAV gerado.

Instale `dist/Onda-0.9.0-debug.apk` por cima da versão anterior, sem desinstalar ou limpar dados. A tela do player identifica o motor em uso. Relatório: [validação 0.7.1](docs/VALIDACAO-0.7.1.md).

## Funcionalidades

- Busca nativa com capas, artista e duração, filtros Músicas/Vídeos/Tudo e buscas recentes.
- Cada resultado tem Ouvir, + Fila e Baixar, sem precisar abrir o navegador.
- Navegador YouTube mantido para vídeo, com tela cheia, voltar e proteção básica opcional.
- Mini-player com capa, estado real, pausar e próxima.
- Player com capa grande, artista, selos Baixada/Online, barra de posição, anterior/próxima, aleatório, repetição e temporizador.
- Fila automática persistente, com A seguir, avanço ao fim da faixa e restauração pausada.
- Fila de downloads com até 50 itens, progresso, cancelar, tentar falhas e baixar lista inteira.
- Configurações de download: somente Wi-Fi e economizar dados.
- Favoritos, playlists com nome/descrição/capa, histórico, filtro sem acentos e ordenação.
- Playlists favoritas, busca dentro da coleção, ordenação por título/artista/data e desfazer exclusão.
- Arquivo baixado preferido automaticamente; selo Baixada nas coleções.
- Serviço de mídia com notificação, controles de tela bloqueada, foco de áudio e pausa ao desconectar fones.
- Letras locais/editáveis por faixa para texto fornecido pelo usuário ou licenciado.
- Modo offline automático opcional para favoritos e fila em Wi-Fi.
- Cache de capas privado e busca executada fora da interface.

## Ouvir com a tela bloqueada

Na busca ou numa coleção, toque em Ouvir. Ao explorar o navegador, abra um vídeo e toque em **Ouvir com tela apagada**. Depois de começar a tocar, bloqueie a tela pelo botão do celular e use a notificação ou os controles de mídia para pausar, voltar ou avançar. A apresentação na tela bloqueada depende das configurações de privacidade do aparelho.

O player usa o arquivo baixado quando disponível; caso contrário, tenta o fluxo online. O WebView serve para descoberta e vídeo, não mantém reprodução própria em segundo plano. Não há bloqueio automático da tela nem permissão de administrador.

Testes de serviço passaram em Android simulado nas APIs 26 e 35. Tela bloqueada física, Bluetooth, chamadas reais e políticas de bateria ainda precisam de validação num aparelho.

## Ouvir offline

1. Na busca, toque em Baixar numa música; ou abra uma coleção e use **Baixar lista**.
2. Acompanhe em Player → Downloads. Falhas podem ser retomadas em Tentar.
3. Depois de concluído, toque em Ouvir. A reprodução usa somente o arquivo local.

O app salva M4A ou WebM, sem conversão para MP3, em armazenamento privado. Desinstalar ou limpar dados apaga tudo. Limite de 512 MB por arquivo, fila de até 50 itens e até 3 tentativas por item. Com somente Wi-Fi ativado, downloads aguardam rede Wi-Fi. O modo economizar dados prefere o menor bitrate compatível.

As primeiras melhorias (0.2) foram orientadas por uma inspeção estática local de um APK de terceiros, mantida fora do repositório público. Veja os relatórios de validação abaixo.

Auditoria e referências: [comparativo 0.4](docs/COMPARATIVO-0.4.md), [validação 0.4](docs/VALIDACAO-0.4.md), [validação 0.5](docs/VALIDACAO-0.5.md) e [validação 0.6](docs/VALIDACAO-0.6.md).

## Limites desta versão

Busca, streaming e downloads dependem do YouTube e podem mudar sem aviso, exigindo atualização do extrator. Capas vêm dos servidores de imagem do YouTube e são cacheadas no aparelho. Não há catálogo próprio, conta, sincronização, letras licenciadas ou recomendações personalizadas. Uma faixa online que falha interrompe o avanço até nova tentativa ou próxima. Forçar parada encerra o serviço; reabrir restaura a seleção pausada. O temporizador não persiste após encerrar o processo.

## Dados

Favoritos, playlists, buscas, histórico, sessão de reprodução, fila de downloads e arquivos ficam no armazenamento privado. Capas ficam em cache privado. Remover o app apaga tudo. Buscas, streaming, capas e downloads são enviados ao YouTube/Google. Não há analytics do Onda. A aplicação usa internet, estado da conexão/Wi-Fi, serviços de download/mídia, wake lock e notificações; não pede acesso geral aos arquivos. O WebView não acessa arquivos locais nem expõe ponte JavaScript nativa. URLs temporárias de mídia não são gravadas. Os formatos antigos de biblioteca/downloads são lidos; novos campos são opcionais.

## Compilar

Requer JDK 17, Android SDK 35 e Gradle 8.11.1. Abra esta pasta no Android Studio ou configure `local.properties` com `sdk.dir` e execute:

```powershell
.\gradlew.bat assembleDebug lintDebug
```

Neste computador, `powershell -ExecutionPolicy Bypass -File .\build.ps1` localiza as ferramentas em `%LOCALAPPDATA%\AndroidBuild`, executa verificações e testes, compila, roda lint e verifica a assinatura. A entrega fica em `dist\Onda-0.9.0-debug.apk`, acompanhada de seu arquivo `.sha256`. As versões anteriores são preservadas.

Para incluir testes reais com o YouTube, use `powershell -ExecutionPolicy Bypass -File .\build.ps1 -LiveTests`. A execução final 0.9.0 passou 65 testes JUnit e 32 verificações independentes. Inclui busca real, resolução de áudio, download completo de 10.271.496 bytes e ExoPlayer carregando um WAV no Android simulado. Isso não substitui a validação de saída de áudio, codecs e bateria num aparelho.

Dependência de extração fixada em `NewPipeExtractor:v0.26.5` (GPL-3.0-or-later). Avisos, licenças e referências de fontes estão em `app/src/main/assets/licenses` e em **Sobre → Licenças**.

O APK de desenvolvimento fica em `app/build/outputs/apk/debug/app-debug.apk`. A assinatura é de depuração, não uma chave de publicação. Para instalar com um dispositivo autorizado conectado:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Teste local da política de URLs

```powershell
javac -d build/policy-test app/src/main/java/app/onda/mobile/UrlPolicy.java tests/UrlPolicyTest.java
java -cp build/policy-test UrlPolicyTest
```

## Verificação em aparelho

1. Abra Buscar, pesquise uma música e reproduza um vídeo.
2. Salve em Favoritos e numa nova playlist; volte à biblioteca e abra os itens.
3. Reinicie o app e confirme a persistência das coleções e do histórico.
4. Teste tela cheia, botão Voltar e rotação durante reprodução.
5. Desative/ative a proteção pelo menu do navegador; confirme o carregamento.
6. Teste sem internet, com teclado aberto e fontes grandes.
7. Confira os controles de notificação com tela apagada, tanto para arquivo baixado quanto para streaming no player nativo.
8. Atualize por cima da 0.1 e confira manutenção dos favoritos. Renomeie uma playlist, filtre sem acentos e alterne ordenação.
9. Inicie uma fila, espere o fim de faixa, teste anterior/próxima, aleatório e os três modos de repetição. Abra o player pela barra.
10. Reinicie o app e confirme que fila e buscas recentes foram preservadas.
11. Baixe um áudio, ative modo avião e teste reprodução, pausa, busca de posição e tela apagada.
12. Teste cancelar um download, perda de conexão, nova tentativa e exclusão. Um download parcial não deve aparecer como pronto.
13. Teste a notificação, desconexão de fones e interrupção por outro app de áudio.
14. Pare/reabra o app: deve restaurar seleção/posição sem começar sozinho. Teste temporizador e edição da fila durante reprodução.

Reprodução real e comportamento específico do YouTube precisam ser validados em um aparelho ou emulador com WebView atualizado.
