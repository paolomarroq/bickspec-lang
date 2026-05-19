# BickSpec

BickSpec is a finance-oriented domain-specific language (DSL) for economic engineering models. It is designed to express financial calculations, cash-flow logic, currency-aware values, simple time units, and decision rules in a readable syntax that is easier to review than general-purpose Java code.

The project implements an ANTLR4-based compiler/transpiler that reads `.bks` source files, validates them, generates Java, compiles the generated Java, and executes the resulting program from the command line. Its main domain is finance and economic engineering: project valuation, investment metrics, runway calculations, payback analysis, and structured financial simulations.

BickSpec is intended for:

- students and reviewers studying compiler construction through a complete DSL pipeline;
- finance/economic engineering users who need readable calculation scripts;
- developers who want a compact ANTLR-to-Java compiler example with diagnostics and generated artifacts.

As a product, BickSpec provides a readable financial DSL, a Java/ANTLR compiler architecture, a Java target runtime, command-line execution, generated parse-tree visualizations, symbol tables, diagnostics, and organized build outputs.

The complete offline documentation site is available at:

```text
docs/site/bickspec_documentation.html
```

The VS Code extension can open that documentation through **BickSpec: Open Documentation** and, when enabled, opens its bundled local copy once on first activation. The extension now bundles the compiler JAR for final users, while developers can still build the same compiler from source in `bickspec-lang`.

## Key Language Features

- Assignments with `:=`
- Numeric expressions with arithmetic and comparisons
- Money literals with `USD`, `GTQ`, and `EUR`
- Currency conversion syntax with `to` and `in`
- Time literals with `year`, `month`, `quarter`, `week`, and `day`
- Control flow with `IF`, `WHILE`, and `REPEAT`
- User-defined expression functions with `FUNCTION`
- Module declarations with `IMPORT`
- Exchange-rate declarations with `FX`
- Built-in financial functions: `NPV()` and `PAYBACK()`
- Console input with `READ`
- Console output with `DISPLAY` or `WRITE`
- Batch assignments such as `CF1, CF2, CF3 := V1, V2, V3 USD`

## Project Evolution by Phase

### Phase I - Lexical Analysis

Objective: define the initial language surface and validate token recognition.

Implemented:

- BickSpec language specification;
- base ANTLR4 grammar;
- functional lexer;
- token and regular-expression definitions;
- six initial test cases.

Executable behavior: Phase I lists tokens and lexemes for one `.bks` file or a directory of `.bks` files. It does not parse, validate syntax, build symbol tables, generate Java, or execute programs.

Difference from Phase II: Phase II adds parser validation, syntax diagnostics, parse trees, semantic visitor traversal, and initial Java translation behavior.

### Phase II - Parser, Trace, and Initial Translation

Objective: validate full syntax and demonstrate syntax-directed compiler stages after lexical analysis.

Implemented:

- ANTLR parser generation from `docs/BickSpec.g4`;
- syntax validation for source files and directories;
- custom lexical and syntax diagnostics;
- initial semantic visitor traversal;
- DOT/SVG parse-tree generation;
- initial Java generation path;
- ten documented tests.

Executable behavior: the Phase II-compatible runner parses files, validates syntax, runs the semantic visitor, prints a semantic visit trace, exports parse-tree artifacts, and reports diagnostics. In the current project state, the same semantic engine used by Phase III is available, so semantic failures are also reported.

Difference from Phase III: Phase II stops before the full final pipeline. It does not use the jar main flow to generate Java, compile generated Java, execute the resulting class, and write the final summary report.

### Phase III - Final Compiler Pipeline

Objective: complete the compiler as an executable product pipeline.

Implemented:

- real symbol table construction;
- semantic validation;
- structured error codes;
- automatic Java generation under `output/java`;
- generated Java build under `output/classes`;
- execution of the generated program;
- professional output organization under `output/`;
- final P1-P12 test suite;
- executable compiler jar: `bickspec-compiler-1.0.0.jar`.

Executable behavior: Phase III is the final compiler pipeline: parse, semantic validation, symbol table CSV export, parse-tree artifact generation, Java generation, Java compilation, program execution, and summary reporting.

## Requirements

- Java JDK 17 or newer for compiler usage.
- Maven 3.x only when building the compiler from source.
- Graphviz is optional. If `dot` is available in `PATH`, SVG parse trees are generated automatically. If Graphviz is missing, DOT files are still generated and compilation continues.
- Final jar artifact: `bickspec-compiler-1.0.0.jar`.
- Default jar main class: `com.bickspec.app.TranspileRunner`.

Build the compiler jar from the repository root:

```bash
mvn -f app/pom.xml package
```

The jar is created at:

```text
app/target/bickspec-compiler-1.0.0.jar
```

