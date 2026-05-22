#!/bin/bash
# Set up an isolated sandbox for re-running a verification task with Claude Code.
#
# The sandbox contains ONLY the verification's SPEC.md. The agent must
# implement the plugin from scratch using the installed kotlin-compiler-plugin-skills,
# without spatial access to:
#   - other verification/* directories (would leak the API patterns we want to test)
#   - evaluation/ (different concern, but isolated for symmetry)
#   - skills/compiler-plugin-bootstrap/example/ (allowed only via the bootstrap skill's pointer; the runner does not copy it)
#
# Use this when re-validating after a Kotlin compiler version bump, or to
# audit whether a skill change still suffices for an agent to implement
# the corresponding extension.
#
# Caveat: the agent retains shell/Read access to absolute paths. For a strict
# trust boundary, run Claude Code with permissions limited to the sandbox dir.

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <verification-id>" >&2
    echo "  e.g. $0 04-status-transformer" >&2
    exit 1
fi

VERIFICATION_ID=$1
REPO_ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
SOURCE_DIR="$REPO_ROOT/verification/$VERIFICATION_ID"

if [ ! -d "$SOURCE_DIR" ]; then
    echo "Error: verification '$VERIFICATION_ID' not found at $SOURCE_DIR" >&2
    echo "Available:" >&2
    ls "$REPO_ROOT/verification" | grep -E '^[0-9]' >&2
    exit 1
fi

if [ ! -f "$SOURCE_DIR/SPEC.md" ]; then
    echo "Error: $SOURCE_DIR/SPEC.md not found" >&2
    exit 1
fi

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
SANDBOX="${TMPDIR:-/tmp}/kotlin-skill-verify-$VERIFICATION_ID-$TIMESTAMP"
mkdir -p "$SANDBOX"

cp "$SOURCE_DIR/SPEC.md" "$SANDBOX/SPEC.md"

cat > "$SANDBOX/AGENT_INSTRUCTIONS.md" <<EOF
# Verification sandbox: $VERIFICATION_ID

Re-implement the plugin described in \`SPEC.md\` from scratch under this directory.

## Constraints

- Use **only** the installed \`kotlin-compiler-plugin-skills\`.
- Do **not** consult \`verification/\` (including the saved implementation of this very task), \`evaluation/\`, or \`skills/compiler-plugin-bootstrap/example/\` in the source repo.
- The working directory is intentionally empty — create \`plugin/\`, \`sample/\`, \`settings.gradle.kts\`, etc. as needed.

## Reporting

When done, write a \`RESULT.md\` describing PASS/FAIL per the criterion in \`SPEC.md\` and any deviations from the original implementation.
EOF

echo "Sandbox created at: $SANDBOX"
echo ""
echo "Next steps:"
echo "  cd \"$SANDBOX\""
echo "  # Open Claude Code (with permissions limited to this dir if possible)"
echo "  # Tell the agent: 'Implement what is described in SPEC.md.'"
echo "  # Compare its RESULT.md to the saved implementation at $SOURCE_DIR before deleting."
