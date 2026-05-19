const vscode = require("vscode");
const cp = require("child_process");
const fs = require("fs");
const path = require("path");
const SetupWizardPanel = require("./setup/SetupWizardPanel");
const setupServices = require("./setup/setupServices");
const { BUNDLED_COMPILER_RELATIVE_PATH, JAR_NAME, findCompilerJar } = require("./compilerResolver");

let outputChannel;
let diagnostics;
let lastRunTarget;
let lastRunWorkspace;
let runStatusBarItem;
let outputStatusBarItem;
let extensionContext;

function activate(context) {
  extensionContext = context;
  outputChannel = vscode.window.createOutputChannel("BickSpec Compiler");
  diagnostics = vscode.languages.createDiagnosticCollection("bickspec");
  runStatusBarItem = vscode.window.createStatusBarItem("bickspec.run", vscode.StatusBarAlignment.Left, 90);
  runStatusBarItem.text = "$(play) BickSpec";
  runStatusBarItem.tooltip = "Run the current BickSpec file";
  runStatusBarItem.command = "bickspec.runCurrentFile";

  outputStatusBarItem = vscode.window.createStatusBarItem("bickspec.output", vscode.StatusBarAlignment.Left, 89);
  outputStatusBarItem.text = "$(output) BickSpec Output";
  outputStatusBarItem.tooltip = "Show BickSpec compiler output";
  outputStatusBarItem.command = "bickspec.showCompilerOutput";

  context.subscriptions.push(
    outputChannel,
    diagnostics,
    runStatusBarItem,
    outputStatusBarItem,
    vscode.commands.registerCommand("bickspec.runCurrentFile", runCurrentFile),
    vscode.commands.registerCommand("bickspec.runFolder", runFolder),
    vscode.commands.registerCommand("bickspec.showCompilerOutput", showCompilerOutput),
    vscode.commands.registerCommand("bickspec.openGeneratedJava", () => openArtifact("java")),
    vscode.commands.registerCommand("bickspec.openSymbolTable", () => openArtifact("symbols")),
    vscode.commands.registerCommand("bickspec.openParseTreeSvg", () => openArtifact("tree")),
    vscode.commands.registerCommand("bickspec.openDocumentation", () => openDocumentation(context)),
    vscode.commands.registerCommand("bickspec.openSetupWizard", () => SetupWizardPanel.createOrShow(context)),
    vscode.commands.registerCommand("bickspec.validateEnvironment", async () => {
      const panel = SetupWizardPanel.createOrShow(context);
      panel.postState(await panel.collectState());
    }),
    vscode.commands.registerCommand("bickspec.selectCompilerJar", async () => {
      const panel = SetupWizardPanel.createOrShow(context);
      await panel.selectJar();
    }),
    vscode.commands.registerCommand("bickspec.selectCompilerRepo", async () => {
      const panel = SetupWizardPanel.createOrShow(context);
      await panel.selectRepo();
    }),
    vscode.commands.registerCommand("bickspec.runSetupTest", async () => {
      const result = await setupServices.runSetupTest();
      if (result.status === "success") {
        vscode.window.showInformationMessage("BickSpec setup test compiled successfully.");
      } else {
        vscode.window.showErrorMessage(result.suggestion);
      }
      const panel = SetupWizardPanel.createOrShow(context);
      panel.postResult("test", result);
    }),
    vscode.commands.registerCommand("bickspec.resetSetup", async () => {
      await context.globalState.update("bickspec.setup.completed", false);
      await vscode.workspace.getConfiguration("bickspec.setup").update("completed", false, vscode.ConfigurationTarget.Global);
      vscode.window.showInformationMessage("BickSpec setup state was reset.");
      const panel = SetupWizardPanel.createOrShow(context);
      panel.postState(await panel.collectState());
    }),
    vscode.window.onDidChangeActiveTextEditor(updateBickSpecStatusBar),
    vscode.workspace.onDidOpenTextDocument(updateBickSpecStatusBar),
    vscode.workspace.onDidCloseTextDocument(updateBickSpecStatusBar)
  );

  updateBickSpecStatusBar();
  maybeOpenDocumentation(context);
  maybePromptSetup(context);
}

