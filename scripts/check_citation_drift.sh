#!/bin/bash
# Detect cited-line drift between two Kotlin tags.
#
# scripts/verify_citations.sh only checks that a cited path EXISTS at the pinned
# tag and prints the first cited line; it cannot tell you that the code moved.
# This script compares the exact cited line range at <old-tag> and <new-tag> and
# flags every citation whose text changed, with candidate new line numbers.
#
# Usage (run AFTER scripts/bump_kotlin_version.sh <old-tag> <new-tag>, because it
# scans for permalinks already pinned to <new-tag>):
#   scripts/check_citation_drift.sh <kotlin-repo-path> <old-tag> <new-tag>
#   e.g. scripts/check_citation_drift.sh ~/Documents/GitHub/kotlin-lang v2.4.10 v2.4.20
#
# Output, one line per citation that needs attention:
#   DRIFT   <topic> <path>#L<start>-L<end> | old: '<first cited line at old-tag>' | now at: <line,line,...>
#   MISSING <topic> <path>       (path does not exist at <new-tag> — moved or deleted)
# Silent citations are byte-identical in the cited range at both tags.
#
# Run it ONCE, right after bump_kotlin_version.sh and BEFORE re-anchoring anything:
# it compares the text at the cited line numbers at both tags, so after you have
# moved an anchor to its new line the same citation will be reported as DRIFT
# again (the old tag has different text at the new line). Re-anchored citations
# are validated by scripts/verify_citations.sh (which prints the cited line at
# the new tag for you to eyeball), not by re-running this script.
#
# Both tags must be fetched in the clone (`git -C <clone> fetch --no-tags origin tag <tag>`).
# The clone's working tree is never read, so its checkout does not matter.
set -euo pipefail

KOTLIN=${1:?usage: check_citation_drift.sh <kotlin-repo-path> <old-tag> <new-tag>}
OLD=${2:?usage: check_citation_drift.sh <kotlin-repo-path> <old-tag> <new-tag>}
NEW=${3:?usage: check_citation_drift.sh <kotlin-repo-path> <old-tag> <new-tag>}
REPO_ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
cd "$REPO_ROOT"

grep -rHoE "blob/${NEW}/[^)#]+#L[0-9]+(-L[0-9]+)?" \
     skills/kotlin-compiler-plugin/SKILL.md \
     skills/kotlin-compiler-plugin/references/*/EVIDENCE.md \
     skills/kotlin-compiler-plugin/references/*/guide.md \
  | sort -u \
  | while IFS=: read -r file url; do
      # NB: the variable is deliberately not named `path` — zsh would clobber $PATH.
      src=$(printf '%s' "$url" | sed -E 's#blob/[^/]+/##; s/#L.*//')
      start=$(printf '%s' "$url" | sed -E 's/.*#L([0-9]+).*/\1/')
      end=$(printf '%s' "$url" | sed -nE 's/.*-L([0-9]+)$/\1/p'); [ -z "$end" ] && end=$start
      topic=$(printf '%s' "$file" | sed -E 's#.*/references/([^/]+)/.*#\1#; s#.*/SKILL.md#SKILL#')
      if ! git -C "$KOTLIN" cat-file -e "${NEW}:${src}" 2>/dev/null; then
        echo "MISSING $topic $src"
        continue
      fi
      old=$(git -C "$KOTLIN" show "${OLD}:${src}" 2>/dev/null | sed -n "${start},${end}p" || true)
      new=$(git -C "$KOTLIN" show "${NEW}:${src}" | sed -n "${start},${end}p")
      if [ "$old" != "$new" ]; then
        first=$(printf '%s\n' "$old" | head -1 | sed 's/^[[:space:]]*//')
        cand=$(git -C "$KOTLIN" show "${NEW}:${src}" | grep -nF -- "$first" | head -3 | cut -d: -f1 | paste -sd, -)
        echo "DRIFT   $topic ${src}#L${start}-L${end} | old: '${first}' | now at: ${cand:-none}"
      fi
    done
