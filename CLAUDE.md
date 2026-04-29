## 参数校验规范

本项目严格执行 `/project:spring-validator` 中定义的参数校验规范：
- 每个接口对应一个独立的 `XxxParamValidator` 类，Controller 只负责调用
- 校验逻辑封装在 `validator/{domain}/` 包下，内部使用 `ParameterValidator` 链式完成
- Request DTO 禁止使用任何 Bean Validation 注解
- 详细规则见 `.claude/skills/spring-validator/SKILL.md`



## 同步开发信息

开发前，应先逐层浏览项目的目录结构，而不是一次性遍历所有目录。逐层了解后，根据任务目标，选择性地阅读必要的内容，再着手完成任务。


- **关键目录速查**：
  - 开发规范：`docs/instructions/`
  - 功能设计：`docs/system-design`


## 项目的进度

- 开发用户管理

