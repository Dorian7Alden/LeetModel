# -*- coding: utf-8 -*-
"""维基百科中文：数学建模相关词条全文抓取（MediaWiki API extracts）。"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import http_get, save_raw, clean_text, DATA_DIR  # noqa: E402

API = "https://zh.wikipedia.org/w/api.php"


def search_titles(query: str, limit: int = 15) -> list:
    """搜索词条，返回 (title, snippet)。中文维基部分词条未建，用搜索兜底。"""
    params = {"action": "query", "format": "json", "list": "search",
              "srsearch": query, "srlimit": limit, "srprop": "snippet"}
    r = http_get(API, params=params)
    if not r or r.status_code != 200:
        return []
    return [(it["title"], it.get("snippet", "")) for it in r.json().get("query", {}).get("search", [])]


def fetch(title: str) -> dict:
    params = {
        "action": "query",
        "format": "json",
        "prop": "extracts|revisions",
        "explaintext": 1,
        "rvprop": "timestamp",
        "rvslots": "main",
        "titles": title,
        "formatversion": 2,
    }
    r = http_get(API, params=params)
    if not r or r.status_code != 200:
        return {"title": title, "error": f"http {r.status_code if r else 'None'}"}
    q = r.json().get("query", {})
    pages = q.get("pages") or []
    if not pages:
        return {"title": title, "error": "no pages"}
    page = pages[0]
    return {
        "title": page.get("title"),
        "extract": clean_text(page.get("extract") or ""),
        "rev_timestamp": (page.get("revisions") or [{}])[0].get("timestamp", ""),
        "url": f"https://zh.wikipedia.org/wiki/{page.get('title')}",
    }


def main():
    seeds = ["数学建模", "数学模型"]
    # 搜索扩展词条
    for q in ["数学建模竞赛", "大学生数学建模", "数学竞赛"]:
        seeds += [t for t, _ in search_titles(q)]
    seen, titles = set(), []
    for t in seeds:
        if t not in seen:
            seen.add(t)
            titles.append(t)
    out = []
    for t in titles[:15]:
        print(f"[wiki] {t}")
        data = fetch(t)
        if data.get("extract"):
            print(f"  字数: {len(data['extract'])}")
        else:
            print(f"  无词条: {data.get('error')}")
        out.append(data)
        fn = t.replace("/", "_")
        save_raw(f"{fn}.json", json.dumps(data, ensure_ascii=False, indent=2), subdir="wiki")
    with (DATA_DIR / "wiki" / "_index.json").open(
            "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"[wiki] 完成 {len(out)} 条 → data/数模爬取/wiki/")


if __name__ == "__main__":
    main()
