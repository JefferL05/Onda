# Validação 0.8.0

- Build: `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1` — SUCESSO.
- Policy tests: 16 URL/filter + 16 library/queue — PASS.
- JUnit: 65 testes, 0 falhas, 3 pulados (rede real) — PASS.
- Lint: sem erros.
- APK: `dist/Onda-0.8.0-debug.apk`, assinatura v2 válida.
- Regressão corrigida: `completionAdvancesAndEndOfQueueStops` falhava porque a rádio automática vinha ligada por padrão e colocava o serviço em `loading` ao fim da fila. Rádio passou a ser opt-in (`autoplay=false` por padrão), com interruptor no player e na fila. Fim de fila volta a parar como na 0.7.1.

## O que validar no aparelho
1. Instalar por cima da 0.7.1 sem desinstalar; conferir favoritos, playlists, fila e downloads.
2. Biblioteca: criar playlist com nome/descrição/capa, favoritar, buscar dentro, ordenar, mover faixa, excluir com Desfazer.
3. Fila: Tocar a seguir tem prioridade; origem mostra playlist/álbum/rádio/manual.
4. Rádio desligada por padrão: fim de fila para. Ligar no player e confirmar sugestões por artista/relações do YouTube, sem repetir artista em sequência.
5. Player e mini-player durante navegação; tela bloqueada mantém áudio.
