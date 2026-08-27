from __future__ import annotations

from fastapi import APIRouter, Query

from app import generators
from app.response import ok

router = APIRouter(prefix="/api/v1/numbers", tags=["数字"])


@router.get("/integers", summary="生成随机整数", description="生成指定范围内的随机整数。")
def integers(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    min_value: int = Query(0, alias="min", description="最小值"),
    max_value: int = Query(100, alias="max", description="最大值"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_integers(count, min_value, max_value, seed))


@router.get("/decimals", summary="生成随机小数", description="生成指定范围内的随机小数，可控制精度。")
def decimals(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    min_value: float = Query(0.0, alias="min", description="最小值"),
    max_value: float = Query(1.0, alias="max", description="最大值"),
    precision: int = Query(2, ge=0, le=10, description="小数位数"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_decimals(count, min_value, max_value, precision, seed))


@router.get("/ids", summary="生成连续 ID", description="从起始值开始生成连续递增的整数 ID。")
def ids(
    start: int = Query(1, ge=0, description="起始 ID"),
    count: int = Query(10, ge=1, le=10000, description="生成数量"),
):
    return ok(generators.gen_ids(start, count))
