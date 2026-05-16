# BickSpec Finance DSL for VS Code

![BickSpec icon](media/bickspec-icon.png)

BickSpec Finance DSL is a Visual Studio Code extension for `.bks` files written in BickSpec, a finance and economic engineering DSL. It provides a polished editor experience around the existing BickSpec compiler while keeping the compiler implementation in the main Java/ANTLR project.

The extension does not reimplement the language compiler. It invokes the existing `bickspec-compiler-1.0.0.jar` and presents the compiler workflow inside VS Code.

## Overview

- Registers the `bickspec` language id.
- Associates `.bks` files with BickSpec.
- Provides branded syntax highlighting scopes and an optional **BickSpec Academic** color theme.
- Adds snippets for common BickSpec constructs.
- Adds line comments with `#` and block comments with `/* ... */`.
- Supports bracket matching, auto-closing pairs, and surrounding pairs.
- Runs the existing compiler pipeline from VS Code:
  - parse;
  - semantic validation;
  - Java generation;
  - generated Java build;
  - generated program execution.
- Shows compiler output in a dedicated **BickSpec Compiler** output channel.
- Converts compiler `[ERROR]` lines into VS Code diagnostics when source locations are available.
- Adds status bar shortcuts while a `.bks` file is active.
- Opens generated Java, symbol table CSV, and parse tree SVG artifacts.
- Bundles an offline documentation page and can open it in the user's browser.

## How It Works

The extension is a thin VS Code frontend over the existing Java compiler. The main pieces are:

- `package.json`: declares the language id, file association, commands, menus, theme, snippets, activation events, and configuration.
- `extension.js`: the runtime entry point that registers commands, creates the output channel, manages diagnostics, and launches the compiler jar.
- `syntaxes/bickspec.tmLanguage.json`: TextMate grammar for syntax highlighting.
- `language-configuration.json`: editor behavior for comments, brackets, auto-closing pairs, and surrounding pairs.
- `snippets/bickspec.code-snippets`: reusable BickSpec code templates.
- `themes/bickspec-academic-color-theme.json`: optional branded color theme for the editor.

Command wiring is intentionally direct. The extension registers command handlers for run and artifact-opening actions, then exposes the same actions in the Command Palette, editor title, explorer context menu, and status bar when a `.bks` file is active.

Compiler integration follows the existing project flow:

1. the extension resolves the compiler jar path;
2. it launches `java -jar bickspec-compiler-1.0.0.jar <target>`;
3. it captures the compiler output in the dedicated output channel;
4. it parses diagnostic lines into VS Code markers;
5. it opens generated files from the output directories used by the compiler.

Non-interactive runs are captured in the output channel. Interactive programs containing `READ` are executed in the integrated terminal so prompts and stdin work as expected.

## Visual Identity

The plugin uses a compact BickSpec icon and a restrained academic palette:

- Navy primary: `#0F274A`
- Slate blue: `#475569`
- Teal accent: `#14C7BE`
- Secondary teal/cyan: `#1DD6C3`
- Light neutral: `#F3F4F6`

The provided visual reference is kept at `../docs/bksicon.png` and `../docs/bks.ico` when present. The extension package icon is a VS Code-ready 128x128 PNG at `media/bickspec-icon.png`, with an editable SVG companion at `media/bickspec-icon.svg`.

## Development and Testing

Open the extension folder in VS Code:

```text
vscode-extension/
```

Run the syntax check:

```bash
npm run check
```

No build step is required because the extension uses plain JavaScript.

To test locally:

1. Build the compiler jar from the repository root if it is not already present.
2. Open `vscode-extension/` in VS Code.
3. Press `F5` to launch the Extension Development Host.
4. Open a `.bks` program and run **BickSpec: Run Current File**.
5. Check the **BickSpec Compiler** output channel and any diagnostics in the editor.
6. Repeat with a file that contains `READ` to verify integrated-terminal behavior.

## Launch in Extension Development Host

1. Open `vscode-extension/` in Visual Studio Code.
2. Press `F5`, or open **Run and Debug** and choose **Launch Extension**.
3. In the Extension Development Host window, open a `.bks` file.
4. Confirm the language mode is **BickSpec**.
5. Optionally select **BickSpec Academic** from **Preferences: Color Theme**.

## Compiler Jar Configuration

The extension invokes:

```text
bickspec-compiler-1.0.0.jar
```

By default, it looks for:

```text
app/target/bickspec-compiler-1.0.0.jar
```

If the jar is missing, build it from the repository root:

```bash
mvn -f app/pom.xml package
```

You can set a custom jar path in VS Code settings:

```json
{
  "bickspec.compiler.jarPath": "app/target/bickspec-compiler-1.0.0.jar"
}
```

Relative paths are resolved from the detected workspace/repository root.

## BickSpec Setup Wizard

Open **BickSpec: Open Setup Wizard** from the Command Palette to verify Java, the compiler JAR, optional repository tooling, workspace write access, and a real sample compilation.

The wizard can:

