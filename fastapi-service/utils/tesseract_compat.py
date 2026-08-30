"""Compatibility import for pytesseract on newer Python versions."""

from __future__ import annotations

import importlib.util
import pkgutil


def load_pytesseract():
    """Import pytesseract, restoring its removed Python 3.14 helper when needed."""
    # pytesseract 0.3.10 imports find_loader directly from pkgutil.  Python
    # 3.14 removed that alias, although importlib provides the equivalent
    # find_spec operation.  Keep this small compatibility shim so existing
    # project environments do not fail during application startup.
    if not hasattr(pkgutil, "find_loader"):
        pkgutil.find_loader = importlib.util.find_spec

    import pytesseract
    return pytesseract