function deactivate() {
  if (diagnostics) {
    diagnostics.dispose();
  }
}

async function runCurrentFile(uri) {
  const editor = vscode.window.activeTextEditor;
  const sourceFile = uri && uri.scheme === "file"
    ? uri.fsPath
    : editor && editor.document.uri.scheme === "file"
      ? editor.document.fileName
      : undefined;

  if (!sourceFile || !sourceFile.toLowerCase().endsWith(".bks")) {
    vscode.window.showErrorMessage("Open a .bks file before running BickSpec.");
    return;
  }

  if (editor && editor.document.fileName === sourceFile && editor.document.isDirty) {
    await editor.document.save();
  }

  const workspaceFolder = vscode.workspace.getWorkspaceFolder(vscode.Uri.file(sourceFile)) || firstWorkspaceFolder();
  const workspaceRoot = resolveWorkspaceRoot(workspaceFolder);
  lastRunTarget = sourceFile;
  lastRunWorkspace = workspaceRoot;

  const sourceText = editor && editor.document.fileName === sourceFile
    ? editor.document.getText()
    : await fs.promises.readFile(sourceFile, "utf8").catch(() => "");
  if (/\bREAD\b/.test(sourceText)) {
    runInTerminal(sourceFile, workspaceRoot, "Current file uses READ; running in the integrated terminal for interactive input.");
    return;
  }

  await runCompiler(sourceFile, workspaceRoot, sourceFile);
}

async function runFolder(uri) {
  const folder = await resolveFolderTarget(uri);
  if (!folder) {
    return;
  }

  const workspaceFolder = vscode.workspace.getWorkspaceFolder(vscode.Uri.file(folder)) || firstWorkspaceFolder();
  const workspaceRoot = resolveWorkspaceRoot(workspaceFolder);
  lastRunTarget = folder;
  lastRunWorkspace = workspaceRoot;

  if (await folderContainsRead(folder)) {
    runInTerminal(folder, workspaceRoot, "Folder contains at least one .bks program with READ; running in the integrated terminal for interactive input.");
    return;
  }

  await runCompiler(folder, workspaceRoot, activeBksFile());
}

function showCompilerOutput() {
  outputChannel.show(true);
}

function updateBickSpecStatusBar() {
  const isBickSpec = Boolean(activeBksFile());
  vscode.commands.executeCommand("setContext", "bickspec:isActiveFile", isBickSpec);
  if (isBickSpec) {
    runStatusBarItem.show();
    outputStatusBarItem.show();
  } else {
    runStatusBarItem.hide();
    outputStatusBarItem.hide();
  }
}

async function resolveFolderTarget(uri) {
  if (uri && uri.scheme === "file") {
    const stat = await fs.promises.stat(uri.fsPath).catch(() => null);
    if (stat && stat.isDirectory()) {
      return uri.fsPath;
    }
  }

  const workspace = firstWorkspaceFolder();
  if (workspace) {
    return resolveWorkspaceRoot(workspace);
  }

  const selection = await vscode.window.showOpenDialog({
    canSelectFiles: false,
    canSelectFolders: true,
    canSelectMany: false,
    openLabel: "Run BickSpec Folder"
  });
  return selection && selection.length > 0 ? selection[0].fsPath : undefined;
}

