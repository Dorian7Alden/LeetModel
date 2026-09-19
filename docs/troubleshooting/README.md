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
| [Mockito匹配MyBatisPlus重载方法时编译歧义.md](Mockito匹配MyBatisPlus重载方法时编译歧义.md) | 测试编译提示 insert 或 updateById 方法匹配不明确 | 无类型 any 无法区分 MyBatis-Plus 的单实体与集合重载 |
| [SpringSecurity与SaToken重复认证导致空403.md](SpringSecurity与SaToken重复认证导致空403.md) | 登录成功后业务接口仍返回空响应体 403 | Gateway 与业务服务使用两套未打通的认证状态 |
| [业务服务启动报MinioClient类缺失.md](业务服务启动报MinioClient类缺失.md) | submission-service 或 ai-review-service 启动报 MinioClient 类缺失 | 启用公共 MinIO 实现的业务服务没有显式声明 MinIO SDK |
| [admin服务看板返回系统内部错误.md](admin服务看板返回系统内部错误.md) | 管理端首页概览报“系统内部错误”，指标显示暂不可用 | admin 排除 MyBatis-Plus 但 `PageResult` 仍引用 `IPage` 导致链接失败 + 前端看板漏掉 `/stats` 端点 |
| [user服务启动报Flyway迁移校验和不一致.md](user服务启动报Flyway迁移校验和不一致.md) | user-service 启动报 migration version 6 checksum mismatch | 演示数据生成器每次产生新的 BCrypt 随机盐，重写了已应用的 V6 迁移 |
| [admin服务查询审计报403导致中央审计暂不可用.md](admin服务查询审计报403导致中央审计暂不可用.md) | 管理端审计板块提示“中央审计暂不可用”，请求报 50001 / FeignException$Forbidden | Nacos 局域网 IP 导致 loopback 判定失败 + 本地缺失 AUDIT_INTERNAL_TOKEN 默认值 |
| [review服务启动报Flyway迁移V9超长失败.md](review服务启动报Flyway迁移V9超长失败.md) | ai-review-service 启动报 Flyway 异常与 sqlSessionTemplate 依赖失败 | V9 脚本向 review_version 插入 23 位版本契约值，超出 VARCHAR(20) 限制导致迁移失败并残留阻断记录 |
| [三级缓存与HTTP协商缓存导致数据库变更后前端展示未更新.md](三级缓存与HTTP协商缓存导致数据库变更后前端展示未更新.md) | 数据库插入多题目标签后，curl 能返回新数据，但浏览器页面仍只展示单标签 | 客户端携带旧 ETag 触发 304 命中浏览器磁盘缓存 + 直接改库未触发多级缓存代际推进与失效通知 |
| [file-service资产列表返回系统内部错误.md](file-service资产列表返回系统内部错误.md) | 管理端存储资产列表提示“系统内部错误”，接口返回 50001 | 历史资产 MIME 类型为空时，压缩包 MIME 集合的 contains(null) 抛出 NullPointerException |
| [真实V3评审与建议链路多阶段失败.md](真实V3评审与建议链路多阶段失败.md) | V3 配置不匹配、Outbox 永久 PENDING、消费者 ClassCastException、30 秒异步超时和建议类别拒绝连续出现 | V3 误用旧配置、自动配置条件顺序、消息转换边界、Servlet 与业务超时窗口不一致及同义枚举未规范化 |
| [官方优秀论文解析缺失导致异常低分.md](官方优秀论文解析缺失导致异常低分.md) | 扫描型官方优秀论文被大量判定内容缺失并异常低分 | 输出截断、布尔载荷、仲裁回退、页覆盖与中英文锚点多层缺陷叠加 |
| [题库ES索引写入报document_parsing_exception.md](题库ES索引写入报document_parsing_exception.md) | 题库索引创建成功但文档全部写入失败，重建却显示成功数量 | 索引 date 映射不接受平台时间格式 + 单条写入吞掉 IOException 导致失败被计成成功 |
| [题库检索索引启动自举未触发.md](题库检索索引启动自举未触发.md) | 索引删除后重启服务不触发索引自举，管理端重建接口却正常 | 低层 REST 客户端对 HEAD 请求固定忽略 404，存在性判断恒为“已存在” |
| [GitHub引用Gitee图床图片无法显示.md](GitHub引用Gitee图床图片无法显示.md) | README 图片直连可访问，推送到 GitHub 后显示为裂图 | GitHub 代理抓取外部图床超时（Gitee `/raw/` 302 到带签名限时地址，海外代理返回 504） |
| [Token黑名单查询被Lettuce默认超时拖住.md](Token黑名单查询被Lettuce默认超时拖住.md) | Redis 停机后携带有效 Token 的请求挂起 15 秒以上，fail-open 降级不生效 | Lettuce 默认 60 秒命令超时 + 响应式链路无错误信号，必须显式配置超时 |
