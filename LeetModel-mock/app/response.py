from __future__ import annotations

from typing import Any


def ok(data: Any) -> dict[str, Any]:
    return {"code": 20000, "message": "success", "data": data}