## VS Code Extension

Final VS Code users only need:

1. VS Code
2. Java installed
3. BickSpec VS Code extension installed

The extension bundles `bickspec-compiler-1.0.0.jar` and uses that bundled JAR by default. Normal extension usage does not require cloning `bickspec-lang`, installing Git, installing Maven, or manually locating `app/target`.

Developers can still build the compiler from source with:

```bash
mvn -f app/pom.xml package
```

The bundled extension copy lives at:

```text
vscode-extension/media/compiler/bickspec-compiler-1.0.0.jar
```

## How to Run Phase I

Phase I corresponds to lexical analysis and token/lexeme listing.

Run Phase I on one file:

```bash
java -cp app/target/bickspec-compiler-1.0.0.jar com.bickspec.app.LexerRunner testing/P1_HolaMundo.bks
```

Run Phase I on a directory:

```bash
java -cp app/target/bickspec-compiler-1.0.0.jar com.bickspec.app.LexerRunner testing
```

Output format:

```text
TOKEN_NAME    'LEXEME'
```

The lexer runner skips EOF and prints each recognized token in source order. Directory mode processes `.bks` files in deterministic filename order.

## How to Run Phase II

Phase II corresponds to parser validation, syntax validation, semantic visitor traversal, and parse-tree visualization.

Run Phase II on one file:

```bash
java -cp app/target/bickspec-compiler-1.0.0.jar com.bickspec.app.ParseRunner testing/P1_HolaMundo.bks
```

Run Phase II on a directory:

```bash
java -cp app/target/bickspec-compiler-1.0.0.jar com.bickspec.app.ParseRunner testing
```

For valid files, `ParseRunner` reports:

- `[STATUS] PARSE OK`
- `[STATUS] SEMANTIC OK`
- semantic visit trace
- symbol table CSV path
- parse-tree DOT/SVG path

For invalid files, it reports lexical, syntax, or semantic diagnostics and does not generate Java.

`ParseRunner` is useful when reviewing the compiler front end independently from the final Phase III build-and-execute pipeline.

## How to Run Phase III

Phase III is the final compiler pipeline.

Run the full pipeline on one file:

```bash
java -jar app/target/bickspec-compiler-1.0.0.jar testing/P1_HolaMundo.bks
```

Run the full pipeline on a directory:

```bash
java -jar app/target/bickspec-compiler-1.0.0.jar testing
```

For each valid source file, the compiler performs:

1. lexical and syntax validation;
2. semantic validation;
3. symbol table CSV export;
4. DOT/SVG parse-tree generation;
5. Java source generation;
6. generated Java compilation with `javac`;
7. execution of the generated Java class.

When a directory contains expected negative tests, such as `testing/P9_FalloLexico.bks`, `testing/P10_FalloSintaxis.bks`, and `testing/P11_FalloSemantico.bks`, diagnostics are printed and the process exits with a non-zero status after processing the suite.

Windows PowerShell helper scripts are also available:

```powershell
.\app\scripts\compile.ps1
.\app\scripts\run_tests.ps1
.\app\scripts\run_tests.ps1 -SaveOutput
```

## Executing Outside the Project

You can run BickSpec outside the repository with only the final jar and a JDK. The generated `output/` directory is created relative to the terminal's current working directory.

Run one `.bks` file:

```bash
java -jar bickspec-compiler-1.0.0.jar path/to/program.bks
```

Run every `.bks` file in a directory:

```bash
java -jar bickspec-compiler-1.0.0.jar path/to/programs
```

Run Phase I-style lexical listing outside the project:

```bash
java -cp bickspec-compiler-1.0.0.jar com.bickspec.app.LexerRunner path/to/program.bks
```

Run Phase II-style parser validation outside the project:

```bash
java -cp bickspec-compiler-1.0.0.jar com.bickspec.app.ParseRunner path/to/program.bks
```

Requirements outside the project:

- `java` must be available in `PATH`;
- `javac` must be available in `PATH` because Phase III compiles generated Java;
- Graphviz `dot` is optional for SVG parse trees;
- no local repository paths are required.

## Output Structure

The compiler writes generated artifacts under `output/` relative to the current working directory.

```text
output/
  java/       Generated Java source files.
  classes/    Compiled `.class` files from generated Java.
  trees/      Parse-tree `.dot` files and optional `.svg` files.
  symbols/    Symbol table CSV files.
  reports/    Directory-mode summary reports and saved script output.
```

Important files include:

- `output/java/<Program>_Generated.java`
- `output/classes/<Program>_Generated.class`
- `output/trees/<Program>_ParseTree.dot`
- `output/trees/<Program>_ParseTree.svg`
- `output/symbols/<Program>_symbols.csv`
- `output/reports/generation_summary.csv`

