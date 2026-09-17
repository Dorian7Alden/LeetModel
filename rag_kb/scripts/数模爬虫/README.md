# 数模爬虫工具（数学建模资料采集）

> 用于采集数学建模竞赛资料的开源爬虫工具集，个人知识库用途。
> 数据落盘：`data/数模爬取/`（原始 HTML/JSON/文本），整理后的知识笔记在 `数学建模/`。

## 工具链（开源优先）

| 工具 | 用途 | 安装位置 |
|------|------|---------|
| Playwright + Chromium | 浏览器级抓取，过 JS 渲染与基础反爬 | `~/.cache/ms-playwright/` |
| trafilatura | HTML 正文提取 | `~/ai-infra/crawl-venv` |
| crawl4ai | 通用网页→Markdown（JS 站首选） | 同上 |
| yt-dlp | B站等视频元数据 | 同上 |
| MediaCrawler（53k⭐ 开源） | 知乎/小红书/B站平台级爬虫（需登录态，备选） | `~/ai-infra/MediaCrawler` |

## 脚本清单（sites/）

| 脚本 | 目标 | 渠道状态（2026-08 实测） |
|------|------|------------------------|
| cumcm.py | 国赛官网 www.mcm.edu.cn（通知/规则/格式规范） | ✅ 成功（含 2026 规则与格式规范全文） |
| comap.py | 美赛官网 comap.org + contest.comap.com | ✅ 成功（规则页 4 万字、2026 结果） |
| wikipedia.py | 维基百科中文词条（MediaWiki API） | ✅ 数学建模/数学模型词条成功 |
| crawl4ai_generic.py | 百度百科/赛氪（JS 站） | ✅ 百科 5 词条成功；赛氪首页成功 |
| github.py | GitHub API 搜数学建模高星仓库 + README | ✅ 98 仓库 / 12 README |
| bilibili_pw.py | B站搜索（视频标题/UP主/播放） | ✅ 160 条视频信息 |
| bing_blog_pw.py | Bing 聚合博客/经验文章 | ✅ 14 篇文章正文 |
| madio.py | 数学中国论坛（http） | ⚠️ 首页可抓，帖子解析待修（版块链接格式需更新） |
| tieba_pw.py | 百度贴吧 | ❌ 百度安全验证拦截（需登录/验证码） |
| zhihu_pw.py | 知乎 | ❌ 登录墙（改用 MediaCrawler CDP 模式，见下） |

## MediaCrawler 深度采集（CDP 连接用户 Chrome，2026-08-04 完成）

| 平台 | 采集方式 | 成果 | 数据位置 |
|------|---------|------|---------|
| 知乎 | CDP 登录态搜索采集（8 关键词 + AI 关键词） | 344 条回答/文章 + 10405 条评论 | MediaCrawler/data/zhihu → 转换至 data/数模爬取/zhihu_mc/ |
| 小红书 | CDP 登录态搜索采集 | 59 条笔记 + 2053 条评论 | MediaCrawler/data/xhs → data/数模爬取/xhs_mc/ |
| B站 | CDP 登录态搜索采集 | 39 条视频详情 + 406 条评论 | MediaCrawler/data/bili → data/数模爬取/bili_mc/ |

- 流程：`mc_switch_platform.py {zhihu|xhs|bili}` 切平台 → `uv run main.py --platform X --lt cookie --type search`
- 转换：`media_to_kb.py --platform X`（JSONL → 知识库素材 Markdown）
- 启动调试 Chrome：`start_cdp_chrome.sh`（deb 版 Chrome，Flatpak 沙箱版无法对外调试）

## 用法

```bash
PY=/home/dorian/ai-infra/crawl-venv/bin/python
$PY sites/cumcm.py           # 任意脚本名
$PY sites/crawl4ai_generic.py baike   # 带参数：baike|saikr
```

## 通用库

- `lib/fetch.py`：httpx 抓取（UA/重试）、trafilatura 提取、playwright 渲染、数据落盘（统一到 `data/数模爬取/`）。

## 说明

- 各渠道不可用原因记录在脚本注释与上表；知乎/小红书可通过 MediaCrawler 配置登录 cookie 后采集（`~/ai-infra/MediaCrawler`）。
- 本工具仅用于个人知识库资料收集，未做破解类对抗。
