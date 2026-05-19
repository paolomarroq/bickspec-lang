const vscode = require("vscode");
const cp = require("child_process");
const fs = require("fs");
const path = require("path");
const { BUNDLED_COMPILER_RELATIVE_PATH, JAR_NAME, findCompilerJar: resolveCompilerJar, getBundledCompilerPath, resolveConfiguredPath } = require("../compilerResolver");
const SETUP_SOURCE = `PROJECT "Setup Test" {
  A := 10
  B := 3
  RESULTADO := (A + B) * 2 / 5

  DISPLAY "Resultado:"
  DISPLAY RESULTADO
}
`;

function execFile(command, args, options = {}) {
  return new Promise(resolve => {
    cp.execFile(command, args, { ...options, windowsHide: true }, (error, stdout = "", stderr = "") => {
      resolve({ error, stdout, stderr, combined: `${stdout}${stderr}`, code: error && typeof error.code === "number" ? error.code : error ? 1 : 0 });
    });
  });
}

function workspaceRoot() {
  const folder = vscode.workspace.workspaceFolders && vscode.workspace.workspaceFolders[0];
  return folder ? folder.uri.fsPath : undefined;
}

async function validateJava() {
  const configured = vscode.workspace.getConfiguration("bickspec").get("javaPath");
  const command = configured && configured.trim() ? configured.trim() : "java";
  const result = await execFile(command, ["-version"]);
  const raw = result.combined.trim();
  if (result.error) {
    return {
      status: "error",
      command,
      rawOutput: raw,
      suggestion: "Java is required to run the bundled BickSpec compiler.",
      details: "Install Java 21 or configure bickspec.javaPath."
    };
  }
  const match = raw.match(/version\s+"([^"]+)"/i) || raw.match(/openjdk\s+([0-9][^\s]*)/i);
  const version = match ? match[1] : "unknown";
  const majorMatch = version.match(/^1\.(\d+)/) || version.match(/^(\d+)/);
  const major = majorMatch ? Number(majorMatch[1]) : undefined;
  return {
    status: major === 21 ? "success" : "warning",
    command,
    version,
    rawOutput: raw,
    suggestion: major === 21 ? "Java detected: 21.x" : "Java detected, but Java 21 is recommended."
  };
}

function configuredRepoPath() {
  const root = workspaceRoot();
  return resolveConfiguredPath(vscode.workspace.getConfiguration("bickspec.compiler").get("repoPath"), root);
}

function isRepositoryRoot(folder) {
  if (!folder) {
    return false;
  }
  return fs.existsSync(path.join(folder, "app", "pom.xml"))
    && fs.existsSync(path.join(folder, "app", "src"))
    && fs.existsSync(path.join(folder, "docs", "BickSpec.g4"));
}

function findContainingRepository(folder) {
  let current = folder;
  while (current && current !== path.dirname(current)) {
    if (isRepositoryRoot(current)) {
      return current;
    }
    current = path.dirname(current);
  }
  return undefined;
}

async function validateTool(command, args, missingSuggestion) {
  const result = await execFile(command, args);
  return result.error
    ? { status: "error", available: false, rawOutput: result.combined.trim(), suggestion: missingSuggestion }
    : { status: "success", available: true, rawOutput: result.combined.trim() };
}

async function validateRepository() {
  const repoPath = configuredRepoPath();
  if (!repoPath) {
    return { status: "warning", repoPath: "", suggestion: "Repository path is optional. Select it if you want clone/build helpers." };
  }
  const required = [
    path.join(repoPath, "app", "pom.xml"),
    path.join(repoPath, "docs", "BickSpec.g4"),
    path.join(repoPath, "app", "src")
  ];
  const missing = required.filter(item => !fs.existsSync(item));
  const jarPath = path.join(repoPath, "app", "target", JAR_NAME);
  return missing.length
    ? { status: "error", repoPath, missing, jarPath, suggestion: "Selected folder is not a valid bickspec-lang repository." }
    : { status: "success", repoPath, jarPath, jarExists: fs.existsSync(jarPath), suggestion: fs.existsSync(jarPath) ? "Repository looks valid." : "Repository looks valid; build the compiler to create the JAR." };
}

async function findCompilerJar(root) {
  const config = vscode.workspace.getConfiguration("bickspec.compiler");
  return findCompilerJarShared({
    basePath: root,
    configuredJarPath: config.get("jarPath")
  });
}

