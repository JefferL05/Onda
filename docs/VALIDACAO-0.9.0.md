# Validação 0.9.0 — Redesenho Oceanic Luminescence

- Build: `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1` — SUCESSO.
- Policy tests: 16 URL/filter + 16 library/queue — PASS.
- JUnit: 65 testes, 0 falhas, 3 pulados (rede real) — PASS.
- Lint: sem erros.
- APK: `dist/Onda-0.9.0-debug.apk`, assinatura v2 válida. Instalar por cima, sem desinstalar.

## Design system aplicado
- `Ui.java` reescrito: canvas `#0B0E14`, superfícies `#121721/#191C22/#1D2026`, acento ciano `#00E5FF`, ultravioleta `#7C4DFF`, textos `#F1F5F9/#94A3B8/#475569`, estados `#10B981/#F59E0B`.
- Componentes: cards de canto 16, pills, badges, chip ativo em ciano, botão circular primário, campo de busca arredondado, mini-player de vidro.

## Telas
- **MainActivity**: cabeçalho com marca + versão + estado de conexão; navegação inferior de 4 abas (Início, Buscar, Biblioteca, Tocando); mini-player de vidro com linha de progresso ciano.
- **Início**: saudação por horário, pills de estado, busca universal com colar link, chips de filtro, widget de fila de downloads, carrossel "Tocadas Recentemente", grade "Playlists & Coleções", resumo "A Seguir na Fila".
- **Buscar**: campo com limpar, linha do extrator, chips de tipo, buscas recentes, resultados com botão circular de play e badges.
- **Biblioteca**: memória do dispositivo, fila de downloads, filtros (inclui "Baixadas"), playlists, artistas/álbuns.
- **OfflineActivity (player)**: capa grande, badges, título/artista, favoritar, progresso, transporte circular (aleatório/anterior/play/próxima/repetir), abas Detalhes/Letras/Fila.

## Preservado
Favoritos, playlists, fila, downloads, posição, histórico, letras locais e o serviço de mídia (foco de áudio, notificação, tela bloqueada) continuam intactos. Nenhum dado local foi apagado.

## Não implementado do mockup
Equalizador gráfico, visualizador 3D, "Normalização de Volume" e métricas de buffer não existem no motor atual e não foram simulados. O mockup é uma referência visual; o app segue com o que é real.

## Medir no aparelho
1. Instalar por cima da 0.8.1 e conferir favoritos/playlists/fila/downloads.
2. Navegar pelas 4 abas; abrir o player e as abas Detalhes/Letras/Fila.
3. Buscar, baixar e reproduzir; bloquear a tela e confirmar áudio contínuo.
4. `adb dumpsys batterystats --reset` + uso de 30min + `dumpsys` para comparar com a 0.8.1.
