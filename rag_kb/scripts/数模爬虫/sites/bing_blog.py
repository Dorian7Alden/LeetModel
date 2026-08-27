# -*- coding: utf-8 -*-
"""Bing 搜索聚合：数学建模经验/博客文章发现器。
对每个查询抓 Bing 结果（标题/链接/摘要），再对去重后的文章链接用 playwright 抓正文。
覆盖: CSDN / 博客园 / 简书 / 知乎专栏 / 掘金 / 思否 / 个人博客 —— 经验分享与讨论渠道。
"""
import asyncio
import json
import re
import sys
from pathlib import Path
from urllib.parse import quote, urlparse

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import http_get, save_raw, clean_text, pw_render_sync  # noqa: E402

QUERIES = [
    "数学建模 经验 踩坑",
    "数学建模 论文写作 技巧",
    "数学建模 新手 入门 建议",
    "国赛 数学建模 经验分享",
    "美赛 数学建模 经验",
    "数学建模 模型 总结 常用算法",
    "数学建模 时间管理 分工",
    "数学建模 获奖 心得",
    "数学建模 查重 注意事项",
    "数学建模 摘要 写法",
]

BLOG_DOMAINS = ("blog.csdn.net", "cnblogs.com", "jianshu.com", "zhuanlan.zhihu.com",
                "juejin.cn", "segmentfault.com", "zhihu.com", "blog.51cto.com",
                "blog.sciencenet.cn", "python", "mp.weixin.qq.com", "sohu.com",
                "163.com", "bilibili.com/read")


def bing_search(q: str) -> list:
    """Bing 结果页解析：标题、链接、摘要。"""
    url = f"https://cn.bing.com/search?q={quote(q)}&count=15"
    r = http_get(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"})
    if not r:
        return []
    items = []
    for m in re.finditer(
            r'<li class="b_algo".*?<h2><a[^>]+href="([^"]+)"[^>]*>(.*?)</a></h2>.*?'
            r'<p[^>]*>(.*?)</p>', r.text, re.S):
        href, title, snip = m.group(1), re.sub(r"<[^>]+>", "", m.group(2)), \
            re.sub(r"<[^>]+>", "", m.group(3))
        items.append({"title": clean_text(title), "url": href,
                      "snippet": clean_text(snip)[:200]})
    return items


async def fetch_article(pw, url: str) -> str | None:
    browser = await pw.chromium.launch(headless=True)
    ctx = await browser.new_context(user_agent=UA, viewport={"width": 1440, "height": 900},
                                    locale="zh-CN")
    page = await ctx.new_page()
    try:
        await page.goto(url, timeout=40000, wait_until="domcontentloaded")
        await page.wait_for_timeout(1500)
        text = await page.evaluate("document.body.innerText")
        await browser.close()
        return clean_text(text)
    except Exception:
        await browser.close()
        return None


UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")


async def main():
    from playwright.async_api import async_playwright
    seen, hits = {}, []
    for q in QUERIES:
        print(f"[bing] {q}")
        for it in bing_search(q):
            host = urlparse(it["url"]).netloc
            if not any(d in host for d in BLOG_DOMAINS):
                continue
            key = it["url"]
            if key not in seen:
                seen[key] = {**it, "query": q}
        await asyncio.sleep(1.2)
    hits = list(seen.values())
    print(f"[bing] 去重后 {len(hits)} 篇文章链接")
    save_raw("bing_links.json", json.dumps(hits, ensure_ascii=False, indent=2),
             subdir="blog")

    # 抓正文（限 30 篇，分布在各平台）
    async with async_playwright() as pw:
        for i, it in enumerate(hits[:30]):
            host = urlparse(it["url"]).netloc.replace(".", "_")
            fn = re.sub(r"[^\w一-鿿]+", "_", it["title"])[:30]
            print(f"  [{i+1}/{min(30,len(hits))}] {it['title'][:30]}")
            text = await fetch_article(pw, it["url"])
            if text and len(text) > 300:
                save_raw(f"art_{host}_{fn}.txt",
                         f"URL: {it['url']}\n标题: {it['title']}\n来源: {host}\n"
                         f"查询: {it['query']}\n\n{text[:15000]}", subdir="blog")
                print(f"    ✓ {len(text)}字")
            else:
                print("    ✗ 不可读")
            await asyncio.sleep(1.0)
    print("[bing] 完成")


if __name__ == "__main__":
    asyncio.run(main())
