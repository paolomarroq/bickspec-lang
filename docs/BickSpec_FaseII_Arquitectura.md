# BickSpec Phase II Architecture

## 1. Phase II Objective

Phase II turns the Phase I lexer into a complete parser-oriented compiler stage for BickSpec. The objective is to prove that source programs can be tokenized, parsed with the ANTLR4 grammar, inspected through a semantic visitor, translated initially to Java, and exported as parse tree diagrams.

Phase II currently includes:

- ANTLR4 grammar in `docs/BickSpec.g4`.
- Lexer and parser generation through Maven/ANTLR.
- Parser validation for valid and invalid `.bks` files.
- Deterministic semantic visit tracing.
- Initial syntax-directed translation to Java.
- DOT and SVG parse tree exports for valid programs.

Phase II intentionally does not complete the full compiler runtime. A full symbol table, advanced semantic validation, complete currency/time/financial runtime behavior, and more complete target-code generation are Phase III responsibilities.

## 2. Complete Processing Pipeline

The complete Phase II pipeline is:

```text
.bks source file
  -> ANTLR CharStream
  -> BickSpecLexer
  -> CommonTokenStream
  -> BickSpecParser
  -> ANTLR parse tree
  -> BickSpecSemanticVisitor trace
  -> BickSpecJavaTranslatorVisitor Java output
  -> ParseTreeGraphGenerator DOT/SVG export
```

`ParseRunner` executes the validation-oriented path. It parses each input file, prints `PARSE OK` or `PARSE FAILED`, prints the parse tree for valid files, prints the semantic visit trace, and writes parse tree graph artifacts.

`TranspileRunner` executes the translation-oriented path. It uses the same parsing and semantic tracing support, writes the same parse tree graph artifacts, and then generates Java source under `testing/generated/`.

Invalid programs stop after lexical or syntax validation. They report the first collected error and do not produce semantic traces, generated Java, DOT files, or SVG files.

## 3. Major Classes

### `LexerRunner`

`LexerRunner` is the Phase I-compatible command-line entry point. It accepts one `.bks` file or a directory, runs the ANTLR-generated `BickSpecLexer`, and prints token names and lexemes. It does not parse or translate programs.

### `BickSpecParseSupport`

`BickSpecParseSupport` is shared infrastructure for Phase II runners. It resolves input files, reads sources as UTF-8, wires `BickSpecLexer` to `CommonTokenStream`, builds `BickSpecParser`, collects lexical and syntax errors, creates the parse tree, and runs `BickSpecSemanticVisitor` on successful parses.

Its `ParseResult` record is the handoff object between stages. Successful results contain the parse tree, parser, printable tree, and semantic trace. Failed results contain the error type and message.

### `ParseRunner`

`ParseRunner` is the parser validation runner. It receives a source file or directory, delegates parsing to `BickSpecParseSupport`, and prints validation results. For valid files, it also prints the semantic trace and calls `ParseTreeGraphGenerator`.

### `BickSpecSemanticVisitor`

`BickSpecSemanticVisitor` walks the parse tree after parser validation succeeds. Its job in Phase II is to demonstrate semantic traversal and record planned actions, such as registering imports, assignments, control flow, functions, money literals, and time literals.

The visitor produces trace strings for review. It does not yet enforce complete symbol-table rules, type compatibility, unit conversion correctness, or financial-domain semantics.

### `TranspileRunner`

`TranspileRunner` is the Java-generation runner. It validates and traces input files through the same support class used by `ParseRunner`, exports parse tree graphs, and then asks `BickSpecJavaTranslatorVisitor` to create Java source files.

### `BickSpecJavaTranslatorVisitor`

`BickSpecJavaTranslatorVisitor` performs the initial syntax-directed translation from a validated BickSpec parse tree to Java source text. It translates project blocks, assignments, display/read statements, control flow, functions, expressions, and selected domain literals.

The generated Java is intentionally a Phase II artifact. It preserves structure and emits TODOs for incomplete runtime behavior, including currency conversion, time conversion, import mapping, dimensional semantics, and financial built-ins.

### `ParseTreeGraphGenerator`

`ParseTreeGraphGenerator` exports visual parse tree artifacts for valid programs. It writes `testing/trees/<BaseName>_ParseTree.dot` as the source graph representation and invokes Graphviz with `dot -Tsvg <input.dot> -o <output.svg>` to produce `testing/trees/<BaseName>_ParseTree.svg`.

If Graphviz is missing or the command fails, parsing still succeeds. The DOT file remains available and the runner prints a warning.

## 4. Valid Test Processing

When a valid `.bks` file is processed:

1. The runner prints a file header.
2. `BickSpecParseSupport` lexes and parses the source.
3. The parser returns a successful `ParseResult`.
4. The runner prints `PARSE OK`.
5. The runner prints the ANTLR parse tree string.
6. `BickSpecSemanticVisitor` trace lines are printed.
7. `ParseTreeGraphGenerator` writes DOT and SVG files under `testing/trees/`.
8. `TranspileRunner`, when used, writes Java output under `testing/generated/`.

Example graph outputs:

```text
testing/trees/P1_HolaMundo_ParseTree.dot
testing/trees/P1_HolaMundo_ParseTree.svg
```

Example generated Java output:

```text
testing/generated/P1_HolaMundo_Generated.java
```

## 5. Invalid Test Rejection

Invalid `.bks` files are rejected before semantic tracing, graph export, or Java generation.

Lexical failures are collected from the lexer error listener and reported with error type `lexical`. Syntax failures are collected from the parser error listener and reported with error type `syntax`.

For invalid files, the runners preserve the failure behavior:

- Print `PARSE FAILED`.
- Print the error type.
- Print the first collected error message.
- Exit with a non-zero status if any input file failed.
- Do not generate DOT, SVG, or Java output for that file.

## 6. Generated Phase II Outputs

Phase II can generate these artifacts:

- Console token streams from `LexerRunner`.
- Console parser validation results from `ParseRunner` and `TranspileRunner`.
- Console semantic visit traces for valid programs.
- DOT parse tree files in `testing/trees/`.
- SVG parse tree files in `testing/trees/` when Graphviz is available.
- Java source files in `testing/generated/` from `TranspileRunner`.

These artifacts support review, classroom demonstration, and regression checking. They are not presented as the final production compiler runtime.

## 7. Phase III Boundary

The following work remains outside Phase II and belongs more naturally to Phase III:

- Full symbol table construction.
- Advanced semantic validation for declarations, scopes, types, units, and function calls.
- Complete runtime support for currency conversion, time conversion, imports, and financial built-ins such as NPV.
- More complete target-code generation and runtime integration.
- Stronger execution-oriented tests for generated Java behavior.

Phase II therefore ends at parser validation, semantic traversal evidence, initial Java emission, and parse tree visualization.
