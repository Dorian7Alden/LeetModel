# -*- coding: utf-8 -*-
"""数模爬虫通用库：httpx 抓取 + trafilatura 正文提取 + playwright 浏览器渲染。
定位：个人知识库资料收集工具，绕过基础反爬（UA/JS 渲染），不做破解类对抗。"""
import asyncio
import json
import os
import re
import time
from pathlib import Path

import httpx

DATA_DIR = Path(__file__).resolve().parents[3] / "data" / "数模爬取"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

CLIENT = httpx.Client(
    headers={
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    },
    follow_redirects=True,
    timeout=30,
)


def http_get(url: str, *, params: dict | None = None, headers: dict | None = None,
             retries: int = 3, delay: float = 1.0) -> httpx.Response | None:
    """GET 带重试；403/429 时退避重试。"""
    for i in range(retries):
        try:
            r = CLIENT.get(url, params=params, headers=headers)
            if r.status_code in (200, 201):
                return r
            if r.status_code in (403, 429):
                time.sleep(delay * (i + 1) * 3)
                continue
            return r
        except Exception as e:
            time.sleep(delay * (i + 1))
    return None


def save_raw(name: str, data: str | bytes, *, subdir: str = "") -> Path:
    """保存原始抓取结果（html/json/md）。"""
    d = DATA_DIR / subdir
    d.mkdir(parents=True, exist_ok=True)
    p = d / name
    if isinstance(data, bytes):
        p.write_bytes(data)
    else:
        p.write_text(data, encoding="utf-8")
    return p


def extract_text(html: str, url: str = "") -> str:
    """trafilatura 正文提取；失败降级去标签。"""
    try:
        from trafilatura import extract
        txt = extract(html, url=url or None, include_comments=False,
                      include_tables=True, favor_recall=True)
        if txt:
            return txt.strip()
    except Exception:
        pass
    txt = re.sub(r"<script[\s\S]*?</script>|<style[\s\S]*?</style>", "", html)
    txt = re.sub(r"<[^>]+>", "\n", txt)
    txt = re.sub(r"\n{3,}", "\n\n", txt)
    return txt.strip()


def clean_text(txt: str) -> str:
    """清理抓取文本：去空白噪音、保留段落。"""
    lines = [l.strip() for l in txt.splitlines()]
    out, blank = [], 0
    for l in lines:
        if l:
            out.append(l)
            blank = 0
        elif blank < 1:
            out.append("")
            blank += 1
    return "\n".join(out)


async def pw_render(url: str, *, wait_for: str | None = None, extra_wait: float = 1.0,
                    headers: dict | None = None) -> str | None:
    """Playwright 渲染页面，返回渲染后 HTML。"""
    from playwright.async_api import async_playwright
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        ctx = await browser.new_context(
            user_agent=UA,
            viewport={"width": 1440, "height": 900},
            locale="zh-CN",
            extra_http_headers=headers or {},
        )
        page = await ctx.new_page()
        try:
            await page.goto(url, timeout=45000, wait_until="domcontentloaded")
            if wait_for:
                try:
                    await page.wait_for_selector(wait_for, timeout=20000)
                except Exception:
                    pass
            await page.wait_for_timeout(int(extra_wait * 1000))
            return await page.content()
        except Exception as e:
            print(f"  [pw_render] {url} 失败: {e}")
            return None
        finally:
            await browser.close()


def pw_render_sync(url: str, **kw) -> str | None:
    return asyncio.run(pw_render(url, **kw))
