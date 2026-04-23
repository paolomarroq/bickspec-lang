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

Java translation is intentionally not included yet.

## Phase II Commit 2/3 scope

Phase II Commit 2/3 adds the initial semantic analysis stage with the ANTLR visitor pattern.

After a file parses successfully, `com.bickspec.app.ParseRunner` now traverses the parse tree with `BickSpecSemanticVisitor` and prints a deterministic semantic visit trace. The trace logs important grammar constructs such as project blocks, imports, exchange rates, assignments, display/read statements, control flow, function declarations, function calls, money literals, and time literals.

Each trace entry shows:

- The parse tree rule or semantic construct being visited.
- The relevant source fragment or identifier.
- The semantic or translation action that would be performed in Phase III.

## Phase II Commit 3/3 scope

Phase II Commit 3/3 completes the Phase II delivery with syntax-directed translation to Java.

This commit adds:

- `com.bickspec.app.TranspileRunner` for Java generation from valid `.bks` files.
- `BickSpecJavaTranslatorVisitor`, an ANTLR visitor that emits readable Java source.
- Generated Java output under `testing/generated/`.
- PowerShell test script alignment with the Phase II parser entry point.

The generated Java is a classroom/demo translation. It reflects the source program structure and includes TODO comments for BickSpec-specific runtime behavior such as currency conversion, time conversion, partial import mapping, and financial built-ins.

## Build in IntelliJ (no `mvn` required)

1. Open the project in IntelliJ IDEA.
2. Open **Maven Tool Window**.
3. Under `app` -> **Lifecycle**, run `package`.

Example jar produced:

`app/target/bickspec-lexer-runner-1.0.0.jar`

The jar keeps `LexerRunner` as its default main class for Phase I compatibility. Phase II tools are invoked by class name with `java -cp`.

Use UTF-8 for `.bks` source files, generated `.java` files, and IDE/project encoding settings. The runners read `.bks` files as UTF-8, and `TranspileRunner` writes generated `.java` files as UTF-8. On Windows consoles, run `chcp 65001` when you need UTF-8 console output.

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

Expected output includes a file header, `PARSE OK`, the ANTLR parse tree, and a `Semantic visit trace:` section.

For valid files, `ParseRunner` also writes a graphical parse tree under `testing/trees/`.

## Run parser validation on all tests

```bash
java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.ParseRunner testing
```

When a directory is provided, the parser runner processes all `*.bks` files in ascending filename order. `P1` through `P6` are expected to parse successfully, `P7_FalloLexico.bks` is expected to fail lexically, and `P8_FalloSintaxis.bks` is expected to fail syntactically.

## Graphical parse trees

`ParseRunner` and `TranspileRunner` generate one parse tree graph per valid `.bks` file.

Outputs are saved under `testing/trees/`:

- `P1_HolaMundo_ParseTree.dot`
- `P1_HolaMundo_ParseTree.svg` when Graphviz is available

DOT files are always generated. SVG generation is attempted with the `dot` command from Graphviz. If Graphviz is not installed or not on `PATH`, the runner still writes the `.dot` file and prints a skip message.

Graphviz is recommended for classroom demos because it turns DOT files into visual diagrams. Install it from `https://graphviz.org/download/`, then make sure `dot` is available in your terminal:

```bash
dot -V
```

## Generate Java from one test file

```bash
java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.TranspileRunner testing/P1_HolaMundo.bks
```

For valid files, `TranspileRunner` prints the parse result, parse tree, semantic visit trace, parse tree graph paths, and generated Java path.

Example generated file:

`testing/generated/P1_HolaMundo_Generated.java`

## Generate Java from all tests

```bash
java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.TranspileRunner testing
```

Valid files generate Java under `testing/generated/`. Invalid files still report lexical or syntax failures and do not generate Java.

Generated Java files always include:

- `import java.util.Scanner;`
- `private static final Scanner INPUT = new Scanner(System.in);`
- `readNumber(String prompt)`
- `convert(double value, String unit)`
- `formatCurrency(double value, String currency)`

Input prompts are centralized through `readNumber`, so a BickSpec prompt followed by `READ CASH` becomes Java like `double CASH = readNumber("CASH inicial USD");`.

Dimensional metadata is preserved as quoted text comments or conversion arguments, for example `double CAPEX = 500000; // "GTQ"` and `convert(MESES, "month")`. Generated TODO comments mark runtime behavior that remains pending.

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

`testing/outputs/phase2_parse_results.txt`
