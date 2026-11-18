"""Turns Lizard's per-function output into one row per class (one row per file).

In this project every Java file holds one top-level class, so a file is a class.
Columns of classes.csv:
  file             path of the file
  nloc             lines of code of the file, without blank and comment lines (Lizard NLOC)
  functions        number of methods or functions
  total_ccn        sum of the cyclomatic complexity of its methods
  max_ccn          highest cyclomatic complexity of one method
  max_ccn_function the method with that highest complexity

Usage: python3 class_summary.py lizard.txt lizard.csv > classes.csv
"""
import csv
import re
import sys
from collections import defaultdict

FILE_ROW = re.compile(r"\s*(\d+)\s+[\d.]+\s+[\d.]+\s+[\d.]+\s+(\d+)\s+(\S+)$")


def read_files(text_path):
    # The file table of lizard.txt starts after its "NLOC Avg.NLOC ..." header
    files = {}
    in_table = False
    with open(text_path) as text:
        for line in text:
            if re.match(r"\s*NLOC\s+Avg\.NLOC", line):
                in_table = True
                continue
            if in_table:
                match = FILE_ROW.match(line)
                if match:
                    files[match.group(3)] = (int(match.group(1)), int(match.group(2)))
                elif line.startswith("="):
                    in_table = False
    return files


def read_functions(csv_path):
    # lizard.csv columns: NLOC, CCN, token, PARAM, length, location, file, function, long name, start, end
    complexity = defaultdict(lambda: [0, 0, ""])
    with open(csv_path) as functions:
        for row in csv.reader(functions):
            ccn, file, function = int(row[1]), row[6], row[7]
            entry = complexity[file]
            entry[0] += ccn
            if ccn > entry[1]:
                entry[1], entry[2] = ccn, function
    return complexity


def main():
    files = read_files(sys.argv[1])
    complexity = read_functions(sys.argv[2])
    writer = csv.writer(sys.stdout, lineterminator="\n")
    writer.writerow(["file", "nloc", "functions", "total_ccn", "max_ccn", "max_ccn_function"])
    rows = []
    for file, (nloc, functions) in files.items():
        total, highest, function = complexity.get(file, [0, 0, ""])
        rows.append([file, nloc, functions, total, highest, function])
    for row in sorted(rows, key=lambda row: (-row[3], row[0])):
        writer.writerow(row)


if __name__ == "__main__":
    main()
