#!/bin/bash
# Bump every GitHub permalink and version mention from one Kotlin tag to another
# across skills/ and top-level docs.
#
# Usage:
#   scripts/bump_kotlin_version.sh v2.3.20 v2.3.21
#
# Does NOT touch:
#   - evaluation/* (historical run records)
#   - skills/*/CHANGES.md (Kotlin version migration history)
#
# After running, verify all linked paths resolve at the new tag, e.g.:
#   git -C $KOTLIN_REPO ls-tree -r --name-only <new-tag> > /tmp/files.txt
#   grep -hoE 'blob/<new-tag>/[^)]+' skills/*/*.md | sed 's|.*<new-tag>/||;s|#.*||' | sort -u | \
#     while read p; do grep -qx "$p" /tmp/files.txt || echo "MISSING: $p"; done

set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Usage: $0 <old-tag> <new-tag>" >&2
  echo "  e.g. $0 v2.3.20 v2.3.21" >&2
  exit 1
fi

OLD=$1
NEW=$2
OLD_VER=${OLD#v}
NEW_VER=${NEW#v}

REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"

# 1. URL replacements in skills (SKILL.md and EVIDENCE.md only — preserve CHANGES.md history)
find skills -type f \( -name SKILL.md -o -name EVIDENCE.md \) -print0 | \
  xargs -0 sed -i '' "s|/blob/${OLD}/|/blob/${NEW}/|g; s|/tree/${OLD}/|/tree/${NEW}/|g"

# 2. README compatibility matrix and CHANGELOG validated-against
sed -i '' "s|${OLD_VER}|${NEW_VER}|g" README.md CHANGELOG.md

echo "Replaced ${OLD} → ${NEW} in skills/*/{SKILL,EVIDENCE}.md, README.md, CHANGELOG.md."
echo "NOT touched: evaluation/*, skills/*/CHANGES.md (preserved as historical record)."
echo "Review code-sample version pins manually:"
grep -rln "${OLD_VER}" skills 2>/dev/null | grep -v CHANGES.md || true
