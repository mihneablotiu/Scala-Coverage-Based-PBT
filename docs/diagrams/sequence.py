"""Sequence diagram of a single fuzz session — conceptual view.

Five lanes, abstract messages. Specific method names live in the code;
this picture is about the *shape* of the orchestration.

Run: ``python3 docs/diagrams/sequence.py``.
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

from matplotlib.patches import FancyBboxPatch  # noqa: E402

from shapes import (  # noqa: E402
    COLOR_ADAPTER,
    COLOR_DOMAIN,
    COLOR_PORT,
    COLOR_SOFT,
    COLOR_STROKE,
    arrow,
    box,
    canvas,
    label,
    save,
    title as draw_title,
)


W, H = 20.0, 13.0
fig, ax = canvas(W, H)

# ── Lanes ──────────────────────────────────────────────────────────────
LANES = [
    ("Application",   "entry point",            COLOR_ADAPTER),
    ("Use Case",      "fuzz orchestrator",      COLOR_DOMAIN),
    ("Input Generator", "produces inputs",      COLOR_PORT),
    ("Coverage",      "tracks + reads",         COLOR_PORT),
    ("Report Writer", "persists output",        COLOR_PORT),
]
LANE_W = 3.0
LANE_GAP = 0.6
START_X = 0.6
TOP_Y = H - 1.2
BOTTOM_Y = 0.5

lane_x = []
x = START_X
for _ in LANES:
    lane_x.append(x + LANE_W / 2)
    x += LANE_W + LANE_GAP

# Lane heads + lifelines
for (t, sub, fill), cx in zip(LANES, lane_x):
    bx = cx - LANE_W / 2
    by = TOP_Y - 1.0
    box(ax, bx, by, LANE_W, 1.2, "", fill=fill, lw=1.6)
    ax.text(cx, by + 0.78, t, ha="center", va="center",
            fontsize=13, fontweight="bold")
    ax.text(cx, by + 0.32, sub, ha="center", va="center",
            fontsize=10, style="italic", color="#444")
    # Lifeline
    ax.plot([cx, cx], [by, BOTTOM_Y], color=COLOR_SOFT,
            linewidth=1.0, linestyle=(0, (6, 5)))


# ── Message helpers ────────────────────────────────────────────────────
def msg(y, src, dst, text, kind="call"):
    x1, x2 = lane_x[src], lane_x[dst]
    color = COLOR_STROKE if kind == "call" else "#6b6b6b"
    lw = 1.8 if kind == "call" else 1.4
    offs = 0.05 if x2 > x1 else -0.05
    arrow(ax, x1 + offs, y, x2 - offs, y, lw=lw, color=color)
    label(ax, (x1 + x2) / 2, y + 0.22, text, fontsize=11,
          weight="bold" if kind == "call" else "normal",
          color="#1A1A1A" if kind == "call" else "#444",
          bg="white")


# ── Pre-loop ───────────────────────────────────────────────────────────
y = TOP_Y - 1.6
msg(y, 0, 1, "start session")
y -= 0.9
msg(y, 1, 3, "reset state")

# ── Loop band ──────────────────────────────────────────────────────────
loop_top = y - 0.6
loop_bot = loop_top - 4.4

loop_rect = FancyBboxPatch(
    (lane_x[1] - LANE_W / 2 - 0.3, loop_bot),
    (lane_x[4] - lane_x[1]) + LANE_W + 0.6,
    (loop_top - loop_bot),
    boxstyle="round,pad=0.0,rounding_size=0.2",
    facecolor="#FFFAEC", edgecolor=COLOR_SOFT, linewidth=1.4,
    linestyle="--",
)
ax.add_patch(loop_rect)
label(ax, lane_x[1] - LANE_W / 2 - 0.15, loop_top - 0.30,
      "loop  ×  N inputs", fontsize=12, ha="left",
      weight="bold", color="#555")

ly = loop_top - 0.95
msg(ly, 1, 2, "ask for next input")
ly -= 0.75
msg(ly, 2, 1, "input value", kind="return")
ly -= 0.70
# Property runs as an internal step of the Use Case; not a cross-lane message.
ax.text(lane_x[1] + 0.40, ly, "(runs the property)",
        ha="left", va="center", fontsize=10.5,
        style="italic", color="#666")
ly -= 0.70
msg(ly, 1, 3, "measure coverage")
ly -= 0.75
msg(ly, 3, 1, "coverage delta", kind="return")

# ── Post-loop ──────────────────────────────────────────────────────────
y = loop_bot - 1.0
msg(y, 1, 3, "read AST + statement coverage")
y -= 0.9
msg(y, 3, 1, "branch tree + covered positions", kind="return")
y -= 0.9
msg(y, 1, 4, "write report")
y -= 0.9
msg(y, 4, 0, "done", kind="return")

# ── Title ──────────────────────────────────────────────────────────────
draw_title(ax, W / 2, H - 0.3, "Session Choreography", fontsize=20)

save(fig, "sequence")
