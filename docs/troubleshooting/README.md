# troubleshooting

> 开发过程中遇到的具体报错与解决方案。每份文档包含：报错现象 → 根因分析 → 修复方案。用于防止重复踩坑，面试中可作为"遇到的挑战"素材。

---

## 文档索引

| 文档 | 报错现象 | 根因 |
|------|---------|------|
| [JDBC连接MySQL报utf8mb4编码不支持.md](JDBC连接MySQL报utf8mb4编码不支持.md) | Flyway 连接 MySQL 报 "Unsupported character encoding 'utf8mb4'" | JDBC 驱动不认识 MySQL 存储引擎的编码名 |
| [Feign调用缺少LoadBalancer依赖.md](Feign调用缺少LoadBalancer依赖.md) | UserFeignClient Bean 创建失败，提示缺少 loadbalancer | Feign 按服务名调用需要 LoadBalancer 解析地址 |
| [SaToken登录报JWT秘钥未配置.md](SaToken登录报JWT秘钥未配置.md) | 登录接口报 "请配置jwt秘钥" | SaTokenConfig 遗漏 setJwtSecretKey() |
| [Problem服务编译报Lombok注解未生效.md](Problem服务编译报Lombok注解未生效.md) | mvn compile 报所有 Lombok getter/setter/builder 找不到 | 父 POM pluginManagement 中的编译插件未被子模块继承 |
| [Problem服务启动报UserFeignClient Bean缺失.md](Problem服务启动报UserFeignClient Bean缺失.md) | 启动报 StpInterfaceImpl 需要 UserFeignClient Bean | common-security 依赖不需要被非鉴权服务引入 |
| [Gateway异常处理器编译报AnnotatedConnectException私有的.md](Gateway异常处理器编译报AnnotatedConnectException私有的.md) | JsonExceptionHandler 编译报 AnnotatedConnectException 私有 | Netty 私有内部类不能出现在 catch/instanceof 中 |
| [微服务启动报YAML配置重复键.md](微服务启动报YAML配置重复键.md) | 启动报 DuplicateKeyException: found duplicate key spring | 6 个配置文件把 spring 配置拆成重复的顶层块 |
| [admin服务启动报MybatisPlusConfig条件装配失败.md](admin服务启动报MybatisPlusConfig条件装配失败.md) | admin 排除 mybatis 后报 Failed to introspect MybatisPlusConfig | 公共模块配置类缺 @ConditionalOnClass 保护 + 未排除 DataSource |
| [team服务启动报Unknown database.md](team服务启动报Unknown database.md) | Flyway 连接报 Unknown database 'leetmodel_team' (1049) | 库名违反 lm_ 规范 + URL 缺 createDatabaseIfNotExist；修复后暴露启动类缺 @EnableFeignClients |
| [bash环境下Windows反斜杠路径写出字面量文件名.md](bash环境下Windows反斜杠路径写出字面量文件名.md) | 批量写知识库文件时根目录多出 C: 字面量目录，README 互相覆盖 | bash 不解析反斜杠路径分隔符，整段 Windows 路径退化为单个字面量文件名 |
| [Knife4j网关聚合文档加载失败.md](Knife4j网关聚合文档加载失败.md) | /doc.html 能打开，但分组文档加载失败，请求 /api/**/v3/api-docs 返回 40100 或 404/500 | 网关路由未剥离前缀，下游服务未在带前缀路径暴露 OpenAPI 文档，且网关与 Security 均未放行该路径 |
