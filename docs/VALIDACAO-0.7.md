# Validação — Onda 0.7.0

**Relatório histórico, substituído por [0.7.1](VALIDACAO-0.7.1.md).** Os testes de serviço da 0.7.0 forçavam o MediaPlayer e não demonstravam que o Media3 funcionava. Foram identificadas regressões de foco, preparação e manutenção do serviço após esta entrega.

## Migração Media3

O player agora tenta AndroidX Media3/ExoPlayer primeiro e mantém o MediaPlayer como fallback quando o backend não consegue preparar o arquivo. A fila, a MediaSession, downloads, favoritos e preferências não mudam de formato.

## Biblioteca local

Onda pode listar áudio do armazenamento Android por MediaStore, agrupando informação de título, artista, álbum, duração, tamanho e capa. O áudio é aberto por `content://`, sem copiar arquivos para a biblioteca do Onda. O usuário precisa conceder a permissão de áudio do Android; a listagem é somente leitura.

## Letras e offline

Letras são armazenadas por ID da faixa no armazenamento privado apenas quando fornecidas pelo usuário/licenciadas. O modo offline automático é opcional e enfileira favoritos/fila quando há Wi-Fi.

## Verificação

- Build, lint e assinatura APK executados.
- Suíte local de testes executada sem falhas.
- Testes reais de rede continuam disponíveis com `build.ps1 -LiveTests`.
- Teste físico de codecs, tela bloqueada, Bluetooth, chamada e permissionamento ainda deve ser repetido no aparelho após instalar o APK.