async function validateCompilerJar() {
  const root = workspaceRoot();
  const compiler = await findCompilerJar(root);
  if (!compiler.path) {
    return {
      status: "error",
      jarPath: "",
      source: "missing",
      bundledJarPath: getBundledCompilerPath(),
      displayPath: BUNDLED_COMPILER_RELATIVE_PATH.replace(/\\/g, "/"),
      suggestion: compiler.invalidConfiguredJarPath
        ? `Configured compiler JAR was not found: ${compiler.invalidConfiguredJarPath}`
        : `Bundled compiler missing. Expected ${BUNDLED_COMPILER_RELATIVE_PATH.replace(/\\/g, "/")}.`
    };
  }
  const java = await validateJava();
  if (java.status === "error") {
    return {
      status: "warning",
      jarPath: compiler.path,
      source: compiler.source,
      bundledJarPath: getBundledCompilerPath(),
      displayPath: displayCompilerPath(compiler.path),
      suggestion: compiler.source === "bundled"
        ? "Bundled BickSpec compiler detected, but Java is unavailable so it could not be executed."
        : compiler.source === "custom"
          ? "Custom compiler JAR detected, but Java is unavailable so it could not be executed."
          : "Developer fallback compiler detected, but Java is unavailable so it could not be executed."
    };
  }
  const command = vscode.workspace.getConfiguration("bickspec").get("javaPath") || "java";
  const probe = await execFile(command, ["-jar", compiler.path], { cwd: root || path.dirname(compiler.path) });
  const launchFailure = /(unable to access jarfile|invalid or corrupt jarfile|no main manifest attribute|could not find or load main class)/i.test(probe.combined);
  return {
    status: launchFailure ? "error" : "success",
    jarPath: compiler.path,
    source: compiler.source,
    bundledJarPath: getBundledCompilerPath(),
    displayPath: displayCompilerPath(compiler.path),
    detectedVersion: probe.combined.match(/(\d+\.\d+\.\d+)/)?.[1],
    rawOutput: probe.combined.trim(),
    suggestion: launchFailure
      ? "The compiler JAR exists but Java could not execute it."
      : compiler.source === "bundled"
        ? "Bundled BickSpec compiler detected."
        : compiler.source === "custom"
          ? "Custom compiler JAR detected."
          : "Developer fallback compiler detected."
  };
}

async function validateWorkspace() {
  const root = workspaceRoot();
  if (!root) {
    return { status: "warning", workspacePath: "", writable: false, suggestion: "Open a folder in VS Code to compile BickSpec files." };
  }
  const outputSetting = vscode.workspace.getConfiguration("bickspec").get("outputDirectory") || "output";
  const outputFolder = resolveConfiguredPath(outputSetting, root);
  const setupDir = path.join(root, ".bickspec", "setup");
  try {
    await fs.promises.mkdir(outputFolder, { recursive: true });
    await fs.promises.mkdir(setupDir, { recursive: true });
    const probe = path.join(setupDir, ".write-test");
    await fs.promises.writeFile(probe, "ok");
    await fs.promises.unlink(probe);
    return { status: "success", workspacePath: root, writable: true, outputFolder, setupDir, suggestion: "Workspace is ready." };
  } catch (error) {
    return { status: "error", workspacePath: root, writable: false, outputFolder, setupDir, suggestion: `Workspace is not writable: ${error.message}` };
  }
}

async function runSetupTest() {
  const workspace = await validateWorkspace();
  const compiler = await validateCompilerJar();
  const java = await validateJava();
  if (workspace.status !== "success" || compiler.status !== "success" || java.status === "error") {
    return { status: "error", workspace, compiler, java, buildLog: "", programOutput: "", diagnostics: [], suggestion: "Resolve Java, compiler, and workspace issues before running the setup test." };
  }
  const filePath = path.join(workspace.setupDir, "Setup_Test.bks");
  await fs.promises.writeFile(filePath, SETUP_SOURCE, "utf8");
  const command = vscode.workspace.getConfiguration("bickspec").get("javaPath") || "java";
  const result = await execFile(command, ["-jar", compiler.jarPath, filePath], { cwd: workspace.workspacePath });
  const output = result.combined.trim();
  const success = result.code === 0 && /\[STATUS\]\s+PARSE OK/.test(output) && /\[STATUS\]\s+SEMANTIC OK/.test(output);
  const lines = output.split(/\r?\n/);
  const outputStart = lines.findIndex(line => line.includes("PROGRAM OUTPUT"));
  const outputEnd = outputStart >= 0 ? lines.findIndex((line, index) => index > outputStart && /^\+[-]+\+$/.test(line.trim())) : -1;
  const programOutput = outputStart >= 0 && outputEnd > outputStart
    ? lines.slice(outputStart + 1, outputEnd)
      .map(line => line.replace(/^\|\s?/, "").replace(/\s?\|$/, "").trimEnd())
      .filter(Boolean)
      .join("\n")
    : lines.filter(line => !line.startsWith("[") && line.trim()).join("\n");
  return {
    status: success ? "success" : "error",
    filePath,
    buildLog: output,
    programOutput,
    diagnostics: output.split(/\r?\n/).filter(line => /^\[ERROR\]/.test(line)),
    suggestion: success ? "Setup test compiled successfully." : "Setup test failed. Review the compiler output and fix the reported issue."
  };
}

