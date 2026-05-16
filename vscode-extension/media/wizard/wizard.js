const vscode = acquireVsCodeApi();
const steps = ["welcome", "environment", "compiler", "compilerValidation", "workspace", "test", "ready"];
let currentStep = 0;
let state;
let lastResult;

window.addEventListener("message", event => {
  if (event.data.type === "state") {
    state = event.data.state;
    render();
  }
  if (event.data.type === "result") {
    lastResult = event.data;
    render();
  }
  if (event.data.type === "resetComplete") {
    currentStep = 0;
    lastResult = undefined;
    render();
  }
});

document.querySelectorAll("[data-step]").forEach(button => {
  button.addEventListener("click", () => {
    const step = steps.indexOf(button.dataset.step);
    if (step >= 0) {
      currentStep = step;
      render();
    }
  });
});

vscode.postMessage({ command: "initialize" });

function render() {
  if (!state) {
    return;
  }
  document.querySelectorAll("[data-step]").forEach(button => {
    button.classList.toggle("active", button.dataset.step === steps[currentStep]);
  });
  document.getElementById("stepLabel").textContent = `Step ${currentStep + 1} of ${steps.length}`;
  document.getElementById("systemStatus").textContent = `System Status: ${overallStatus()}`;
  const step = steps[currentStep];
  document.getElementById("screen").innerHTML = screens[step]();
  document.getElementById("actions").innerHTML = actions[step]();
  bindActions();
}

