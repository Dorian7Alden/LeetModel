# -*- coding: utf-8 -*-
"""Bing 搜索（playwright 渲染版）：数学建模经验/博客文章发现器。
cn.bing.com 对纯 HTTP 请求返回反爬页，改用浏览器渲染拿真实结果。
"""
import asyncio
import json
import re
import sys
from pathlib import Path
from urllib.parse import quote, urlparse

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import save_raw, clean_text  # noqa: E402

QUERIES = [
    '"数学建模竞赛" 经验',
    '"数学建模" 国赛 经验 总结',
    '"数学建模" 论文 写作 技巧 分享',
    '"数模" 竞赛 踩坑 新手',
    '"数学建模" 一等奖 心得',
    '"数学建模" 常用算法 模型 大全',
    '"数学建模" 摘要 怎么写',
    '"数学建模" 论文 查重',
    '"数学建模" 入门 学习路线',
    '"数学建模" 编程 python 技巧',
    '"数学建模" 时间 分配 三天',
    '"美赛" 数模 获奖 经验',
]

# 结果级过滤：标题或链接必须与建模相关（解决"美赛"被分词成"美"的歧义）
def relevant(it: dict) -> bool:
    blob = (it["title"] + it["url"] + it["site"]).lower()
    return any(k in blob for k in ("建模", "数模", "math", "mcm", "icm", "cumcm"))

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")


async def bing_search_pw(page, q: str) -> list:
    url = f"https://cn.bing.com/search?q={quote(q)}&count=20&mkt=zh-CN"
    await page.goto(url, timeout=45000, wait_until="domcontentloaded")
    await page.wait_for_timeout(2500)
    results = await page.evaluate("""
        Array.from(document.querySelectorAll('li.b_algo')).map(li => {
            const h2 = li.querySelector('h2 a');
            const p = li.querySelector('p');
            const cite = li.querySelector('cite');
            return h2 ? {title: h2.innerText.trim(), url: h2.href,
                         snippet: p ? p.innerText.trim() : '',
                         site: cite ? cite.innerText.trim() : ''} : null;
        }).filter(Boolean)
    """)
    return results


async def fetch_article(ctx, url: str) -> str | None:
    page = await ctx.new_page()
    try:
        await page.goto(url, timeout=40000, wait_until="domcontentloaded")
        await page.wait_for_timeout(1800)
        text = clean_text(await page.evaluate("document.body.innerText"))
        return text if len(text) > 300 else None
    except Exception:
        return None
    finally:
        await page.close()


async def main():
    from playwright.async_api import async_playwright
    seen, hits = {}, []
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(headless=True)
        ctx = await browser.new_context(user_agent=UA, viewport={"width": 1440, "height": 900},
                                        locale="zh-CN")
        page = await ctx.new_page()
        for q in QUERIES:
            print(f"[bing] {q}")
            try:
                for it in await bing_search_pw(page, q):
                    if not relevant(it):
                        continue
                    if it["url"] not in seen:
                        seen[it["url"]] = {**it, "query": q}
            except Exception as e:
                print(f"  ✗ {e}")
            await asyncio.sleep(1.0)
        await page.close()
        hits = list(seen.values())
        print(f"[bing] 去重后 {len(hits)} 篇")
        save_raw("bing_links.json", json.dumps(hits, ensure_ascii=False, indent=2),
                 subdir="blog")

        for i, it in enumerate(hits[:30]):
            host = urlparse(it["url"]).netloc.replace(".", "_")
            fn = re.sub(r"[^\w一-鿿]+", "_", it["title"])[:30]
            print(f"  [{i+1}] {it['title'][:35]}")
            text = await fetch_article(ctx, it["url"])
            if text:
                save_raw(f"art_{host}_{fn}.txt",
                         f"URL: {it['url']}\n标题: {it['title']}\n来源: {it.get('site','')}\n"
                         f"查询: {it['query']}\n\n{text[:15000]}", subdir="blog")
                print(f"    ✓ {len(text)}字")
            else:
                print("    ✗ 不可读")
            await asyncio.sleep(0.8)
        await browser.close()
    print("[bing] 完成")


if __name__ == "__main__":
    asyncio.run(main())
