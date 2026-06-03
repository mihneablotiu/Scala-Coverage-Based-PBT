"""Complementarity of the feedback channels — which *kind* of hard branch each one can reach.

The headline finding: random/pool/mutation/gradient each crack a different kind of branch, so the
all-three composite (`coverage-guided-mutation-guided-pool`) covers the most. Cells are ticked from the
benchmark results (e.g. `compareInts` is reached only by the gradient, `accessLevel` only by the pool).

Run: ``python3 docs/scripts/mechanisms.py``.
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import FancyBboxPatch  # noqa: E402

from shapes import COLOR_SOFT, COLOR_STROKE, COLOR_TEXT, save  # noqa: E402

# Columns are the feedback channels (coloured to match the charts); rows are kinds of hard branch.
CHANNELS = [("random", "#2E5C8A"), ("pool", "#1F8A70"), ("mutation", "#E67E22"), ("gradient", "#C0392B")]
ROWS = [
    ('magic int / long   "n == 42"', [0, 1, 0, 1]),
    ('magic string / key   "admin"', [0, 1, 0, 0]),
    ("float edge   NaN, ∞", [0, 0, 1, 0]),
    ("numeric relation   a == −b", [0, 0, 0, 1]),
    ('structure / parse   sorted, "v1.2"', [0, 0, 0, 0]),
]

YES_FILL, NO_FILL = "#A9DFBF", "#F4F6F6"
YES_TXT, NO_TXT = "#196F3D", "#C0392B"

X0, CW, CH, GAP = 5.0, 1.5, 0.82, 0.14
W = X0 + len(CHANNELS) * (CW + GAP) + 0.2
H = 7.0

fig, ax = plt.subplots(figsize=(W, H))
ax.set_xlim(0, W)
ax.set_ylim(0, H)
ax.set_aspect("equal")
ax.axis("off")


def cell_x(j: int) -> float:
    return X0 + j * (CW + GAP)


ax.text(W / 2, H - 0.45, "The tactics are complementary", ha="center", va="center", fontsize=17, fontweight="bold", color=COLOR_TEXT)

for j, (name, col) in enumerate(CHANNELS):
    ax.text(cell_x(j) + CW / 2, H - 1.35, name, ha="center", va="center", fontsize=12.5, fontweight="bold", color=col)

top = H - 2.0
for i, (label, marks) in enumerate(ROWS):
    cy = top - i * (CH + GAP)
    ax.text(X0 - 0.25, cy, label, ha="right", va="center", fontsize=11, family="monospace", color=COLOR_TEXT)
    for j, m in enumerate(marks):
        ax.add_patch(
            FancyBboxPatch(
                (cell_x(j), cy - CH / 2), CW, CH,
                boxstyle="round,pad=0,rounding_size=0.12",
                facecolor=YES_FILL if m else NO_FILL, edgecolor=COLOR_STROKE, linewidth=0.8,
            )
        )
        ax.text(cell_x(j) + CW / 2, cy, "✓" if m else "✗", ha="center", va="center", fontsize=17, fontweight="bold", color=YES_TXT if m else NO_TXT)

ax.text(
    W / 2, 0.55,
    "Each tactic cracks a different kind of branch → coverage-guided-mutation-guided-pool combines all three.\n"
    "Pure structure / parsing is reached by none — the open frontier.",
    ha="center", va="center", fontsize=10, style="italic", color=COLOR_SOFT,
)

save(fig, "mechanisms")
