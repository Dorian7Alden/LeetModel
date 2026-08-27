from __future__ import annotations

from fastapi import APIRouter, Query

from app import generators
from app.response import ok

router = APIRouter(prefix="/api/v1/dates", tags=["日期时间"])


@router.get("", summary="生成日期", description="生成指定范围内的日期，格式为 YYYY-MM-DD。")
def dates(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    start: str = Query("2020-01-01", description="起始日期"),
    end: str = Query("2026-12-31", description="结束日期"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_dates(count, start, end, seed))


@router.get("/datetimes", summary="生成日期时间", description="生成指定范围内的日期时间，格式为 YYYY-MM-DD HH:MM:SS。")
def datetimes(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    start: str = Query("2020-01-01 00:00:00", description="起始日期时间"),
    end: str = Query("2026-12-31 23:59:59", description="结束日期时间"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_datetimes(count, start, end, seed))
