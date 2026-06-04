"""Diagram: where the next input comes from — ScalaCheck (one random source) vs this engine
(three coverage-driven sources mixed together). Companion to the QuickChick/FuzzChick figure.

Run: ``python3 docs/scripts/sources.py``  (Graphviz `dot` must be installed).
"""

from __future__ import annotations

import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
IMAGES = os.path.normpath(os.path.join(HERE, "..", "images"))

# Channel colours match the charts: random=blue, pool=teal, mutation=orange.
DOT = r"""
digraph sources {
  rankdir=TB; bgcolor="white"; compound=true; nodesep=0.35; ranksep=0.55;
  fontname="Helvetica"; labelloc="t"; fontsize=18;
  label=<<b>Where the next input comes from</b>>;
  node [fontname="Helvetica", fontsize=11];
  edge [fontname="Helvetica", fontsize=9, color="#34495E", penwidth=1.5, arrowsize=0.8];

  subgraph cluster_sc {
    label=<<b>ScalaCheck &mdash; the random baseline</b>>; fontsize=13; labeljust="l";
    style="rounded,filled"; fillcolor="#F6F8FA"; color="#9AA5B1";
    a_rng [label="RNG", shape=circle, style=filled, fillcolor="#E5E8EC", width=0.55];
    a_gen [label=<Generator<br/><font point-size="9">Arbitrary[A]</font>>, shape=box, style="rounded,filled", fillcolor="#D6E4F0"];
    a_sut [label="SUT + property", shape=box, style=filled, fillcolor="#ECEFF2"];
    a_out [label="counterexample", shape=box, style="rounded,filled", fillcolor="#FADBD8"];
    a_rng -> a_gen [label="random bits"];
    a_gen -> a_sut [label="value"];
    a_sut -> a_out [label="fail"];
    a_sut -> a_gen [label="pass &rarr; next", style=dashed, constraint=false];
  }

  subgraph cluster_cg {
    label=<<b>This engine &mdash; coverage-guided</b>>; fontsize=13; labeljust="l";
    style="rounded,filled"; fillcolor="#F5FBF8"; color="#7FB8A4";

    s_rand [label=<<b>random draw</b><br/><font point-size="9">Arbitrary[A]</font>>, shape=box, style="rounded,filled", fillcolor="#2E5C8A", fontcolor="white"];
    s_pool [label=<<b>pool</b><br/><font point-size="9">literals of uncovered branches</font>>, shape=box, style="rounded,filled", fillcolor="#1F8A70", fontcolor="white"];
    s_mut  [label=<<b>mutation</b><br/><font point-size="9">perturb a corpus seed</font>>, shape=box, style="rounded,filled", fillcolor="#E67E22", fontcolor="white"];

    mix [label=<<b>mix</b><br/><font point-size="9">Gen.frequency</font>>, shape=box, style="rounded,filled", fillcolor="#2C3E50", fontcolor="white"];
    sut [label=<SUT + property<br/><font point-size="9">scoverage-instrumented</font>>, shape=box, style=filled, fillcolor="#ECEFF2", color="#2C3E50", penwidth=2];
    cov [label=<coverage<br/><font point-size="9">fired branches</font>>, shape=box, style=filled, fillcolor="#E8F0EB"];
    fb  [label=<<b>Feedback</b><br/><font point-size="9">covered leaves &middot; corpus</font>>, shape=box, style="rounded,filled", fillcolor="#FCF3CF"];
    out [label="counterexample", shape=box, style="rounded,filled", fillcolor="#FADBD8"];

    { rank=same; s_rand; s_pool; s_mut; }
    s_rand -> mix; s_pool -> mix; s_mut -> mix;
    mix -> sut [label="next input", penwidth=2.2];
    sut -> out [label="fail"];
    sut -> cov [label="run"];
    cov -> fb;
    fb -> s_pool [label="still-needed literals",   color="#1F8A70", fontcolor="#16735C", style=dashed, constraint=false];
    fb -> s_mut  [label="keep coverage-growers",   color="#E67E22", fontcolor="#B35E10", style=dashed, constraint=false];
  }
}
"""


def main() -> None:
    os.makedirs(IMAGES, exist_ok=True)
    for fmt in ("png", "svg"):
        out = os.path.join(IMAGES, f"sources.{fmt}")
        subprocess.run(["dot", f"-T{fmt}", "-o", out], input=DOT.encode(), check=True)
        print(f"wrote {out}")


if __name__ == "__main__":
    main()
