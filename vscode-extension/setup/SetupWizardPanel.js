const vscode = require("vscode");
const fs = require("fs");
const path = require("path");
const services = require("./setupServices");

class SetupWizardPanel {
  static currentPanel;

  static createOrShow(context) {
    const column = vscode.window.activeTextEditor ? vscode.window.activeTextEditor.viewColumn : undefined;
    if (SetupWizardPanel.currentPanel) {
      SetupWizardPanel.currentPanel.panel.reveal(column);
      return SetupWizardPanel.currentPanel;
    }
    const panel = vscode.window.createWebviewPanel("bickspecSetupWizard", "BickSpec Setup Wizard", column || vscode.ViewColumn.One, {
      enableScripts: true,
      localResourceRoots: [vscode.Uri.file(path.join(context.extensionPath, "media"))]
    });
    SetupWizardPanel.currentPanel = new SetupWizardPanel(panel, context);
    return SetupWizardPanel.currentPanel;
  }

  constructor(panel, context) {
    this.panel = panel;
    this.context = context;
    this.lastTestSucceeded = Boolean(context.globalState.get("bickspec.setup.lastTestSucceeded", false));
    this.panel.webview.html = this.getHtml();
    this.panel.onDidDispose(() => { SetupWizardPanel.currentPanel = undefined; });
    this.panel.webview.onDidReceiveMessage(message => this.handleMessage(message));
  }

  async handleMessage(message) {
    switch (message.command) {
      case "initialize":
      case "validateEnvironment":
        return this.postState(await this.collectState());
      case "validateJava":
        return this.postResult("java", await services.validateJava());
      case "validateCompiler":
        return this.postResult("compiler", await services.validateCompilerJar());
      case "validateWorkspace":
        return this.postResult("workspace", await services.validateWorkspace());
      case "selectJava":
        return this.selectJava();
      case "selectJar":
        return this.selectJar();
      case "selectRepo":
        return this.selectRepo();
      case "cloneRepo":
        this.postResult("clone", { status: "running", rawOutput: "Checking Git..." });
        return this.postResult("clone", await services.cloneRepository());
      case "buildCompiler":
        return this.postResult("build", await services.buildCompiler());
      case "runTest":
        {
          const result = await services.runSetupTest();
          this.lastTestSucceeded = result.status === "success";
          await this.context.globalState.update("bickspec.setup.lastTestSucceeded", this.lastTestSucceeded);
          return this.postResult("test", result);
        }
      case "openSample":
        return this.openSample();
      case "finishSetup":
        if (!this.lastTestSucceeded) {
          vscode.window.showWarningMessage("Run a successful setup test before finishing setup.");
          return this.postState(await this.collectState());
        }
        await this.context.globalState.update("bickspec.setup.completed", true);
        await vscode.workspace.getConfiguration("bickspec.setup").update("completed", true, vscode.ConfigurationTarget.Global);
        await this.persistResolvedOutputFolder();
        this.panel.dispose();
        return;
      case "skipSetup":
        this.panel.dispose();
        return;
      case "reset":
        await this.context.globalState.update("bickspec.setup.completed", false);
        await this.context.globalState.update("bickspec.setup.lastTestSucceeded", false);
        this.lastTestSucceeded = false;
        await vscode.workspace.getConfiguration("bickspec.setup").update("completed", false, vscode.ConfigurationTarget.Global);
        this.panel.webview.postMessage({ type: "resetComplete" });
        return this.postState(await this.collectState());
    }
  }

  async collectState() {
    const [java, compiler, repository, git, maven, workspace] = await Promise.all([
      services.validateJava(),
      services.validateCompilerJar(),
      services.validateRepository(),
      services.validateTool("git", ["--version"], "Git was not found."),
      services.validateTool("mvn", ["-version"], "Maven was not found."),
      services.validateWorkspace()
    ]);
    return {
      java,
      compiler,
      repository,
      git,
      maven,
      workspace,
      completed: Boolean(this.context.globalState.get("bickspec.setup.completed", false)),
      canFinish: this.lastTestSucceeded
    };
  }

