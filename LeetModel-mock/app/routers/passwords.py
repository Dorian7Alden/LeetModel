from __future__ import annotations

from fastapi import APIRouter, Query

from app import generators
from app.response import ok

router = APIRouter(prefix="/api/v1/passwords", tags=["密码"])


@router.get("/plain", summary="生成随机明文密码", description="生成指定长度和数量的随机明文密码。")
def plain_passwords(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    length: int = Query(12, ge=4, le=64, description="密码长度"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_plain_passwords(count, length, seed))


@router.get("/bcrypt", summary="生成 BCrypt 密码哈希", description="对指定明文密码进行 BCrypt 加密。不传 password 时生成随机明文并返回明文与哈希。")
def bcrypt_passwords(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    password: str | None = Query(None, description="需要加密的明文密码"),
):
    return ok(generators.gen_bcrypt_passwords(count, password))
