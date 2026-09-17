## scripts

> 仓库级启动、基础设施、测试、验证和故障演练脚本入口。所有脚本均从仓库根目录执行，路径由 `lib/paths.sh` 统一计算。

Docker Compose 入口位于仓库根目录：`compose.yaml` 管理后端基础设施，`compose.observability.yaml` 管理可选观察性栈，挂载配置统一放在根 `docker/`。


### 目录职责

| 目录 | 职责 |
|------|------|
| `dev/` | 启动和停止本地业务服务与前端开发服务器 |
| `infra/` | 初始化基础设施、RocketMQ、审计数据库和 SkyWalking Agent |
| `test/` | 执行后端测试、前端构建和全量本地验证 |
| `verify/` | 验证契约、指标、日志、审计、可观测性和最终门禁 |
| `drill/` | 执行隔离的故障演练和告警闭环测试 |
| `query/` | 只读查询隔离演练产生的关联事实 |
| `lib/` | 共享路径和公共 Shell 配置 |


### 常用命令

```bash
# 启动后端
./scripts/dev/start-mvp.sh

# 启动前端
./scripts/dev/start-frontend.sh

# 停止后端
./scripts/dev/stop-mvp.sh

# 后端全量测试
./scripts/test/backend.sh

# 前端生产构建
./scripts/test/frontend.sh

# 后端、前端和最终静态门禁
./scripts/test/full.sh

# 最终静态门禁
./scripts/verify/verify-final-gate.sh
```


### 数据脚本边界

- `LeetModel-mock/scripts/` 是独立 Mock 数据生成项目，不迁移到本目录。
- `data/scripts/import-problems.sh` 只负责导入固定 AI 评审测试题面。
- `rag_kb/scripts/` 只负责知识源采集和整理，不参与平台启动与运行验收。
