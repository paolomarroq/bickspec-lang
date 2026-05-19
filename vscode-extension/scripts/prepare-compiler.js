const fs = require("fs");
const path = require("path");

const JAR_NAME = "bickspec-compiler-1.0.0.jar";
const repoRoot = path.resolve(__dirname, "..", "..");
const source = path.join(repoRoot, "app", "target", JAR_NAME);
const destination = path.join(repoRoot, "vscode-extension", "media", "compiler", JAR_NAME);

if (!source) {
  console.error("Compiler JAR not found. Build it first with:");
  console.error("mvn -f app/pom.xml package");
  process.exitCode = 1;
} else if (!fs.existsSync(source)) {
  console.error("Compiler JAR not found. Build it first with:");
  console.error("mvn -f app/pom.xml package");
  process.exitCode = 1;
} else {
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(source, destination);
  console.log(`Copied compiler JAR to ${destination}`);
}