async function runCompiler(targetPath, cwd, fallbackDiagnosticFile) {
  const compiler = resolveCompilerSelection(cwd);
  if (!compiler.path) {
    await showCompilerNotFoundMessage(compiler);
    outputChannel.show(true);
    outputChannel.appendLine("[FAILURE] Compiler JAR not found.");
    outputChannel.appendLine(`[BickSpec] Missing bundled compiler: ${compiler.bundledJarPath}`);
    if (compiler.invalidConfiguredJarPath) {
      outputChannel.appendLine(`[BickSpec] Invalid custom compiler path: ${compiler.invalidConfiguredJarPath}`);
    }
    return;
  }

  const java = await setupServices.validateJava();
  if (java.status === "error") {
    outputChannel.show(true);
    outputChannel.appendLine("[FAILURE] Java is unavailable.");
    outputChannel.appendLine(`[BickSpec] ${java.suggestion}`);
    const choice = await vscode.window.showErrorMessage(
      "Java is required to run the bundled BickSpec compiler.",
      "Open Setup Wizard"
    );
    if (choice === "Open Setup Wizard") {
      SetupWizardPanel.createOrShow(extensionContext);
    }
    return;
  }

  diagnostics.clear();
  outputChannel.show(true);
  outputChannel.appendLine("");
  outputChannel.appendLine(`[STARTING] BickSpec compiler`);
  outputChannel.appendLine(`[BickSpec] Using ${compiler.source} compiler:`);
  outputChannel.appendLine(compiler.path);
  outputChannel.appendLine(`[JAR] ${compiler.path}`);
  outputChannel.appendLine(`[TARGET] ${targetPath}`);
  outputChannel.appendLine(`[WORKING DIRECTORY] ${cwd}`);

  const javaCommand = configuredJavaCommand();
  const child = cp.spawn(javaCommand, ["-jar", compiler.path, targetPath], {
    cwd,
    shell: false
  });

  let combined = "";
  child.stdout.on("data", chunk => {
    const text = chunk.toString();
    combined += text;
    outputChannel.append(text);
  });
  child.stderr.on("data", chunk => {
    const text = chunk.toString();
    combined += text;
    outputChannel.append(text);
  });
  child.on("error", error => {
    outputChannel.appendLine(`[FAILURE] Failed to start compiler: ${error.message}`);
    vscode.window.showErrorMessage(`Failed to start BickSpec compiler: ${error.message}`);
  });
  child.on("close", exitCode => {
    applyDiagnostics(combined, cwd, fallbackDiagnosticFile);
    if (exitCode === 0) {
      outputChannel.appendLine(`[SUCCESS] BickSpec compiler completed successfully.`);
      vscode.window.showInformationMessage("BickSpec compiler completed successfully.");
    } else {
      outputChannel.appendLine(`[FAILURE] BickSpec compiler exited with code ${exitCode}.`);
      vscode.window.showErrorMessage(`BickSpec compiler failed with exit code ${exitCode}. See BickSpec Compiler output.`);
    }
  });
}

function runInTerminal(targetPath, cwd, reason) {
  setupServices.validateJava().then(async java => {
    if (java.status === "error") {
      const choice = await vscode.window.showErrorMessage(
        "Java is required to run the bundled BickSpec compiler.",
        "Open Setup Wizard"
      );
      if (choice === "Open Setup Wizard") {
        SetupWizardPanel.createOrShow(extensionContext);
      }
      return;
    }

    const compiler = resolveCompilerSelection(cwd);
    if (!compiler.path) {
      await showCompilerNotFoundMessage(compiler);
      return;
    }

    diagnostics.clear();
    outputChannel.show(true);
    outputChannel.appendLine("");
    outputChannel.appendLine(`[STARTING] BickSpec compiler`);
    outputChannel.appendLine(`[INTERACTIVE] ${reason}`);
    outputChannel.appendLine(`[BickSpec] Using ${compiler.source} compiler:`);
    outputChannel.appendLine(compiler.path);
    outputChannel.appendLine(`[JAR] ${compiler.path}`);
    outputChannel.appendLine(`[TARGET] ${targetPath}`);
    outputChannel.appendLine(`[WORKING DIRECTORY] ${cwd}`);

    const terminal = vscode.window.createTerminal({
      name: "BickSpec Interactive",
      cwd
    });
    terminal.show();
    terminal.sendText(`"${configuredJavaCommand()}" -jar "${compiler.path}" "${targetPath}"`);
    outputChannel.appendLine("[STARTED] Interactive compiler process launched in the integrated terminal.");
  });
}

