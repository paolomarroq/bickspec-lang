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
  - `testing/P9_FalloLexico.bks`
  - `testing/P10_FalloSintaxis.bks`

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

Expected output includes a file header, `[STATUS] PARSE OK`, `[STATUS] SEMANTIC OK`, a symbol CSV path, and a parse tree graph path.

For valid files, `ParseRunner` also writes graphical parse tree files under `testing/trees/`.

## Run parser validation on all tests

```bash
java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.ParseRunner testing
```

When a directory is provided, the parser runner processes all `*.bks` files in ascending test-number order. `P1` through `P8` and `P12` are expected to pass parse and semantic validation, `P9_FalloLexico.bks` is expected to fail lexically, `P10_FalloSintaxis.bks` is expected to fail syntactically, and `P11_FalloSemantico.bks` is expected to fail semantically.

## Graphical parse trees

`ParseRunner` and `TranspileRunner` generate parse tree graph artifacts for every valid `.bks` file.

Outputs are saved under `testing/trees/`:

- `testing/trees/P1_HolaMundo_ParseTree.dot`
- `testing/trees/P1_HolaMundo_ParseTree.svg`

The `.dot` file is the source representation. After writing it, the runners invoke Graphviz with `dot -Tsvg <input.dot> -o <output.svg>` to render the `.svg` visualization automatically.

Graphviz must be installed and available in `PATH` for SVG rendering. If the `dot` command fails, the runner keeps the `.dot` file, does not fail the parse, and prints a warning.

Install Graphviz from `https://graphviz.org/download/`, then make sure `dot` is available in your terminal:

```bash
dot -V
```

## Generate Java from one test file

```bash
java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.TranspileRunner testing/P1_HolaMundo.bks
```

For valid files, `TranspileRunner` prints parse status, semantic status, symbol CSV path, parse tree graph path, generated Java path, and a completion action.

Example generated file:

`testing/generated/P1_HolaMundo_Generated.java`

## Generate Java from all tests

```bash
java -cp app/target/bickspec-lexer-runner-1.0.0.jar com.bickspec.app.TranspileRunner testing
```

Valid files generate Java under `testing/generated/`. Invalid files report lexical, syntax, or semantic failures and do not generate Java.

Generated Java files always include:

- `import java.util.Scanner;`
- `private static final Scanner INPUT = new Scanner(System.in);`
- `readNumber(String prompt)`
- `convert(double value, String unit)`
- `toUsd(double amount, String currency)`
- `fromUsd(double amount, String currency)`
- `formatMoney(double value, String currency)`
- `NPV(double rate, double capex, double... cashflows)`
- `PAYBACK(double capex, double... cashflows)`

Input prompts are centralized through `readNumber`, so a BickSpec prompt followed by `READ CASH` becomes Java like `double CASH = readNumber("CASH inicial USD");`.

Money is stored internally as USD. A literal such as `500000 GTQ` is emitted as `toUsd(500000, "GTQ")`, using `FX GTQ := ...` where the BickSpec source provides it. The expression form `DISPLAY value in GTQ` affects presentation only and emits `formatMoney(fromUsd(value, "GTQ"), "GTQ")`; it does not alter the stored USD value.

Dimensional metadata for time is preserved as quoted text comments or helper arguments, for example `double T1 = 1; // "month"` and `convert(MESES, "month")`.

## Phase III Commit 1/3 scope

Phase III adds the real semantic-analysis foundation required before Java generation:

- A symbol table for identifiers, declared type, scope, declaration line, initialization state, and notes.
- Semantic checks for undeclared variables, duplicate declarations in the same scope, simple type mismatches, and undeclared function calls.
- Standard compiler diagnostic codes for lexical, syntax, semantic, and generation errors.
- Symbol-table CSV export under `testing/symbols/`.
- A semantic gate that blocks both symbol CSV export and Java generation when validation fails.

Console errors now use this format:

```text
[ERROR] SEM01 - Variable 'X' used before declaration at line 2:11
```

Implemented error-code categories:

- `LEX01`: lexical recognition error
- `SYN01`: parser syntax error
- `SEM01`: variable used before declaration or initialization
- `SEM02`: duplicate declaration in the same scope
- `SEM03`: simple type mismatch
- `SEM04`: undeclared function call
- `GEN01`, `GEN02`, `GEN03`: generation or output infrastructure errors

Successful semantic validation writes an IntelliJ-friendly comma-separated CSV:

```text
name,type,scope,declared_at,initialized,notes
R,number,global,5,true,read input
CAPEX,number,global,8,true,unit USD
```

Example path:

`testing/symbols/P3_Input_If_symbols.csv`

Semantic failures do not generate CSV or Java:

```text
==== testing/P11_FalloSemantico.bks ====
[STATUS] PARSE OK
[STATUS] SEMANTIC FAILED
[ERROR] SEM01 - Variable 'X' used before declaration at line 2:11
[SYMBOLS] not generated
[JAVA] not generated
```

### Test numbering policy

The Phase II test names and numbering are intentionally preserved for continuity:

`P1_HolaMundo.bks` through `P10_FalloSintaxis.bks`.

Phase III adds the required cases as new tests instead of renumbering older files:

- `P11_FalloSemantico.bks`: semantic-negative test.
- `P12_Recursividad.bks`: recursion/function lookup test.

## Phase III Commit 2/3 scope

Phase III Commit 2/3 completes the Java generation layer while preserving the semantic gate from Commit 1/3.

The generator now translates:

- Variables and assignments to Java `double` locals.
- Arithmetic and logical expressions.
- `IF`/`ELSE`, `WHILE`, and `REPEAT ... TIMES`.
- Function declarations and function calls, including recursive function declarations.
- Batch assignments as explicit Java assignments with shared money/time metadata.
- BickSpec imports as generated Java runtime module markers.
- Official built-ins `NPV(...)` and `PAYBACK(...)` as real Java helper methods.

Generated Java files include a runtime header documenting the built-in model:

- USD is the internal representation of money.
- GTQ and EUR values are converted using FX rates.
- `expr in GTQ` and `expr in EUR` affect display only.
- `NPV()` and `PAYBACK()` are BickSpec built-ins implemented as Java helpers.

Example successful generation output:

```text
==== testing/P6_Imports_NPV.bks ====
[STATUS] PARSE OK
[STATUS] SEMANTIC OK
[SYMBOLS] testing/symbols/P6_Imports_NPV_symbols.csv
[TREE] testing/trees/P6_Imports_NPV_ParseTree.svg
[JAVA] testing/generated/P6_Imports_NPV_Generated.java
[ACTION] Java generation completed successfully
```

Example generated code:

```java
double CAPEX = toUsd(500000, "GTQ"); // "GTQ"
double NPV_VAL = NPV(R, CAPEX, CF1, CF2, CF3); // BickSpec built-in
System.out.println(formatMoney(fromUsd(NPV_VAL, "GTQ"), "GTQ"));
```

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

`testing/outputs/phase3_parse_results.txt`
