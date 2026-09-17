## 本地开发与运行

> 本文承接根 README 中的完整本地运行、基础设施、观测栈和验收说明，面向需要深入排障或复验系统的开发者。


### 运行前提

- JDK 17、Maven 3.9+
- Node.js 20+、npm 10+
- Docker Engine 与 Docker Compose

MySQL、安全状态 Redis、业务缓存 Redis、MinIO、Nacos 2.3.2、Elasticsearch 8.14.3、RocketMQ 5.5.0 和独立第三方 AI 网关 new-api 由 Docker Compose 管理。

业务缓存 Redis 绑定 `127.0.0.1:6380`，使用 `volatile-lfu` 且不持久化；Token 黑名单继续使用 `6379` 的安全状态 Redis。业务数据库首次启动会执行 Flyway 迁移并写入演示数据。`ai-gateway-service` 的文本与多模态 Chat 默认通过 new-api 调用。


### 启动 Nacos

Nacos 使用单机内置 Derby，配置和日志分别持久化到 `nacos-data` 和 `nacos-logs` 命名卷：

```bash
docker compose -f compose.yaml up -d --wait nacos
curl --fail http://127.0.0.1:8848/nacos/v1/console/health/readiness
```

Nacos 控制台为 `http://127.0.0.1:8848/nacos`。

常规停止可使用：

```bash
docker compose -f compose.yaml stop nacos
```

`docker compose down` 会停止容器，但默认保留命名卷。不要使用 `docker compose down -v` 或删除 `nacos-data`，除非明确要清空 Nacos 配置。


### 启动 new-api

```bash
docker compose -f compose.yaml up -d --wait new-api
curl --fail http://localhost:3000/api/status
```

首次启动后访问 `http://localhost:3000` 完成管理员初始化，再配置供应商渠道和 LeetModel 专用 Relay Token。数据保存在 Docker 卷 `new-api-data` 中。

详细边界、接口和验证方式见 [new-api 第三方网关集成](../project/02-架构设计/new-api第三方网关集成.md)。


### 启动 Elasticsearch

RAG V1 使用固定版本 Elasticsearch `8.14.3`。它可以独立启动并保留索引数据：

```bash
docker compose -f compose.yaml up -d --wait elasticsearch
curl -fsS http://127.0.0.1:9200/_cluster/health
```

本地端口仅绑定 `127.0.0.1:9200`，JVM 堆限制为 512 MiB，容器内存限制为 1 GiB。

常规停止：

```bash
docker compose -f compose.yaml stop elasticsearch
```

不要使用 `docker compose down -v` 或删除 `elasticsearch-data`，除非明确要清空本地索引。


### 验证 Actuator 与 Prometheus

后端 15 个服务的 Actuator/Prometheus 静态契约可独立验证。全部服务由 `start-mvp.sh` 启动后，可附加运行时验证：

```bash
./scripts/verify/verify-actuator-contract.sh
./scripts/verify/verify-metric-contract.sh
./scripts/verify/verify-actuator-contract.sh --runtime
```

`verify-metric-contract.sh` 校验 HTTP 直方图、关键业务指标和禁止 ID 标签策略。`/actuator/health/liveness` 与 `/readiness` 是编排探针；`info` 和 `prometheus` 只允许本机访问，或者携带匹配 `MANAGEMENT_TOKEN` 的 `X-LeetModel-Management-Token`。


### 启动与验证观测栈

本地观测栈会启动 SkyWalking/BanyanDB、Prometheus、Alertmanager 与 Grafana。启动脚本在 Git 忽略目录生成或复用管理 Token。随后由 `start-mvp.sh` 启动的 15 个服务会自动使用同一 Token，Prometheus 直接抓取各服务而不经过 Gateway：

```bash
./scripts/infra/start-observability.sh
./scripts/dev/start-mvp.sh

# 快速配置门禁
./scripts/verify/verify-observability-stack.sh --static

# 包含临时服务与 Prometheus 中断的完整运行验收
./scripts/verify/verify-observability-stack.sh

# 告警规则与通知闭环
./scripts/verify/verify-alerting-contract.sh
./scripts/drill/drill-alerting.sh
```

Prometheus、Alertmanager、Grafana、OAP 和 Horizon 分别只在本机 `19090`、`19093`、`13000`、`11234/11800/12800/17128` 与 `18080` 提供端口。

Grafana 自动加载系统总览、MVP 主链、AI 资源与稳定性、异步任务、可靠消息和遥测管道六类看板。22 条规则覆盖服务与遥测空洞、Outbox、MQ、DLQ、AI 队列、UNKNOWN 和领域租约。版本化 Runbook 位于 [可观测 Runbook](observability/README.md)。

