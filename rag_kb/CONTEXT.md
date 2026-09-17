# RAG 知识库上下文

## 定位

`rag_kb/` 是 LeetModel 当前知识内容源，不是应用代码目录。知识内容的事实源是受 Git 管理的 Markdown；Elasticsearch 索引是可重建的派生数据。

历史客服在线 RAG V1 归 `ai-assistant-service`，并继续拥有当前索引构建工具。S12 新增的 `knowledge-retrieval-service` 实现 `VECTOR_RAG_V1` 及实验性的 `AI_DIRECTORY_V1`、`HYBRID_RETRIEVAL_V1`；正式论文建议只启用向量分支。目录选文只读取受控清单并由服务端校验路径。`.kb/` 继续定义原子笔记、目录组织和人工维护规则，不进入模型上下文。


## 读取顺序

1. 阅读本文件确认运行索引边界。
2. 维护知识内容时阅读 [知识库规范总纲](.kb/README.md)，再按其索引加载相关规范。
3. 只执行人工目录导航时阅读 [AI 导航检索工作流](.kb/03-检索工作流.md)。
4. 设计或实现在线检索时阅读 [RAG 知识库架构](../docs/project/02-架构设计/RAG知识库.md)、[知识检索服务](../docs/project/03-微服务设计/knowledge-retrieval-service/README.md) 和对应调用方说明，不把人工导航规则当作可自由访问文件的授权。


## RAG V1 索引边界

只纳入 `数学建模/` 下整理后的内容 Markdown，并排除所有层级的 `README.md`。

明确排除：

- `.kb/`、`.claude/` 和本文件。
- `data/` 原始抓取内容。
- `数模评审参考资料/` PDF 原始材料。
- `scripts/`、`.git/` 和未声明的新顶层目录。

新增目录不会自动进入索引。索引任务必须先生成确定性文件清单，再加载正文。


## 维护规则

- 知识内容仍遵守 `.kb/` 中的原子笔记、frontmatter、目录和 README 同步规则。
- 修改内容时同步更新摘要和所在目录 README。
- 不在知识文件中保存密钥、Token、用户对话、论文提交或其他敏感业务数据。
- 不用索引状态覆盖 Markdown 内容；索引失败时修复派生索引或源内容问题。
- 不执行大规模重分类、批量重命名或删除，除非当前任务明确要求并完成影响核验。


## 版本与降级

在线索引使用独立 `ragIndexVersion`，并记录内容、Embedding 模型和切分策略版本。检索失败时历史 assistant 降级为无 RAG 回答；`GROUNDED_SUGGESTION_V2` 则明确失败，不允许无参考资料生成。知识目录本身不承担在线可用性保证。


## Agent 入口

本文件是 `rag_kb/` 唯一的工具无关 Agent 入口。旧工具专属说明已经迁移并删除；所有 Agent 都以本文件和 `.kb/` 规范为准。
