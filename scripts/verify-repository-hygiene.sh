#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
project_dir="$(CDPATH= cd -- "$script_dir/.." && pwd)"
cd "$project_dir"

tracked_temporary_files="$(git ls-files 'temp/**' 'tmp/**')"
if [[ -n "$tracked_temporary_files" ]]; then
  echo "Temporary files must never be tracked:" >&2
  printf '%s\n' "$tracked_temporary_files" >&2
  exit 1
fi

unexpected_markdown="$(git ls-files '*.md' '*.MD' | grep -v '^README\.md$' || true)"
if [[ -n "$unexpected_markdown" ]]; then
  echo "README.md is the only Markdown file allowed in the repository:" >&2
  printf '%s\n' "$unexpected_markdown" >&2
  exit 1
fi

for removed_path in package.json capacitor.config.ts src; do
  if [[ -e "$removed_path" ]]; then
    echo "Legacy web runtime found: $removed_path" >&2
    exit 1
  fi
done

empty_tree="$(git hash-object -t tree /dev/null)"
git diff --check "$empty_tree" HEAD -- . ':(exclude)android/gradlew.bat'
git diff --check
