# -*- coding: utf-8 -*-
"""美赛官网：comap.org（主站介绍）+ contest.comap.com（规则/注册页）。"""
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import http_get, save_raw, extract_text, clean_text  # noqa: E402

TARGETS = [
    ("MCM_ICM主页面", "https://www.comap.org/contests/mcm-icm"),
    ("竞赛总览", "https://www.comap.org/contests/contestsoverview"),
    ("2026结果公布", "https://www.comap.org/blog/item/2026-mcm-icm-results"),
    ("规则注册指南", "https://www.contest.comap.com/undergraduate/contests/mcm/instructions.html"),
    ("获奖论文资源", "https://www.contest.comap.com/undergraduate/contests/mcm/outstanding.html"),
]


def crawl():
    results = []
    for name, url in TARGETS:
        r = http_get(url)
        if not r:
            print(f"[comap] ✗ {name} 请求失败")
            continue
        body = clean_text(extract_text(r.text, url) or "")
        if len(body) < 150:
            print(f"[comap] ✗ {name} 正文过短（{len(body)}）")
            continue
        fn = re.sub(r"[^\w一-鿿]+", "_", name)[:30]
        save_raw(f"{fn}.txt", f"URL: {url}\n标题: {name}\n\n{body}", subdir="comap")
        results.append({"name": name, "url": url, "chars": len(body)})
        print(f"  ✓ {name}（{len(body)}字）")
    save_raw("comap_index.json", __import__("json").dumps(results, ensure_ascii=False, indent=2),
             subdir="comap")
    print(f"[comap] 完成 {len(results)} 页")


if __name__ == "__main__":
    crawl()
