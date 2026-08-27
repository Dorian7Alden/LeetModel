from __future__ import annotations

from fastapi import APIRouter, Query

from app import generators
from app.response import ok

router = APIRouter(prefix="/api/v1/text", tags=["文本"])


@router.get("/usernames", summary="生成用户名", description="生成指定数量的用户名，可按字符长度范围过滤。")
def usernames(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    min_length: int = Query(4, ge=1, description="最小字符长度"),
    max_length: int = Query(16, ge=1, description="最大字符长度"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_usernames(count, min_length, max_length, seed))


@router.get("/emails", summary="生成邮箱", description="生成指定数量的邮箱地址。")
def emails(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_emails(count, seed))


@router.get("/words", summary="生成词语", description="生成指定数量的词语，可指定语言和字符长度范围。")
def words(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    min_length: int = Query(2, ge=1, description="最小字符长度"),
    max_length: int = Query(12, ge=1, description="最大字符长度"),
    locale: str = Query("zh_CN", description="语言，如 zh_CN 或 en_US"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_words(count, min_length, max_length, locale, seed))


@router.get("/sentences", summary="生成句子", description="生成指定数量的句子，可指定语言和字符长度范围。")
def sentences(
    count: int = Query(10, ge=1, le=1000, description="生成数量"),
    min_length: int = Query(5, ge=1, description="最小字符长度"),
    max_length: int = Query(60, ge=1, description="最大字符长度"),
    locale: str = Query("zh_CN", description="语言，如 zh_CN 或 en_US"),
    seed: int | None = Query(None, description="随机种子，相同种子生成相同数据"),
):
    return ok(generators.gen_sentences(count, min_length, max_length, locale, seed))
