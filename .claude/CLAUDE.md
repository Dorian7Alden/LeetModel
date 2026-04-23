## 参数校验规范

本项目严格执行 `/project:spring-validator` 中定义的参数校验规范：
- 所有参数校验必须在 Controller 内通过 ParameterValidator 链式完成
- Request DTO 禁止使用任何 Bean Validation 注解
- 详细规则见 `.claude/commands/spring-validator.md`



## 开发前同步规范

- 每次开始开发前，先完整阅读一次 `README.md` 文档
- 阅读相关文档完成后再进入编码与变更，确保规则同步