# Auditoria 0.9.0 — Onda Music

Auditoria feita lendo o código-fonte completo (5.432 linhas em `app/src/main/java`, 9 telas/fluxos), os testes, o manifest, o design system e os relatórios de validação anteriores. Tudo o que está marcado como "confirmado" foi verificado no código; o que depende de aparelho está marcado assim.

## O que realmente existe hoje

O app é um cliente musical Android em Java puro (sem Kotlin, sem AppCompat/Material, views construídas à mão). Base funcional sólida e honesta: busca nativa via NewPipeExtractor, reprodução online/offline com Media3 + fallback MediaPlayer, download de áudio para armazenamento privado, favoritos/playlists/histórico/fila em SharedPreferences, serviço de mídia com notificação e controles de tela bloqueada, letras fornecidas pelo usuário, rádio opt-in baseada em metadados reais.

Isso é mais do que parece: não há "botão de mentira" na busca/player/download, e há testes JVM reais (65 no último relatório) incluindo o adaptador Media3 com um `Player` controlado.

## Problemas confirmados, por prioridade

### P0 — quebra a promessa central do produto

1. **Fila de downloads x reprodução quebram com o processo em segundo plano.** `OfflineService` e `MainActivity`/`OfflineActivity` encerram quando o sistema limpa a tarefa; o `BroadcastReceiver` de "fones desconectados" é registrado dentro de `onCreate` e nunca é desregistrado corretamente em todos os caminhos, e o serviço depende de preferências compartilhadas para reconstruir a fila. O caminho funciona no cenário feliz, mas não é resiliente a morte de processo.

2. **`MainActivity` é um `Activity` único com 1.676 linhas** que faz busca, navegação, WebView, browser, fila, biblioteca, playlists, downloads e player. Cada navegação reconstrói a árvore de views inteira (`content.removeAllViews()`), sem `RecyclerView`, sem paginação real (só um "Mostrar mais" de 60 em 60), sem reciclagem. Em listas grandes, e em telas pequenas, a rolagem trava e o consumo de memória cresce (cada linha infla views + `ImageView` + loader). Nenhum estado de rolagem é preservado ao voltar de uma tela.

3. **Cada item de lista tem seu próprio listener de drag/drop** (`setOnDragListener` por linha) e cada linha cria um `View` de sombra; a regra de reordenação é frágil (só funciona na ordem manual e sem filtro, e reconstrói a tela inteira a cada drop).

4. **O player recria o motor em vários caminhos** (`releasePlayer()` + `prepareMedia3()` a cada `select`), e não há `crossfade`, `equalizador`, `gapless`, nem detecção de "próxima faixa" no fim natural sem reabrir a Activity. A UI de progresso depende de polling (`tick` de 1 s) em vez de callbacks de posição — funciona, mas gasta bateria e faz a barra "pular".

5. **`OfflineActivity` e `MainActivity` duplicam o player**: há dois lugares que mostram capa, título, transporte, fila e downloads com lógicas divergentes (o mini-player em `MainActivity` não toca nada de verdade, apenas envia intents; a `OfflineActivity` é a tela real). Não existe "tela do player" única.

### P1 — qualidade de produto e funcionalidade incompleta

6. **Busca**: sem sugestões enquanto digita, sem correção de erros, sem agrupamento por categoria, sem relevância além da ordem do YouTube, sem "tocar tudo" a partir do resultado. A busca na biblioteca é um botão separado e uma tela separada.

7. **Descoberta**: "Rádio automática" é opt-in e só funciona quando o usuário chega ao fim da fila. Não existem Daily Mix, Descobertas, mixes por gênero, nem "músicas semelhantes". `RadioPlanner` é transparente, mas limitado a metadados e relacionados do YouTube — não aprende com o comportamento do usuário.

8. **Biblioteca**: favoritos, playlists e histórico existem, mas não há seguir artistas, salvar álbuns, "recentemente reproduzidos" como coleção própria, sincronização (nem local entre abas), nem organização por gênero. "Artistas/Álbuns" são agrupamentos derivados de metadados, não entidades.

9. **Player**: sem letras sincronizadas (só texto), sem equalizador, sem crossfade, sem controle de volume interno, sem "reprodução contínua" real, sem recuperação automática de falha de streaming (só mensagem de erro), sem `MediaSession` com metadados de álbum/capa (só título/artista), sem `PlaybackState` com velocidade correta em todos os estados.

10. **Acessibilidade**: ícones de navegação com `contentDescription` genérico, `ImageView` marcadas como não importantes em alguns lugares, textos sem `liveRegion` para mudanças de estado, contraste do texto secundário (`#94A3B8` sobre `#121721`) próximo do limite, alvos de toque de 36–40 dp em alguns botões (mínimo recomendado 48 dp), sem suporte a fonte grande/escala.

