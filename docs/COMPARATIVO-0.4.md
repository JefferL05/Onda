# Auditoria e referências — Onda 0.4

## Evidências e escopo

As referências abaixo são documentação oficial consultada, não testes de execução dos aplicativos comerciais. Nenhum APK de YouTube Music, Apple Music ou Deezer foi instalado ou decompilado nesta etapa. A inspeção anterior do Spotify está em `analysis/spotify/RELATORIO.md`.

| Recurso | Referência confirmada | Situação no Onda 0.3 | Benefício, esforço e prioridade | Implementação 0.4 |
|---|---|---|---|---|
| Controle de mídia fora da tela do aplicativo | Apple: Tela Bloqueada/Central de Controle; YouTube Music: reprodução em segundo plano | Serviço só para baixadas; sem anterior/próxima na sessão | Continuidade de áudio; alto; P0 | Serviço único para local/online, MediaSession com ações e notificação de mídia |
| Mini-player e fila editável | Spotify: barra Tocando agora e organização da fila | Barra era atalho ao vídeo; fila manual separada | Estado consistente; médio; P0 | Barra conectada ao estado do serviço, pausa/próxima e fila automática |
| Biblioteca integrada a downloads | YouTube Music: Biblioteca/Downloads; Deezer: downloads na Biblioteca; Apple: categoria Downloads | Downloads separados dos favoritos/playlists | Ouvir sem gastar dados; médio; P0 | Preferência pelo arquivo local ao tocar qualquer faixa; selo Baixada; favoritos no player |
| Aleatório e repetição | Apple: guia de reprodução e links oficiais de aleatório/repetir; Spotify: documentação de fila/aleatório | Embaralhava uma lista de links só uma vez | Controle da sequência; médio; P1 | Permutação estável, repetir uma/fila/desligado e botões persistidos |
| Filtro e histórico | YouTube Music: filtragem por categoria e classificação por atividade | Já existiam filtros, ordenação e buscas recentes | Descoberta rápida; baixo; P1 | Preservados e conectados à reprodução nativa |
| Retomar posição sem autoplay | Decisão própria de produto, não comportamento atribuído aos concorrentes | Não existia | Continuidade sem áudio inesperado; médio; P1 | Snapshot versionado; restauração pausada; posição salva periodicamente |
| Temporizador | Proposta própria nesta etapa; não validado nos concorrentes | Não existia | Pausar ao dormir; baixo; P2 | 15/30/60 minutos; pausa sem apagar fila |
| Mix personalizado tipo Flow | Deezer descreve curadoria + aprendizado de máquina + histórico/favoritos | Sem catálogo/backend de recomendação | Descoberta; muito alto; futuro | Não implementado; não é substituído por aleatório com rótulo de IA |
| Letras, HiFi e sincronização entre contas | Serviços proprietários têm requisitos próprios de catálogo/conta | Sem fonte licenciada, catálogo ou backend | Depende de serviços externos; futuro | Não prometidos nesta versão |

## Fontes consultadas

- Spotify, **Adicionar à fila**: https://support.spotify.com/br-pt/article/play-queue/ — a página identifica os recursos de fila móvel como Premium.
- YouTube Music, **Personalizar suas músicas e podcasts**: https://support.google.com/youtubemusic/answer/6313542?hl=pt-BR
- YouTube Music, **Ouça músicas ou podcasts em segundo plano**: https://support.google.com/youtubemusic/answer/6313552?hl=pt-BR — músicas em segundo plano são apresentadas como recurso Premium do serviço oficial.
- Apple, **Reproduza músicas no iPhone**: https://support.apple.com/pt-br/guide/iphone/iph0138fb328/ios
- Deezer, **Offline**: https://www.deezer.com/explore/features/offline/
- Deezer, **Flow**: https://www.deezer.com/explore/features/flow/
- Android, **Play media in the background**: https://developer.android.com/media/platform/mediaplayer/background
- Android, **Manage audio focus**: https://developer.android.com/media/optimize/audio-focus

## Auditoria técnica e decisões

1. O serviço existente já tinha MediaPlayer, MediaSession, wake lock, foco de áudio e notificação; foi ampliado, não duplicado.
2. Antes, `onCompletion` só pausava. Agora consulta a fila e aplica repetição, aleatório ou fim de fila.
3. A barra da tela principal antes usava `lastVideo`; agora observa o serviço e mostra o estado efetivo, inclusive carregamento e falha.
4. A resolução de áudio online ocorre fora da thread principal. Uma geração por requisição descarta resultados e callbacks de músicas já canceladas/trocadas.
5. A seleção tenta primeiro o arquivo local válido. Sem download e sem rede, mostra um erro recuperável em vez de iniciar requisições indefinidamente.
6. O mesmo serviço iniciado mantém o player quando as Activities param ou saem de cena. Telas removem apenas seus listeners e bindings.
7. MediaSession publica título, duração, posição, fila e ações. Notificação inclui anterior, pausar/ouvir e próxima, com visibilidade pública. A configuração de privacidade do Android ainda controla a apresentação na tela bloqueada.
8. Perda de foco/chamada ou desconexão de fones pausa. Por decisão conservadora, a retomada após uma interrupção exige toque em Ouvir.
9. Wake lock de reprodução é controlado pelo MediaPlayer; rede usa WifiLock durante preparo/reprodução online e o libera ao pausar/parar. Resolução tem prazo de 90 segundos.
10. O temporizador usa relógio monotônico e não bloqueia o aparelho. O usuário bloqueia a tela pelo botão físico normalmente.

## Dados e atualização

Versão 0.4 usa o mesmo applicationId e assinatura de desenvolvimento. Favoritos, playlists, fila e metadados de downloads mantêm o formato anterior. O novo arquivo de preferências `playback` contém snapshot versão 1, URL canônica da música, posição, modos e ordem. Não grava URLs temporárias dos fluxos nem um estado de autoplay. Snapshot inválido não altera a biblioteca. Ao reiniciar, nada toca até uma ação explícita.

Fila vazia encerra o avanço automático. Se uma faixa falhar, o player mostra a falha e oferece nova tentativa/próxima, sem entrar num ciclo infinito. Edição de playlists não reescreve automaticamente uma fila já iniciada; iniciar a playlist novamente cria a nova sequência.

## Uso

- Em favoritos/playlists/baixadas, tocar numa faixa abre o player nativo.
- Ao explorar o YouTube, use **Ouvir com tela apagada** para transferir o áudio ao player.
- Use **Player** para posição, anterior/próxima, modos, fila, favoritos e temporizador.
- O navegador continua disponível para busca e vídeo; a página WebView não é o mecanismo de reprodução em segundo plano.

## Verificação

Os resultados finais de build/testes e limitações de dispositivo estão em `VALIDACAO-0.4.md`.
