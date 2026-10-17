# Validação — Onda 0.5.0

## Resultado da execução final

Comando: `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -LiveTests`.

- Compilação `assembleDebug`: passou.
- `lintDebug`: zero erros, 29 avisos. Avisos são internacionalização de textos em português, dois atributos de manifesto pré-existentes e espaço utilizável. Sem baseline para esconder falhas.
- Testes JUnit: **45 executados, zero falhas, zero ignorados**.
- Verificações independentes de URLs/filtros/pesquisa/fila: **32 passaram**.
- `apksigner verify --verbose`: assinatura APK v2 válida.
- APK: `dist/Onda-0.5.0-debug.apk`, 1.832.610 bytes.
- SHA-256: `b20b852ab3745a3193ceb55539fcb683bcfc297d93938db4fa93a43d5e4e7aa9`.

## O que foi testado

### Busca nativa e regras

Seleção da maior capa HTTPS, rejeição de capas vazias, fila com deduplicação e limite de 3 tentativas, bloqueio por somente Wi-Fi, formatação de duração e busca real que retornou 5 itens para um termo de teste. Download real completo de 10.271.496 bytes M4A, removido ao final.

### Serviço e reprodução

Cenários em Android simulado nas APIs 26 e 35: avanço automático, fim de fila, restauração sem autoplay, callbacks tardios ignorados, preservação de favoritos/playlists/downloads, foco de áudio negado/concedido, notificação com token MediaSession e ações anterior/pausar/próxima, temporizador e pausa ao desconectar fones. Arquivos de teste representam metadados locais; MediaPlayer é simulado.

### Downloads

Conclusão atômica, cancelamento com remoção de parcial, HTML inválido, resposta parcial, preservação de arquivo existente e contêiner inválido. Fila com espera, ativa, falha e concluído, além de portão Wi-Fi.

## Compatibilidade de dados

Mesmo applicationId e assinatura de desenvolvimento. Favoritos, playlists, fila e metadados de downloads mantêm o formato; novos campos de artista, capa e duração são opcionais. Nova preferência de fila de downloads e flags de Wi-Fi/economia de dados. Reabrir não inicia áudio sozinho.

## Ainda pendente em aparelho

`adb devices -l` não mostrou dispositivo. Pendentes: instalar por cima da 0.4; busca com teclado e fontes grandes; capas em rede lenta; fila de 50 itens; somente Wi-Fi com troca de rede; economia de dados; tela bloqueada real; Bluetooth; chamadas; modo avião; bateria do fabricante; áudio online/offline no MediaPlayer real. Não foi feita publicação em loja.

## Como verificar no celular

1. Instale `Onda-0.5.0-debug.apk` por cima da versão anterior e confira favoritos e downloads mantidos.
2. Busque uma música, confira capa, artista e duração, e use Ouvir, + Fila e Baixar no mesmo resultado.
3. Monte uma lista, use Baixar lista e acompanhe a fila com progresso, cancelamento e nova tentativa.
4. Ative somente Wi-Fi, teste em dados móveis e depois em Wi-Fi.
5. Bloqueie a tela durante reprodução online e offline; teste notificação, fones e interrupções.
6. Pare o app e reabra: sessão pausada com seleção e posição restauradas.
