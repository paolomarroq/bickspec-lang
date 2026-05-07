# Phase III Semantic Analysis

Phase III adds a real semantic-analysis foundation after ANTLR parsing and before Java generation.

## Compiler gates

The compiler pipeline is:

1. Lexical validation
2. Syntax validation
3. Semantic validation
4. Symbol-table CSV export
5. Parse-tree graph export
6. Java generation

Java generation is allowed only when lexical, syntax, and semantic validation all succeed. Symbol-table CSV files are also generated only after a successful parse and successful semantic validation.

## Java generation and runtime model

`TranspileRunner` writes generated Java files under `testing/generated/` only after all gates pass. Each generated file has a `main` method for the project block, helper methods, and a header documenting the BickSpec runtime model.

Money is stored internally as USD:

- `USD` literals are stored directly.
- `GTQ` and `EUR` literals are converted with `toUsd(amount, currency)`.
- FX declarations use the rule `1 USD = FX currency`.
- `DISPLAY expr in GTQ` and `DISPLAY expr in EUR` convert with `fromUsd(...)` for presentation only and do not change stored values.

Official built-ins are emitted as real Java helpers:

- `private static double NPV(double rate, double capex, double... cashflows)`
- `private static double PAYBACK(double capex, double... cashflows)`

Imports such as `IMPORT finance` are represented as generated Java runtime module markers. Multi-file linking is intentionally kept lightweight for this phase; imported built-ins used by the official fixtures are available through generated helper methods.

Batch assignments expand to explicit Java assignments. Shared money and time metadata is preserved across every expanded target.

## Diagnostic codes

All compiler errors use the same console format:

`[ERROR] CODE - Message at line X:Y`

Implemented categories:

- `LEX01`: lexical recognition error
- `SYN01`: parser syntax error
- `SEM01`: variable used before declaration or initialization
- `SEM02`: duplicate declaration in the same scope
- `SEM03`: simple type mismatch, such as assigning text to a numeric variable
- `SEM04`: function call to an undeclared function
- `GEN01`: input/read infrastructure error
- `GEN02`: symbol CSV write error
- `GEN03`: Java source write error

## Symbol table

The semantic visitor records symbols with these CSV columns:

`name,type,scope,declared_at,initialized,notes`

Tracked entries include imports, exchange-rate constants, functions, function parameters, read variables, assigned variables, and batch assignment targets. Function parameters use `function:<name>` scopes; project-level symbols use `global`.

CSV outputs are written under `testing/symbols/`, for example:

`testing/symbols/P3_Input_If_symbols.csv`

## Test numbering policy

The Phase II fixtures keep their original names and numbering for continuity:

- `P1_HolaMundo.bks`
- `P2_Aritmetica.bks`
- `P3_Input_If.bks`
- `P4_Funcion.bks`
- `P5_While_Runway.bks`
- `P6_Imports_NPV.bks`
- `P7_BatchMoney.bks`
- `P8_BatchTime.bks`
- `P9_FalloLexico.bks`
- `P10_FalloSintaxis.bks`

Phase III adds the new required cases without renumbering prior tests:

- `P11_FalloSemantico.bks`: parses successfully, fails semantic validation, and does not generate CSV or Java.
- `P12_Recursividad.bks`: validates function registration and recursive lookup.