function resolveCompilerSelection(cwd) {
  const configured = vscode.workspace.getConfiguration("bickspec.compiler").get("jarPath");
  const selection = findCompilerJar({ basePath: cwd, configuredJarPath: configured });
  return {
    ...selection,
    source: selection.source === "custom"
      ? "custom"
      : selection.source === "bundled"
        ? "bundled"
        : selection.source === "developer"
          ? "developer fallback"
          : undefined
  };
}

async function showCompilerNotFoundMessage(compiler) {
  const message = compiler.invalidConfiguredJarPath
    ? `Configured compiler JAR was not found at ${compiler.invalidConfiguredJarPath}.`
    : `Bundled BickSpec compiler is missing from the extension package. Expected ${BUNDLED_COMPILER_RELATIVE_PATH}.`;
  const choice = await vscode.window.showErrorMessage(
    `${message} Reinstall the extension or run: mvn -f app/pom.xml package`,
    "Open Setup Wizard"
  );
  if (choice === "Open Setup Wizard") {
    SetupWizardPanel.createOrShow(extensionContext);
  }
}

async function folderContainsRead(folder) {
  const entries = await fs.promises.readdir(folder, { withFileTypes: true }).catch(() => []);
  for (const entry of entries) {
    if (!entry.isFile() || !entry.name.toLowerCase().endsWith(".bks")) {
      continue;
    }
    const file = path.join(folder, entry.name);
    const text = await fs.promises.readFile(file, "utf8").catch(() => "");
    if (/\bREAD\b/.test(text)) {
      return true;
    }
  }
  return false;
}

function applyDiagnostics(output, cwd, fallbackDiagnosticFile) {
  const byFile = new Map();
  let currentFile = fallbackDiagnosticFile;

  for (const rawLine of output.split(/\r?\n/)) {
    const header = rawLine.match(/^====\s+(.+?)\s+====$/);
    if (header) {
      currentFile = resolveCompilerPath(header[1], cwd);
      continue;
    }

    const diagnostic = parseDiagnosticLine(rawLine);
    if (!diagnostic) {
      continue;
    }

    const file = currentFile || fallbackDiagnosticFile;
    if (!file) {
      continue;
    }

    const uri = vscode.Uri.file(file);
    const list = byFile.get(uri.toString()) || [];
    list.push(diagnostic);
    byFile.set(uri.toString(), list);
  }

  for (const [uriText, list] of byFile.entries()) {
    diagnostics.set(vscode.Uri.parse(uriText), list);
  }
}

function parseDiagnosticLine(line) {
  const match = line.match(/^\[ERROR\]\s+((?:LEX|SYN|SEM|GEN|BUILD|EXEC|FS|LINK)\d*)\s+-\s+(.+?)(?:\s+at line\s+(\d+):(\d+))?\s*$/);
  if (!match) {
    return undefined;
  }

  const lineNumber = match[3] ? Math.max(0, Number(match[3]) - 1) : 0;
  const column = match[4] ? Math.max(0, Number(match[4])) : 0;
  const range = new vscode.Range(lineNumber, column, lineNumber, column + 1);
  const diagnostic = new vscode.Diagnostic(range, `${match[1]} - ${match[2]}`, vscode.DiagnosticSeverity.Error);
  diagnostic.source = "BickSpec";
  diagnostic.code = match[1];
  return diagnostic;
}