function overallStatus() {
  if (state.java.status === "error" || state.compiler.status === "error" || state.workspace.status === "error") return "Needs attention";
  if (state.completed) return "Ready";
  return "In progress";
}
function badge(item) {
  return `<span class="status-${item.status}">${item.status.toUpperCase()}</span>`;
}
function esc(text = "") {
  return String(text).replace(/[&<>"]/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[char]));
}
function canContinue(step) {
  if (step === "environment") return state.java.status !== "error";
  if (step === "compiler") return state.repository.status !== "error";
  if (step === "compilerValidation") return state.compiler.status === "success";
  if (step === "workspace") return state.workspace.status === "success";
  if (step === "test") return state.canFinish;
  return true;
}
function continueAttrs(step, reason) {
  return canContinue(step) ? "" : `disabled title="${esc(reason)}"`;
}

const screens = {
  welcome: () => `
    <h1>Welcome to BickSpec</h1>
    <p>This wizard wires the real Java compiler into VS Code without changing the language you already use.</p>
    <div class="grid">
      <div class="card"><span class="label">Checks</span><div class="value">Java, compiler JAR, repository helpers, workspace, test compilation</div></div>
      <div class="card"><span class="label">Current setup</span><div class="value">${state.completed ? "Completed" : "Not completed yet"}</div></div>
    </div>`,
  environment: () => `
    <h1>Check Java Installation</h1>
    <p>BickSpec prefers Java 21. Git and Maven are only needed for clone/build helpers.</p>
    <div class="grid">
      <div class="card"><span class="label">Java</span><div class="value">${badge(state.java)} · ${esc(state.java.version || "not found")}</div></div>
      <div class="card"><span class="label">Command</span><div class="value">${esc(state.java.command || "")}</div></div>
      <div class="card"><span class="label">Git</span><div class="value">${badge(state.git)}</div></div>
      <div class="card"><span class="label">Maven</span><div class="value">${badge(state.maven)}</div></div>
    </div>
    <div class="terminal"><header>terminal — java -version</header><pre>${esc(state.java.rawOutput || state.java.suggestion)}</pre></div>
    ${resultBlock("java")}`,
  compiler: () => `
    <h1>Compiler Setup</h1>
    <p>Select an existing JAR directly, or point the wizard at the repository root if you want build helpers.</p>
    <div class="grid">
      <div class="card wide"><span class="label">Repository</span><div class="value">${badge(state.repository)} · ${esc(state.repository.repoPath || "optional")}</div></div>
    </div>
    ${resultBlock("repo")}
    ${resultBlock("clone")}`,
  compilerValidation: () => `
    <h1>Validate Compiler</h1>
    <p>The JAR may be selected directly or discovered from the repository at <code>app/target/${esc("bickspec-compiler-1.0.0.jar")}</code>.</p>
    <div class="grid">
      <div class="card wide"><span class="label">Compiler JAR</span><div class="value">${badge(state.compiler)} · ${esc(state.compiler.jarPath || "not configured")}</div></div>
    </div>
    ${resultBlock("compiler")}
    ${resultBlock("build")}`,
  workspace: () => `
    <h1>Workspace Setup</h1>
    <p>The workspace must be writable so BickSpec can create output and a temporary setup test file.</p>
    <div class="grid">
      <div class="card wide"><span class="label">Workspace</span><div class="value">${badge(state.workspace)} · ${esc(state.workspace.workspacePath || "no folder open")}</div></div>
      <div class="card wide"><span class="label">Output Folder</span><div class="value">${esc(state.workspace.outputFolder || "unavailable")}</div></div>
    </div>
    ${resultBlock("workspace")}`,
  test: () => `
    <h1>Run Test Compilation</h1>
    <p>The wizard writes a valid non-interactive <code>.bks</code> sample and runs the real compiler against it.</p>
    ${resultBlock("test")}`,
  ready: () => `
    <h1>BickSpec is ready</h1>
    <p>${state.completed ? "Setup is complete. You can now open, edit, compile, and run .bks files directly in VS Code." : "Run a successful setup test, then finish to persist setup."}</p>
    <div class="grid">
      <div class="card">Syntax highlighting</div><div class="card">Snippets</div>
      <div class="card">Diagnostics</div><div class="card">Compile file</div>
      <div class="card">Run file</div><div class="card">Generate Java</div>
    </div>`
};

function resultBlock(kind) {
  if (!lastResult || lastResult.kind !== kind) return "";
  const r = lastResult.result;
  const heading = kind === "test" ? "build log" : `${kind} status`;
  return `<div class="terminal"><header>${heading}</header><pre>${esc(r.buildLog || r.rawOutput || r.suggestion || "")}</pre></div>${r.programOutput ? `<p class="note">Program Output:<br>${esc(r.programOutput).replace(/\n/g, "<br>")}</p>` : ""}`;
}

const actions = {
  welcome: () => `<div class="button-group"><button class="action" data-action="reset">Reset Setup</button></div><div class="button-group"><button class="action primary" data-nav="1">Start Setup</button></div>`,
  environment: () => `<div class="button-group"><button class="action" data-nav="-1">Back</button><button class="action" data-action="validateJava">Re-check Java</button><button class="action" data-action="selectJava">Select Java</button></div><div class="button-group"><button class="action primary" data-nav="1" ${continueAttrs("environment", "Java must be available before continuing")}>Continue</button></div>`,
  compiler: () => `<div class="button-group"><button class="action" data-nav="-1">Back</button><button class="action" data-action="selectRepo">Select Repository</button><button class="action" data-action="cloneRepo" ${state.git.available ? "" : "disabled title='Git is unavailable'"}>Clone from GitHub</button></div><div class="button-group"><button class="action primary" data-nav="1" ${continueAttrs("compiler", "Select a valid repository root or leave the optional repository unset")}>Continue</button></div>`,
  compilerValidation: () => `<div class="button-group"><button class="action" data-nav="-1">Back</button><button class="action" data-action="selectJar">Select Compiler JAR</button><button class="action" data-action="validateCompiler">Validate Compiler</button><button class="action" data-action="buildCompiler" ${state.maven.available && state.repository.status === "success" ? "" : "disabled title='Requires Maven and a valid repository root'"}>Build Compiler</button></div><div class="button-group"><button class="action primary" data-nav="1" ${continueAttrs("compilerValidation", "A valid compiler JAR is required before continuing")}>Continue</button></div>`,
  workspace: () => `<div class="button-group"><button class="action" data-nav="-1">Back</button><button class="action" data-action="validateWorkspace">Validate Workspace</button></div><div class="button-group"><button class="action primary" data-nav="1" ${continueAttrs("workspace", "Open a writable workspace folder before continuing")}>Continue</button></div>`,
  test: () => `<div class="button-group"><button class="action" data-nav="-1">Back</button><button class="action" data-action="runTest">Run Test Compilation</button><button class="action" data-action="openSample" ${state.workspace.status === "success" ? "" : "disabled title='Requires a writable workspace'"}>Open Sample File</button></div><div class="button-group"><button class="action primary" data-nav="1" ${continueAttrs("test", "Run a successful setup test before continuing")}>Continue</button></div>`,
  ready: () => `<div class="button-group"><button class="action" data-nav="-1">Back</button><button class="action" data-action="reset">Reset Setup</button></div><div class="button-group"><button class="action" data-action="skipSetup">Skip for Now</button><button class="action primary" data-action="finishSetup" ${state.canFinish ? "" : "disabled title='Run a successful setup test first'"}>Finish</button></div>`
};

function bindActions() {
  document.querySelectorAll("[data-nav]").forEach(button => button.addEventListener("click", () => {
    if (button.disabled) return;
    currentStep = Math.max(0, Math.min(steps.length - 1, currentStep + Number(button.dataset.nav)));
    render();
  }));
  document.querySelectorAll("[data-action]").forEach(button => button.addEventListener("click", () => {
    if (button.disabled) return;
    vscode.postMessage({ command: button.dataset.action });
  }));
}
