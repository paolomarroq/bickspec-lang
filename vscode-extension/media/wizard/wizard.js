const vscode = acquireVsCodeApi();
const steps = ["environment", "compiler", "workspace", "test", "ready"];
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
  if (step === "compiler") return state.compiler.status === "success";
  if (step === "workspace") return state.workspace.status === "success";
  if (step === "test") return state.canFinish;
  return true;
}

function continueAttrs(step, reason) {
  return canContinue(step) ? "" : `disabled title="${esc(reason)}"`;
}

function resultBlock(kind) {
  if (!lastResult || lastResult.kind !== kind) return "";
  const result = lastResult.result;
  const heading = kind === "test" ? "build log" : `${kind} status`;
  return `<div class="terminal"><header>${heading}</header><pre>${esc(result.buildLog || result.rawOutput || result.suggestion || "")}</pre></div>${result.programOutput ? `<p class="note">Program Output:<br>${esc(result.programOutput).replace(/\n/g, "<br>")}</p>` : ""}`;
}

const screens = {
  environment: () => `
    <h1>Java Check</h1>
    <p>${state.java.status === "error" ? "Java is required to run the bundled BickSpec compiler." : "Java was detected. Java 21 is recommended for the bundled compiler flow."}</p>
    <div class="grid">
      <div class="card"><span class="label">Java</span><div class="value">${badge(state.java)} · ${esc(state.java.version || "not found")}</div></div>
      <div class="card"><span class="label">Command</span><div class="value">${esc(state.java.command || "java")}</div></div>
    </div>
    <div class="terminal"><header>terminal - java -version</header><pre>${esc(state.java.rawOutput || state.java.suggestion || "")}</pre></div>
    ${state.java.details ? `<p class="note">${esc(state.java.details)}</p>` : ""}
    ${resultBlock("java")}
    ${resultBlock("javaInstall")}`,
  compiler: () => `
    <h1>Bundled Compiler Check</h1>
    <p>${state.compiler.status === "success" && state.compiler.source === "bundled" ? "Bundled BickSpec compiler detected." : "The extension prefers the bundled compiler by default and only uses repository fallbacks for developer scenarios."}</p>
    <div class="grid">
      <div class="card wide"><span class="label">Bundled Compiler</span><div class="value">${badge(state.compiler)} · ${esc(state.compiler.displayPath || "media/compiler/bickspec-compiler-1.0.0.jar")}</div></div>
      <div class="card"><span class="label">Resolved Source</span><div class="value">${esc(state.compiler.source || "missing")}</div></div>
      <div class="card"><span class="label">Resolved Path</span><div class="value">${esc(state.compiler.jarPath || state.compiler.bundledJarPath || "missing")}</div></div>
    </div>
    ${resultBlock("compiler")}
    <details class="advanced">
      <summary>Advanced</summary>
      <p>Optional developer-only actions. Normal users do not need Git, Maven, or a local repository clone.</p>
      <div class="grid">
        <div class="card"><span class="label">Custom Compiler JAR</span><div class="value">${esc(state.compiler.jarPath || "Not selected")}</div></div>
        <div class="card"><span class="label">Repository</span><div class="value">${badge(state.repository)} · ${esc(state.repository.repoPath || "Not selected")}</div></div>
        <div class="card"><span class="label">Git</span><div class="value">${badge(state.git)}</div></div>
        <div class="card"><span class="label">Maven</span><div class="value">${badge(state.maven)}</div></div>
      </div>
      ${resultBlock("repo")}
      ${resultBlock("clone")}
      ${resultBlock("build")}
    </details>`,
  workspace: () => `
    <h1>Workspace Check</h1>
    <p>The workspace must be writable so BickSpec can create compiler output and a temporary setup test file.</p>
    <div class="grid">
      <div class="card wide"><span class="label">Workspace</span><div class="value">${badge(state.workspace)} · ${esc(state.workspace.workspacePath || "no folder open")}</div></div>
      <div class="card wide"><span class="label">Output Folder</span><div class="value">${esc(state.workspace.outputFolder || "unavailable")}</div></div>
    </div>
    ${resultBlock("workspace")}`,
  test: () => `
    <h1>Setup Test</h1>
    <p>The wizard writes a valid non-interactive <code>.bks</code> sample and runs the real compiler against it.</p>
    ${resultBlock("test")}`,
  ready: () => `
    <h1>Ready</h1>
    <p>${state.completed ? "Setup is complete. Open a .bks file and run BickSpec: Run Current File." : "Run a successful setup test, then finish to persist setup."}</p>
    <div class="grid">
      <div class="card">Bundled compiler</div><div class="card">Problems diagnostics</div>
      <div class="card">Integrated terminal for READ</div><div class="card">Generated artifacts</div>
    </div>`
};

const actions = {
  environment: () => `<div class="button-group"><button class="action" data-action="installJava">Install Java</button><button class="action" data-action="selectJava">Select Java Manually</button><button class="action" data-action="validateJava">Re-check Java</button></div><div class="button-group"><button class="action primary" data-nav="1" ${continueAttrs("environment", "Java must be available before continuing")}>Continue</button></div>`,
  compiler: () => `<div class="button-group"><button class="action" data-nav="-1">Back</button><button class="action" data-action="validateCompiler">Re-check Bundled Compiler</button><button class="action" data-action="selectJar">Select Custom Compiler JAR</button><button class="action" data-action="selectRepo">Select bickspec-lang Repository</button><button class="action" data-action="cloneRepo" ${state.git.available ? "" : "disabled title='Git is unavailable'"}>Clone/Update Repository</button><button class="action" data-action="buildCompiler" ${state.maven.available && state.repository.status === "success" ? "" : "disabled title='Requires Maven and a valid repository root'"}>Build Compiler with Maven</button></div><div class="button-group"><button class="action primary" data-nav="1" ${continueAttrs("compiler", "A valid compiler JAR is required before continuing")}>Continue</button></div>`,
  workspace: () => `<div class="button-group"><button class="action" data-nav="-1">Back</button><button class="action" data-action="validateWorkspace">Validate Workspace</button></div><div class="button-group"><button class="action primary" data-nav="1" ${continueAttrs("workspace", "Open a writable workspace folder before continuing")}>Continue</button></div>`,
  test: () => `<div class="button-group"><button class="action" data-nav="-1">Back</button><button class="action" data-action="runTest">Run Setup Test</button><button class="action" data-action="openSample" ${state.workspace.status === "success" ? "" : "disabled title='Requires a writable workspace'"}>Open Sample File</button></div><div class="button-group"><button class="action primary" data-nav="1" ${continueAttrs("test", "Run a successful setup test before continuing")}>Continue</button></div>`,
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
