from __future__ import annotations

from fastapi import FastAPI

from app.routers import dates, misc, names, numbers, passwords, texts

app = FastAPI(title="LeetModel Mock API", version="0.2.0")

app.include_router(names.router)
app.include_router(numbers.router)
app.include_router(dates.router)
app.include_router(texts.router)
app.include_router(passwords.router)
app.include_router(misc.router)


@app.get("/health", tags=["health"], summary="健康检查")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/", tags=["health"], summary="服务信息")
def root() -> dict[str, str]:
    return {"service": "LeetModel Mock API", "docs": "/docs"}
