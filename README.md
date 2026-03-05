# bickspec-lang
BickSpec is a finance-oriented structured programming language designed for economic engineering modeling, built with Java and ANTLR4 as part of a Compiler Design project.

## Phase I scope

Phase I implements **lexical analysis only**.

The CLI lexer runner prints one token per line using:

`TOKEN_NAME<TAB>'LEXEME'`

EOF is skipped.

## Build in IntelliJ (no `mvn` required)

1. Open the project in IntelliJ IDEA.
2. Open **Maven Tool Window**.
3. Under `app` -> **Lifecycle**, run `package`.

Example jar produced:

`app/target/bickspec-lexer-runner-1.0.0.jar`

## Run lexer on one test file

```bash
java -jar app/target/bickspec-lexer-runner-1.0.0.jar testing/P1_HolaMundo.bks
```

## Run lexer on all tests (directory mode)

```bash
java -jar app/target/bickspec-lexer-runner-1.0.0.jar testing
```

When a directory is provided, the runner processes all `*.bks` files in filename order and prints file headers like:

`==== testing/P1_HolaMundo.bks ====`

## Run all tests with script (Windows PowerShell)

Run and print to console:

```powershell
.\app\scripts\run_tests.ps1
```

Run and also save output:

```powershell
.\app\scripts\run_tests.ps1 -SaveOutput
```

Default output file:

`testing/outputs/phase1_tokens.txt`
