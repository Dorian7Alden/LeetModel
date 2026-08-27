# -*- coding: utf-8 -*-
"""赛氪 saikr.com：数学建模竞赛列表与详情。赛氪是竞赛聚合平台，赛事信息齐全。"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import http_get, save_raw, extract_text, clean_text, pw_render_sync  # noqa: E402

BASE = "https://www.saikr.com/"


def crawl():
    # 首页（JS 渲染，playwright）
    print("[saikr] 抓首页 …")
    html = pw_render_sync(BASE, extra_wait=2.5)
    if html:
        save_raw("saikr_home.html", html, subdir="saikr")
    else:
        r = http_get(BASE)
        html = r.text if r else ""
        save_raw("saikr_home.html", html, subdir="saikr")

    # 提取含"建模"的链接
    links = set(re.findall(r'href="([^"#]+)"[^>]*>([^<]{2,30}建模[^<]{0,20})</a>', html))
    targets = []
    for href, text in links:
        url = urljoin(BASE, href)
        if "/login" in url or "javascript" in url:
            continue
        targets.append((url, clean_text(text)))
    print(f"[saikr] 建模相关链接 {len(targets)} 个")

    results = []
    for url, text in targets[:20]:
        # 详情页 JS 渲染，用 playwright
        detail_html = pw_render_sync(url, extra_wait=2.0)
        body = clean_text(extract_text(detail_html or "", url) or "")
        if len(body) < 150:
            continue
        fn = re.sub(r"[^\w一-鿿]+", "_", text)[:30]
        save_raw(f"{fn}.txt", f"URL: {url}\n标题: {text}\n\n{body}", subdir="saikr")
        results.append({"url": url, "title": text, "chars": len(body)})
        print(f"  ✓ {text}（{len(body)}字）")

    save_raw("saikr_index.json", json.dumps({"base": BASE, "crawled": results},
                                            ensure_ascii=False, indent=2), subdir="saikr")
    print(f"[saikr] 完成，详情 {len(results)} 页")


if __name__ == "__main__":
    crawl()
