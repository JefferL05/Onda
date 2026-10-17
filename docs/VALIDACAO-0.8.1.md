# Validação 0.8.1 — Performance e bateria

- Build: `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1` — SUCESSO.
- Policy tests: 16 URL/filter + 16 library/queue — PASS.
- JUnit: 65 testes, 0 falhas, 3 pulados (rede real) — PASS.
- Lint: sem erros.
- APK: `dist/Onda-0.8.1-debug.apk`, assinatura v2 válida. Instalar por cima, sem desinstalar.

## O que mudou e por quê (bateria)
1. `OfflineService.java` — tick adaptativo: 1s tocando, 2s ocupado (download/carregando/timer), 5s parado. Antes era 1s sempre com o serviço vivo. Persistência só tocando/baixando; pausado salva no `pause()`.
2. `OfflineService.java` — WifiLock `WIFI_MODE_FULL` em vez de `FULL_HIGH_PERF` (alto desempenho desliga o power-save do Wi-Fi). Adquire só no streaming remoto ativo; `pause()`/`stop()`/`falha` liberam.
3. `OfflineActivity.java` — `renderQueuePreview()`/`renderDownloadQueue()` só rebuildam quando a assinatura muda (fila/download/progresso em blocos de 5%). Antes rebuildavam a 1Hz. Textos/barra continuam ao vivo.
4. `CoverCache.java` — com Economia de dados + rede móvel, pula download de capa remota (placeholder). Sem wake/rede extra.
5. `MainActivity.java` — coleções paginadas (60 + Mostrar mais) e filtro com debounce de 300ms. Antes rebuildava 100 itens a cada tecla.

## Baseline e medição no aparelho
- Sem device via adb neste PC; baseline de regressão = suíte acima.
- No aparelho, com cabo:
  - `adb dumpsys batterystats --reset`, use 30min (20min tocando tela desligada + 10min pausado), depois `adb dumpsys batterystats > bateria.txt` e Battery Historian.
  - Alvos: sem `PARTIAL_WAKE_LOCK` com app pausado; `Onda:Streaming` só durante streaming; CPU em background ~0; sem tráfego periódico parado.
  - Profiler > Energy: avanço de faixa e fim de fila não devem gerar rede quando rádio desligada.
- Limites: listas ainda são LinearLayout (sem RecyclerView); próxima etapa é migrar coleções para RecyclerView + Coil/paging real. Foreground types mantidos (`mediaPlayback|dataSync`) com tipo dinâmico no runtime.