- use `java` from `PATH` or a custom `bickspec.javaPath`;
- select the compiler JAR through `bickspec.compiler.jarPath`;
- optionally remember a local repository in `bickspec.compiler.repoPath`;
- run a real non-interactive setup test using valid BickSpec syntax.

If Java is missing, install Java 21 or configure `bickspec.javaPath`. If the compiler JAR is missing, select `bickspec-compiler-1.0.0.jar` or build it from the main repository with `mvn -f app/pom.xml package`.

## Bundled Documentation

The extension includes a local offline documentation page at:

```text
media/docs/bickspec_documentation.html
```

It opens automatically once on first activation when:

```json
{
  "bickspec.documentation.openOnFirstActivation": true
}
```

You can also open it manually at any time from the Command Palette with **BickSpec: Open Documentation**.

Jar resolution order:

1. `bickspec.compiler.jarPath` if configured.
2. `app/target/bickspec-compiler-1.0.0.jar` under the current workspace/repository.
3. Nearby repository-root fallbacks used by the extension when launched from a nested workspace.

## Commands

Open the Command Palette and run:

- **BickSpec: Run Current File**
- **BickSpec: Run Folder**
- **BickSpec: Show Compiler Output**
- **BickSpec: Open Generated Java**
- **BickSpec: Open Symbol Table CSV**
- **BickSpec: Open Parse Tree SVG**
- **BickSpec: Open Documentation**
- **BickSpec: Open Setup Wizard**
- **BickSpec: Validate Environment**
- **BickSpec: Select Compiler JAR**
- **BickSpec: Select bickspec-lang Repository**
- **BickSpec: Run Setup Test**
- **BickSpec: Reset Setup**

When a `.bks` file is active, the status bar also shows:

- `$(play) BickSpec` to run the current file;
- `$(output) BickSpec Output` to open the compiler output channel.

## Run the Current File

1. Open a `.bks` file, for example `testing/P1_HolaMundo.bks`.
2. Run **BickSpec: Run Current File** from the Command Palette, editor title action, Explorer context menu, or status bar.
3. The extension saves the active file if needed.
4. The extension runs:

   ```bash
   java -jar path/to/bickspec-compiler-1.0.0.jar path/to/file.bks
   ```

5. Compiler output appears in **BickSpec Compiler**.
6. If the compiler reports errors with line/column locations, VS Code editor markers are created.
7. If the file contains `READ`, the extension uses the integrated terminal instead of only the output channel.

## Run a Folder

Run **BickSpec: Run Folder** from the Command Palette or from a folder in the Explorer context menu.

If no folder is selected, the command uses the current workspace folder. The selected folder is passed directly to the compiler jar, preserving the existing compiler directory-mode behavior.
If any `.bks` file in the selected folder contains `READ`, the extension uses the integrated terminal for the run so interactive input remains usable.

## Diagnostics

The extension parses compiler lines that start with these diagnostic families:

- `[ERROR] LEX...`
- `[ERROR] SYN...`
- `[ERROR] SEM...`
- `[ERROR] GEN...`

Example compiler diagnostic:

```text
[ERROR] SEM01 - Variable 'X' used before declaration at line 2:11
```

VS Code marker:

- severity: Error;
- source: BickSpec;
- code: `SEM01`;
- location: line 2, column 11.

During folder runs, diagnostics are attached to the current compiler file header when possible.

## Limitations and Current Scope

This extension is a VS Code plugin frontend for the existing compiler, not a full language server.

That means:

- it provides editor integration, not deep semantic awareness inside the editor;
- it parses compiler diagnostics from process output instead of speaking a language-server protocol;
- advanced IntelliSense, refactoring, and live semantic analysis remain future work;
- the compiler itself still lives in the main Java project and should be built there first.

## Interactive Programs

BickSpec programs that contain `READ` require real terminal input. When the extension detects `READ` in the current file, or in any direct `.bks` file inside a selected folder, it runs the compiler in the integrated terminal.

This preserves prompts and user input exactly as the compiler expects. The output channel still records launch details, but the interactive process itself belongs to the terminal.

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

## Snippet Examples

Type `project`:

```bickspec
PROJECT "Project Name" {
  DISPLAY "Hello BickSpec"
}
```

Type `fx`:

```bickspec
FX GTQ := 7.80
```

## Demo Walkthrough

1. Open a `.bks` file such as `testing/P1_HolaMundo.bks`.
2. Run **BickSpec: Run Current File**.
3. Inspect the **BickSpec Compiler** output channel and any editor diagnostics.
4. Open **BickSpec: Open Generated Java** to inspect the Java translation.
5. Open **BickSpec: Open Symbol Table CSV** and **BickSpec: Open Parse Tree SVG** to review compiler artifacts.
6. Open `testing/P11_FalloSemantico.bks` to see semantic diagnostics in the editor.
7. Open a file with `READ` and run it again to confirm integrated-terminal input.

## Scope

This plugin is intentionally lightweight and demo-friendly. It provides editor support, compiler execution, diagnostics, generated artifact access, and branded presentation without adding a full language server.
