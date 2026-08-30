<p align="center">
	<img src="https://gitee.com/kualk/pic-go/raw/master/imgs/image-20260423174510998.png" alt="LeetModel Logo" height="80px">
</p>
<h1 align="center">LeetModel</h1>

<p align="center"><strong>v2.0.0 · 微服务架构首个正式版本</strong></p>

### 项目介绍

LeetModel，中文名力模，是一款面向数学建模学习者的在线实训平台，核心聚焦 **"选题、组队、提交、评审、排行"** 全链路。

平台以数学建模论文的 AI 自动评审为核心能力，为建模学习者提供高效、可量化的论文评估与排名体验。

### 发布状态

当前稳定版本为 `v2.0.0`，已完成微服务架构下的 MVP 主链验收。普通用户可完成“选题 → 组队 → 提交 PDF → AI 评审 → 论文建议 → 排行榜”闭环，管理员可使用完整管理看板和 AI 质量评价能力。

本版本已通过前端生产构建、后端 17 模块自动化测试、12 个业务服务真实启动，以及桌面端和 `390x844` 移动端浏览器复验。

#### 版本沿革

| 版本 | 架构 | 说明 |
|------|------|------|
| `v1.0.0` | Spring Boot 单体架构 | 2026-05-04 停更前的单体项目版本 |
| `v2.0.0` | Spring Cloud 微服务架构 | 当前版本，完成选题、组队、提交、AI 评审、建议、排行和管理闭环 |

### 主要功能

- 选题：题目浏览、分类筛选、标签检索
- 组队：团队创建、成员管理、解散留存
- 提交：论文 PDF 上传与提交记录管理
- 评审：AI 自动评审打分
- 建议：基于题面、论文和评审结果生成结构化改进建议
- 排行：按题目展示最终提交的评审排名
- AI 客服：平台操作答疑、受控选题辅助和历史会话
- 管理端：用户与权限、题目与标签、队伍、提交、评审、建议、排行、AI 调用和质量评价

### 项目结构

| 路径 | 说明 |
|------|------|
| `LeetModel-backend/` | 微服务后端，当前主要开发区域。Maven 多模块工程，基于 Spring Boot 3 与 Spring Cloud Alibaba |
| `LeetModel-mock/` | Python mock 数据 API 服务，提供基础数据接口与场景脚本 |
| `LeetModel-frontend/` | MVP 唯一前端，基于 Vue 3，与当前后端接口完成真实联调 |
| `data/` | AI 评审本地测试数据集，按题目目录保存 Markdown 题面和对应论文 PDF；使用前阅读 `data/README.md` |
| `docs/` | 项目全部文档，按 project / troubleshooting / learning / concepts / standards 五类组织 |
| `rag_kb/` | 当前 RAG 知识源。V1 只索引 `数学建模/` 下排除 README 后的整理内容 |
| `legacy/` | 历史与暂缓内容隔离区，含旧单体后端、旧 SQL、旧 AI 协作配置与历史知识库 |
| `CONTEXT.md` | 给 AI 看的行为说明与协作规则 |
| `TODO.md` | 开发计划与进度追踪 |
| `README.md` | 本文件，项目正常说明 |

### 后端模块

| 模块 | 说明 |
|------|------|
| `gateway-service` | API 网关，路由转发、鉴权、跨域、文档聚合 |
| `user-service` | 用户服务，注册登录、个人信息、RBAC 权限 |
| `team-service` | 团队服务，组队、成员管理、解散留存 |
| `problem-service` | 题目服务，题目与标签 CRUD、分页筛选 |
| `admin-service` | 管理后台服务，Feign 聚合统计 |
| `submission-service` | PDF 上传、版本记录、最终提交与评审触发 |
| `ai-gateway-service` | AI 供应商适配、调用路由和审计 |
| `ai-review-service` | AI 论文评审工作流与结构化结果 |
| `ai-suggestion-service` | 论文改进建议生成与查询 |
| `ai-assistant-service` | AI 客服会话、消息历史和受控工具 |
| `ranking-service` | 最终提交排名计算与查询 |
| `ai-evaluation-service` | AI 评审版本的固定测试集与质量评价 |
| `common` | 公共模块，含 common-core、common-api、common-security |

### 本地运行

#### 环境要求

- JDK 17、Maven 3.9+
- Node.js 20+、npm 10+
- Docker Engine 与 Docker Compose
- 本地 Nacos（默认路径 `~/repo/nacos`），或通过 `NACOS_HOME` 指定安装目录

MySQL、Redis、MinIO 和独立第三方 AI 网关 new-api 由 Docker Compose 管理。业务数据库首次启动会执行 Flyway 迁移并写入演示数据。`ai-gateway-service` 的文本与多模态 Chat 默认通过 new-api 调用。

#### 启动 new-api

```bash
cd LeetModel-backend
docker compose up -d --wait new-api
curl --fail http://localhost:3000/api/status
```

首次启动后访问 `http://localhost:3000` 完成管理员初始化，再配置供应商渠道和 LeetModel 专用 Relay Token。数据保存在 Docker 卷 `new-api-data` 中。详细边界、接口和验证方式见 [new-api 第三方网关集成](docs/project/02-架构设计/new-api第三方网关集成.md)。

#### 启动 Elasticsearch

RAG V1 使用固定版本 Elasticsearch `8.14.3`。它可独立启动并保留索引数据：

```bash
cd LeetModel-backend
docker compose up -d --wait elasticsearch
curl -fsS http://127.0.0.1:9200/_cluster/health
```

本地端口仅绑定 `127.0.0.1:9200`，JVM 堆限制为 512 MiB，容器内存限制为 1 GiB。常规停止使用 `docker compose stop elasticsearch`；`docker compose down` 默认保留命名卷。不要使用 `down -v` 或删除 `elasticsearch-data`，除非明确要清空本地索引。

#### 1. 启动后端

```bash
cd LeetModel-backend
./scripts/start-mvp.sh
```

脚本会构建并启动 12 个业务服务，网关地址为 `http://localhost:8080`。已完成构建时可使用 `./scripts/start-mvp.sh --skip-build`。

AI 对话与评审要求 `ai-gateway-service` 的运行环境提供 new-api Relay Token。不要将 Token 写入仓库文件；未配置时 AI 网关无法启动。

#### 2. 启动前端

新建终端窗口：

```bash
cd LeetModel-frontend
npm install
npm run dev
```

请使用终端输出的地址访问（默认为 `http://localhost:5173`）。本地开发服务器会将 `/api` 请求代理到后端网关。演示管理员账号为 `admin`，密码为 `123456`；普通使用者可直接注册。

#### 3. 停止服务

```bash
cd LeetModel-backend
./scripts/stop-mvp.sh
```

该脚本只停止业务服务，保留 MySQL、Redis、MinIO 和 Nacos。如需停止 Docker 基础设施，再执行 `docker compose down`。

### 验证命令

```bash
# 后端全量测试
cd LeetModel-backend
mvn test

# 前端生产构建
cd ../LeetModel-frontend
npm run build
```

运行或评审 AI 流程前，请先阅读 `data/README.md` 中的测试数据对应关系和文件限制。
