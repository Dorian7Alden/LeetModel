from __future__ import annotations

from fastapi import APIRouter, Query

from app import generators
from app.response import ok

router = APIRouter(prefix="/api/v1", tags=["其他"])


@router.get("/avatars", summary="生成头像 URL", description="生成 DiceBear 头像 URL。")
def avatars(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    style: str = Query("micah", description="头像风格，如 micah、avataaars"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_avatars(count, style, seed))


@router.get("/urls", summary="生成 URL", description="生成指定数量的随机 URL。")
def urls(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_urls(count, seed))


@router.get("/phone-numbers", summary="生成手机号", description="生成指定数量的中国大陆手机号。")
def phone_numbers(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_phone_numbers(count, seed))


@router.get("/booleans", summary="生成布尔值", description="生成指定数量的随机布尔值。")
def booleans(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_booleans(count, seed))