async function openArtifact(kind) {
  const sourceFile = activeBksFile() || (lastRunTarget && lastRunTarget.toLowerCase().endsWith(".bks") ? lastRunTarget : undefined);
  const workspaceRoot = lastRunWorkspace || resolveWorkspaceRoot(firstWorkspaceFolder());

  if (!sourceFile || !workspaceRoot) {
    vscode.window.showErrorMessage("Open or run a .bks file before opening generated artifacts.");
    return;
  }

  const baseName = generatedBaseName(sourceFile);
  const artifactPath = artifactPathFor(kind, workspaceRoot, baseName);
  if (!artifactPath || !fs.existsSync(artifactPath)) {
    vscode.window.showWarningMessage(`BickSpec artifact not found: ${artifactPath || kind}`);
    return;
  }

  const uri = vscode.Uri.file(artifactPath);
  if (kind === "tree") {
    await vscode.commands.executeCommand("vscode.open", uri);
    return;
  }
  const document = await vscode.workspace.openTextDocument(uri);
  await vscode.window.showTextDocument(document);
}

function artifactPathFor(kind, workspaceRoot, baseName) {
  if (kind === "java") {
    return path.join(workspaceRoot, "output", "java", `${baseName}_Generated.java`);
  }
  if (kind === "symbols") {
    return path.join(workspaceRoot, "output", "symbols", `${baseName}_symbols.csv`);
  }
  if (kind === "tree") {
    return path.join(workspaceRoot, "output", "trees", `${baseName}_ParseTree.svg`);
  }
  return undefined;
}

function generatedBaseName(sourceFile) {
  const stem = path.basename(sourceFile, path.extname(sourceFile));
  const sanitized = stem.replace(/[^A-Za-z0-9_]/g, "_");
  return sanitized || "BickSpecProgram";
}

function resolveCompilerPath(displayPath, cwd) {
  const normalized = displayPath.replace(/\//g, path.sep);
  return path.isAbsolute(normalized) ? normalized : path.resolve(cwd, normalized);
}

function activeBksFile() {
  const editor = vscode.window.activeTextEditor;
  if (!editor || editor.document.uri.scheme !== "file") {
    return undefined;
  }
  return editor.document.fileName.toLowerCase().endsWith(".bks") ? editor.document.fileName : undefined;
}

function firstWorkspaceFolder() {
  return vscode.workspace.workspaceFolders && vscode.workspace.workspaceFolders.length > 0
    ? vscode.workspace.workspaceFolders[0]
    : undefined;
}

function resolveWorkspaceRoot(workspaceFolder) {
  if (!workspaceFolder) {
    return contextExtensionParent();
  }

  const root = workspaceFolder.uri.fsPath;
  if (path.basename(root) === "vscode-extension" && fs.existsSync(path.join(path.dirname(root), "app"))) {
    return path.dirname(root);
  }
  return root;
}

function contextExtensionParent() {
  return path.resolve(__dirname, "..");
}

function configuredJavaCommand() {
  return vscode.workspace.getConfiguration("bickspec").get("javaPath") || "java";
}

async function maybePromptSetup(context) {
  const completed = context.globalState.get("bickspec.setup.completed", false);
  const autoOpen = vscode.workspace.getConfiguration("bickspec.setup").get("autoOpenOnFirstLaunch", true);
  if (completed || !autoOpen) {
    return;
  }
  const answer = await vscode.window.showInformationMessage(
    "BickSpec needs setup before compiling files. Open Setup Wizard?",
    "Open Setup Wizard",
    "Later"
  );
  if (answer === "Open Setup Wizard") {
    SetupWizardPanel.createOrShow(context);
  }
}

async function openDocumentation(context) {
  const docsUri = vscode.Uri.joinPath(
    context.extensionUri,
    "media",
    "docs",
    "bickspec_documentation.html"
  );
  await vscode.env.openExternal(docsUri);
}

async function maybeOpenDocumentation(context) {
  const openOnFirstActivation = vscode.workspace.getConfiguration("bickspec.documentation").get("openOnFirstActivation", true);
  const hasOpened = context.globalState.get("bickspec.documentation.hasOpened", false);
  if (!openOnFirstActivation || hasOpened) {
    return;
  }
  await openDocumentation(context);
  await context.globalState.update("bickspec.documentation.hasOpened", true);
}

module.exports = {
  activate,
  deactivate
};