告警闭环演练只使用临时端口 `19094`，指标栈验收只使用临时端口 `18094`，均不停止标准端口业务服务。


### 启动 RocketMQ

本地可靠消息环境固定使用 Broker `5.5.0` 与 RocketMQ Spring `2.3.3`。自动创建 Topic 和消费组已关闭，必须通过版本化脚本显式创建五个业务 NORMAL Topic、一个操作审计专用 NORMAL Topic 及其六个消费组：

```bash
docker compose -f compose.yaml up -d --wait rocketmq-namesrv rocketmq-broker
./scripts/infra/init-rocketmq.sh
./scripts/verify/verify-rocketmq.sh
./scripts/verify/verify-audit-contract.sh
./scripts/drill/drill-messaging-failures.sh status
```

需要同时验证 Broker 重启与数据卷恢复时：

```bash
ROCKETMQ_VERIFY_RESTART=true ./scripts/verify/verify-rocketmq.sh
```

真实 Java 发送、重复消费、Inbox 幂等和客户端重试测试：

```bash
cd LeetModel-backend
RUN_ROCKETMQ_INTEGRATION=true mvn -pl common/common-messaging test
```

操作审计的严格信封、ACL 2.0 正负路径、固定重试与 DLQ 使用：

```bash
./scripts/verify/verify-audit-rocketmq.sh
```

该脚本在一次性非标准端口 Broker 集中验证，不修改常驻开发 Broker。`scripts/drill/drill-messaging-failures.sh` 提供 Broker 网络中断、MySQL 短故障和指定消息服务进程终止等单步演练命令。暂停故障后必须显式执行对应的 resume 命令。

可选 Dashboard：

```bash
docker compose -f compose.yaml --profile tools up -d rocketmq-dashboard
```

访问地址为 `http://127.0.0.1:8180`。

NameServer、Broker 和 Dashboard 均只绑定本机端口。本地 Broker 数据保存在 `rocketmq-broker-store` 命名卷。不要使用 `docker compose down -v` 或删除 RocketMQ 命名卷，除非明确要清空本地消息与消费位点。

生产环境必须另行部署多副本集群，采用 `docker/rocketmq/broker-acl.conf.example` 的 ACL 2.0 开关，并从 Secret Manager 注入管理与应用凭据。审计生产账号只授予精确 Topic `Pub`，归档账号只授予精确 Topic 与 Group `Sub`。


### 启动后端

```bash
./scripts/dev/start-mvp.sh
```

脚本会完成以下工作：

1. 启动 Elasticsearch、RocketMQ 等项目基础设施和关联资源。
2. 执行 RocketMQ Topic 与消费组初始化。
3. 构建后端 Reactor。
4. 启动 15 个业务服务。

网关地址为：

```text
http://localhost:8080
```

已完成构建时可使用：

```bash
./scripts/dev/start-mvp.sh --skip-build
```

AI 对话与评审要求 `ai-gateway-service` 的运行环境提供 new-api Relay Token。不要将 Token 写入仓库文件；未配置时 AI 网关无法启动。

需要本地 Trace/APM 时，先启动观测栈，再启用 SkyWalking Agent 启动业务服务：

```bash
./scripts/infra/start-observability.sh
LEETMODEL_SKYWALKING_ENABLED=true ./scripts/dev/start-mvp.sh
```

组件版本、端口、资源、登录和兼容限制见 [可观测性组件基线](../project/02-架构设计/可观测性组件基线.md)。


### 启动前端

新建终端窗口：

```bash
./scripts/dev/start-frontend.sh
```

默认访问地址为 `http://localhost:5173`。开发服务器会把 `/api` 请求代理到后端网关。

演示管理员账号为 `admin`，密码为 `123456`。普通使用者可直接注册。


### 停止服务

```bash
./scripts/dev/stop-mvp.sh
```

该脚本只停止业务服务，保留 MySQL、Redis、MinIO、Nacos、Elasticsearch 和 RocketMQ 等 Docker 基础设施。

如需停止 Docker 基础设施：

```bash
docker compose -f compose.yaml down
```

该命令默认保留命名卷。


### 完整验证

```bash
# 后端全量测试
./scripts/test/backend.sh

# 最终静态门禁
./scripts/verify/verify-final-gate.sh

# 可观测基线真实运行验收
./scripts/verify/verify-observability-baseline.sh

# Metrics、Grafana 与 Alertmanager 验收
./scripts/verify/verify-observability-stack.sh --static
./scripts/verify/verify-observability-stack.sh
./scripts/verify/verify-alerting-contract.sh
./scripts/drill/drill-alerting.sh

# 前端生产构建
./scripts/test/frontend.sh
```

运行或评审 AI 流程前，请先阅读 [AI 评审数据说明](../../data/README.md) 中的测试数据对应关系和文件限制。