async function buildCompiler() {
  const repo = await validateRepository();
  if (repo.status !== "success") {
    return { status: "error", rawOutput: "", suggestion: repo.suggestion };
  }
  const maven = await validateTool("mvn", ["-version"], "Maven was not found.");
  if (!maven.available) {
    return { status: "error", rawOutput: maven.rawOutput, suggestion: "Maven is required to build the compiler from source." };
  }
  const result = await execFile("mvn", ["-f", path.join(repo.repoPath, "app", "pom.xml"), "package"], { cwd: repo.repoPath });
  return {
    status: result.error ? "error" : "success",
    rawOutput: result.combined.trim(),
    suggestion: result.error ? "Maven build failed." : "Compiler build completed."
  };
}

async function cloneRepository() {
  const logs = ["Checking Git..."];
  const git = await validateTool("git", ["--version"], "Git was not found.");
  if (!git.available) {
    return { status: "error", suggestion: "Git is required to clone the repository.", rawOutput: `${logs.join("\n")}\n${git.rawOutput}` };
  }
  const selection = await vscode.window.showOpenDialog({
    canSelectFiles: false,
    canSelectFolders: true,
    canSelectMany: false,
    title: "Select parent folder where bickspec-lang will be cloned",
    openLabel: "Select Parent Folder"
  });
  if (!selection || !selection[0]) {
    return { status: "warning", suggestion: "Clone cancelled.", rawOutput: logs.join("\n") };
  }
  const selected = selection[0].fsPath;
  if (isRepositoryRoot(selected)) {
    logs.push(`Repository found: ${selected}`);
    await persistRepositorySelection(selected);
    return repoSelectionResult(selected, logs, "Existing repository selected.");
  }
  const containingRepo = findContainingRepository(selected);
  if (containingRepo) {
    return {
      status: "warning",
      suggestion: "This folder appears to be inside an existing bickspec-lang repository. Select the repository root or its parent folder.",
      rawOutput: `${logs.join("\n")}\nRepository root detected: ${containingRepo}`
    };
  }
  const parent = selected;
  const destination = path.join(parent, "bickspec-lang");
  if (fs.existsSync(destination)) {
    if (isRepositoryRoot(destination)) {
      logs.push(`Repository found: ${destination}`);
      const choice = await vscode.window.showInformationMessage(
        "A bickspec-lang repository already exists here.",
        "Use Existing",
        "Update"
      );
      if (choice === "Update") {
        logs.push("Updating repository...");
        const update = await execFile("git", ["pull"], { cwd: destination });
        logs.push(update.error ? "Update failed." : "Repository updated.");
        if (update.error) {
          return {
            status: "error",
            repoPath: destination,
            rawOutput: `${logs.join("\n")}\n${update.combined.trim()}`,
            suggestion: "Repository update failed."
          };
        }
      } else if (choice !== "Use Existing") {
        return { status: "warning", repoPath: destination, rawOutput: logs.join("\n"), suggestion: "Repository selection cancelled." };
      }
      await persistRepositorySelection(destination);
      return repoSelectionResult(destination, logs, "Existing repository found; using it.");
    }
    return { status: "error", suggestion: `Destination already exists and is not a valid bickspec-lang repository: ${destination}`, rawOutput: logs.join("\n") };
  }
  const url = vscode.workspace.getConfiguration("bickspec.compiler").get("githubUrl");
  logs.push(`Cloning repository to: ${destination}`);
  const result = await execFile("git", ["clone", url, destination], { cwd: parent });
  if (!result.error) {
    await persistRepositorySelection(destination);
  }
  logs.push(result.error ? "Clone failed." : `Cloned successfully to: ${destination}`);
  return result.error
    ? { status: "error", repoPath: destination, rawOutput: `${logs.join("\n")}\n${result.combined.trim()}`, suggestion: "Clone failed." }
    : repoSelectionResult(destination, logs, "Repository cloned.");
}

