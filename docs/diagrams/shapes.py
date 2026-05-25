"""Shared drawing primitives for the architecture diagrams.

The aim is a clean, thesis-grade look: pastel fills, slate strokes,
generous whitespace, role-based labels. Anything implementation-specific
belongs in prose / tables, not in the figures.

Outputs go to ``docs/images/<name>.{png,svg}``.

Usage::

    from shapes import box, arrow, column, save
    fig, ax = canvas(width=18, height=10)
    column(ax, x=0.5, y=0.5, w=3.5, h=9, label="Driving adapters")
    box(ax, x=1, y=4, w=2.5, h=1.4, label="Application", fill=COLOR_ADAPTER)
    arrow(ax, 3.5, 4.7, 4.5, 4.7)
    save(fig, "hexagon")
"""

from __future__ import annotations

import os
from typing import Optional

import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch

# ── Refined pastel palette ─────────────────────────────────────────────
COLOR_ADAPTER = "#BBDEFB"   # soft blue   — adapters
COLOR_PORT    = "#FFF59D"   # soft yellow — ports
COLOR_DOMAIN  = "#C8E6C9"   # soft green  — domain / use-case core
COLOR_DATA    = "#E1BEE7"   # soft purple — data / accumulated values
COLOR_STROKE  = "#37474F"   # slate       — borders & arrows
COLOR_TEXT    = "#263238"   # near-black
COLOR_SOFT    = "#78909C"   # muted slate — secondary lines / labels
COLOR_BG      = "#FFFFFF"
COLOR_NEUTRAL = "#ECEFF1"   # neutral fill — outer container

FONT_BASE     = "Helvetica"
OUT_DIR       = os.path.join(os.path.dirname(__file__), "..", "images")


def canvas(width: float = 16, height: float = 10):
    """Create a figure/axes pre-configured for diagram drawing."""
    fig, ax = plt.subplots(figsize=(width, height))
    ax.set_xlim(0, width)
    ax.set_ylim(0, height)
    ax.set_aspect("equal")
    ax.axis("off")
    return fig, ax


def box(
    ax,
    x: float,
    y: float,
    w: float,
    h: float,
    label: str,
    fill: str = COLOR_NEUTRAL,
    edge: str = COLOR_STROKE,
    lw: float = 1.8,
    fontsize: float = 13,
    fontweight: str = "normal",
    family: str = FONT_BASE,
    radius: float = 0.22,
):
    """Rounded box with a centered label."""
    rect = FancyBboxPatch(
        (x, y), w, h,
        boxstyle=f"round,pad=0.0,rounding_size={radius}",
        facecolor=fill, edgecolor=edge, linewidth=lw,
    )
    ax.add_patch(rect)
    if label:
        ax.text(
            x + w / 2, y + h / 2, label,
            ha="center", va="center",
            fontsize=fontsize, fontweight=fontweight,
            family=family, color=COLOR_TEXT,
        )


def labeled_box(
    ax,
    x: float, y: float, w: float, h: float,
    title: str, subtitle: str = "",
    fill: str = COLOR_NEUTRAL, edge: str = COLOR_STROKE,
    title_size: float = 13, subtitle_size: float = 10,
    title_weight: str = "bold",
    radius: float = 0.22,
):
    """Rounded box with a bold title and an optional italic subtitle below."""
    box(ax, x, y, w, h, label="", fill=fill, edge=edge, radius=radius)
    if subtitle:
        ax.text(
            x + w / 2, y + h / 2 + 0.22, title,
            ha="center", va="center", fontsize=title_size,
            fontweight=title_weight, color=COLOR_TEXT,
        )
        ax.text(
            x + w / 2, y + h / 2 - 0.32, subtitle,
            ha="center", va="center", fontsize=subtitle_size,
            style="italic", color=COLOR_SOFT,
        )
    else:
        ax.text(
            x + w / 2, y + h / 2, title,
            ha="center", va="center", fontsize=title_size,
            fontweight=title_weight, color=COLOR_TEXT,
        )


def column(
    ax,
    x: float, y: float, w: float, h: float, label: str,
    edge: str = COLOR_STROKE, lw: float = 1.4,
    pad_top: float = 0.55,
    label_size: float = 13,
):
    """Outer column box with a header label centered at the top."""
    rect = FancyBboxPatch(
        (x, y), w, h,
        boxstyle="round,pad=0.0,rounding_size=0.3",
        facecolor="none", edgecolor=edge, linewidth=lw,
    )
    ax.add_patch(rect)
    ax.text(
        x + w / 2, y + h - pad_top, label,
        ha="center", va="center", fontsize=label_size, fontweight="bold",
        color=COLOR_TEXT,
    )


def arrow(
    ax,
    x1: float, y1: float, x2: float, y2: float,
    color: str = COLOR_STROKE, lw: float = 1.8,
    style: str = "->,head_width=0.4,head_length=0.6",
    connection: str = "arc3,rad=0.0",
):
    """Straight arrow between two points."""
    a = FancyArrowPatch(
        (x1, y1), (x2, y2),
        arrowstyle=style, mutation_scale=14,
        linewidth=lw, color=color,
        connectionstyle=connection,
    )
    ax.add_patch(a)


def label(ax, x: float, y: float, text: str,
          fontsize: float = 11, color: str = COLOR_TEXT,
          ha: str = "center", va: str = "center",
          style: str = "normal", weight: str = "normal",
          family: str = FONT_BASE, bg: Optional[str] = None):
    kwargs = dict(ha=ha, va=va, fontsize=fontsize, color=color,
                  style=style, fontweight=weight, family=family)
    if bg:
        kwargs["bbox"] = dict(facecolor=bg, edgecolor="none", pad=2.5)
    ax.text(x, y, text, **kwargs)


def title(ax, x: float, y: float, text: str, fontsize: float = 18):
    ax.text(x, y, text, ha="center", va="top",
            fontsize=fontsize, fontweight="bold", color=COLOR_TEXT)


def save(fig, name: str):
    """Save the figure as both PNG (high-DPI) and SVG into docs/images/."""
    os.makedirs(OUT_DIR, exist_ok=True)
    png = os.path.abspath(os.path.join(OUT_DIR, f"{name}.png"))
    svg = os.path.abspath(os.path.join(OUT_DIR, f"{name}.svg"))
    fig.savefig(png, dpi=200, bbox_inches="tight", facecolor=COLOR_BG)
    fig.savefig(svg, bbox_inches="tight", facecolor=COLOR_BG)
    plt.close(fig)
    print(f"wrote {os.path.relpath(png)}")
    print(f"wrote {os.path.relpath(svg)}")
