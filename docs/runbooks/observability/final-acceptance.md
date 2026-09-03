# 最终验收门禁

最终门禁由 `LeetModel-backend/scripts/verify-final-gate.sh` 提供，覆盖 Actuator、结构化日志、Metrics、审计契约、故障保护、演练计划和全部 Shell 语法。脚本只读仓库与本地隔离资源，不 push、不改写历史、不停止标准端口服务。

卡片收敛时按以下顺序留存证据：

1. `mvn -f LeetModel-backend/pom.xml test`：后端 reactor 全量测试。
2. `npm run build`（工作目录 `LeetModel-frontend`）：管理端页面和路由构建。
3. `./LeetModel-backend/scripts/verify-final-gate.sh`：静态契约与演练矩阵门禁。
4. 在具备隔离依赖的环境中按 [全链路故障演练矩阵](fault-drills.md) 运行 `drill-fault-protection.sh --live`，确认 OAP、Prometheus、Broker、audit-service、租约和 AI UNKNOWN 的信号与恢复判据。

闭环判定必须能从核心请求的指标定位到 Trace/日志，再回到业务事实和操作审计，经过管理端受控恢复后看到指标、归档和状态恢复；任何采集空洞均标记 `unavailable`，不以零值、空集合或 HTTP 成功掩盖。
