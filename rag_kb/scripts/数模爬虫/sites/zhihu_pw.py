# -*- coding: utf-8 -*-
"""知乎 playwright 爬虫：未登录可读的内容（话题页/部分问题页/搜索页标题）。
知乎反爬较强，未登录搜索 JSON 接口 403；本脚本抓渲染页可读文本 + 链接。"""
import asyncio
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import save_raw, clean_text  # noqa: E402

TOPICS = [
    "https://www.zhihu.com/topic/19551237",  # 数学建模 话题（id 可能变动，脚本内验证）
    "https://www.zhihu.com/collection/16858765",  # 数学建模收藏夹示例
]

KEYWORDS = ["数学建模", "数学建模竞赛", "美赛", "数学建模经验"]

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")


async def grab(page, url: str) -> dict:
    await page.goto(url, timeout=45000, wait_until="domcontentloaded")
    await page.wait_for_timeout(2500)
    # 抓正文可见文本
    text = await page.evaluate("document.body.innerText")
    text = clean_text(text)
    # 抓问题链接
    links = await page.evaluate(
        "Array.from(document.querySelectorAll('a')).map(a=>({t:a.innerText.trim(),h:a.href})).filter(x=>x.t&&x.h.includes('zhihu.com/question'))"
    )
    return {"url": url, "text": text[:6000], "links": links[:60]}


async def search_one(pw, kw: str) -> dict:
    browser = await pw.chromium.launch(headless=True)
    ctx = await browser.new_context(user_agent=UA, viewport={"width": 1440, "height": 900},
                                    locale="zh-CN")
    page = await ctx.new_page()
    url = f"https://www.zhihu.com/search?type=content&q={kw}"
    data = await grab(page, url)
    await browser.close()
    return data


async def main():
    from playwright.async_api import async_playwright
    all_data = {}
    async with async_playwright() as pw:
        for kw in KEYWORDS:
            print(f"[zhihu] 搜索: {kw}")
            try:
                d = await search_one(pw, kw)
                print(f"  文本 {len(d['text'])} 字, {len(d['links'])} 个问题链接")
                all_data[f"search_{kw}"] = d
            except Exception as e:
                print(f"  失败: {e}")
    save_raw("zhihu_search.json", json.dumps(all_data, ensure_ascii=False, indent=2),
             subdir="zhihu")
    print("[zhihu] 完成")


if __name__ == "__main__":
    asyncio.run(main())
