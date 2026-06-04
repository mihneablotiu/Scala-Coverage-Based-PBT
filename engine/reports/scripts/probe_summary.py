#!/usr/bin/env python3
"""Summarise a `make probe` run into one readable table.

`app.Probe` runs each (strategy, seed) in its own JVM and emits a self-contained tab-delimited line per method:

    PROBE\t<strategy>\t<seed>\t<category>\t<method>\t<covered>\t<total>

This reads that log (the raw per-run dump `make probe` captures) and prints, per method, the **median coverage %
across the seeds** for every strategy — so 8 strategies × N seeds × M methods collapses to one grid. Nothing is
written; it only reads. Usage: ``python3 probe_summary.py <logfile>``.
"""
from __future__ import annotations

import re
import statistics
import sys
from collections import defaultdict

# Canonical simplest→complex order + short column labels, mirroring compare.py / Strategy.names.
ORDER = [
    "random", "random-pool", "mutation-guided", "mutation-guided-pool",
    "coverage-guided", "coverage-guided-pool", "coverage-guided-mutation-guided", "coverage-guided-mutation-guided-pool",
]
SHORT = {
    "random": "rand", "random-pool": "+pool", "mutation-guided": "mut", "mutation-guided-pool": "mut+pl",
    "coverage-guided": "grad", "coverage-guided-pool": "grd+pl", "coverage-guided-mutation-guided": "grd+mut",
    "coverage-guided-mutation-guided-pool": "ALL",
}
LINE = re.compile(r"PROBE\t([^\t]+)\t(\d+)\t([^\t]+)\t([^\t]+)\t(\d+)\t(\d+)")


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("usage: probe_summary.py <logfile>")

    pct = defaultdict(list)   # (method, strategy) -> [pct per seed]
    total = {}                # method -> leaf count
    seeds: set = set()
    strategies: set = set()

    for raw in open(sys.argv[1], errors="ignore"):
        m = LINE.search(raw)
        if not m:
            continue
        strat, seed, cat, method, cov, tot = m[1], int(m[2]), m[3], m[4], int(m[5]), int(m[6])
        key = f"{cat}/{method}"
        pct[(key, strat)].append(100.0 * cov / tot if tot else 0.0)
        total[key] = tot
        seeds.add(seed)
        strategies.add(strat)

    if not total:
        sys.exit("no PROBE lines found — did the probe run? (looked in: %s)" % sys.argv[1])

    cols = [s for s in ORDER if s in strategies] + sorted(strategies - set(ORDER))
    methods = sorted(total)
    namew = max(len(m) for m in methods)

    print(f"\nProbe summary — median coverage % across {len(seeds)} seed(s)  "
          f"(seeds: {','.join(map(str, sorted(seeds)))})\n")
    header = f"{'method'.ljust(namew)}  " + "".join(f"{SHORT.get(s, s):>8}" for s in cols) + f"{'leaves':>8}"
    print(header)
    print("-" * len(header))
    for meth in methods:
        row = f"{meth.ljust(namew)}  "
        best = max((statistics.median(pct[(meth, s)]) for s in cols if (meth, s) in pct), default=0.0)
        for s in cols:
            vals = pct.get((meth, s))
            if not vals:
                row += f"{'·':>8}"
            else:
                med = statistics.median(vals)
                cell = f"{med:.0f}"
                # star the winner(s) so the eye lands on which tactic owns the method
                row += f"{(cell + '*' if med >= best - 1e-9 and best > 0 else cell):>8}"
        row += f"{total[meth]:>8}"
        print(row)
    print(f"\n(* = best strategy for that method.  Run was: see the raw log at {sys.argv[1]}.)")


if __name__ == "__main__":
    main()
