# -*- coding: utf-8 -*-
"""百度百科：数学建模竞赛词条。百科 SSR 输出正文，trafilatura 提取。"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import http_get, save_raw, extract_text, clean_text  # noqa: E402

ITEMS = [
    ("中国大学生数学建模竞赛", "https://baike.baidu.com/item/中国大学生数学建模竞赛"),
    ("美国大学生数学建模竞赛", "https://baike.baidu.com/item/美国大学生数学建模竞赛"),
    ("数学建模", "https://baike.baidu.com/item/数学建模"),
    ("全国研究生数学建模竞赛", "https://baike.baidu.com/item/全国研究生数学建模竞赛"),
    ("深圳杯数学建模", "https://baike.baidu.com/item/深圳杯数学建模挑战赛"),
]


def crawl():
    out = []
    for name, url in ITEMS:
        print(f"[baike] {name}")
        r = http_get(url)
        if not r:
            out.append({"name": name, "error": "http failed"})
            continue
        body = clean_text(extract_text(r.text, url) or "")
        if len(body) < 100:
            # 百度百科可能反爬/需要 JS，试 playwright
            print("  HTTP 提取失败，尝试 playwright …")
            from lib.fetch import pw_render_sync
            html = pw_render_sync(url, extra_wait=2.5)
            if html:
                save_raw(f"baike_{name}.html", html, subdir="baike")
                body = clean_text(extract_text(html, url) or "")
        if len(body) < 100:
            out.append({"name": name, "url": url, "error": "extract failed"})
            print(f"  ✗ 失败")
            continue
        save_raw(f"baike_{name}.txt", f"URL: {url}\n\n{body}", subdir="baike")
        out.append({"name": name, "url": url, "chars": len(body)})
        print(f"  ✓ {len(body)}字")
    save_raw("baike_index.json", json.dumps(out, ensure_ascii=False, indent=2), subdir="baike")


if __name__ == "__main__":
    crawl()
