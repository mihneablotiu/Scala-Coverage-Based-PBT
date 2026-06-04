"""Per-strategy diagrams: each coverage-guided tactic combined with the random baseline, then all four
together. Each draw the *strategy picks* which tactic produces the next strongly-typed value; coverage
feeds back into the tactic. Deliberately simple — keywords, not sentences.

Run: ``python3 docs/scripts/strategies.py``  (Graphviz `dot` must be installed).
"""

from __future__ import annotations

import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
IMAGES = os.path.normpath(os.path.join(HERE, "..", "images"))

# soft, flat palette
BLUE, TEAL, ORANGE, ROSE, AMBER, GREY = "#E4ECF5", "#C9E8DD", "#F6E0CC", "#F6D8D5", "#F6EDCF", "#EEF1F4"
TEAL_A, ORANGE_A, ROSE_A = "#2F7A68", "#9A6B2F", "#9A4039"
TEAL_E, ORANGE_E, ROSE_E = "#3A9C83", "#D08A3A", "#C25A50"

STYLE = """
  bgcolor="white"; rankdir=TB; nodesep=0.6; ranksep=0.62; fontname="Helvetica"; labelloc="t"; fontsize=20;
  node [fontname="Helvetica", fontsize=13, shape=box, style="rounded,filled", penwidth=0, margin="0.25,0.16"];
  edge [fontname="Helvetica", fontsize=10.5, color="#8A95A1", penwidth=1.7, arrowsize=0.85, fontcolor="#566069"];
"""


def _rand() -> str:
    return f'rand [label=<random<br/><font point-size="10" color="#5B6B7B">a fresh typed value</font>>, fillcolor="{BLUE}"];'


def _common() -> str:
    return (
        f'pick [label=<<b>pick</b><br/><font point-size="10" color="#5B6B7B">the strategy</font>>, shape=oval, fillcolor="{GREY}"];\n'
        f'  run [label=<<b>run</b><br/><font point-size="10" color="#8A7A40">instrumented SUT</font>>, fillcolor="{AMBER}"];'
    )


POOL = f"""digraph pool {{
  {STYLE}
  label=<<b>pool</b>  +  random>;
  {_rand()}
  pool [label=<<b>pool</b><br/><font point-size="10" color="{TEAL_A}">every literal mined<br/>from the method</font>>, fillcolor="{TEAL}"];
  cov  [label=<any branch<br/>still uncovered?>, fillcolor="{TEAL}"];
  {_common()}
  rand -> pick [label="typed value"]; pool -> pick [label="typed value"];
  pick -> run; run -> cov [label="coverage", style=dashed];
  cov -> pool [label="keep injecting", style=dashed, color="{TEAL_E}", fontcolor="{TEAL_A}"];
}}"""

MUTATION = f"""digraph mutation {{
  {STYLE}
  label=<<b>mutation</b>  +  random>;
  {_rand()}
  mut  [label=<<b>mutation</b><br/><font point-size="10" color="{ORANGE_A}">tweak a kept input</font>>, fillcolor="{ORANGE}"];
  kept [label=<kept inputs<br/><font point-size="10" color="{ORANGE_A}">those that covered<br/>something new</font>>, fillcolor="{ORANGE}"];
  {_common()}
  rand -> pick [label="typed value"]; mut -> pick [label="typed value"];
  kept -> mut [label="tweak one"];
  pick -> run; run -> kept [label="keep if new coverage", style=dashed, color="{ORANGE_E}", fontcolor="{ORANGE_A}"];
}}"""

GRADIENT = f"""digraph gradient {{
  {STYLE}
  label=<<b>gradient</b>  +  random>;
  {_rand()}
  grad    [label=<<b>gradient</b><br/><font point-size="10" color="{ROSE_A}">nudge toward an<br/>uncovered branch</font>>, fillcolor="{ROSE}"];
  closest [label=<closest input<br/><font point-size="10" color="{ROSE_A}">smallest distance to<br/>an uncovered branch</font>>, fillcolor="{ROSE}"];
  {_common()}
  rand -> pick [label="typed value"]; grad -> pick [label="typed value"];
  closest -> grad [label="nudge it"];
  pick -> run; run -> closest [label="keep the closest", style=dashed, color="{ROSE_E}", fontcolor="{ROSE_A}"];
}}"""

COMBINED = f"""digraph combined {{
  {STYLE}
  label=<<b>all four together</b>>;
  {_rand()}
  pool [label=<<b>pool</b>>, fillcolor="{TEAL}", fontcolor="{TEAL_A}"];
  mut  [label=<<b>mutation</b>>, fillcolor="{ORANGE}", fontcolor="{ORANGE_A}"];
  grad [label=<<b>gradient</b>>, fillcolor="{ROSE}", fontcolor="{ROSE_A}"];
  {_common()}
  cov  [label=<coverage>, fillcolor="{GREY}"];
  {{ rank=same; rand; pool; mut; grad; }}
  rand -> pick [label="typed value"]; pool -> pick; mut -> pick; grad -> pick;
  pick -> run; run -> cov [style=dashed];
  cov -> pool [label="inject while uncovered", style=dashed, color="{TEAL_E}",  fontcolor="{TEAL_A}"];
  cov -> mut  [label="keep if new",            style=dashed, color="{ORANGE_E}", fontcolor="{ORANGE_A}"];
  cov -> grad [label="keep the closest",        style=dashed, color="{ROSE_E}",   fontcolor="{ROSE_A}"];
}}"""

DIAGRAMS = {"pool": POOL, "mutation": MUTATION, "gradient": GRADIENT, "combined": COMBINED}


def main() -> None:
    os.makedirs(IMAGES, exist_ok=True)
    for name, dot in DIAGRAMS.items():
        for fmt in ("png", "svg"):
            subprocess.run(["dot", f"-T{fmt}", "-o", os.path.join(IMAGES, f"{name}.{fmt}")], input=dot.encode(), check=True)
        print(f"wrote {name}.png / .svg")


if __name__ == "__main__":
    main()
