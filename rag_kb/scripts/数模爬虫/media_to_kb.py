# -*- coding: utf-8 -*-
"""MediaCrawler 采集数据 → 知识库素材 转换器。
读取 ~/ai-infra/MediaCrawler/data/*.jsonl（知乎回答/小红书笔记/B站视频+评论），
转成 data/数模爬取/{zhihu,xhs,bili}_mc/ 下的 Markdown 素材（标题/作者/链接/正文/评论）。

用法: python media_to_kb.py [--platform zhihu|xhs|bili]
"""
import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lib.fetch import DATA_DIR, clean_text  # noqa: E402

MC_DATA = Path.home() / "ai-infra" / "MediaCrawler" / "data"


def read_jsonl(f: Path) -> list:
    out = []
    for line in f.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            try:
                out.append(json.loads(line))
            except Exception:
                pass
    return out


def zhihu_item(d: dict) -> str:
    """知乎数据行 → Markdown 素材（回答/文章）。字段以 MediaCrawler zhihu_config 实际输出为准。"""
    title = d.get("title") or "未命名问题"
    author = d.get("user_nickname") or ""
    url = d.get("content_url") or ""
    content = d.get("content_text") or d.get("desc") or ""
    likes = d.get("voteup_count") or d.get("like_count") or ""
    kw = d.get("source_keyword") or ""
    md = [f"# {title}", f"作者: {author} | 链接: {url} | 赞同: {likes} | 来源关键词: {kw}",
          "", clean_text(str(content))[:8000]]
    return "\n".join(md)


def zhihu_comments(comments: list, content_id: str) -> str:
    """按 content_id 关联评论，拼成评论小节。"""
    rel = [c for c in comments if str(c.get("content_id", "")) == str(content_id)]
    if not rel:
        return ""
    out = ["", "## 评论"]
    for c in rel[:10]:
        who = c.get("user_nickname") or ""
        txt = c.get("content") or ""
        likes = c.get("like_count") or ""
        out.append(f"- {who}（赞{likes}）: {clean_text(str(txt))[:300]}")
    return "\n".join(out)


def xhs_item(d: dict) -> str:
    """小红书笔记 → Markdown 素材。"""
    title = d.get("title") or "未命名"
    author = d.get("author_name") or d.get("nickname") or ""
    url = d.get("url") or f"https://www.xiaohongshu.com/explore/{d.get('note_id','')}"
    content = d.get("desc") or d.get("content") or ""
    likes = d.get("like_count") or d.get("likes") or ""
    comments = d.get("comments") or []
    md = [f"# {title}", f"作者: {author} | 链接: {url} | 点赞: {likes}",
          "", clean_text(str(content))[:8000]]
    if comments:
        md.append("")
        md.append("## 评论")
        for c in comments[:10]:
            who = c.get("author_name") or c.get("nickname") or ""
            txt = c.get("content") or c.get("comment_content") or str(c)[:200]
            md.append(f"- {who}: {clean_text(str(txt))[:300]}")
    return "\n".join(md)


def bili_item(d: dict) -> str:
    """B站视频 → Markdown 素材。"""
    title = d.get("title") or "未命名"
    author = d.get("author_name") or d.get("uploader") or ""
    url = d.get("url") or f"https://www.bilibili.com/video/{d.get('bvid','')}"
    content = d.get("desc") or d.get("description") or ""
    stats = d.get("stats") or {}
    likes = stats.get("likes", "") if isinstance(stats, dict) else ""
    comments = d.get("comments") or []
    md = [f"# {title}", f"UP主: {author} | 链接: {url} | 点赞: {likes}",
          "", clean_text(str(content))[:4000]]
    if comments:
        md.append("")
        md.append("## 评论")
        for c in comments[:10]:
            who = c.get("author_name") or c.get("member") or ""
            txt = c.get("content") or c.get("comment_content") or str(c)[:200]
            md.append(f"- {who}: {clean_text(str(txt))[:300]}")
    return "\n".join(md)


def main(platform: str):
    sub = f"{platform}_mc"
    out_dir = DATA_DIR / sub
    out_dir.mkdir(parents=True, exist_ok=True)
    total = 0

    if platform == "zhihu":
        # 知乎：contents 与 comments 分开存储，评论按 content_id 关联
        content_files = list((MC_DATA / "zhihu" / "jsonl").glob("search_contents_*.jsonl"))
        comment_files = list((MC_DATA / "zhihu" / "jsonl").glob("search_comments_*.jsonl"))
        all_comments = []
        for cf in comment_files:
            all_comments += read_jsonl(cf)
        for cf in content_files:
            rows = read_jsonl(cf)
            ok = 0
            for i, d in enumerate(rows):
                if not isinstance(d, dict):
                    continue
                try:
                    md = zhihu_item(d)
                    md += zhihu_comments(all_comments, d.get("content_id", ""))
                except Exception:
                    continue
                if len(md) < 100:
                    continue
                title = (d.get("title") or f"item{i}")[:40]
                fn = re.sub(r"[^\w一-鿿]+", "_", title)
                (out_dir / f"{cf.stem[:12]}_{fn}.md").write_text(md, encoding="utf-8")
                ok += 1
            if ok:
                print(f"  {cf.name}: 转换 {ok} 条")
            total += ok
    else:
        # xhs/bili：只处理内容文件（comments 单独文件，跳过避免误转）
        handlers = {"xhs": xhs_item, "bili": bili_item}
        for f in sorted((MC_DATA / platform).glob("**/*.jsonl")):
            if "comment" in f.name:
                continue
            rows = read_jsonl(f)
            if not rows:
                continue
            ok = 0
            for i, d in enumerate(rows):
                if not isinstance(d, dict):
                    continue
                try:
                    md = handlers[platform](d)
                except Exception:
                    continue
                if len(md) < 100:
                    continue
                title = (d.get("title") or f"item{i}")[:40]
                fn = re.sub(r"[^\w一-鿿]+", "_", title)
                (out_dir / f"{f.stem[:20]}_{fn}.md").write_text(md, encoding="utf-8")
                ok += 1
            if ok:
                print(f"  {f.name}: 转换 {ok} 条")
            total += ok
    print(f"[media_to_kb] {platform} 共 {total} 条 → {out_dir}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--platform", default="zhihu", choices=["zhihu", "xhs", "bili"])
    main(ap.parse_args().platform)