  async selectJava() {
    const selection = await vscode.window.showOpenDialog({ canSelectFiles: true, canSelectFolders: false, canSelectMany: false, openLabel: "Select Java Executable" });
    if (selection && selection[0]) {
      await vscode.workspace.getConfiguration("bickspec").update("javaPath", selection[0].fsPath, vscode.ConfigurationTarget.Global);
    }
    return this.postState(await this.collectState());
  }

  async selectJar() {
    const selection = await vscode.window.showOpenDialog({ canSelectFiles: true, canSelectFolders: false, canSelectMany: false, filters: { "Java archive": ["jar"] }, openLabel: "Select Compiler JAR" });
    if (selection && selection[0]) {
      await vscode.workspace.getConfiguration("bickspec.compiler").update("jarPath", selection[0].fsPath, vscode.ConfigurationTarget.Global);
    }
    return this.postState(await this.collectState());
  }

  async selectRepo() {
    const selection = await vscode.window.showOpenDialog({
      canSelectFiles: false,
      canSelectFolders: true,
      canSelectMany: false,
      title: "Select the bickspec-lang repository root",
      openLabel: "Select Repository Root"
    });
    if (selection && selection[0]) {
      const result = await services.selectRepository(selection[0].fsPath);
      if (result.status !== "success") {
        vscode.window.showWarningMessage(result.suggestion);
      }
      return this.postResult("repo", result);
    }
    return this.postState(await this.collectState());
  }

  async persistResolvedOutputFolder() {
    const workspace = await services.validateWorkspace();
    if (workspace.status === "success" && workspace.outputFolder) {
      await vscode.workspace.getConfiguration("bickspec").update("outputDirectory", workspace.outputFolder, vscode.ConfigurationTarget.Global);
    }
  }

  async openSample() {
    const workspace = await services.validateWorkspace();
    if (workspace.status !== "success") {
      vscode.window.showWarningMessage(workspace.suggestion);
      return;
    }
    const file = path.join(workspace.setupDir, "Setup_Test.bks");
    if (!fs.existsSync(file)) {
      await fs.promises.writeFile(file, services.SETUP_SOURCE, "utf8");
    }
    const document = await vscode.workspace.openTextDocument(file);
    await vscode.window.showTextDocument(document);
  }

  postState(state) {
    this.panel.webview.postMessage({ type: "state", state });
  }

  postResult(kind, result) {
    this.panel.webview.postMessage({ type: "result", kind, result });
    return this.collectState().then(state => this.postState(state));
  }

  getHtml() {
    const webview = this.panel.webview;
    const cssUri = webview.asWebviewUri(vscode.Uri.file(path.join(this.context.extensionPath, "media", "wizard", "wizard.css")));
    const jsUri = webview.asWebviewUri(vscode.Uri.file(path.join(this.context.extensionPath, "media", "wizard", "wizard.js")));
    const nonce = getNonce();
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource}; script-src 'nonce-${nonce}';">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link href="${cssUri}" rel="stylesheet">
  <title>BickSpec Setup Wizard</title>
</head>
<body>
  <nav class="rail"><div>▤</div><div>⌕</div><div>⑂</div><div>▷</div><div class="active">⬡</div></nav>
  <header class="topbar"><strong>Extension Setup Wizard</strong><span>×</span></header>
  <main>
    <section class="wizard">
      <aside>
        <button data-step="welcome">01 Welcome</button>
        <button data-step="environment">02 Environment</button>
        <button data-step="compiler">03 Repository</button>
        <button data-step="compilerValidation">04 Compiler</button>
        <button data-step="workspace">05 Workspace</button>
        <button data-step="test">06 Test</button>
        <button data-step="ready">07 Ready</button>
      </aside>
      <article>
        <div id="screen"></div>
        <footer id="actions"></footer>
      </article>
    </section>
  </main>
  <footer class="status"><span id="systemStatus">System Status: Checking</span><span id="stepLabel">Step 1 of 5</span></footer>
  <script nonce="${nonce}" src="${jsUri}"></script>
</body>
</html>`;
  }
}

function getNonce() {
  let text = "";
  const possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  for (let i = 0; i < 32; i += 1) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  return text;
}

module.exports = SetupWizardPanel;
