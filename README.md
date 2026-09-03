<p align="center">
	<img src="https://gitee.com/kualk/pic-go/raw/master/imgs/image-20260423174510998.png" alt="LeetModel Logo" height="80px">
</p>
<h1 align="center">LeetModel</h1>

<p align="center"><strong>v2.0.0 · 微服务架构首个正式版本</strong></p>

### 项目介绍

LeetModel，中文名力模，是一款面向数学建模学习者的在线实训平台，核心聚焦 **"选题、组队、提交、评审、排行"** 全链路。

平台以数学建模论文的 AI 自动评审为核心能力，为建模学习者提供高效、可量化的论文评估与排名体验。

### 发布状态

当前已发布稳定版本为 `v2.0.0`。`dev` 分支在该发布基线上继续开发，已经完成微服务架构下的 MVP 主链验收。普通用户可完成“选题 → 组队 → 提交 PDF → AI 评审 → 论文建议 → 排行榜”闭环，管理员可使用完整管理看板和 AI 质量评价能力。

当前 `dev` 基线已通过前端生产构建、后端 20 项 Maven Reactor 全量构建与 605 项自动化测试、13 个业务服务真实启动，以及桌面端和 `390x844` 移动端浏览器复验。RocketMQ 五条业务消息协议、六个相关服务的全新 MySQL 迁移和统一消息运维接口也已完成真实环境验收。这些标签之后的开发成果不追溯计入 `v2.0.0` 发布内容。

#### 版本沿革

| 版本 | 架构 | 说明 |
|------|------|------|
| `v1.0.0` | Spring Boot 单体架构 | 2026-05-04 停更前的单体项目版本 |
| `v2.0.0` | Spring Cloud 微服务架构 | 当前已发布版本，完成选题、组队、提交、AI 评审、建议、排行和管理闭环 |

### 主要功能

- 选题：题目浏览、分类筛选、标签检索
- 组队：团队创建、成员管理、解散留存
- 提交：论文 PDF 上传与提交记录管理
- 评审：AI 自动评审打分
- 建议：基于题面、论文和评审结果生成结构化改进建议
- 排行：按题目展示最终提交的评审排名
- AI 客服：平台操作答疑、受控选题辅助和历史会话
- 管理端：用户与权限、题目与标签、队伍、提交、评审、建议、排行、AI 调用、质量评价和可靠消息运维

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
| `AGENTS.md` | 给 AI 看的行为说明与协作规则 |
| `TODO.md` | 当前任务、候选任务和条件任务 |
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
| `ai-gateway-service` | AI 业务调度、new-api 协议适配、调用审计和计量 |
| `ai-review-service` | AI 论文评审工作流与结构化结果 |
| `ai-suggestion-service` | 论文改进建议生成与查询 |
| `ai-assistant-service` | AI 客服会话、消息历史和受控工具 |
| `knowledge-retrieval-service` | 版本化知识检索、来源适用性校验和检索快照 |
| `ranking-service` | 最终提交排名计算与查询 |
| `ai-evaluation-service` | AI 评审版本的固定测试集与质量评价 |
| `common` | 公共模块，含 common-core、common-api、common-security、common-cache、common-messaging、common-ai |

### 本地运行

#### 环境要求

- JDK 17、Maven 3.9+
- Node.js 20+、npm 10+
- Docker Engine 与 Docker Compose

MySQL、安全状态 Redis、业务缓存 Redis、MinIO、Nacos 2.3.2、Elasticsearch 8.14.3、RocketMQ 5.5.0 和独立第三方 AI 网关 new-api 由 Docker Compose 管理。业务缓存 Redis 绑定 `127.0.0.1:6380`，使用 `volatile-lfu` 且不持久化；Token 黑名单继续使用 `6379` 的安全状态 Redis。业务数据库首次启动会执行 Flyway 迁移并写入演示数据。`ai-gateway-service` 的文本与多模态 Chat 默认通过 new-api 调用。

#### 启动 Nacos

Nacos 使用单机内置 Derby，配置和日志分别持久化到 `nacos-data` 和 `nacos-logs` 命名卷：

```bash
cd LeetModel-backend
docker compose up -d --wait nacos
curl --fail http://127.0.0.1:8848/nacos/v1/console/health/readiness
```

Nacos 控制台为 `http://127.0.0.1:8848/nacos`。常规停止可使用 `docker compose stop nacos`；`docker compose down` 会停止容器，但默认保留命名卷。不要使用 `down -v` 或删除 `nacos-data`，除非明确要清空 Nacos 配置。

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

#### 验证 Actuator 与 Prometheus

后端 14 个服务的 Actuator/Prometheus 静态契约可独立验证；全部服务由 `start-mvp.sh` 启动后可附加运行时验证：

```bash
cd LeetModel-backend
./scripts/verify-actuator-contract.sh
./scripts/verify-metric-contract.sh
./scripts/verify-actuator-contract.sh --runtime
```

`verify-metric-contract.sh` 校验 HTTP 直方图、关键业务指标和禁止 ID 标签策略。`/actuator/health/liveness` 与 `/readiness` 是编排探针；`info/prometheus` 只允许本机或携带 `X-LeetModel-Management-Token` 且匹配 `MANAGEMENT_TOKEN` 的请求。

#### 启动与验证观测栈

本地观测栈会启动 SkyWalking/BanyanDB、Prometheus、Alertmanager 与 Grafana。启动脚本在 Git 忽略目录生成或复用管理 Token；随后用 `start-mvp.sh` 启动的 14 个服务会自动使用同一 Token，Prometheus 直接抓取各服务而不经过 Gateway：

