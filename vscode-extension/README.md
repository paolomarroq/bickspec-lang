# BickSpec Language Support

BickSpec Language Support is a Visual Studio Code extension for `.bks` files written in BickSpec, a finance and economic engineering DSL.

The extension provides editor support and command integration with the existing BickSpec compiler jar. It does not reimplement compiler logic inside VS Code.

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
- Runs the existing compiler pipeline from VS Code:
  - parse;
  - semantic validation;
  - Java generation;
  - generated Java build;
  - generated program execution.
- Shows compiler output in a dedicated **BickSpec Compiler** output channel.
- Converts compiler `[ERROR]` diagnostics into VS Code editor markers when the compiler reports a source location.
- Opens generated Java, symbol table CSV, and parse tree SVG artifacts when they exist.

## Compiler Jar

The extension invokes the existing compiler jar:

```text
bickspec-compiler-1.0.0.jar
```

By default, it looks for the jar at:

```text
app/target/bickspec-compiler-1.0.0.jar
```

If the jar does not exist, build it from the repository root:

```bash
mvn -f app/pom.xml package
```

You can also configure a custom jar path in VS Code settings:

```json
{
  "bickspec.compiler.jarPath": "app/target/bickspec-compiler-1.0.0.jar"
}
```

Relative paths are resolved from the detected workspace/repository root.

## Commands

Open the Command Palette and run:

- **BickSpec: Run Current File**
- **BickSpec: Run Folder**
- **BickSpec: Show Compiler Output**
- **BickSpec: Open Generated Java**
- **BickSpec: Open Symbol Table CSV**
- **BickSpec: Open Parse Tree SVG**

## Run the Current File

1. Open a `.bks` file.
2. Run **BickSpec: Run Current File** from the Command Palette, editor title action, or Explorer context menu.
3. The extension runs:

   ```bash
   java -jar path/to/bickspec-compiler-1.0.0.jar path/to/file.bks
   ```

4. Output is written to **BickSpec Compiler**.
5. Diagnostics such as `[ERROR] SEM01 - Variable 'X' used before declaration at line 2:11` are shown as VS Code error markers.

## Run a Folder

Run **BickSpec: Run Folder** from the Command Palette or from a folder in the Explorer context menu.

If no folder is selected, the command uses the current workspace folder. The extension passes the folder path to the compiler jar, preserving the existing compiler behavior for directory mode.

## Diagnostics

The extension parses compiler lines that start with these diagnostic families:

- `[ERROR] LEX...`
- `[ERROR] SYN...`
- `[ERROR] SEM...`
- `[ERROR] GEN...`

When the compiler includes a location in the format `at line <line>:<column>`, the extension creates a VS Code error marker at that location. During folder runs, diagnostics are associated with the current compiler file header when possible.

## Interactive Programs

BickSpec programs that contain `READ` require real terminal input. When the extension detects `READ` in the current file, or in any direct `.bks` file inside a selected folder, it runs the compiler in the integrated terminal instead of only using the output channel.

This preserves prompts and user input exactly as the compiler expects. Interactive terminal runs still log launch details to **BickSpec Compiler**, but they do not provide captured output-channel diagnostics because the process is owned by the terminal.

## Generated Artifacts

After a successful non-interactive run, use:

- **BickSpec: Open Generated Java**
- **BickSpec: Open Symbol Table CSV**
- **BickSpec: Open Parse Tree SVG**

Artifacts are opened from the compiler's standard output folders:

```text
output/java
output/symbols
output/trees
```

## Launch in VS Code Extension Development Host

1. Open this folder in Visual Studio Code:

   ```text
   vscode-extension/
   ```

2. Press `F5`, or open **Run and Debug** and select **Launch Extension**.

3. In the Extension Development Host window, open any `.bks` file.

4. Confirm the language mode is **BickSpec** and try snippets such as `project`, `if`, `while`, `function`, `fx`, `display`, or `read`.

5. Build the compiler jar if needed, then run **BickSpec: Run Current File** on a `.bks` source file.
