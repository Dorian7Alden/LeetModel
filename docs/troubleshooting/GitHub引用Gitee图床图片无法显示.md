# GitHub 引用 Gitee 图床图片无法显示

> 影响范围：README 与 `docs/` 中引用外部图床的图片

## 报错现象

README 里的系统架构图在本地 Typora 和浏览器直接粘贴图片地址时都能正常显示，仓库推送到 GitHub 后，GitHub 仓库首页对应的位置却是裂图。同一份 README 里的 Logo 显示正常，其它 6 张截图也正常（它们是仓库内相对路径）。

## 排查过程

1. 先确认原图本身是否可访问：

```bash
curl -sL -o /tmp/arch.png -w "HTTP %{http_code} 类型 %{content_type} 大小 %{size_download}\n" \
  'https://gitee.com/Dorian7Alden/pic-go/raw/master/typora/LeetModel系统架构全景图.png'
```

结果为 `HTTP 200 类型 image/png 大小 483186`，说明原图没问题，问题出在渲染平台取图这一跳。

2. 取 GitHub 实际渲染时使用的图片地址。GitHub 不会让浏览器直连外部图床，而是把外链改写成自己的代理地址，从仓库页面 HTML 里可以直接读到：

```bash
curl -s -A "Mozilla/5.0" https://github.com/<owner>/<repo> > /tmp/page.html
grep -o '<img[^>]*gitee.com[^>]*>' /tmp/page.html
```

得到的 `src` 指向 `https://camo.githubusercontent.com/<hmac>/<十六进制URL>`，把十六进制部分解码，正是 README 里那条 Gitee 地址，说明外链已被正确识别和改写。

3. 直接请求这个代理地址：

```bash
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" "https://camo.githubusercontent.com/<hmac>/<十六进制URL>"
```

结果是 `504 text/plain`，正文为 `Error Fetching Resource`，即 GitHub 的代理去抓图失败。

4. 对照实验：同一份 README 里的 Logo 也是 Gitee 外链，但体积只有 97KB，它的代理地址返回 200；本机带上 GitHub 代理的 User-Agent、不带 Referer 直接请求原图也能拿到 PNG。说明不是路径编码问题，也不是 Gitee 对所有来源一刀切拒绝，而是海外代理按这条链路取图超时。

## 根因分析

GitHub 通过 `camo.githubusercontent.com` 代表浏览器抓取外部图片，抓取有超时限制。Gitee 的 `/raw/` 地址已经不是静态直出，而是 302 跳转到 `raw.giteeusercontent.com` 的**带签名、带过期时间的临时地址**：

```text
HTTP/1.1 302 Found
Location: https://raw.giteeusercontent.com/<repo>/raw/master/<path>.png?metadata=...&signature=...
```

从国内网络直连这条链路很快，但 GitHub 的代理服务器在海外，完成“跳转 → 生成签名地址 → 回源取图”的耗时超过了代理的超时阈值，于是返回 504。图片越大越容易超时：97KB 的 Logo 能过，483KB 的架构图就失败，这正好解释了“有的外链图能显示、有的不能”。

## 修复方案

仓库内文档的图片改为仓库内资源，用相对路径引用，不再依赖任何图床：

1. 把图片作为资源提交到 `docs/assets/` 下，命名使用 ASCII，避免中文路径在不同平台的转义差异。
2. README 与其它文档统一用相对路径引用；子目录文档按层级写相对路径。
3. 同一张图在多处被引用时只保留一份资源，避免复制出多个副本。
4. 项目 Logo 使用仓库里的一等源图 `LeetModel-frontend/src/assets/images/logo-big.png`（与 README 原先展示的图逐像素一致），复制一份到 `docs/assets/logo.png` 供文档引用。

本次修复范围只覆盖根目录 README：`docs/` 下其它文档的图床外链按项目决定保持原样，它们不是仓库首页的展示面。文档规范同步收窄为“根目录 README 必须使用仓库内图片资源”，并说明其它文档的外链在 GitHub 上可能显示不出来。

## 回归验证

- README 中 8 个相对路径逐个用 `git cat-file -e HEAD:<path>` 校验，均能在仓库中找到对应文件。
- 图片体积符合文档规范（架构图 483KB、Logo 171KB，均小于 1MB）。
- GitHub 渲染仓库内图片走 `raw.githubusercontent.com`，不再经过外部图床代理，直接和仓库内容一致。

## 经验

- “链接能打开”不等于“渲染时能显示”。判断图片问题必须看渲染平台实际发出的请求，而不是只看原始地址。
- 外部图床对仓库内文档是不可靠依赖：防盗链、跳转签名、跨境网络和代理超时任意一项变化都会让图片失效。
- 同一类问题往往是批量存在的，修复时先统计所有外链，优先处理对外展示面（README、架构文档），测试数据里的外链单独评估。
