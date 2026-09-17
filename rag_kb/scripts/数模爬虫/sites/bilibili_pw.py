# -*- coding: utf-8 -*-
"""B站搜索页 playwright 爬虫：关键词搜索视频列表（标题/播放/简介/UP主）。
search.bilibili.com 未登录可访问渲染结果页。"""
import asyncio
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import save_raw, clean_text  # noqa: E402

KEYWORDS = [
    "数学建模竞赛",
    "数学建模 国赛",
    "数学建模 美赛",
    "数学建模 论文写作",
    "数学建模 入门",
    "数学建模 算法",
    "数学建模 经验分享",
    "数学建模 踩坑",
]

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")


async def search_one(p, kw: str) -> list:
    from playwright.async_api import async_playwright
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=True)
        ctx = await browser.new_context(user_agent=UA, viewport={"width": 1440, "height": 900},
                                        locale="zh-CN")
        page = await ctx.new_page()
        url = f"https://search.bilibili.com/video?keyword={kw}&order=click"
        await page.goto(url, timeout=45000, wait_until="domcontentloaded")
        try:
            await page.wait_for_selector(".bili-video-card", timeout=20000)
        except Exception:
            pass
        await page.wait_for_timeout(1500)
        cards = await page.query_selector_all(".bili-video-card")
        out = []
        for c in cards[:20]:
            try:
                title = await c.query_selector(".bili-video-card__info--tit")
                up = await c.query_selector(".bili-video-card__info--author")
                play = await c.query_selector(".bili-video-card__stats--item")
                desc = await c.query_selector(".bili-video-card__info--desc")
                href = await c.query_selector("a.bili-video-card__image")
                out.append({
                    "title": clean_text(await title.inner_text()) if title else "",
                    "up": clean_text(await up.inner_text()) if up else "",
                    "play": clean_text(await play.inner_text()) if play else "",
                    "desc": clean_text(await desc.inner_text())[:200] if desc else "",
                    "url": await href.get_attribute("href") if href else "",
                })
            except Exception:
                continue
        await browser.close()
        return out


async def main():
    all_data = {}
    for kw in KEYWORDS:
        print(f"[bili] 搜索: {kw}")
        try:
            items = await search_one(None, kw)
            print(f"  获取 {len(items)} 条")
            all_data[kw] = items
        except Exception as e:
            print(f"  失败: {e}")
            all_data[kw] = []
    save_raw("bilibili_search.json", json.dumps(all_data, ensure_ascii=False, indent=2),
             subdir="bilibili")
    print(f"[bili] 完成，共 {sum(len(v) for v in all_data.values())} 条视频信息")


if __name__ == "__main__":
    asyncio.run(main())
