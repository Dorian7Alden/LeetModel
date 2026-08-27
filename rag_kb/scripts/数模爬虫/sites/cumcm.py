# -*- coding: utf-8 -*-
"""全国大学生数学建模竞赛官网 www.mcm.edu.cn：抓首页导航 + 章程/规则类页面。
官网是静态生成站点，直接抓 HTML + trafilatura 提正文。"""
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import http_get, save_raw, extract_text, clean_text  # noqa: E402

BASE = "https://www.mcm.edu.cn/"
KEYWORDS = ["章程", "规则", "参赛", "评奖", "报名", "赛题", "简介", "通知", "奖项"]


def crawl():
    r = http_get(BASE)
    if not r:
        print("[cumcm] 首页抓取失败")
        return
    html = r.text
    save_raw("cumcm_home.html", html, subdir="cumcm")

    # 提取站内链接
    links = {}
    for href, text in re.findall(r'<a[^>]+href="([^"#]+)"[^>]*>([^<]{2,40})</a>', html):
        if href.startswith("http") and "mcm.edu.cn" not in href:
            continue
        url = urljoin(BASE, href)
        text = clean_text(text)
        if text and url not in links:
            links[url] = text
    print(f"[cumcm] 发现 {len(links)} 个站内链接")

    # 全部站内页都抓（官网页面少，全量更有价值）
    picked = list(links.items())[:40]
    print(f"[cumcm] 抓取 {len(picked)} 页")

    results = []
    for url, text in picked:
        r = http_get(url)
        if not r:
            continue
        body = extract_text(r.text, url)
        body = clean_text(body or "")
        if len(body) < 200:
            continue
        fn = re.sub(r"[^\w一-鿿]+", "_", text)[:30] or f"page_{len(results)}"
        save_raw(f"{fn}.txt", f"URL: {url}\n标题: {text}\n\n{body}", subdir="cumcm")
        results.append({"url": url, "title": text, "chars": len(body)})
        print(f"  ✓ {text}（{len(body)}字）")

    # 保存全量链接索引
    idx = {"base": BASE, "picked": results,
           "all_links": [{"url": u, "text": t} for u, t in links.items()]}
    save_raw("cumcm_index.json", __import__("json").dumps(idx, ensure_ascii=False, indent=2),
             subdir="cumcm")
    print(f"[cumcm] 完成，正文 {len(results)} 页")


if __name__ == "__main__":
    crawl()
