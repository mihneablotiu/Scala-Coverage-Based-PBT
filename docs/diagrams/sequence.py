"""Sequence diagram of a single fuzz session — conceptual view.

Five lanes: the entry point, the use case, and the three driven ports
the use case talks to (Coverage Reader, Tree Builder, Report Writer).
There is no Input Generator lane because the strategies live inside the
use case as plain in-process modules, not as a driven port.

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


W, H = 21.0, 14.0
fig, ax = canvas(W, H)

# ── Lanes ──────────────────────────────────────────────────────────────
LANES = [
    ("Application",     "entry point",            COLOR_ADAPTER),
    ("Use Case",        "fuzz orchestrator",      COLOR_DOMAIN),
    ("Coverage Reader", "snapshots scoverage",    COLOR_PORT),
    ("Tree Builder",    "parses the source",      COLOR_PORT),
    ("Report Writer",   "persists output",        COLOR_PORT),
]
LANE_W = 3.2
LANE_GAP = 0.5
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


def note(y, lane, text):
    """Internal step of a lane — drawn as an italic note next to the lifeline."""
    ax.text(lane_x[lane] + 0.40, y, text,
            ha="left", va="center", fontsize=10.5,
            style="italic", color="#666")


# ── Pre-loop ───────────────────────────────────────────────────────────
y = TOP_Y - 1.6
msg(y, 0, 1, "start session")
y -= 0.9
msg(y, 1, 2, "clean stale data")

# ── Loop band ──────────────────────────────────────────────────────────
loop_top = y - 0.6
loop_bot = loop_top - 4.6

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
note(ly, 1, "(pick next input via the selected strategy)")
ly -= 0.85
note(ly, 1, "(run the property: side-effect into the SUT)")
ly -= 0.85
msg(ly, 1, 2, "read coverage snapshot")
ly -= 0.75
msg(ly, 2, 1, "covered positions", kind="return")
ly -= 0.70
note(ly, 1, "(fold delta into Session Feedback)")

# ── Post-loop ──────────────────────────────────────────────────────────
y = loop_bot - 1.0
msg(y, 1, 3, "build method tree")
y -= 0.85
msg(y, 3, 1, "branch tree", kind="return")
y -= 0.85
msg(y, 1, 2, "final coverage snapshot")
y -= 0.85
msg(y, 2, 1, "branch lines + covered", kind="return")
y -= 0.85
msg(y, 1, 4, "write report")
y -= 0.85
msg(y, 4, 0, "done", kind="return")

# ── Title ──────────────────────────────────────────────────────────────
draw_title(ax, W / 2, H - 0.3, "Session Choreography", fontsize=20)

save(fig, "sequence")
