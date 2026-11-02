# Diretrizes do Projeto Onda

## Fluxo de Trabalho e Git Automático
- Ao concluir qualquer solicitação do usuário que envolva modificação de código, adição de recursos, correções ou geração de nova versão do APK:
  1. Execute a validação necessária (testes e build via `.\build.ps1` ou `gradlew`).
  2. Faça o stage dos arquivos alterados (`git add .`, respeitando o `.gitignore`).
  3. Crie o commit com uma mensagem descritiva e clara em português.
  4. Envie as alterações diretamente para o GitHub (`git push origin main`).
  5. Apresente no relatório final o hash do commit gerado e a confirmação de envio ao repositório remoto.
