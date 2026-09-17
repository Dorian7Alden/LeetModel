# -*- coding: utf-8 -*-
"""百度贴吧 playwright 爬虫：数学建模吧等吧的帖子列表 + 热门帖正文。
贴吧未登录可渲染列表页与帖子页（部分楼层需登录，抓可见部分）。"""
import asyncio
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import save_raw, clean_text  # noqa: E402

BARS = ["数学建模", "数学建模竞赛", "美赛", "数学建模美赛", "数学建模国赛"]
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")


async def crawl_bar(pw, bar: str) -> dict:
    browser = await pw.chromium.launch(headless=True)
    ctx = await browser.new_context(user_agent=UA, viewport={"width": 1440, "height": 900},
                                    locale="zh-CN")
    page = await ctx.new_page()
    url = f"https://tieba.baidu.com/f?kw={bar}&ie=utf-8"
    await page.goto(url, timeout=45000, wait_until="domcontentloaded")
    try:
        await page.wait_for_selector(".j_thread_list", timeout=20000)
    except Exception:
        pass
    await page.wait_for_timeout(1500)

    # 帖子列表（标题+链接）
    items = await page.evaluate("""
        Array.from(document.querySelectorAll('.j_thread_list li')).map(li => {
            const a = li.querySelector('a.j_th_tit');
            const author = li.querySelector('.frs-author-name');
            const reply = li.querySelector('.threadlist_rep_num');
            return a ? {title: a.innerText.trim(), href: a.href,
                        author: author ? author.innerText.trim() : '',
                        reply: reply ? reply.innerText.trim() : ''} : null;
        }).filter(Boolean)
    """)
    print(f"[tieba:{bar}] 列表 {len(items)} 条")
    save_raw(f"list_{bar}.json", json.dumps(items, ensure_ascii=False, indent=2),
             subdir="tieba")

    # 抓前 12 个帖正文
    posts = []
    for it in items[:12]:
        if not it["href"] or "tieba.baidu.com/p/" not in it["href"]:
            continue
        try:
            await page.goto(it["href"], timeout=45000, wait_until="domcontentloaded")
            await page.wait_for_timeout(1800)
            text = await page.evaluate("document.body.innerText")
            text = clean_text(text)
            if len(text) > 200:
                fn = re.sub(r"[^\w一-鿿]+", "_", it["title"])[:30]
                save_raw(f"post_{bar}_{fn}.txt",
                         f"URL: {it['href']}\n标题: {it['title']}\n吧: {bar}\n\n{text[:12000]}",
                         subdir="tieba")
                posts.append({"title": it["title"], "url": it["href"],
                              "bar": bar, "chars": len(text)})
                print(f"  ✓ {it['title'][:25]}（{len(text)}字）")
        except Exception as e:
            print(f"  ✗ {it['title'][:20]}: {e}")
    await browser.close()
    return {"bar": bar, "list": items[:50], "posts": posts}


async def main():
    from playwright.async_api import async_playwright
    all_data = {}
    async with async_playwright() as pw:
        for bar in BARS:
            print(f"[tieba] 吧: {bar}")
            try:
                all_data[bar] = await crawl_bar(pw, bar)
            except Exception as e:
                print(f"  失败: {e}")
                all_data[bar] = {"error": str(e)}
    save_raw("tieba_index.json", json.dumps(all_data, ensure_ascii=False, indent=2),
             subdir="tieba")
    total = sum(len(v.get("posts", [])) for v in all_data.values())
    print(f"[tieba] 完成，共 {total} 帖正文")


if __name__ == "__main__":
    asyncio.run(main())
