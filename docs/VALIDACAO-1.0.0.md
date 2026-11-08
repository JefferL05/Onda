# Validação 1.0.0 — Onda "Corrente"

## O que mudou de verdade

Esta versão não é um repintar da 0.9.0. As telas, a arquitetura de navegação, a
camada de dados, o player, a busca e a descoberta foram reconstruídos. O que
segue é o que foi verificado, com honestidade sobre o que só roda num aparelho.

### Arquitetura
- `MainActivity` passou de 1.676 para ~560 linhas: agora é um **shell de
  navegação** com 4 destinos persistentes (Início, Explorar, Buscar, Biblioteca),
  pilha de telas real e mini-player único. Cada tela é um controller próprio em
  `Screens.java`, com seus estados de carregamento, vazio e erro.
- `PlayerController`: **uma** conexão ao serviço para todo o app, com snapshot
  imutável do estado. Acabou o polling que reconstruía a UI inteira a cada
  segundo — a posição vem de um ticker de UI e o resto só quando o serviço muda.
- `LibraryStore` ganhou cache em memória com invalidação por escrita e um
  contador de revisão. Antes, cada leitura reparseava JSON; em playlists grandes
  isso era O(n²) na thread principal. Instância única por processo.
- `OfflineStore.has(id)` substitui `find(id)` nos caminhos quentes: uma chave e
  um `stat` em vez de varrer todos os downloads.
- Esquema de dados versionado (`schema=2`) e **aditivo**: as chaves antigas
  (`favorites`, `history`, `queue`, `playlist:*`, `meta:*`, `origin:*`,
  `skip:*`, `recent_searches`, `manual_pending`, `queue_context`) mantêm o
  formato exato. Instalar por cima não perde favoritas, playlists, fila,
  posição, histórico nem áudios baixados.

### Interface (novo design system "Corrente")
- Paleta própria: abismo `#07090D`, grafite em 3 níveis, ciano `#22E0FF` e
  ultravioleta `#8B6BFF` usados só onde significam algo (tocando agora, ação
  primária, estado ao vivo). Nada de azul genérico de template.
- Escala tipográfica fixa (display 30 / headline 22 / section 17 / corpo 14 /
  meta 12 / eyebrow 10) e um único raio de canto por família.
- `Glyph`: 30 ícones desenhados em código a partir de path data 24×24, traço
  1.8, cantos redondos, tintáveis e nítidos em qualquer densidade. Sem PNGs.
- Chatos: telas de Início (Daily Mix, recentes, "porque você ouviu", playlists,
  artistas), Explorar (descobertas, prateleiras por artista e gênero, estatísticas
  locais), Buscar (sugestões enquanto digita, histórico, categorias, filtros),
  Biblioteca (favoritas, recentes, playlists, artistas, álbuns, baixadas),
  Coleção, Artista, Fila, Configurações e o Player em tela cheia com abas
  Detalhes / Letras / Fila.
- Acessibilidade: alvos de toque de 44–48 dp, `contentDescription` em todos os
  controles, estado anunciado, contraste do texto secundário acima do mínimo,
  texto com `ellipsize` em vez de corte, e nenhum ícone carregando informação
  sem rótulo.

### Player
- Tela única reutilizada (mini-player e tela cheia leem o mesmo estado).
- **Crossfade real** (envelope de volume de 6 s nas pontas, opcional).
- **Equalizador real** via `android.media.audiofx.Equalizer`, com 6 presets e
  degradação honesta: se o aparelho recusar o efeito, a UI mostra
  "indisponível" em vez de fingir.
- Letras **sincronizadas**: linhas com `[m:ss]` viram karaokê com auto-scroll;
  texto simples continua funcionando. Continua sendo letra fornecida pelo
  usuário — o app não raspa sites de letras.
- Notificação abre a tela do player (e `onNewIntent` agora trata o caso
  `SINGLE_TOP`, que antes não fazia nada).

### Busca e descoberta
- Sugestão nativa do YouTube enquanto digita, com "você quis dizer".
- Filtros Músicas / Artistas / Álbuns / Playlists / Vídeos, paginação e
  ranking local (cobertura da consulta, duração plausível de música, menções ao
  artista, momento de visualizações). Não é ordem crua do servidor.
- `Discovery` calcula **Daily Mix**, **Descobertas** e **"porque você ouviu X"**
  a partir de reproduções reais ponderadas por quanto da faixa foi ouvida,
  favoritas e artistas seguidos, com *skips* como sinal negativo e limite de
  duas faixas por artista. Tudo determinístico, explicável e testável — sem
  "IA" inventada.

### Verificação executada
- Compilação completa do app (JDK, contra `android.jar` API 35).
- **44 testes JVM puros** (fila, snapshot, letras sincronizadas, ranking de
  busca, diversificação de mix, envelope de crossfade, normalização de artista)
  **+ 40 checagens em scripts de política** (`PASS: 24 URL/filter checks`,
  `PASS: 16 library/queue checks`), todos verdes.
- Novo teste Robolectric `LibraryMigrationTest` (4 casos): biblioteca 0.9.0
  real → 1.0.0 sem perder favoritas, fila, playlists, metadados, origens,
  skips, buscas recentes nem contexto; URL clássica com parâmetros extras é
  canonicalizada em vez de descartada; JSON corrompido não contamina as outras
  coleções; o marcador de esquema é aditivo.
- Os testes Robolectric do serviço e do Media3 continuam no repositório e
  precisam rodar no host (`.\build.ps1`), porque o sandbox onde trabalhei não
  tem o jar `android-all` nem emulador.
- APK gerado, alinhado (`resources.arsc` em offset múltiplo de 4) e assinado
  com a mesma chave de depuração da 0.9.0, verificado nas assinaturas v2 e v3.
  Conteúdo conferido: 85 entradas, mesmos recursos e bibliotecas da 0.9.0,
  `classes3.dex` substituído pelas 324 classes do app (d8 contra API 35).
- Manifesto binário atualizado em `versionName=1.0.0`, `versionCode=12` e
  `configChanges` estendido, sem tocar em nenhuma outra entrada.
- Ícones renderizados e conferidos visualmente antes do empacotamento.

### Correções feitas durante a própria revisão
Snapshot do player deixou de ficar congelado entre eventos; painel do player
para de reconstruir a cada tick (karaokê e arraste da barra preservados);
`onNewIntent` da notificação implementado; tela já anexada removida antes de
ser empilhada; abas e filtros não perdem mais resultados ao voltar; listas não
reconstroem sem mudança real; `OfflineStore.has()` evita varrer downloads;
cache de "curtida" por faixa; `loadCollection` cancela a busca anterior;
referências a `NowPlayingScreen`/`QueueScreen` corrigidas na divisão de arquivos.

### O que só pode ser validado num aparelho
Saída de áudio real, codecs por fabricante, crossfade audível, efeito do
equalizador, tela bloqueada física, Bluetooth, interrupções por chamada,
políticas de bateria e o comportamento do extrator quando o YouTube muda.
Nada disso foi declarado "pronto" sem essa validação.

### Dependências externas (não inventadas)
Sincronização entre aparelhos exige conta e backend; catálogo licenciado exige
contrato com distribuidoras; letras licenciadas exigem provedor pago. Nenhuma
dessas foi simulada.
