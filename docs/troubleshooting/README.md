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
| [Problem服务启动报UserFeignClient Bean缺失.md](Problem服务启动报UserFeignClient%20Bean缺失.md) | 启动报 StpInterfaceImpl 需要 UserFeignClient Bean | common-security 依赖不需要被非鉴权服务引入 |
