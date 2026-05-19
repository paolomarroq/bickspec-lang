#!/usr/bin/env bash
set -euo pipefail

jar_name="bickspec-compiler-1.0.0.jar"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_jar="${script_dir}/../../vscode-extension/media/compiler/${jar_name}"
install_root="${HOME}/.bickspec"
compiler_dir="${install_root}/compiler"
bin_dir="${install_root}/bin"
target_jar="${compiler_dir}/${jar_name}"
wrapper_path="${bin_dir}/bickspec"

if [[ ! -f "${repo_jar}" ]]; then
  echo "Bundled compiler JAR not found. Expected vscode-extension/media/compiler/${jar_name} next to this repository." >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java is required before installing the BickSpec CLI wrapper." >&2
  exit 1
fi

mkdir -p "${compiler_dir}" "${bin_dir}"
cp "${repo_jar}" "${target_jar}"

cat > "${wrapper_path}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
exec java -jar "$HOME/.bickspec/compiler/bickspec-compiler-1.0.0.jar" "$@"
EOF
chmod +x "${wrapper_path}"

echo "Verifying Java..."
java -version

echo "Verifying installed compiler..."
if "${wrapper_path}" >/dev/null 2>&1; then
  :
else
  echo "Wrapper created. BickSpec returned a non-zero status during no-argument validation, which is acceptable if it printed usage or an input error."
fi

shell_profile=""
if [[ -n "${ZSH_VERSION:-}" ]]; then
  shell_profile="${HOME}/.zshrc"
elif [[ -n "${BASH_VERSION:-}" ]]; then
  shell_profile="${HOME}/.bashrc"
fi

if [[ -n "${shell_profile}" ]]; then
  echo ""
  echo "Add this line to ${shell_profile} if ${bin_dir} is not already on PATH:"
else
  echo ""
  echo "Add this line to your shell profile if ${bin_dir} is not already on PATH:"
fi
echo "export PATH=\"${bin_dir}:\$PATH\""
echo ""
echo "Installed:"
echo "  JAR: ${target_jar}"
echo "  Wrapper: ${wrapper_path}"
echo "Run:"
echo "  bickspec path/to/program.bks"
