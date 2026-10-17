# Correção de reprodução — 0.7.1

## Falhas identificadas no código

- ExoPlayer solicitava foco automaticamente enquanto o serviço também solicitava/abandonava foco. A perda de foco recebida pelo serviço podia pausar o novo player. Agora o serviço é o único responsável, com a mesma política para os dois motores.
- `STATE_READY` era tratado como uma nova preparação a cada seek/rebuffer. Agora a posição inicial é aplicada apenas uma vez por fonte; eventos posteriores atualizam o buffering sem reposicionar ou retomar uma faixa pausada.
- Notificação e encerramento do serviço verificavam somente `MediaPlayer`, não o Media3. Agora usam a presença do motor ativo e do carregamento.
- Arquivos MediaStore deixavam `current` nulo, mas a retomada tentava gravá-lo no histórico de links YouTube. Agora fontes do celular têm tratamento explícito e não entram como registros inválidos nesse histórico.
- Fallback não liberava consistentemente o motor anterior nem mantinha estado de carregamento/posição/intenção de pausa. Agora há um único caminho de fallback, protegido por geração e identidade do motor.
- Negar a permissão de áudio reabria a solicitação em loop. Agora é possível continuar usando os downloads e solicitar acesso local posteriormente.
- A limpeza de downloads interrompidos podia remover arquivos completos sem metadados correspondentes. Agora limpa somente `.part`, preservando arquivos completos para possível recuperação.

## Refatoração e regressão

`Media3AudioEngine` recebe o Player por construtor nos testes; a fábrica de produção cria um ExoPlayer com foco automático desativado, wake mode, User-Agent e timeouts explícitos. Não há mais desvio por nome de aparelho, classe Robolectric, propriedade global ou flag estática. Os testes antigos provocam uma falha de inicialização explicitamente para cobrir o fallback. Os novos testes executam o adaptador e o serviço Media3 com eventos determinísticos, sem trocar o caminho por MediaPlayer.

O teste de integração cria o **ExoPlayer real**, gera um WAV PCM, prepara-o por caminho com espaços, verifica duração, play, seek e pausa. O Android/AudioTrack são simulados: esse teste não demonstra som físico no alto-falante.

## Execução final

`powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -LiveTests`

- Build: aprovado.
- Lint: **0 erros, 41 avisos**; sem desativar verificações. As APIs opt-in usadas do Media3 têm anotação explícita.
- JUnit: **65 testes, 0 falhas, 0 ignorados**.
- Regras independentes: **32 verificações aprovadas**.
- 18 novos cenários de regressão do serviço/adaptador executados nas APIs 26 e 35: reprodução baixada, READY repetido, pausa durante buffering, notificação, fallback pausado com posição, callbacks obsoletos, fonte content, caminhos com espaços e preservação de downloads completos.
- Teste do ExoPlayer real com WAV: aprovado. O Robolectric ainda registra ausência de `media_metrics`, sem impedir preparo, play, seek ou pausa; não foi necessário ocultar/desativar o motor para passar esse teste.
- Busca e resolução online reais: aprovadas. Download completo de 10.271.496 bytes aprovado e arquivo de teste removido.
- Assinatura APK v2: válida.

Entrega: `dist/Onda-0.7.1-debug.apk` (5.772.186 bytes), versionCode **8**, versionName **0.7.1**.

SHA-256: `67fada469d5c932ab797243154d8311a00d538be418a652c7a1b6e9424993039`.

## Confirmação no aparelho

Não havia dispositivo em `adb devices -l`, portanto não foi possível reproduzir o erro específico do celular ou obter seu logcat. Instale por cima da 0.7.0 e teste primeiro uma música já baixada em modo avião, depois um arquivo do celular e uma música online. Confirme qual motor aparece na tela (Media3 ou Compatibilidade).

Não desinstale nem limpe os dados: os downloads são privados do aplicativo. Arquivos apagados por versões anteriores não são recriados automaticamente por esta correção.

O suporte local permanece de uma faixa selecionada por vez; não foi implementada uma fila de álbum local nesta correção. Próxima não salta para uma fila YouTube sem relação ao terminar um arquivo do celular. Favoritos/letras de links não aceitam uma URL nula de arquivo local.
