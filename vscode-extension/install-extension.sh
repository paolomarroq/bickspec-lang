#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
vsix="$(find "${script_dir}" -maxdepth 1 -type f -name '*.vsix' -print0 | xargs -0 ls -t 2>/dev/null | head -n 1)"

if [[ -z "${vsix}" ]]; then
  echo "No .vsix file was found in ${script_dir}. Run npm run package:vsix first." >&2
  exit 1
fi

if ! command -v code >/dev/null 2>&1; then
  echo "VS Code CLI 'code' was not found in PATH. Install VS Code and enable the shell command first." >&2
  exit 1
fi

code --install-extension "${vsix}" --force
echo "Installed VSIX: $(basename "${vsix}")"
echo "Open VS Code normally, open a .bks file, and run 'BickSpec: Run Current File'."
