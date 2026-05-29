"""Hexagonal (ports-and-adapters) architecture — conceptual view.

Shows the pattern as it is wired today, not as it might be wired tomorrow.
Three driven ports (Tree Builder, Coverage Reader, Report Writer), each
with a single adapter. The fuzz-input strategies are *not* a port — they
live inside the use case as plain modules selected by a sealed-trait
match, so they appear inside the Use Case column rather than as a
driven-port row. The mapping to real Scala classes lives in the prose
under ``docs/architecture.md``.

Run: ``python3 docs/scripts/hexagon.py``.
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

from shapes import (  # noqa: E402
    COLOR_ADAPTER,
    COLOR_DATA,
    COLOR_DOMAIN,
    COLOR_PORT,
    COLOR_SOFT,
    arrow,
    canvas,
    column,
    labeled_box,
    save,
    title as draw_title,
)

# ── Canvas & geometry ─────────────────────────────────────────────────
W, H = 24.0, 14.0
fig, ax = canvas(W, H)

COL_Y = 0.6
COL_H = 12.4

# Column geometry: (x0, width, header)
COLS = [
    (0.5, 3.4, "Driving adapters"),
    (4.3, 3.0, "Driving ports"),
    (7.7, 8.0, "Use case"),
    (16.1, 4.2, "Driven ports"),
    (20.7, 2.8, "Driven adapters"),
]

for x0, w, header in COLS:
    column(ax, x0, COL_Y, w, COL_H, header, label_size=13)

# ── Driven-port rows (three rows now — Tree, Coverage, Writer) ────────
ROW_Y = [9.30, 6.55, 3.80]   # box bottoms
BOX_H = 1.40

# ── Driving side ──────────────────────────────────────────────────────
labeled_box(
    ax, 0.95, 6.10, 2.5, 1.6,
    title="Application",
    subtitle="entry point",
    fill=COLOR_ADAPTER,
)

labeled_box(
    ax, 4.75, 6.20, 2.1, 1.4,
    title="Test Runner",
    fill=COLOR_PORT,
)

# ── Use case + Domain + Strategies ────────────────────────────────────
# Italic subtitle below the column header.
ax.text(11.7, 11.5, "orchestrates the fuzz session",
        ha="center", va="center", fontsize=11,
        style="italic", color=COLOR_SOFT)

# Test Runner Handler box — the orchestrator.
labeled_box(
    ax, 8.6, 9.10, 6.2, 1.6,
    title="Test Runner Handler",
    subtitle="folds an immutable Session Feedback",
    fill=COLOR_DOMAIN, radius=0.28,
    title_size=14, subtitle_size=10,
)

# Domain types box — the data model the use case folds.
labeled_box(
    ax, 8.6, 4.30, 6.2, 4.60,
    title="", fill=COLOR_DOMAIN, radius=0.28,
)
ax.text(11.7, 8.20, "Domain", ha="center", va="center",
        fontsize=18, fontweight="bold")

DOMAIN_LINES = [
    "Session Feedback",
    "Branch Tree",
    "Coverage Snapshot",
    "Input Record / Growth Curve",
]
y0 = 7.30
for i, line in enumerate(DOMAIN_LINES):
    ax.text(11.7, y0 - i * 0.75, line,
            ha="center", va="center", fontsize=12.5,
            color="#1A1A1A")

# Strategies box — three in-process modules selected by sealed-trait match.
labeled_box(
    ax, 8.6, 1.90, 6.2, 2.10,
    title="", fill=COLOR_DATA, radius=0.28,
)
ax.text(11.7, 3.55, "Strategies", ha="center", va="center",
        fontsize=14, fontweight="bold")
ax.text(11.7, 3.15, "in-process modules, sealed-trait match",
        ha="center", va="center", fontsize=10,
        style="italic", color=COLOR_SOFT)
STRATEGY_LINES = ["Random", "Mutation-Guided"]
# Centre the strategy labels horizontally inside the Strategies box,
# evenly spaced regardless of how many strategies are listed today.
strat_box_left = 8.6
strat_box_w = 6.2
slot_w = strat_box_w / len(STRATEGY_LINES)
for i, line in enumerate(STRATEGY_LINES):
    ax.text(strat_box_left + slot_w * (i + 0.5), 2.40, line,
            ha="center", va="center", fontsize=12,
            color="#1A1A1A")

# ── Driven ports (three rows of role-based labels) ────────────────────
PORT_LABELS = [
    "Tree Builder",
    "Coverage Reader",
    "Report Writer",
]
PORT_W = 3.6
PORT_X = 16.4

for y, label in zip(ROW_Y, PORT_LABELS):
    labeled_box(ax, PORT_X, y, PORT_W, BOX_H, title=label,
                fill=COLOR_PORT, title_size=12.5)

# ── Driven adapters (one per port) ────────────────────────────────────
ADAPTER_X = 21.0
ADAPTER_W = 2.4

ADAPTERS = [
    ("Parser", ROW_Y[0]),
    ("Snapshot", ROW_Y[1]),
    ("Writer", ROW_Y[2]),
]
for lbl, y in ADAPTERS:
    labeled_box(ax, ADAPTER_X, y, ADAPTER_W, BOX_H, title=lbl,
                fill=COLOR_ADAPTER, title_size=12)

# ── Arrows ─────────────────────────────────────────────────────────────
# Driving side flows right: adapter → port → use case
arrow(ax, 3.45, 6.90, 4.75, 6.90)
arrow(ax, 6.85, 6.90, 8.00, 6.90)

# Use case → driven ports: arrows from the right edge of the use-case
# column to each of the three port boxes.
for y in ROW_Y:
    arrow(ax, 14.85, y + BOX_H / 2, 16.40, y + BOX_H / 2)

# Driven adapter → driven port: arrows pointing left into the port (adapter implements port)
for _, y in ADAPTERS:
    arrow(ax, 21.0, y + BOX_H / 2, 20.00, y + BOX_H / 2,
          color=COLOR_SOFT, lw=1.5)

# ── Title ──────────────────────────────────────────────────────────────
draw_title(ax, W / 2, H - 0.3, "Hexagonal Architecture", fontsize=20)

save(fig, "hexagon")