## Test Suite

The final documented suite is `testing/P1` through `testing/P12`.

| Test | File | Type | Coverage |
| --- | --- | --- | --- |
| P1 | `P1_HolaMundo.bks` | Positive | Basic project and output |
| P2 | `P2_Aritmetica.bks` | Positive | Arithmetic expressions |
| P3 | `P3_Input_If.bks` | Positive, interactive | `READ`, `IF`, FX, currency conversion |
| P4 | `P4_Funcion.bks` | Positive | User-defined function |
| P5 | `P5_While_Runway.bks` | Positive, interactive | `WHILE`, time units, runway calculation |
| P6 | `P6_Imports_NPV.bks` | Positive | Imports, FX, `NPV()`, finance case |
| P7 | `P7_BatchMoney.bks` | Positive | Batch assignment with money units |
| P8 | `P8_BatchTime.bks` | Positive | Batch assignment with time units |
| P9 | `P9_FalloLexico.bks` | Negative | Lexical error |
| P10 | `P10_FalloSintaxis.bks` | Negative | Syntax error |
| P11 | `P11_FalloSemantico.bks` | Negative | Semantic error |
| P12 | `P12_Recursividad.bks` | Positive | Recursive function declaration/translation case |

Positive tests are expected to parse, pass semantic validation, generate Java, compile, and run. Negative tests are expected to report diagnostics and skip Java generation.

## Error Codes

BickSpec diagnostics use short code families:

- `LEX`: lexical errors, such as an unrecognized character.
- `SYN`: syntax errors, such as a missing `THEN` in an `IF` statement.
- `SEM`: semantic errors, such as using a variable before declaration or calling an undeclared function.
- `GEN`: generation, file, Graphviz, Java compilation, or execution errors.

Representative examples:

```text
[ERROR] LEX01 - Token recognition error
[ERROR] SYN01 - Mismatched input
[ERROR] SEM01 - Variable 'X' used before declaration
[ERROR] SEM02 - Duplicate declaration
[ERROR] SEM03 - Invalid expression type for a numeric context
[ERROR] SEM04 - Function is not declared
[ERROR] GEN03 - Failed to write generated Java file
[ERROR] GEN06 - Generated Java compilation failed
```

## Interactive Programs

Programs that use `READ` must be run individually when real user input is required. They read directly from the terminal.

Examples:

```bash
java -jar app/target/bickspec-compiler-1.0.0.jar testing/P3_Input_If.bks
java -jar app/target/bickspec-compiler-1.0.0.jar testing/P5_While_Runway.bks
```

Non-interactive programs may show captured output inside a boxed `PROGRAM OUTPUT` section. Interactive programs inherit terminal input/output directly so prompts and user input are not broken by boxed output formatting.

## Repository Structure

```text
bickspec-lang/
  README.md
  app/
    pom.xml
    scripts/
      compile.ps1
      run_tests.ps1
    src/main/java/com/bickspec/app/
      LexerRunner.java
      ParseRunner.java
      TranspileRunner.java
      BickSpecParseSupport.java
      BickSpecSemanticVisitor.java
      BickSpecJavaTranslatorVisitor.java
      SymbolTable.java
      SymbolInfo.java
      SymbolTableCsvExporter.java
      ParseTreeGraphGenerator.java
      CompilerDiagnostic.java
  docs/
    BickSpec.g4
    BickSpec_Spec_ANTLR_FaseI.pdf
    BickSpec_Spec_ANTLR_FaseII.pdf
    BickSpec_Spec_ANTLR_FaseIII.pdf
    PHASE_III_SEMANTICS.md
    site/
      bickspec_documentation.html
      assets/
      screenshots/
      README.md
  input/
    README.md
  testing/
    P1_HolaMundo.bks
    ...
    P12_Recursividad.bks
    expected/
    generated/
    symbols/
    trees/
  output/
    java/
    classes/
    trees/
    symbols/
    reports/
  vscode-extension/
    media/docs/
      bickspec_documentation.html
```

## Final Product Status

BickSpec is complete through Phase III as a command-line compiler/transpiler. The current product can tokenize, parse, semantically validate, generate Java, compile generated Java, execute valid programs, export symbol tables, generate parse-tree artifacts, and report structured diagnostics.

The repository also includes a VS Code plugin frontend for the compiler. Its documentation lives in [vscode-extension/README.md](vscode-extension/README.md).

Future or optional work could include:

- an IDE or editor extension;
- a plugin system for domain libraries;
- a stronger static type system for units, currencies, and function signatures;
- richer module linking for `IMPORT`;
- expanded financial runtime functions;
- more formal optimization and intermediate representation stages;
- packaged installers or CI release artifacts.
