"""
Marvo AI — app.py
=================
Root application entrypoint connecting Flask backend and agent architecture.
Usage:
    python app.py
    python -m flask run
"""

import os
import sys

# Ensure repository root is on sys.path
_root_dir = os.path.dirname(os.path.abspath(__file__))
if _root_dir not in sys.path:
    sys.path.insert(0, _root_dir)

from server.server import app

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)

