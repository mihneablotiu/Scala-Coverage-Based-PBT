"""Per-iteration feedback cycle — conceptual view.

A closed cycle of four conceptual stages plus the accumulating session
state at its centre. The input-picking stage lives inside the use case
(it is a plain module, not a driven port), so it is coloured as a
domain step rather than a port step.

Run: ``python3 docs/scripts/loop.py``.
"""

from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

from shapes import (  # noqa: E402
    COLOR_ADAPTER,
    COLOR_DATA,
    COLOR_DOMAIN,
    COLOR_PORT,
    arrow,
    canvas,
    labeled_box,
    save,
    title as draw_title,
)

W, H = 14.0, 11.0
fig, ax = canvas(W, H)

CX, CY = W / 2, H / 2 - 0.4
R = 3.5

# Four cycle nodes around the centre, plus the centre accumulator.
NODES = [
    # (angle°, title,            subtitle,                 fill,           w,    h)
    ( 90, "Pick Input",          "selected strategy",      COLOR_DOMAIN,  3.0, 1.3),
    (  0, "Property",            "exercise the SUT",       COLOR_ADAPTER, 3.0, 1.3),
    (270, "Coverage Reader",     "snapshot scoverage",     COLOR_PORT,    3.2, 1.3),
    (180, "Session Feedback",    "accumulator + view",     COLOR_DOMAIN,  3.2, 1.3),
]

positions = []
for angle, t, sub, fill, w, h in NODES:
    rad = math.radians(angle)
    cx = CX + R * math.cos(rad)
    cy = CY + R * math.sin(rad)
    labeled_box(ax, cx - w / 2, cy - h / 2, w, h,
                title=t, subtitle=sub, fill=fill,
                title_size=13, subtitle_size=10)
    positions.append((cx, cy, w, h))


def edge(src, dst, txt):
    sx, sy, sw, sh = positions[src]
    dx, dy, dw, dh = positions[dst]
    ang = math.atan2(dy - sy, dx - sx)
    pad_s = max(sw, sh) / 2 * 0.90
    pad_d = max(dw, dh) / 2 * 0.90
    x1, y1 = sx + pad_s * math.cos(ang), sy + pad_s * math.sin(ang)
    x2, y2 = dx - pad_d * math.cos(ang), dy - pad_d * math.sin(ang)
    arrow(ax, x1, y1, x2, y2, lw=1.9,
          style="->,head_width=0.4,head_length=0.65")
    mx, my = (x1 + x2) / 2, (y1 + y2) / 2
    perp = ang + math.pi / 2
    off = 0.45
    lx, ly = mx + off * math.cos(perp), my + off * math.sin(perp)
    ax.text(lx, ly, txt, ha="center", va="center", fontsize=11,
            color="#1A1A1A",
            bbox=dict(facecolor="white", edgecolor="none", pad=3.0))


EDGES = [
    (0, 1, "input"),
    (1, 2, "side effects"),
    (2, 3, "coverage delta"),
    (3, 0, "fresh feedback"),
]
for s, d, l in EDGES:
    edge(s, d, l)

labeled_box(ax, CX - 1.8, CY - 0.55, 3.6, 1.1,
            title="loop  ×  N",
            subtitle="feedback grows",
            fill=COLOR_DATA, title_size=13, subtitle_size=10)

draw_title(ax, W / 2, H - 0.35, "Per-iteration Feedback Cycle", fontsize=20)

save(fig, "loop")
