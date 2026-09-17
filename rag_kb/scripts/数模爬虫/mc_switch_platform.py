# -*- coding: utf-8 -*-
"""切换 MediaCrawler 采集平台。用法: python mc_switch_platform.py xhs|bili|zhihu
同时可带 --max 调整每关键词采集条数。"""
import argparse
import re
from pathlib import Path

CONFIG = Path.home() / "ai-infra" / "MediaCrawler" / "config" / "base_config.py"

KEYWORDS = {
    "zhihu": "数学建模,数学建模竞赛,数学建模经验,美赛,国赛,数学建模踩坑,数学建模论文写作,数学建模入门",
    "xhs": "数学建模,数学建模竞赛,数学建模经验,美赛,国赛,数学建模踩坑,数学建模论文写作,数学建模入门",
    "bili": "数学建模,数学建模竞赛,数学建模经验,美赛,国赛,数学建模踩坑,数学建模论文写作,数学建模入门",
}


def switch(platform: str, max_notes: int | None):
    s = CONFIG.read_text(encoding="utf-8")
    s = re.sub(r'PLATFORM = "[a-z]+"', f'PLATFORM = "{platform}"', s, count=1)
    s = re.sub(r'KEYWORDS = "[^"]*"', f'KEYWORDS = "{KEYWORDS[platform]}"', s, count=1)
    if max_notes:
        s = re.sub(r'CRAWLER_MAX_NOTES_COUNT = \d+',
                   f'CRAWLER_MAX_NOTES_COUNT = {max_notes}', s, count=1)
    CONFIG.write_text(s, encoding="utf-8")
    for line in s.splitlines():
        if line.strip().startswith(("PLATFORM =", "KEYWORDS =", "CRAWLER_MAX_NOTES_COUNT")):
            print(" ", line.strip()[:90])


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("platform", choices=["zhihu", "xhs", "bili"])
    ap.add_argument("--max", type=int, default=None)
    switch(ap.parse_args().platform, ap.parse_args().max)
