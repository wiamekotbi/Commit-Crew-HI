# Code Metrics

Raw measurements of the code for Phase IV. Every measurement uses the same tool, version and configuration.

| Folder | Measured code |
|---|---|
| `phase3/` | tag `phase3`, before any Phase IV change (baseline) |
| `phase4-refactored/` | tag `phase4-refactored`, after the refactorings |
| `phase4/` | tag `phase4`, with the optional modules |

## Tool and configuration

- **Tool:** Lizard 1.26.1 (`pip install lizard==1.26.1`)
- **Languages:** Java and JavaScript (`-l java -l javascript`)
- **Included folders:** the production code only: `TheBakeway/backend/src/main/java` and `TheBakeway/backend/src/main/resources/static/js`. Tests (`src/test`) are excluded by not passing their folder to Lizard, so no `-x` option is needed.
- **Thresholds:** Lizard's defaults, unchanged: cyclomatic complexity 15, function length 1000, parameters 100.
- **Class-level rule:** the cyclomatic complexity of a class is the **sum** of the CCN of its methods. The **max** CCN of one method is given beside it, as "sum / max".
- **Source:** the files committed at the tag (`git archive`), not the working tree.

## Files in each folder

- `lizard.txt`: Lizard's default report: one row per function (NLOC, CCN, tokens, parameters, length), one row per file, the warnings and the totals.
- `lizard.csv`: the same functions as CSV, without a header. Columns: NLOC, CCN, token count, parameter count, length, location, file, function, long name, start line, end line.
- `classes.csv`: one row per class (one class per Java file), computed from the two files above by `class_summary.py`: lines of code, number of methods, total and highest cyclomatic complexity.

## How to measure

From the repository root, with Lizard 1.26.1 installed:

```bash
docs/metrics/measure.sh phase3 docs/metrics/phase3
docs/metrics/measure.sh phase4-refactored docs/metrics/phase4-refactored
docs/metrics/measure.sh phase4 docs/metrics/phase4
```

Class coupling of the hotspots (the number of distinct classes a class references directly) is computed by hand and given in the report.
