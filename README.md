# Onda — Android

Versão **1.0.0** ("Corrente"). Player musical Android com identidade própria:
busca nativa no YouTube com capas, player unificado online/offline, fila,
biblioteca local, letras sincronizadas fornecidas pelo usuário e descoberta
calculada no aparelho a partir do que você realmente ouve.

Instale `dist/Onda-1.0.0-debug.apk` por cima da versão anterior, sem desinstalar
ou limpar dados. Relatórios: [auditoria 1.0](docs/AUDITORIA-1.0.md) ·
[validação 1.0.0](docs/VALIDACAO-1.0.0.md).

## O que há de novo na 1.0

- **Nova arquitetura de telas**: shell com 4 destinos persistentes, mini-player
  único e telas próprias (Início, Explorar, Buscar, Biblioteca, Coleção,
  Artista, Fila, Ajustes, Player). O estado da tela é preservado ao navegar.
- **Design system "Corrente"**: paleta e escala tipográfica próprias, ícones
  vetoriais desenhados em código, modo escuro coerente, alvos de toque grandes.
- **Player profissional**: crossfade opcional, equalizador real com presets
  (quando o aparelho suporta), letras sincronizadas por marcação `[m:ss]`,
  notificação que abre o player e modo de recuperação quando uma fonte falha.
- **Descoberta real**: Daily Mix, Descobertas da semana e "porque você ouviu X"
  calculados no aparelho; nada de recomendações aleatórias apresentadas como IA.
- **Busca melhor**: sugestões enquanto digita, histórico, filtros por tipo,
  agrupamento e ranking local de relevância.
- **Biblioteca completa**: favoritas, recentes, playlists com reordenação,
  artistas, álbuns salvos, downloads e histórico — tudo sincronizado entre telas.
- **Desempenho**: cache da biblioteca com invalidação, consultas O(1) nos
  caminhos quentes, listas paginadas e eventos do serviço só quando mudam.

## Limites e privacidade

Busca, streaming e downloads dependem do YouTube/Google e podem mudar sem
aviso. Favoritas, playlists, histórico, letras e arquivos ficam no armazenamento
privado do app; não há conta nem sincronização entre aparelhos. O app não raspa
sites de letras e não usa analytics próprio. Remover o app apaga tudo.

## Compilar

Requer JDK 17, Android SDK 35 e Gradle 8.11.1:

```powershell
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

ou, neste computador, `powershell -ExecutionPolicy Bypass -File .\build.ps1`.

## Funcionalidades

- **Início** com Daily Mix, tocadas recentemente, recomendações "porque você
  ouviu" e seus artistas — tudo calculado a partir do seu uso no aparelho.
- **Explorar** com descobertas da semana, prateleiras por artista e por gênero
  e um resumo do que você mais ouviu.
- **Buscar** com sugestões enquanto digita, "você quis dizer", histórico,
  filtros Músicas/Artistas/Álbuns/Playlists/Vídeos e ranking local de relevância.
- **Biblioteca** com favoritas, recentes, playlists (reordenáveis, com capa e
  descrição), artistas seguidos, álbuns salvos, downloads e histórico.
- **Coleções** (playlist, álbum, mix, download) com reproduzir/embaralhar,
  filtro, ordenação, baixar a lista e salvar como playlist.
- **Player** em tela cheia com transporte completo, aleatório, repetição
  (tudo/uma), temporizador de sono, fila, crossfade opcional, equalizador real
  e letras sincronizadas.
- **Mini-player** persistente com capa, título, artista, curtir, play/pause,
  próxima e barra de progresso, presente em todas as abas.
- **Serviço de mídia** com notificação, controles de tela bloqueada, foco de
  áudio, pausa ao desconectar fones e restauração do estado ao reabrir.
- **Downloads** com fila de até 50 itens, progresso, cancelar, tentar falhas,
  baixar lista inteira e modo offline automático em Wi-Fi.
- **Navegador YouTube** mantido para vídeo (tela cheia, voltar, salvar vídeo),
  com o player nativo por cima para ouvir com a tela apagada.
- **Ajustes** com rádio automática, Wi-Fi apenas, economia de dados, download
  automático, apagamento de histórico/recomendações e licenças.

## Ouvir com a tela bloqueada

Na busca ou numa coleção, toque em Reproduzir. Depois de começar a tocar,
bloqueie a tela pelo botão do celular e use a notificação ou os controles de
mídia para pausar, voltar ou avançar. A apresentação na tela bloqueada depende
das configurações de privacidade do aparelho.

O player usa o arquivo baixado quando disponível; caso contrário, tenta o
fluxo online. Se uma fonte online falhar, o player tenta uma qualidade
alternativa antes de mostrar o erro.

## Ouvir offline

1. Na busca, toque em Baixar numa música; ou abra uma coleção e use **Baixar
   esta lista**.
2. Acompanhe em Ajustes ou no Player → Detalhes.
3. Depois de concluído, toque em Reproduzir. A reprodução usa somente o arquivo local.

O app salva M4A ou WebM, sem conversão para MP3, em armazenamento privado.
Desinstalar ou limpar dados apaga tudo. Limite de 512 MB por arquivo, fila de
até 50 itens e até 3 tentativas por item.

## Limites desta versão

Busca, streaming e downloads dependem do YouTube e podem mudar sem aviso,
exigindo atualização do extrator. Capas vêm dos servidores de imagem do YouTube
e são cacheadas no aparelho. Não há catálogo próprio, conta ou sincronização
entre aparelhos. Forçar parada encerra o serviço; reabrir restaura a seleção
pausada. O temporizador de sono não persiste após encerrar o processo.

## Dados

Favoritas, playlists, buscas, histórico, sessão de reprodução, fila de
downloads e arquivos ficam no armazenamento privado. Capas ficam em cache
privado. Remover o app apaga tudo. Buscas, streaming, capas e downloads são
enviados ao YouTube/Google. Não há analytics do Onda. Os formatos antigos de
biblioteca/downloads continuam sendo lidos; os campos novos são opcionais.

## Compilar

Requer JDK 17, Android SDK 35 e Gradle 8.11.1:

```powershell
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

ou, neste computador, `powershell -ExecutionPolicy Bypass -File .\build.ps1`.

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
