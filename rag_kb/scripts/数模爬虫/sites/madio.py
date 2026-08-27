# -*- coding: utf-8 -*-
"""数学中国论坛 madio.net（Discuz!）：数学建模版块热门帖。用 http 协议（https 不通）。"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import http_get, save_raw, extract_text, clean_text  # noqa: E402

BASE = "http://www.madio.net/forum.php"
HOST = "http://www.madio.net/"


def crawl():
    r = http_get(BASE)
    if not r:
        print("[madio] 首页失败")
        return
    html = r.text
    save_raw("madio_home.html", html, subdir="madio")

    # 版块链接（Discuz 静态格式 forum-{fid}-1.html）
    board_links = set(re.findall(r'href="(forum-\d+-1\.html)"', html))
    # 偏好数学建模相关版块
    scored = []
    for u in board_links:
        score = sum(1 for k in ["建模", "model"] if k in u.lower())
        scored.append((u, score))
    board_urls = [urljoin(HOST, u) for u, s in sorted(scored, key=lambda x: -x[1])][:10]
    print(f"[madio] 发现 {len(board_urls)} 个版块")

    posts = []
    for b_url in board_urls:
        r = http_get(b_url)
        if not r:
            continue
        # 帖子链接 thread-{tid}-{fid}-{page}.html
        for m in re.finditer(r'href="(thread-\d+-\d+-1\.html)"[^>]*>\s*([^<]{4,40}?)\s*</a>', r.text):
            url, title = urljoin(HOST, m.group(1)), clean_text(m.group(2))
            if "置顶" in title or "公告" in title or url in [p[0] for p in posts]:
                continue
            posts.append((url, title))
        if len(posts) >= 40:
            break
    print(f"[madio] 收集 {len(posts)} 个帖子")

    results = []
    for url, title in posts[:30]:
        r = http_get(url)
        if not r:
            continue
        body = clean_text(extract_text(r.text, url) or "")
        if len(body) < 100:
            continue
        fn = re.sub(r"[^\w一-鿿]+", "_", title)[:30]
        save_raw(f"{fn}.txt", f"URL: {url}\n标题: {title}\n\n{body}", subdir="madio")
        results.append({"url": url, "title": title, "chars": len(body)})
        print(f"  ✓ {title}（{len(body)}字）")

    save_raw("madio_index.json", json.dumps(results, ensure_ascii=False, indent=2),
             subdir="madio")
    print(f"[madio] 完成，帖子 {len(results)} 个")


if __name__ == "__main__":
    crawl()