11. **Responsividade**: existe layout "wide" para >= 840 dp (tablet), mas não há adaptação real para dobra/landscape, a tela do player usa capa fixa de 240 dp e não se adapta à altura, e o mini-player ocupa a mesma faixa em todas as larguras.

12. **Desempenho**: `CoverCache` tem cache de memória de 8 MB e disco de 64 MB (ok), mas `LibraryStore.tracks(key)` relê e faz parse de JSON a cada chamada — e isso acontece dentro de loops (ex.: `trackMenu`, `showQueue`, `queueSnapshot`, `updateSession` para cada item da fila). Com playlists grandes isso é O(n²) de parse de JSON no main thread. `OfflineStore.songs()` também é chamado repetidamente e relê todas as chaves.

13. **`OfflineService` mistura responsabilidades**: download, player, notificação, sessão de mídia, agenda de rede, fila e persistência em uma classe de 1.038 linhas. O lock `synchronized (OfflineService.class)` em volta do download inteiro é um ponto de contenção global.

### P2 — segurança, dados e engenharia

14. **`SharedPreferences` sem versão de esquema**: campos novos são tolerados, mas não há migração explícita nem teste de compatibilidade com os formatos antigos. Renomear playlist reescreve `playlist:` e `meta:` em uma transação — bom — mas `deletePlaylist` guarda apenas um "undo" e não valida colisão em todos os caminhos.

15. **`WebView`**: JavaScript ligado, `setAllowFileAccess(false)` e `setAllowContentAccess(false)` (bom), mas o `WebView` é destruído no `onDestroy` e recriado a cada instância; não há `WebViewAssetLoader`, nem bloqueio de `window.open` além de `setSupportMultipleWindows(false)`, e o cookie do YouTube é compartilhado com o app (`CookieManager` global). O link "Abrir link do YouTube" aceita qualquer URL do domínio YouTube, incluindo `javascript:`? — Não: `UrlPolicy.youtube` exige `https` e host conhecido. Ok.

16. **Permissões**: `READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE` pedidas no player; `POST_NOTIFICATIONS` pedido em dois pontos diferentes com o mesmo requestCode 30 (pode colidir). Sem `FOREGROUND_SERVICE_MEDIA_PROCESSING` (não é necessário). Sem `queries` no manifest.

17. **Testes**: os testes JVM puros cobrem regras de URL, fila, snapshot e letras; os testes Robolectric cobrem serviço e Media3. Não há testes de UI, de migração de dados, de busca, nem de performance. Não há teste de `LibraryStore` (o núcleo de dados) — o mais crítico — além dos testes de regras em `tests/`.

18. **Build**: `build.ps1` depende de `%LOCALAPPDATA%\AndroidBuild` e de um `subst` para caminhos com acento; `dist/` acumula APKs antigos. Não há versionamento automático nem changelog gerado.

## O que será feito nesta evolução (escopo realista e verificável)

Vou executar em incrementos que mantêm o projeto compilando e testado:

- **Camada de dados** nova: `LibraryStore` com cache em memória + versão de esquema + migração segura, entidades de artista/álbum, "seguir", "recentemente reproduzidos", playlists com reordenação e undo robustos.
- **Player**: motor único (`PlaybackEngine`) com Media3, crossfade opcional, equalizador (via `android.media.audiofx`), letras (texto + sincronização por tempo quando o usuário fornecer marcações), `MediaSession` com metadados completos, recuperação automática de falha, e uma **tela de player única** reutilizada por mini-player e tela cheia.
- **UI**: novo design system (tipografia, espaçamento, elevação, estados), navegação por 4 destinos com estado preservado, listas com `RecyclerView`, telas: Início (mixes reais), Explorar, Buscar (sugestões + categorias), Biblioteca, Coleção, Player, Fila, Configurações.
- **Descoberta**: mixes calculados a partir do histórico/curtidas/skips reais, com explicação ("porque você ouviu X"), Daily Mix e Descobertas.
- **Busca**: sugestões enquanto digita (via NewPipe `getSearchSuggestion`), histórico, correção, resultados agrupados, estados de carregamento/vazio/erro.
- **Segurança/desempenho**: validação, limites, sem logging de dados pessoais, cache, paginação, medição de tempo de abertura.
- **Testes**: novos testes JVM para dados/ranking/busca/player rules, e relatório honesto do que só pode ser validado no aparelho.

O que **não** será feito (e por quê): sincronização entre dispositivos exige backend/conta (não existe no projeto, e não vou inventar credenciais); catálogo licenciado próprio exige contratos com gravadoras; letras licenciadas exigem provedor pago. Esses pontos ficam documentados como dependências externas.
