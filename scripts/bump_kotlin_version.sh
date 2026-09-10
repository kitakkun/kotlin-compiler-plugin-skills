#!/bin/bash
# Re-pin every GitHub permalink in the skill from one Kotlin tag to another.
#
# Usage:
#   scripts/bump_kotlin_version.sh v2.4.10 v2.4.20
#
# Touches only the permalink *tag segment* (`/blob/<tag>/`, `/tree/<tag>/`) in
#   skills/**/SKILL.md, skills/**/guide.md, skills/**/EVIDENCE.md
#
# Does NOT touch:
#   - skills/**/CHANGES.md      (Kotlin version migration history — old tags are intentional)
#   - README.md / CHANGELOG.md  (historical Compatibility-matrix rows and release notes —
#                                add the NEW row/entry by hand instead of rewriting old ones)
#   - evaluation/*, verification/* (historical run records)
#   - code-sample version pins such as `kotlin("jvm") version "2.4.10"` — see the list
#     printed at the end and update them deliberately
#   - cited LINE NUMBERS — run scripts/check_citation_drift.sh afterwards
#
# Typical sequence for a version bump (see CONTRIBUTING.md "When Kotlin ships a new minor"):
#   scripts/bump_kotlin_version.sh v2.4.10 v2.4.20
#   scripts/check_citation_drift.sh ~/Documents/GitHub/kotlin-lang v2.4.10 v2.4.20
#   scripts/verify_citations.sh ~/Documents/GitHub/kotlin-lang

set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Usage: $0 <old-tag> <new-tag>" >&2
  echo "  e.g. $0 v2.4.10 v2.4.20" >&2
  exit 1
fi

OLD=$1
NEW=$2
OLD_VER=${OLD#v}
NEW_VER=${NEW#v}

REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"

find skills -type f \( -name SKILL.md -o -name guide.md -o -name EVIDENCE.md \) -print0 | \
  xargs -0 sed -i '' "s|/blob/${OLD}/|/blob/${NEW}/|g; s|/tree/${OLD}/|/tree/${NEW}/|g"

echo "Re-pinned permalinks ${OLD} → ${NEW} in skills/**/{SKILL,guide,EVIDENCE}.md."
echo "NOT touched: CHANGES.md, README.md, CHANGELOG.md, evaluation/, verification/."
echo
echo "Remaining literal mentions of ${OLD_VER} outside CHANGES.md (version pins, prose markers) — review each by hand:"
grep -rn --include=SKILL.md --include=guide.md --include=EVIDENCE.md --include='*.kts' "${OLD_VER}" skills 2>/dev/null | grep -v "/blob/${NEW}/" || true
