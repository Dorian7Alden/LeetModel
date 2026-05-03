---
name: git-commit
description: 当用户要求提交代码、commit、提交变更、保存到 git 时触发。自动分析工作区变更，按前后端/文档/配置拆分，生成规范的中文提交信息并分批提交。在用户提到"提交"、"commit"、"提交代码"、"提交未提交"时优先使用此 skill。
---

# Git 提交 Skill

按照项目提交规范，分析工作区变更并分批提交。

## 提交流程

### 第一步：分析当前状态

并行执行以下命令了解工作区全貌：

```bash
git status
git diff --stat
git diff --cached --stat
git log --oneline -10
```

### 第二步：分组变更文件

根据文件路径将变更分为以下组，分别提交：

| 分组 | 识别规则 | scope |
|------|----------|-------|
| 后端代码 | `LeetModel-backend/src/main/java/` 下的 `.java`、`.xml` | `back` |
| 前端代码 | `LeetModel-vue/src/` 下的 `.vue`、`.js`、`.css`、`.ts` | `front` |
| SQL | `sqls/` 目录下文件 | `sql` |
| 配置 | `pom.xml`、`application*.yml`、`package.json`、`vite.config.*`、`.env` 示例文件 | `config` |
| 文档 | `docs/`、`*.md` 文件 | 省略或模块名 |

新文件（untracked）与对应分组的已修改文件一起提交。

### 第三步：确定 type

根据变更内容选择 type：

| type | 判断依据 |
|------|---------|
| `feat` | 新增接口、页面、功能模块 |
| `fix` | 修复 bug、异常、兼容性问题 |
| `ref` | 重构代码结构、提取公共逻辑、优化实现（不改变功能） |
| `docs` | 文档、规范、说明文件 |
| `chore` | 依赖版本、构建脚本、环境配置 |
| `file` | 文件移动、重命名、删除 |

不确定时优先选 `feat`（新增多）或 `ref`（改动多）。

### 第四步：编写 subject

- 使用中文简述，一行概括这组变更的核心意图
- 聚焦一个变更点，不写流水账（"A 和 B 和 C" 说明拆分不够细）
- 控制在 20 字以内

示例：
- `feat(back): 用户批量更新接口与参数校验`
- `fix(back): 修复登录 token 过期未刷新问题`
- `ref(front): 提取公共表格组件，减少重复代码`
- `feat(front): 个人中心页面与活动日历`
- `docs: 补充 Controller 开发规范`
- `chore(config): 升级 Spring Boot 至 3.2`

### 第五步：展示并确认

向用户展示提交计划，格式如下：

```
计划创建 N 个提交：

1. feat(back): <subject>
   - file1.java
   - file2.java
   ...

2. feat(front): <subject>
   - file1.vue
   - file2.js
   ...

3. docs: <subject>
   - file1.md
```

等待用户确认后再执行。

### 第六步：逐批执行

用户确认后，按顺序逐批提交：

```bash
# 每批：先 add 再 commit
git add <file1> <file2> ...
git commit -m "<type>(<scope>): <subject>"
```

每批提交后报告结果。**不要在 commit message 末尾添加 Co-Authored-By 署名。**

### 第七步：验证

全部提完后确认：

```bash
git status
git log --oneline -5
```

## 强制规则

1. **前后端代码不得混在同一个提交中** — 这是最核心的规则，代码提交必须按前后端拆分。
2. **文档、配置、代码三者独立提交** — 不同类型不混在一起。
3. **提交格式固定为 `type(scope): 中文subject`** — type 和 scope 必须使用约定值。
4. **不要跳过钩子** — 不使用 `--no-verify`、`--no-gpg-sign` 等参数。如果钩子失败，修复问题后重新提交。
5. **不要提交敏感文件** — `.env`、`credentials.*`、含密钥/密码的文件需警告用户。

## 边界情况

- **工作区无变更**：直接告知用户 "工作区干净，无需提交"。
- **只有已暂存（staged）变更**：同样分析分组，按规范提交。
- **只有一种类型的变更**：仍然按规范格式单次提交，无需用户确认直接执行。
- **变更量大无法一笔概括**：说明需要进一步拆分，建议用户先部分暂存。
- **文件路径不在常规分组中**：根据文件内容和性质判断，或询问用户。

## 规范依据

- `CLAUDE.md` — "Git 提交规范" 章节
- `docs/instructions/05-git-commit-regulation.md` — 完整规范文档
