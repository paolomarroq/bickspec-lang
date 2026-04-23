# bickspec-lang
BickSpec is a finance-oriented structured programming language designed for economic engineering modeling, built with Java and ANTLR4 as part of a Compiler Design project.

## Phase I scope

Phase I implements **lexical analysis only**.

The CLI lexer runner prints one token per line using:

`TOKEN_NAME<TAB>'LEXEME'`

EOF is skipped.

## Phase II Commit 1/3 scope

Phase II Commit 1/3 adds ANTLR parser validation while preserving the Phase I lexer runner.

This commit adds:

- Parser generation from `docs/BickSpec.g4`.
- `com.bickspec.app.ParseRunner` for validating one `.bks` file or every `.bks` file in a directory.
- Clear lexical and syntax error reporting through custom ANTLR error listeners.
- Negative parser tests:
  - `testing/P7_FalloLexico.bks`
  - `testing/P8_FalloSintaxis.bks`

Semantic visitor logic and Java translation are intentionally not included yet.

## Build in IntelliJ (no `mvn` required)

1. Open the project in IntelliJ IDEA.
2. Open **Maven Tool Window**.
3. Under `app` -> **Lifecycle**, run `package`.

Example jar produced:

`app/target/bickspec-lexer-runner-1.0.0.jar`

The jar keeps `LexerRunner` as its default main class for Phase I compatibility. The parser runner is invoked by class name.

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

## Run parser validation on one test file

```bash
java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.ParseRunner testing/P1_HolaMundo.bks
```

Expected output includes a file header, `PARSE OK`, and the ANTLR parse tree.

## Run parser validation on all tests

```bash
java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.ParseRunner testing
```

When a directory is provided, the parser runner processes all `*.bks` files in ascending filename order. `P1` through `P6` are expected to parse successfully, `P7_FalloLexico.bks` is expected to fail lexically, and `P8_FalloSintaxis.bks` is expected to fail syntactically.

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
