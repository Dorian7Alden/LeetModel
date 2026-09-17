# -*- coding: utf-8 -*-
"""GitHub API：搜索数学建模高星开源仓库 + 抓取 README 精华。未认证 60req/h 足够。"""
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import http_get, save_raw  # noqa: E402

QUERIES = [
    "数学建模",
    "数学建模竞赛",
    "数学建模 笔记",
    "mathematical modeling MCM",
    "美赛 论文",
    "CUMCM",
]

API = "https://api.github.com"
HEADERS = {"Accept": "application/vnd.github+json"}


def search_repos(q: str) -> list:
    r = http_get(f"{API}/search/repositories",
                 params={"q": q, "sort": "stars", "order": "desc", "per_page": 20},
                 headers=HEADERS)
    if not r or r.status_code != 200:
        print(f"  [github] search '{q}' http {r.status_code if r else 'None'}")
        return []
    items = r.json().get("items", [])
    return [{
        "full_name": it["full_name"], "stars": it["stargazers_count"],
        "description": it.get("description"), "html_url": it["html_url"],
        "topics": it.get("topics", []), "pushed_at": it.get("pushed_at"),
    } for it in items]


def fetch_readme(full_name: str) -> str | None:
    for branch in ("HEAD", "master", "main"):
        r = http_get(f"https://raw.githubusercontent.com/{full_name}/{branch}/README.md")
        if r and r.status_code == 200 and len(r.text) > 100:
            return r.text
    return None


def main():
    seen, repos = {}, []
    for q in QUERIES:
        print(f"[github] 搜索: {q}")
        for it in search_repos(q):
            if it["full_name"] not in seen:
                seen[it["full_name"]] = it
        time.sleep(1)
    repos = sorted(seen.values(), key=lambda x: -x["stars"])
    print(f"[github] 共 {len(repos)} 个独立仓库")
    save_raw("github_repos.json", json.dumps(repos, ensure_ascii=False, indent=2),
             subdir="github")

    # 抓 top15 仓库 README
    saved = 0
    for it in repos[:15]:
        readme = fetch_readme(it["full_name"])
        if readme:
            fn = it["full_name"].replace("/", "__")
            save_raw(f"README_{fn}.md", readme, subdir="github")
            saved += 1
            print(f"  ✓ {it['full_name']}（{len(readme)}字）")
        time.sleep(0.5)
    print(f"[github] 完成：仓库 {len(repos)}，README {saved}")


if __name__ == "__main__":
    main()
