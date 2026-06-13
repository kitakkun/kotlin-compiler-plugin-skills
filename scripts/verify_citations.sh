#!/bin/bash
# Verify that every GitHub permalink in SKILL.md / EVIDENCE.md resolves at its
# pinned tag AND that the cited line range still contains plausible content.
#
# Usage:
#   scripts/verify_citations.sh <kotlin-repo-path>
#   e.g. scripts/verify_citations.sh ~/Documents/GitHub/kotlin-lang
#
# For each `blob/<tag>/<path>#L<start>(-L<end>)` citation it prints:
#   OK   <topic> <path>:<start>  <first cited line of source>
#   MOVED/MISSING when the path does not exist at <tag>
#
# It does NOT judge semantic correctness — it surfaces the cited line so a human
# (or agent) can confirm the claim still matches. Pair with the EVIDENCE text.
set -euo pipefail

KOTLIN=${1:?usage: verify_citations.sh <kotlin-repo-path>}
REPO_ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
cd "$REPO_ROOT"

grep -rhoE 'blob/v[0-9.]+(-[A-Za-z0-9]+)?/[^)#]+#L[0-9]+(-L[0-9]+)?' \
     skills/kotlin-compiler-plugin/SKILL.md \
     skills/kotlin-compiler-plugin/references/*/EVIDENCE.md \
  | sort -u \
  | while IFS= read -r url; do
      tag=$(printf '%s' "$url" | sed -E 's#blob/([^/]+)/.*#\1#')
      path=$(printf '%s' "$url" | sed -E 's#blob/[^/]+/##; s/#L.*//')
      start=$(printf '%s' "$url" | sed -E 's/.*#L([0-9]+).*/\1/')
      if git -C "$KOTLIN" cat-file -e "${tag}:${path}" 2>/dev/null; then
        line=$(git -C "$KOTLIN" show "${tag}:${path}" | sed -n "${start}p" | sed 's/^[[:space:]]*//')
        printf 'OK    %-90s | %s\n' "${path}:${start}" "$line"
      else
        printf 'MISSING %s (%s)\n' "$path" "$tag"
      fi
    done
