"""Diagram: how the tactics combine. Every strategy runs the *same* loop — read Feedback, let each
active tactic propose a biased draw, mix them with a plain random draw. A strategy is just *which*
tactics are switched on, so the four strategies are the four subsets of {Pool, Mutation}.

Run: ``python3 docs/scripts/composition.py``  (Graphviz `dot` must be installed).
"""

from __future__ import annotations

import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
IMAGES = os.path.normpath(os.path.join(HERE, "..", "images"))

POOL, MUT, RAND = "#1F8A70", "#E67E22", "#2E5C8A"

# (name, pool?, mutation?)
STRATEGIES = [
    ("random", 0, 0),
    ("pool", 1, 0),
    ("mutation", 0, 1),
    ("pool-mutation", 1, 1),
]


def _check(on: int, color: str) -> str:
    cell = f'<font color="{color}"><b>&#10003;</b></font>' if on else '<font color="#C9CED4">&middot;</font>'
    return f'<td align="center">{cell}</td>'


def _rows() -> str:
    out = []
    for name, p, m in STRATEGIES:
        out.append(
            f'<tr><td align="left"><font face="Courier" point-size="10">{name}</font></td>'
            f"{_check(p, POOL)}{_check(m, MUT)}</tr>"
        )
    return "".join(out)


TABLE = f"""<
  <table border="0" cellborder="1" cellspacing="0" cellpadding="5" color="#9AA5B1">
    <tr>
      <td align="left"><b>strategy</b></td>
      <td bgcolor="{POOL}"><font color="white"><b>Pool</b></font></td>
      <td bgcolor="{MUT}"><font color="white"><b>Mut</b></font></td>
    </tr>
    {_rows()}
  </table>
>"""

DOT = f"""
digraph composition {{
  rankdir=TB; bgcolor="white"; nodesep=0.4; ranksep=0.5;
  fontname="Helvetica"; labelloc="t"; fontsize=18;
  label=<<b>How the tactics combine</b>>;
  node [fontname="Helvetica", fontsize=11];
  edge [fontname="Helvetica", fontsize=9, color="#34495E", penwidth=1.5, arrowsize=0.8];

  subgraph cluster_flow {{
    label=<<b>One uniform loop &mdash; read Feedback, each active tactic proposes, mix with random</b>>;
    fontsize=12; labeljust="l"; style="rounded,filled"; fillcolor="#F6F8FA"; color="#9AA5B1";

    fb   [label=<<b>Feedback</b><br/><font point-size="9">covered leaves &middot; corpus</font>>, shape=box, style="rounded,filled", fillcolor="#FCF3CF"];
    pool [label=<<b>Pool</b><br/><font point-size="9">inject literals of<br/>uncovered leaves</font>>, shape=box, style="rounded,filled", fillcolor="{POOL}", fontcolor="white"];
    mut  [label=<<b>Mutation</b><br/><font point-size="9">perturb a<br/>corpus seed</font>>, shape=box, style="rounded,filled", fillcolor="{MUT}", fontcolor="white"];
    rand [label=<<b>random</b><br/><font point-size="9">always present</font>>, shape=box, style="rounded,filled", fillcolor="{RAND}", fontcolor="white"];
    mix  [label=<<b>mix</b> &middot; <font point-size="9">Gen.frequency</font>>, shape=box, style="rounded,filled", fillcolor="#2C3E50", fontcolor="white"];
    nxt  [label="next input", shape=box, style="rounded,filled", fillcolor="#ECEFF2"];

    {{ rank=same; pool; mut; rand; }}
    fb -> pool [label="reads", style=dashed]; fb -> mut [style=dashed];
    pool -> mix [label="propose"]; mut -> mix; rand -> mix;
    mix -> nxt [penwidth=2.2];
  }}

  subgraph cluster_subsets {{
    label=<<b>A strategy = which tactics are on &rArr; the 4 strategies are the 4 subsets</b>>;
    fontsize=12; labeljust="l"; style="rounded,filled"; fillcolor="#F5FBF8"; color="#7FB8A4";
    tbl [shape=plaintext, label={TABLE}];
  }}

  nxt -> tbl [style=invis];
}}
"""


def main() -> None:
    os.makedirs(IMAGES, exist_ok=True)
    for fmt in ("png", "svg"):
        out = os.path.join(IMAGES, f"composition.{fmt}")
        subprocess.run(["dot", f"-T{fmt}", "-o", out], input=DOT.encode(), check=True)
        print(f"wrote {out}")


if __name__ == "__main__":
    main()
