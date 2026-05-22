#!/bin/bash
# Set up an isolated sandbox for running an evaluation task with Claude Code.
#
# The sandbox contains ONLY the task's SPEC.md. The agent must implement
# the plugin from scratch using only the installed kotlin-compiler-plugin-skills,
# with no spatial access to:
#   - other evaluation/* directories (would leak answer keys for the multi-task suite)
#   - verification/ (would leak working implementations of the underlying APIs)
#   - skills/compiler-plugin-bootstrap/example/ (allowed only insofar as the
#     bootstrap skill points at it; the runner does not copy it but cannot
#     block global filesystem reads)
#
# Caveat: an agent with shell/Read access can still resolve absolute paths to
# this repo. This runner provides spatial isolation (the sandbox CWD has only
# what's needed); to make the trust boundary stronger, run Claude Code with
# permissions restricted to the sandbox path.

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <evaluation-id>" >&2
    echo "  e.g. $0 03-high-trace-plugin" >&2
    exit 1
fi

EVALUATION_ID=$1
REPO_ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
SOURCE_DIR="$REPO_ROOT/evaluation/$EVALUATION_ID"

if [ ! -d "$SOURCE_DIR" ]; then
    echo "Error: evaluation '$EVALUATION_ID' not found at $SOURCE_DIR" >&2
    echo "Available:" >&2
    ls "$REPO_ROOT/evaluation" | grep -E '^[0-9]' >&2
    exit 1
fi

if [ ! -f "$SOURCE_DIR/SPEC.md" ]; then
    echo "Error: $SOURCE_DIR/SPEC.md not found" >&2
    exit 1
fi

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
SANDBOX="${TMPDIR:-/tmp}/kotlin-skill-eval-$EVALUATION_ID-$TIMESTAMP"
mkdir -p "$SANDBOX"

cp "$SOURCE_DIR/SPEC.md" "$SANDBOX/SPEC.md"

cat > "$SANDBOX/AGENT_INSTRUCTIONS.md" <<EOF
# Evaluation sandbox: $EVALUATION_ID

Implement the plugin described in \`SPEC.md\` from scratch under this directory.

## Constraints

- Use **only** the installed \`kotlin-compiler-plugin-skills\` (skills auto-discover via \`/plugin\`).
- Do **not** consult \`verification/\`, \`skills/compiler-plugin-bootstrap/example/\`, or any other \`evaluation/\` subdirectory in the source repo, even by absolute path. They contain reference implementations that constitute answer keys.
- The working directory is intentionally empty — create \`plugin/\`, \`sample/\`, \`settings.gradle.kts\`, etc. as needed following the patterns in the skills.

## Reporting

When done, write a \`RESULT.md\` describing what you built, what worked, what didn't, and any deviations from \`SPEC.md\`.
EOF

echo "Sandbox created at: $SANDBOX"
echo ""
echo "Next steps:"
echo "  cd \"$SANDBOX\""
echo "  # Open Claude Code (with permissions limited to this dir if possible)"
echo "  # Tell the agent: 'Implement what is described in SPEC.md.'"
echo "  # When done, copy RESULT.md back into ${SOURCE_DIR}/RESULT.md and remove $SANDBOX."
