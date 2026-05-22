#!/usr/bin/env python3
"""Convert backtick-enclosed file path citations in EVIDENCE.md / SKILL.md
to GitHub permalinks against a Kotlin release tag.

Idempotent: paths already inside [...](...) markdown links are skipped.

Run from the repo root:
    python3 scripts/link_citations.py skills/*/EVIDENCE.md
    python3 scripts/link_citations.py --tag v2.3.21 skills/*/SKILL.md

Configuration:
    KOTLIN_REPO env var or --repo flag — local clone of JetBrains/kotlin
    --tag flag — Kotlin release tag (e.g. v2.3.21)

Resolves shortened citations (e.g. `kotlin/fir/extensions/X.kt`) by
suffix-matching against `git ls-tree -r <tag>` and applying heuristics
(prefer src/ over gen/, drop test paths) when multiple matches exist.
"""
import argparse
import os
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path


def build_index(repo_path: str, tag: str):
    result = subprocess.run(
        ["git", "-C", repo_path, "ls-tree", "-r", "--name-only", tag],
        capture_output=True, text=True, check=True,
    )
    all_files = set(result.stdout.splitlines())
    suffix_index = defaultdict(list)
    for f in all_files:
        parts = f.split("/")
        for i in range(len(parts)):
            suffix_index["/".join(parts[i:])].append(f)
    return all_files, suffix_index


def pick_best(matches):
    if len(matches) == 1:
        return matches[0]

    def score(p):
        s = 0
        if "/src/" in p: s -= 2
        if "/gen/" in p: s += 1
        if "/test" in p.lower(): s += 5
        if "/testdata/" in p.lower(): s += 10
        s += len(p) * 0.001
        return s
    return sorted(matches, key=score)[0]


def resolve(path, all_files, suffix_index):
    p = path
    if p.startswith("kotlin/"):
        p = p[len("kotlin/"):]
    if "/.../" in p or p.endswith("/...") or p.startswith(".../"):
        if "/.../" in p:
            p = p.split("/.../")[-1]
        elif p.startswith(".../"):
            p = p[4:]
    if p in all_files:
        return p
    matches = suffix_index.get(p, [])
    if matches:
        return pick_best(matches)
    parts = p.split("/")
    for i in range(1, len(parts)):
        candidate = "/".join(parts[i:])
        cands = suffix_index.get(candidate, [])
        if cands:
            return pick_best(cands)
    return None


def make_link(display, repo_path, lines, gh_base):
    url = gh_base + repo_path
    if lines:
        nums = lines.lstrip(":")
        if "-" in nums and "," not in nums:
            a, b = nums.split("-")
            url += f"#L{a}-L{b}"
        elif "," in nums:
            first = nums.split(",")[0].strip()
            if "-" in first:
                a, b = first.split("-")
                url += f"#L{a}-L{b}"
            else:
                url += f"#L{first}"
        else:
            url += f"#L{nums}"
    return f"[`{display}`]({url})"


PATH_LIKE = re.compile(
    r"`([^`]+?\.(?:kt|kts))(:[0-9]+(?:[-,][0-9]+| *, *[0-9]+(?:-[0-9]+)?)*)?`"
)
LINK_REGION = re.compile(r"\[[^\]]*\]\([^)]*\)")


def process_text(text, all_files, suffix_index, gh_base, unresolved):
    masked = bytearray(b" " * len(text))
    for m in LINK_REGION.finditer(text):
        for i in range(m.start(), m.end()):
            masked[i] = ord("X")

    def is_inside_link(start, end):
        return any(masked[i] == ord("X") for i in range(start, end))

    out, last = [], 0
    for m in PATH_LIKE.finditer(text):
        if is_inside_link(m.start(), m.end()):
            continue
        path = m.group(1)
        lines = m.group(2) or ""
        if "/" not in path:
            continue
        resolved = resolve(path, all_files, suffix_index)
        if resolved is None:
            unresolved.append(path + lines)
            continue
        out.append(text[last:m.start()])
        out.append(make_link(path + lines, resolved, lines, gh_base))
        last = m.end()
    out.append(text[last:])
    return "".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+", type=Path)
    ap.add_argument("--tag", default="v2.3.21",
                    help="Kotlin release tag (default: v2.3.21)")
    ap.add_argument("--repo",
                    default=os.environ.get("KOTLIN_REPO",
                                            "/Users/kitakkun/Documents/GitHub/kotlin-lang"),
                    help="Path to local JetBrains/kotlin clone")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    gh_base = f"https://github.com/JetBrains/kotlin/blob/{args.tag}/"
    all_files, suffix_index = build_index(args.repo, args.tag)

    unresolved = []
    for f in args.files:
        original = f.read_text()
        new = process_text(original, all_files, suffix_index, gh_base, unresolved)
        if new != original:
            if args.dry_run:
                print(f"[would update] {f}")
            else:
                f.write_text(new)
                print(f"[updated] {f}")
        else:
            print(f"[unchanged] {f}")

    if unresolved:
        print(f"\n{len(unresolved)} unresolved citation(s):", file=sys.stderr)
        for path in unresolved:
            print(f"  - {path}", file=sys.stderr)


if __name__ == "__main__":
    main()
