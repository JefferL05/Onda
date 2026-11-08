# Validação 1.0.1 — correção do crash na inicialização

## O sintoma

Depois de instalar a 1.0.0, o app abria e fechava sozinho antes de mostrar
qualquer tela.

## Causa 1 — o parser de ícones (confirmada em execução)

Todo ícone do Onda é desenhado a partir de path data, e a barra de abas é
montada no `onCreate` da `MainActivity`. Ou seja: o parser de path data rodava
na primeira coisa que o app fazia.

O parser antigo fazia duas suposições que o próprio conjunto de ícones viola:

1. Exigia espaço entre cada par de números. Mas `M12 4v10.4M7.4 10.2 12
   14.8l4.6-4.6` tem `4.6-4.6`: dois números colados, separados só pelo sinal.
   O tokenizador lia `"4.6-4.6"` como um número só e `Float.parseFloat`
   lançava `NumberFormatException`.
2. Assumia que cada comando aparecia com um único conjunto de parâmetros. Mas
   `a6.6 6.6 0 1 1 0 13.2 6.6 6.6 0 0 1 0-13.2` repete o conjunto de arco dentro
   do mesmo comando. Os índices estouravam o fim da string e a leitura seguinte
   pegava string vazia.

Confirmado copiando o parser original para uma classe Java sem dependências
Android e executando contra os ícones reais:

```
ok    HOME
CRASH SPARK    -> NumberFormatException: For input string: "-1.8-5.4"
CRASH SEARCH   -> NumberFormatException: empty String
ok    LIBRARY
CRASH HEART    -> NumberFormatException: empty String
CRASH SETTINGS -> NumberFormatException: empty String
```

`SPARK` é o ícone da aba Explorar e `SEARCH` o da aba Buscar — os dois são
criados no `onCreate`, antes do primeiro frame.

## Causa 2 — `LibraryStore.shared(this)` em inicializador de campo

`MainActivity` declarava `private final LibraryStore store = LibraryStore.shared(this);`.
Inicializador de campo roda dentro do construtor da Activity, antes de
`attachBaseContext` ser chamado. `LibraryStore` usa
`context.getApplicationContext()`, que nesse momento não tem para onde apontar.
Passou a ser atribuído no início do `onCreate`, depois do `super`.

## O que mudou

`PathData` é uma classe nova, Java puro, sem tipos Android: tokenizador de
verdade (número pode ser separado por sinal ou ponto, aceita notação
científica), repetição implícita de parâmetros, `l` implícito depois de `M`,
reflexão de ponto de controle para `S` e `T`, e `A` como arco elíptico real
(conversão endpoint→centro da especificação SVG, respeitando `large-arc` e
`sweep`) em vez da aproximação por um único círculo. Dados malformados param a
leitura em vez de lançar: um ícone ruim nunca mais derruba o processo.

`Glyph.path` agora delega para `PathData` e guarda cache dos `Path` montados —
antes cada linha de lista reparseava o mesmo ícone.

## Verificação executada

- Build completo do app contra `android.jar` API 35, sem erros nem avisos.
- **56 testes JVM verdes** (eram 44), incluindo o novo `PathDataTest` com 12
  casos: os dois padrões exatos que causavam o crash, todos os 24 ícones do app
  parseando com geometria e um único subpath, comandos relativos, `Z`
  restaurando o ponto inicial, flag de arco grande gerando mais segmentos que a
  pequena, `A` com raio 0 virando linha, `S` refletindo o controle anterior,
  notação científica, e 20 entradas malformadas que não podem lançar nem travar.
- APK `dist/Onda-1.0.1-debug.apk`: alinhado, assinado com a mesma chave de
  depuração (v2 + v3 verificadas), manifesto em `versionName=1.0.1` /
  `versionCode=13`, `classes3.dex` com as 333 classes do app incluindo
  `PathData` e `PathData$Sink`.

## O que não foi validado

Não há emulador neste ambiente, então a confirmação é de compilação,
execução do parser em JVM e conteúdo do APK — não de tela aberta em aparelho.
Instalar por cima da 1.0.0 mantém os dados (mesmo pacote, mesma chave, chaves
de SharedPreferences inalteradas).
