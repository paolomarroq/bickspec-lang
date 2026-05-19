const fs = require("fs");
const path = require("path");

const JAR_NAME = "bickspec-compiler-1.0.0.jar";
const BUNDLED_COMPILER_RELATIVE_PATH = path.join("media", "compiler", JAR_NAME);

function resolveConfiguredPath(value, base) {
  if (!value || !value.trim()) {
    return undefined;
  }
  return path.isAbsolute(value) ? value : path.resolve(base || process.cwd(), value);
}

function getExtensionRoot() {
  return __dirname;
}

function getBundledCompilerPath() {
  return path.join(getExtensionRoot(), BUNDLED_COMPILER_RELATIVE_PATH);
}

function getDeveloperFallbackCandidates(basePath) {
  const candidates = [];
  if (basePath) {
    candidates.push(path.join(basePath, "app", "target", JAR_NAME));
    candidates.push(path.join(path.dirname(basePath), "app", "target", JAR_NAME));
  }
  candidates.push(path.resolve(getExtensionRoot(), "..", "app", "target", JAR_NAME));
  return [...new Set(candidates)];
}

function discoverCompilerJar(directory) {
  if (!directory || !fs.existsSync(directory)) {
    return undefined;
  }
  const matches = fs.readdirSync(directory)
    .filter(file => /^bickspec-compiler-.*\.jar$/.test(file) && !file.startsWith("original-"))
    .sort();
  return matches.length > 0 ? path.join(directory, matches[matches.length - 1]) : undefined;
}

function samePath(left, right) {
  return path.resolve(left).toLowerCase() === path.resolve(right).toLowerCase();
}

function isDeveloperFallbackPath(candidatePath, basePath) {
  if (!candidatePath) {
    return false;
  }
  const fallbackCandidates = getDeveloperFallbackCandidates(basePath);
  return fallbackCandidates.some(candidate => samePath(candidatePath, candidate));
}

function findCompilerJar(options = {}) {
  const configuredJarPath = resolveConfiguredPath(options.configuredJarPath, options.basePath);
  if (configuredJarPath && fs.existsSync(configuredJarPath) && !isDeveloperFallbackPath(configuredJarPath, options.basePath)) {
    return { path: configuredJarPath, source: "custom", exists: true };
  }

  const bundledJarPath = getBundledCompilerPath();
  if (fs.existsSync(bundledJarPath)) {
    return { path: bundledJarPath, source: "bundled", exists: true };
  }

  for (const candidate of getDeveloperFallbackCandidates(options.basePath)) {
    if (fs.existsSync(candidate)) {
      return { path: candidate, source: "developer", exists: true };
    }
  }

  for (const directory of getDeveloperFallbackCandidates(options.basePath).map(candidate => path.dirname(candidate))) {
    const discovered = discoverCompilerJar(directory);
    if (discovered) {
      return { path: discovered, source: "developer", exists: true };
    }
  }

  return {
    path: undefined,
    source: undefined,
    exists: false,
    invalidConfiguredJarPath: configuredJarPath,
    bundledJarPath,
    developerCandidates: getDeveloperFallbackCandidates(options.basePath)
  };
}

module.exports = {
  BUNDLED_COMPILER_RELATIVE_PATH,
  JAR_NAME,
  discoverCompilerJar,
  findCompilerJar,
  getBundledCompilerPath,
  isDeveloperFallbackPath,
  resolveConfiguredPath
};
