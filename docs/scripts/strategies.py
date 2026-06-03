"""Per-strategy flow diagrams in the FuzzChick style: each shows one coverage-guided tactic combined
with the random baseline, then one with all four together. Thick arrows, a decision diamond, the
instrumented SUT, and the coverage-feedback loop that makes each tactic coverage-guided.

Run: ``python3 docs/scripts/strategies.py``  (Graphviz `dot` must be installed).
"""

from __future__ import annotations

import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
IMAGES = os.path.normpath(os.path.join(HERE, "..", "images"))

BLUE, GRAY, GREEN = "#3B6FB5", "#6B7785", "#2E8B57"
POOL_F, MUT_F, GRAD_F, COV_F = "#DCEFE9", "#FBE6D4", "#FBE0DC", "#FBF4D8"  # light channel tints

STYLE = """
  bgcolor="white"; rankdir=TB; splines=polyline; nodesep=0.55; ranksep=0.7;
  fontname="Helvetica"; labelloc="t"; fontsize=20;
  node [shape=box, style="filled", fillcolor="white", color="#222222", penwidth=1.8, fontname="Helvetica", fontsize=13];
  edge [color="%s", penwidth=3.0, arrowsize=1.0, fontname="Helvetica", fontsize=11.5, fontcolor="#111111"];
  rng [label="RNG", shape=circle, fillcolor="#F0F2F4", width=0.8, fixedsize=true];
""" % BLUE


def _instr(extra: str = "") -> str:
    return (
        'subgraph cluster_instr { label="instrumentation"; labelloc="b"; fontsize=11; '
        'style="filled"; fillcolor="#E6E9ED"; color="#9AA5B1"; penwidth=1.4; '
        "sut [label=<SUT  +  property>]; %s }" % extra
    )


MUTATION = f"""digraph mutation {{
  {STYLE}
  label=<<b>random  +  mutation</b>>;
  gen    [label="Generator"];
  mut    [label="Mutator"];
  corpus [label=<Corpus<br/><font point-size="10">coverage-growing seeds</font>>, shape=cylinder, fillcolor="{MUT_F}"];
  newq   [label=<covers a<br/>new leaf?>, shape=diamond, fillcolor="{MUT_F}", width=1.9, height=1.3, fixedsize=true];
  report [label="report to user"];
  throw  [label="throw away", fillcolor="#F0F2F4"];
  {_instr()}
  rng -> gen [label="random bits"];
  rng -> mut [label="random bits"];
  corpus -> mut [label="pick a seed"];
  gen -> sut [label="random value"];
  mut -> sut [label="mutated value"];
  sut -> report [label="fail"];
  sut -> newq [label="coverage", color="{GRAY}", fontcolor="#444"];
  newq -> corpus [label="yes — keep", color="{GREEN}", fontcolor="#1E6B3A"];
  newq -> throw [label="no", color="{GRAY}", fontcolor="#444"];
}}"""

POOL = f"""digraph pool {{
  {STYLE}
  label=<<b>random  +  pool</b>>;
  gen     [label="Generator"];
  litpool [label=<Literals<br/><font point-size="10">mined from the guards</font>>, shape=cylinder, fillcolor="{POOL_F}"];
  target  [label=<literals of the<br/>still-uncovered leaves>, fillcolor="{POOL_F}"];
  report  [label="report to user"];
  {_instr()}
  rng -> gen [label="random bits"];
  gen -> sut [label="random value"];
  litpool -> target [label="mined literals"];
  target -> sut [label="splice in a needed literal"];
  sut -> report [label="fail"];
  sut -> target [label="coverage — which leaves\nare still uncovered", color="{GRAY}", fontcolor="#444"];
}}"""

GRADIENT = f"""digraph gradient {{
  {STYLE}
  label=<<b>random  +  gradient</b>>;
  gen    [label="Generator"];
  best   [label=<Best so far<br/><font point-size="10">closest to a target</font>>, shape=cylinder, fillcolor="{GRAD_F}"];
  climb  [label=<climb branch-distance<br/>to the nearest uncovered leaf>];
  closer [label=<closer to an<br/>uncovered leaf?>, shape=diamond, fillcolor="{GRAD_F}", width=2.0, height=1.35, fixedsize=true];
  report [label="report to user"];
  throw  [label="throw away", fillcolor="#F0F2F4"];
  {_instr()}
  rng -> gen [label="random bits"];
  rng -> climb [label="random bits"];
  best -> climb [label="mutate"];
  gen -> sut [label="random value"];
  climb -> sut [label="candidate"];
  sut -> report [label="fail"];
  sut -> closer [label="coverage", color="{GRAY}", fontcolor="#444"];
  closer -> best [label="yes — new best", color="{GREEN}", fontcolor="#1E6B3A"];
  closer -> throw [label="no", color="{GRAY}", fontcolor="#444"];
}}"""

COMBINED = f"""digraph combined {{
  {STYLE}
  label=<<b>all four together</b>>;
  gen    [label="Generator"];
  inject [label=<<b>Pool</b><br/><font point-size="10">literal of an uncovered leaf</font>>, fillcolor="{POOL_F}"];
  mut    [label=<<b>Mutation</b><br/><font point-size="10">perturb a corpus seed</font>>, fillcolor="{MUT_F}"];
  climb  [label=<<b>Gradient</b><br/><font point-size="10">climb to nearest uncovered leaf</font>>, fillcolor="{GRAD_F}"];
  mix    [label=<<b>mix</b>  &middot;  <font point-size="10">Gen.frequency</font>>, fillcolor="#D9E2EC"];
  fb     [label=<<b>Feedback</b><br/><font point-size="10">covered leaves &middot; corpus</font>>, fillcolor="{COV_F}"];
  report [label="report to user"];
  {_instr()}
  {{ rank=same; gen; inject; mut; climb; }}
  rng -> gen [label="random bits"];
  rng -> mut; rng -> climb;
  gen -> mix [label="random"];
  inject -> mix; mut -> mix; climb -> mix;
  mix -> sut [label="next input", penwidth=3.4];
  sut -> report [label="fail"];
  sut -> fb [label="coverage", color="{GRAY}", fontcolor="#444"];
  fb -> inject [label="still-needed literals", color="#1F8A70", fontcolor="#16735C"];
  fb -> mut [label="keep coverage-growers", color="#B35E10", fontcolor="#B35E10"];
  fb -> climb [label="nearest uncovered + best", color="#922B21", fontcolor="#922B21"];
}}"""

DIAGRAMS = {"pool": POOL, "mutation": MUTATION, "gradient": GRADIENT, "combined": COMBINED}


def main() -> None:
    os.makedirs(IMAGES, exist_ok=True)
    for name, dot in DIAGRAMS.items():
        for fmt in ("png", "svg"):
            out = os.path.join(IMAGES, f"{name}.{fmt}")
            subprocess.run(["dot", f"-T{fmt}", "-o", out], input=dot.encode(), check=True)
        print(f"wrote {name}.png / .svg")


if __name__ == "__main__":
    main()
