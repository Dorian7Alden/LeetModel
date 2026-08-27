# LeetModel TODO

> 本文件只管理当前一轮任务。这里不是完整功能清单、长期路线图或 idea 池。

## 当前任务

### 任务名称

new-api 第三方 AI 网关本地部署与架构问题沉淀

### 背景

LeetModel 当前自行实现了供应商协议适配、模型目录和部分调用治理能力。调查发现，开源项目 new-api 已提供 OpenAI 兼容调用、供应商渠道管理、模型映射、故障重试、额度计量和调用日志等成熟能力。继续在 LeetModel 内重复建设全部通用 AI 网关能力，会增加接口适配、运维和测试成本。

本轮先验证并建立 new-api 作为独立第三方基础设施的本地运行方式，为后续调整 AI 调用架构提供可运行前提。本轮不修改 `ai-gateway-service` Java 实现，不切换现有 AI 调用链，也不配置真实供应商密钥。

### 目标

- 将问题发现、能力边界和阶段性决策写入架构文档。
- 使用固定版本 Docker 镜像把 new-api 纳入 LeetModel 本地基础设施。
- 使用独立持久化数据卷启动 new-api，并通过健康接口验证服务可访问。
- 写清首次初始化、渠道和 Token 配置、常用接口验证及停止方式。

### 范围

- 修改 `LeetModel-backend/docker-compose.yml`，增加独立的 `new-api` 服务。
- 更新项目运行说明和 AI 网关相关设计文档。
- 真实拉取、启动并检查 new-api 容器。
- 保留 `ai-gateway-service` 当前供应商直连实现，不修改 Java 代码、配置或调用契约。
- 不把 `/home/dorian/repo/new-api` 源码复制或合并进 LeetModel。

### 实施清单

- [x] 记录重复建设通用 AI 网关的问题、new-api 能力和集成边界。
- [x] 在 Docker Compose 中增加固定版本 new-api 服务、健康检查和持久卷。
- [x] 补齐本地启动、首次初始化、接口检查、停止和数据持久化说明。
- [x] 启动容器并验证 `/api/status` 健康接口；服务报告 `v1.0.0-rc.26`，容器状态为 `healthy`。
- [x] 验证 `new-api-data` 中生成 SQLite 数据库，容器重启后仍健康；未携带 Token 访问 `/v1/models` 返回 401。
- [x] 核对本轮未修改 `ai-gateway-service` Java 实现。

### 完成标准

- `docker compose config` 校验通过。
- `docker compose up -d --wait new-api` 可以完成启动。
- `http://localhost:3000/api/status` 返回成功状态。
- 文档明确 new-api 是独立第三方服务，当前尚未接入 LeetModel AI 调用链。
- Git diff 中没有 `ai-gateway-service` Java 代码改动。
