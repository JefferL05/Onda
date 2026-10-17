# Validação — Onda 0.6.0

- Letras locais/editáveis por faixa, apenas para texto fornecido pelo usuário ou licenciado.
- Modo offline automático opcional para favoritos e fila em Wi-Fi.
- Cache de capas em memória/disco privado e busca em worker fora da UI.
- Fila de downloads persistida, deduplicada, com progresso, cancelamento e retentativas.

Build/lint e a suíte JUnit devem ser executados com `powershell -ExecutionPolicy Bypass -File .\build.ps1 -LiveTests`. O teste físico de tela bloqueada, Bluetooth, chamadas e modo avião continua recomendado.

Letras não são raspadas nem fornecidas automaticamente. O modo offline automático não usa dados móveis quando somente Wi-Fi está habilitado e pode sofrer restrições de bateria do fabricante.
