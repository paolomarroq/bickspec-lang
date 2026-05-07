# BickSpec Language Support

BickSpec Language Support is a Visual Studio Code extension for `.bks` files written in BickSpec, a finance and economic engineering DSL.

This first extension scaffold focuses on editor language support only. It does not run the BickSpec compiler yet.

## Current Support

- Registers the `bickspec` language id.
- Associates `.bks` files with BickSpec.
- Provides line comments with `#`.
- Provides block comments with `/* ... */`.
- Supports bracket matching and auto-closing pairs.
- Adds TextMate syntax highlighting for:
  - language declarations and control flow;
  - input/output keywords;
  - currency and time units;
  - strings, numbers, identifiers, comments, and operators.
- Adds snippets for common BickSpec constructs:
  - `PROJECT` block;
  - `IF / THEN / ELSE / END`;
  - `WHILE / DO / END`;
  - `FUNCTION`;
  - `FX`;
  - `DISPLAY`;
  - `READ`.

## Launch in VS Code Extension Development Host

1. Open this folder in Visual Studio Code:

   ```text
   vscode-extension/
   ```

2. Press `F5`, or open **Run and Debug** and select **Launch Extension**.

3. In the Extension Development Host window, open any `.bks` file.

4. Confirm the language mode is **BickSpec** and try snippets such as `project`, `if`, `while`, `function`, `fx`, `display`, or `read`.

## Scope of This Scaffold

This extension currently provides language registration, syntax highlighting, editor configuration, and snippets. Compiler execution commands, diagnostics integration, generated artifact views, and task automation are reserved for later commits.
