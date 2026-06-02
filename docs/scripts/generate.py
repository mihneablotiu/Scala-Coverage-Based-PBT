"""Regenerate the architecture diagrams under docs/images/.

Run: ``python3 docs/scripts/generate.py``.
"""

from __future__ import annotations

import os
import runpy

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPTS = ["hexagon.py", "loop.py", "mechanisms.py"]

if __name__ == "__main__":
    for name in SCRIPTS:
        print(f"--- {name} ---")
        runpy.run_path(os.path.join(HERE, name), run_name="__main__")
    print("done")
