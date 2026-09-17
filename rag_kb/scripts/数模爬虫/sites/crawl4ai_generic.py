# -*- coding: utf-8 -*-
"""crawl4ai 通用抓取：浏览器级渲染，输出 Markdown。处理 JS 重/反爬站点（百度百科、赛氪等）。
用法: python sites/crawl4ai_generic.py --group baike|saikr
"""
import asyncio
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import save_raw, clean_text  # noqa: E402

GROUPS = {
    "baike": [
        ("中国大学生数学建模竞赛", "https://baike.baidu.com/item/中国大学生数学建模竞赛"),
        ("美国大学生数学建模竞赛", "https://baike.baidu.com/item/美国大学生数学建模竞赛"),
        ("数学建模", "https://baike.baidu.com/item/数学建模"),
        ("全国研究生数学建模竞赛", "https://baike.baidu.com/item/全国研究生数学建模竞赛"),
        ("深圳杯数学建模挑战赛", "https://baike.baidu.com/item/深圳杯数学建模挑战赛"),
        ("电工杯数学建模竞赛", "https://baike.baidu.com/item/电工杯数学建模竞赛"),
        ("数学建模大赛", "https://baike.baidu.com/item/数学建模大赛"),
    ],
    "saikr": [
        ("赛氪首页", "https://www.saikr.com/"),
        ("赛氪数学建模竞赛列表", "https://www.saikr.com/vs/math"),
    ],
}


async def fetch_one(crawler, name: str, url: str) -> dict:
    from crawl4ai import CrawlerRunConfig
    try:
        r = await crawler.arun(url, config=CrawlerRunConfig(verbose=False))
        md = (r.markdown or "") if r.success else ""
        if len(md) < 150:
            return {"name": name, "url": url, "chars": 0, "error": "empty"}
        fn = re.sub(r"[^\w一-鿿]+", "_", name)[:30]
        save_raw(f"{fn}.md", f"# {name}\nURL: {url}\n\n{md}", subdir="crawl4ai")
        print(f"  ✓ {name}（{len(md)}字）")
        return {"name": name, "url": url, "chars": len(md)}
    except Exception as e:
        print(f"  ✗ {name}: {e}")
        return {"name": name, "url": url, "error": str(e)}


async def main(group: str):
    from crawl4ai import AsyncWebCrawler, BrowserConfig
    items = GROUPS.get(group, [])
    cfg = BrowserConfig(headless=True)
    results = []
    async with AsyncWebCrawler(config=cfg) as crawler:
        for name, url in items:
            results.append(await fetch_one(crawler, name, url))
            await asyncio.sleep(0.8)
    save_raw(f"{group}_index.json", json.dumps(results, ensure_ascii=False, indent=2),
             subdir="crawl4ai")
    ok = sum(1 for r in results if r.get("chars"))
    print(f"[crawl4ai:{group}] 完成 {ok}/{len(results)}")


if __name__ == "__main__":
    group = sys.argv[1] if len(sys.argv) > 1 else "baike"
    asyncio.run(main(group))