```bash
cd LeetModel-backend
./scripts/start-observability.sh
./scripts/start-mvp.sh

# 快速配置门禁，以及包含临时服务与 Prometheus 中断的完整运行验收
./scripts/verify-observability-stack.sh --static
./scripts/verify-observability-stack.sh

# 生产告警规则/路由静态门禁，以及隔离 webhook 的 firing/resolved 闭环演练
./scripts/verify-alerting-contract.sh
./scripts/drill-alerting.sh
```

Prometheus、Alertmanager、Grafana、OAP 和 Horizon 分别只在本机 `19090`、`19093`、`13000`、`11234/11800/12800/17128` 与 `18080` 提供端口。Grafana 自动加载系统总览、MVP 主链、AI 资源与稳定性、异步任务、可靠消息和遥测管道六类看板。22 条规则覆盖服务/遥测空洞、Outbox/MQ/DLQ、AI 队列/UNKNOWN 和领域租约；版本化 Runbook 位于 `docs/runbooks/observability/`。告警闭环演练只使用临时端口 `19094`，指标栈验收只使用临时端口 `18094`，均不停止现有标准端口业务服务。

#### 启动 RocketMQ

本地可靠消息环境固定使用 Broker `5.5.0` 与 RocketMQ Spring `2.3.3`。自动创建 Topic 和消费组已关闭，必须通过版本化脚本显式创建五个业务 NORMAL Topic、一个操作审计专用 NORMAL Topic 及其六个消费组：

```bash
cd LeetModel-backend
docker compose up -d --wait rocketmq-namesrv rocketmq-broker
./scripts/init-rocketmq.sh
./scripts/verify-rocketmq.sh
./scripts/verify-audit-contract.sh
./scripts/drill-messaging-failures.sh status
```

需要同时验证 Broker 重启与数据卷恢复时使用 `ROCKETMQ_VERIFY_RESTART=true ./scripts/verify-rocketmq.sh`。真实 Java 发送、重复消费、Inbox 幂等和客户端重试测试使用 `RUN_ROCKETMQ_INTEGRATION=true mvn -pl common/common-messaging test`。操作审计的严格信封、ACL 2.0 正负路径、固定重试与 DLQ 使用 `./scripts/verify-audit-rocketmq.sh` 在一次性非标准端口 Broker 集中验证，不修改常驻开发 Broker。`scripts/drill-messaging-failures.sh` 还提供 Broker 网络中断、MySQL 短故障和指定消息服务进程终止等单步演练命令；暂停故障必须显式执行对应的 resume 命令。可选 Dashboard 通过 `docker compose --profile tools up -d rocketmq-dashboard` 启动并访问 `http://127.0.0.1:8180`。

NameServer、Broker 和 Dashboard 均只绑定本机端口。本地 Broker 数据保存在 `rocketmq-broker-store` 命名卷；常规停止使用 `docker compose stop rocketmq-broker rocketmq-namesrv`。`docker compose down` 默认保留消息，禁止使用 `down -v` 或删除 RocketMQ 命名卷，除非明确要清空本地消息与消费位点。常驻单 Broker 仍是受回环网络保护的开发环境；生产环境必须另行部署多副本集群，采用 `docker/rocketmq/broker-acl.conf.example` 的 ACL 2.0 开关并从 Secret Manager 注入管理与应用凭据。审计生产账号只授予精确 Topic `Pub`，归档账号只授予精确 Topic/Group `Sub`，不得把隔离验收的临时凭据用于其他环境。

#### 1. 启动后端

```bash
cd LeetModel-backend
./scripts/start-mvp.sh
```

脚本会确保 Elasticsearch、RocketMQ 等项目基础设施和显式消息资源就绪，并构建、启动 14 个业务服务（包含端口 `8093` 的 knowledge-retrieval-service 与端口 `8094` 的 audit-service）；网关地址为 `http://localhost:8080`。已完成构建时可使用 `./scripts/start-mvp.sh --skip-build`。

AI 对话与评审要求 `ai-gateway-service` 的运行环境提供 new-api Relay Token。不要将 Token 写入仓库文件；未配置时 AI 网关无法启动。

需要本地 Trace/APM 时，先执行 `./scripts/start-observability.sh`，再以 `LEETMODEL_SKYWALKING_ENABLED=true ./scripts/start-mvp.sh` 启动业务服务。组件版本、端口、资源、登录和兼容限制见 [可观测性组件基线](docs/project/02-架构设计/可观测性组件基线.md)。

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

该脚本只停止业务服务，保留 MySQL、Redis、MinIO、Nacos、Elasticsearch 和 RocketMQ 等 Docker 基础设施。如需停止 Docker 基础设施，再执行 `docker compose down`；该命令默认保留命名卷。

### 验证命令

```bash
# 后端全量测试
cd LeetModel-backend
mvn test

# 可观测基线真实运行验收
./scripts/verify-observability-baseline.sh

# Metrics/Grafana/Alertmanager 静态与真实运行验收
./scripts/verify-observability-stack.sh --static
./scripts/verify-observability-stack.sh
./scripts/verify-alerting-contract.sh
./scripts/drill-alerting.sh

# 前端生产构建
cd ../LeetModel-frontend
npm run build
```

运行或评审 AI 流程前，请先阅读 `data/README.md` 中的测试数据对应关系和文件限制。
