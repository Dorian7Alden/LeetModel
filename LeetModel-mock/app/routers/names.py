from __future__ import annotations

from fastapi import APIRouter, Query

from app import generators
from app.response import ok

router = APIRouter(prefix="/api/v1/names", tags=["名字"])


@router.get("/chinese", summary="生成中文名字", description="生成指定数量的中文名字，可按字符长度范围过滤。")
def chinese_names(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    min_length: int = Query(2, ge=1, description="最小字符长度"),
    max_length: int = Query(3, ge=1, description="最大字符长度"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_chinese_names(count, min_length, max_length, seed))


@router.get("/english", summary="生成英文名字", description="生成指定数量的英文名字，可按字符长度范围过滤。")
def english_names(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    min_length: int = Query(4, ge=1, description="最小字符长度"),
    max_length: int = Query(24, ge=1, description="最大字符长度"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_english_names(count, min_length, max_length, seed))
