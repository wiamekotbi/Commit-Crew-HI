#!/usr/bin/env bash
# Measures the production code of a git tag or commit with Lizard (Phase IV, Steps 1 and 3).
# The same tool, version and options are used for phase3, phase4-refactored and phase4.
#
# Usage, from the repository root:
#   pip install lizard==1.26.1
#   docs/metrics/measure.sh phase3 docs/metrics/phase3
set -euo pipefail

ref="$1"
out="$2"
here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Measure exactly the committed code of the ref, not the working tree
git archive "$ref" TheBakeway/backend/src/main | tar -x -C "$work"
mkdir -p "$out"

paths=(TheBakeway/backend/src/main/java TheBakeway/backend/src/main/resources/static/js)
# Lizard exits with 1 when it prints warnings, which is not an error here
(cd "$work" && lizard -l java -l javascript "${paths[@]}") > "$out/lizard.txt" || true
(cd "$work" && lizard -l java -l javascript --csv "${paths[@]}") > "$out/lizard.csv" || true

python3 "$here/class_summary.py" "$out/lizard.txt" "$out/lizard.csv" > "$out/classes.csv"
echo "Measured $ref into $out"