async function selectRepository(folder) {
  if (isRepositoryRoot(folder)) {
    await persistRepositorySelection(folder);
    return repoSelectionResult(folder, [`Repository root detected: ${folder}`], "Repository selected.");
  }
  const containingRepo = findContainingRepository(folder);
  if (containingRepo) {
    return {
      status: "warning",
      repoPath: folder,
      rawOutput: `Repository root detected: ${containingRepo}`,
      suggestion: "You selected a subfolder. Please select the bickspec-lang root folder."
    };
  }
  return {
    status: "error",
    repoPath: folder,
    rawOutput: "",
    suggestion: "Selected folder does not look like the bickspec-lang repository root."
  };
}

async function persistRepositorySelection(repoPath) {
  await vscode.workspace.getConfiguration("bickspec.compiler").update("repoPath", repoPath, vscode.ConfigurationTarget.Global);
  const jarPath = path.join(repoPath, "app", "target", JAR_NAME);
  if (fs.existsSync(jarPath)) {
    await vscode.workspace.getConfiguration("bickspec.compiler").update("jarPath", jarPath, vscode.ConfigurationTarget.Global);
  }
}

function repoSelectionResult(repoPath, logs, suggestion) {
  const jarPath = path.join(repoPath, "app", "target", JAR_NAME);
  const jarExists = fs.existsSync(jarPath);
  logs.push(jarExists ? `Compiler JAR found: ${jarPath}` : "Compiler JAR not found. Build required.");
  return {
    status: "success",
    repoPath,
    jarPath,
    jarExists,
    rawOutput: logs.join("\n"),
    suggestion
  };
}

async function openJavaInstallGuide() {
  const platform = process.platform;
  if (platform === "win32") {
    const winget = await validateTool("winget", ["--version"], "winget was not found.");
    if (winget.available) {
      const choice = await vscode.window.showInformationMessage(
        "Java is required to run the bundled BickSpec compiler.",
        "Install with winget",
        "Open Adoptium Page"
      );
      if (choice === "Install with winget") {
        const terminal = vscode.window.createTerminal({ name: "Install Java 21" });
        terminal.show();
        terminal.sendText("winget install EclipseAdoptium.Temurin.21.JDK --accept-package-agreements --accept-source-agreements");
        return { status: "success", suggestion: "Launched winget install for Eclipse Temurin JDK 21." };
      }
      if (choice === "Open Adoptium Page") {
        await vscode.env.openExternal(vscode.Uri.parse("https://adoptium.net/temurin/releases/?version=21"));
        return { status: "success", suggestion: "Opened the Adoptium Java 21 download page." };
      }
      return { status: "warning", suggestion: "Java installation was cancelled." };
    }
    await vscode.env.openExternal(vscode.Uri.parse("https://adoptium.net/temurin/releases/?version=21"));
    return { status: "warning", suggestion: "winget was not found. Opened the official Adoptium Java 21 page." };
  }

  if (platform === "darwin") {
    await vscode.env.openExternal(vscode.Uri.parse("https://adoptium.net/temurin/releases/?version=21"));
    vscode.window.showInformationMessage("Install Java 21 from Adoptium. Apple Silicon: choose macOS arm64/aarch64 PKG. Intel: choose macOS x64 PKG.");
    return { status: "success", suggestion: "Opened the official Adoptium Java 21 page with macOS guidance." };
  }

  await vscode.env.openExternal(vscode.Uri.parse("https://adoptium.net/installation/"));
  return { status: "success", suggestion: "Opened the official Adoptium installation page." };
}

function findCompilerJarShared(options) {
  return resolveCompilerJar(options);
}

function displayCompilerPath(compilerPath) {
  const bundled = getBundledCompilerPath();
  if (compilerPath === bundled) {
    return BUNDLED_COMPILER_RELATIVE_PATH.replace(/\\/g, "/");
  }
  return compilerPath;
}

module.exports = {
  SETUP_SOURCE,
  buildCompiler,
  cloneRepository,
  findCompilerJar,
  openJavaInstallGuide,
  runSetupTest,
  selectRepository,
  validateCompilerJar,
  validateJava,
  validateRepository,
  validateTool,
  validateWorkspace
};
