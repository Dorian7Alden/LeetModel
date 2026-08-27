# rag_kb — RAG 知识库

> 本目录是 LeetModel 当前知识内容源。Agent 进入本目录先阅读 `CONTEXT.md`；内容维护规则从 `.kb/README.md` 进入。

正式 RAG V1 使用 Embedding 与 Elasticsearch 向量检索。`.kb/` 中的纯 AI 目录导航是人工检索和未来 RAG V2 的设计基础，不代表 V1 在线实现。

## 子文件夹

| 子文件夹 | 说明 |
|---------|------|
| 数学建模 | RAG V1 唯一纳入范围，索引整理后的内容 Markdown并排除各级 README |
| data | 原始抓取 Markdown，不进入 V1 索引 |
| 数模评审参考资料 | PDF 原始材料，不进入 V1 索引 |
| scripts | 知识整理脚本，不进入 V1 索引 |

## 文档

| 文档 | 说明 |
|------|------|
| CONTEXT.md | Agent 行为、索引边界和维护入口 |
