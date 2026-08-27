<p align="center">
	<img src="https://gitee.com/kualk/pic-go/raw/master/imgs/image-20260423174510998.png" alt="LeetModel Logo" height="80px">
</p>
<h1 align="center">LeetModel</h1>

### 项目介绍

LeetModel，中文名力模，是一款面向数学建模学习者的在线实训平台，核心聚焦 **"选题、组队、提交、评审、排行"** 全链路。

平台以数学建模论文的 AI 自动评审为核心能力，为建模学习者提供高效、可量化的论文评估与排名体验。

### 主要功能

- 选题：题目浏览、分类筛选、标签检索
- 组队：团队创建、成员管理、解散留存
- 提交：论文 PDF 上传与提交记录管理
- 评审：AI 自动评审打分
- 排行：多维度排行榜

### 项目结构

| 路径 | 说明 |
|------|------|
| `LeetModel-backend/` | 微服务后端，当前主要开发区域。Maven 多模块工程，基于 Spring Boot 3 与 Spring Cloud Alibaba |
| `LeetModel-mock/` | Python mock 数据 API 服务，提供基础数据接口与场景脚本 |
| `LeetModel-frontend/` | MVP 唯一前端，基于 Vue 3，与当前后端接口完成真实联调 |
| `data/` | AI 评审本地测试数据集，按题目目录保存 Markdown 题面和对应论文 PDF；使用前阅读 `data/README.md` |
| `docs/` | 项目全部文档，按 project / troubleshooting / learning / concepts / standards 五类组织 |
| `legacy/` | 历史与暂缓内容隔离区，含旧单体后端、旧 SQL、旧 AI 协作配置与暂缓的 RAG 知识库 |
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
| `common` | 公共模块，含 common-core、common-api、common-security |

### 本地运行

#### 环境要求

- JDK 17、Maven 3.9+
- Node.js 20+、npm 10+
- Docker Engine 与 Docker Compose
- 本地 Nacos（默认路径 `~/repo/nacos`），或通过 `NACOS_HOME` 指定安装目录

MySQL、Redis 和 MinIO 由 Docker Compose 启动。首次启动会执行 Flyway 迁移并写入演示数据。

#### 1. 启动后端

```bash
cd LeetModel-backend
./scripts/start-mvp.sh
```

脚本会构建并启动 12 个业务服务，网关地址为 `http://localhost:8080`。已完成构建时可使用 `./scripts/start-mvp.sh --skip-build`。

AI 对话与评审需要模型密钥，在启动脚本前按需设置：

```bash
export DEEPSEEK_API_KEY="你的密钥"
export KIMI_API_KEY="你的密钥"
```

未配置密钥时，题库、组队、提交等非 AI 功能仍可使用，AI 功能会明确返回不可用状态。不要将密钥写入仓库文件。

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
