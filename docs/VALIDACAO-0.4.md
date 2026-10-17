# Validação — Onda 0.4.0

## Resultado da execução final

Comando: `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -LiveTests`.

- Compilação `assembleDebug`: passou.
- `lintDebug`: zero erros, dez avisos. Há uso de APIs antigas do framework e avisos de recursos/manifesto; não foi usado baseline para esconder falhas.
- Testes JUnit: **39 executados, zero falhas, zero ignorados**.
- Verificações independentes de URLs/filtros/pesquisa/fila: **32 passaram**.
- `apksigner verify --verbose`: assinatura APK v2 válida.
- APK: `dist/Onda-0.4.0-debug.apk`, 1.877.415 bytes.
- SHA-256: `735c675e9a7fdc5707b548a9ef808d6e794ae26e6e09fba0e64446eb6d9fbac5`.

## O que foi testado

### Regras de reprodução

Avanço e fim da fila, repetição de uma faixa sem impedir pulo manual, repetição da fila, fila unitária/vazia, aleatório sem repetir itens antes do fim, mudança de ordem/removidos, restauração da permutação, snapshot corrompido, exclusão de URLs temporárias da sessão, preferência local e posição fora dos limites.

### Serviço Android simulado

Oito cenários executados em cada uma das APIs **26 e 35**, via Robolectric:

1. Retirar listeners da interface não interrompe áudio; desconexão de fones pausa.
2. Fim de faixa avança e fim de fila encerra a reprodução.
3. Recriação restaura posição/modos sem autoplay.
4. Callback de preparo atrasado após Parar não reinicia a faixa.
5. Favoritos, playlist e arquivos baixados permanecem após recriar o serviço.
6. Perda de foco pausa; foco negado impede reprodução; foco concedido permite retomada explícita.
7. Notificação contém token MediaSession e ações anterior/pausar/próxima.
8. Temporizador pausa sem avançar ou apagar a fila.

Os arquivos usados nessa simulação representam metadados locais; o MediaPlayer é simulado. Não são testes de codec, alto-falante, Bluetooth físico, rádio Wi-Fi ou tela de bloqueio real.

### Download e rede

Testes locais cobrem conclusão atômica, cancelamento com remoção de parcial, HTML inválido, resposta incompleta, preservação de arquivo existente e cabeçalho de contêiner.

Testes reais do extrator consultaram um vídeo público de teste, obtiveram áudio M4A, receberam bytes de mídia e baixaram o arquivo inteiro de **10.271.496 bytes**. O arquivo foi removido após o teste. Isso confirma o caminho de obtenção/download nesse ambiente, não a disponibilidade de todo vídeo ou rede.

## Ainda pendente em aparelho

`adb devices -l` não mostrou dispositivo. A consulta ao processador indicou virtualização de firmware desabilitada, portanto não foi instalado um emulador acelerado.

Pendentes: instalar por cima da 0.3; ouvir com tela realmente bloqueada; usar botões Bluetooth; receber chamada; trocar entre Wi-Fi/dados/modo avião; testar autonomia e restrições de bateria do fabricante; ouvir áudio online/offline no MediaPlayer real; verificar fontes grandes, leitor de tela, rotação e tamanhos de tela. Não foi feita publicação em loja.

## Como verificar no celular

1. Instale `Onda-0.4.0-debug.apk` por cima da versão anterior.
2. Abra um vídeo e toque **Ouvir com tela apagada**; aguarde iniciar e bloqueie a tela pelo botão físico.
3. Confira pausa/anterior/próxima na notificação; volte ao app sem reiniciar a faixa.
4. Repita com música baixada em modo avião.
5. Monte fila de duas faixas, verifique avanço automático, repetição/aleatório e erro recuperável se uma faixa online estiver indisponível.
6. Troque de app, conecte/desconecte fones, receba chamada e teste o temporizador.
7. Pare o app e abra de novo: a sessão deve estar pausada, com seleção e posição restauradas.
