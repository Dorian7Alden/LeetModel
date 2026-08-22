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
| `LeetModel-frontend/` | 旧版前端，Vue 3。当前阶段不维护，前后端暂不对齐 |
| `docs/` | 项目全部文档，按 project / troubleshooting / learning / concepts / standards 五类组织 |
| `knowledge-base/` | 论文评审 RAG 知识库，按标签目录组织原子笔记 |
| `legacy/` | 历史内容隔离区，含旧单体后端、旧 SQL 与旧 AI 协作配置，后续统一处理 |
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
