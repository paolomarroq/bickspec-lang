# BickSpec Finance DSL for VS Code

![BickSpec icon](media/bickspec-icon.png)

BickSpec Finance DSL is a Visual Studio Code extension for `.bks` files. It runs the real BickSpec Java compiler from inside VS Code without reimplementing compiler logic.

The extension now bundles `bickspec-compiler-1.0.0.jar`, so normal users do not need to clone `bickspec-lang`, install Maven, install Git, or manually point the extension at `app/target`.

## Simple Installation

1. Install the extension from the Marketplace or install the generated VSIX.
2. Install Java 21 if prompted by the setup wizard.
3. Open a `.bks` file.
4. Run **BickSpec: Run Current File**.

Normal user requirements:

- VS Code
- Java installed
- BickSpec VS Code extension installed

## Bundled Compiler

The extension includes the compiler JAR at:

```text
media/compiler/bickspec-compiler-1.0.0.jar
```

Compiler resolution order:

1. `bickspec.compiler.jarPath` if configured and valid.
2. Bundled compiler JAR inside the extension.
3. Developer fallback to `app/target/bickspec-compiler-1.0.0.jar` when running from the source repository.

Advanced users may still override the compiler JAR path or configure a local `bickspec-lang` repository, but those are optional developer workflows.

## Setup Wizard

Open **BickSpec: Open Setup Wizard** to walk through:

1. Java Check
2. Bundled Compiler Check
3. Workspace Check
4. Setup Test
5. Ready

If Java is missing, the wizard shows:

```text
Java is required to run the bundled BickSpec compiler.
```

Available Java actions:

- **Install Java**
- **Select Java Manually**
- **Re-check Java**

Advanced actions remain available but are optional:

- Select custom compiler JAR
- Select `bickspec-lang` repository
- Clone/update repository
- Build compiler with Maven

## Commands

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

## Run Current File

When you run **BickSpec: Run Current File**:

1. The extension checks Java.
2. It uses the bundled compiler automatically when available.
3. Compiler output appears in **BickSpec Compiler**.
4. Diagnostics appear in the **Problems** panel when source locations are available.
5. If the file contains `READ`, the compiler runs in the integrated terminal so interactive input still works.

The output channel shows:

```text
[BickSpec] Using bundled compiler:
<extension>/media/compiler/bickspec-compiler-1.0.0.jar
```

## Development and Packaging

Prepare the bundled compiler:

```bash
npm run prepare:compiler
```

Run the syntax check:

```bash
npm run check
```

Package the extension:

```bash
npm run package:vsix
```

This runs `prepare:compiler` first and produces a `.vsix` file in `vscode-extension/`.

## Troubleshooting

### Java not found

- Open **BickSpec: Open Setup Wizard**.
- Use **Install Java** or **Select Java Manually**.
- The extension requires Java to run the bundled compiler.

### Bundled compiler missing

- This is an extension packaging problem.
- Reinstall the extension or rebuild the VSIX with `npm run package:vsix`.

### READ programs open in the integrated terminal

- This is expected.
- Interactive BickSpec programs use the integrated terminal so `READ` input still works.

### Diagnostics appear in Problems

- Compiler `[ERROR]` lines with locations are converted into VS Code diagnostics.
- Use the **Problems** panel to jump back to the source line.
