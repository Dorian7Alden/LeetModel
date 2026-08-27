# YAML 配置键唯一性与 Spring Boot 配置加载

## 核心问题

为什么 Spring Boot 的 YAML 配置文件里写重复键会启动失败？为什么本地脚本校验却测不出来？

## YAML 的键唯一性规则

YAML 本质上是一棵由映射（Map）和序列（List）构成的嵌套树。**同一映射层级内，键必须唯一**——这是 YAML 规范（YAML 1.2）的硬性要求。

但解析器对违反规则的行为分两派：

| 解析器 | 行为 | 代表 |
|--------|------|------|
| 严格模式 | 遇到重复键直接抛异常 | snakeyaml（Spring Boot 默认）、Go yaml.v3 |
| 宽松模式 | 后写的键静默覆盖先写的 | PyYAML、JS 的 js-yaml（默认） |

**面试要点**：Spring Boot 用 snakeyaml，所以重复键直接让应用起不来；而 Python/JS 生态里"覆盖"是常态，两边对同一份 YAML 的容忍度完全不同。排查此类问题用脚本校验时，必须自定义构造器复刻严格行为，`safe_load` 测不出来。

## Spring Boot 配置文件加载顺序

Spring Boot 启动时按固定顺序加载多份配置，**同名键的覆盖规则是"后加载的覆盖先加载的"**：

```
application.yml（公共配置）→ application-{profile}.yml（环境配置，后加载）
```

以本项目为例：

- `application.yml`：服务名、profile、Nacos 配置中心地址等公共配置
- `application-dev.yml`：端口、数据库、Redis 等环境差异配置
- 启动时 `spring.profiles.active: dev` 生效，两份文件合并成一份配置树，**dev 覆盖公共**的同名键

**关键区分（面试高频）**：

- **跨文件同名键**：合法，是 profile 覆盖机制，故意设计的
- **同文件重复键**：非法，YAML 解析阶段直接报错——因为合并发生在 YAML 解析**之后**（Spring 把每份文件解析成 Map 再按顺序合并），同一份文件内部的键重复在解析阶段就炸了

这也是本项目事故的根因：把"优雅关闭"的 `spring.lifecycle` 拆成独立顶层块，与第一个 `spring:` 块重复——YAML 解析器在"跨文件合并"发生前就拒绝了这个文档。

## 配置加载的关键顺序（Spring Boot 2.4+）

版本 2.4 之后配置处理顺序（从高到低，高优先级覆盖低优先级）：

1. 命令行参数（`--spring.profiles.active=prod` 等）
2. 环境变量
3. `application-{profile}.yml`
4. `application.yml`
5. `spring.config.import` 导入的外部配置（如 Nacos）

**踩坑点**：`spring.config.import` 的 Nacos 配置优先级低于本地 `application.yml`，如果两边定义了同名键，本地会覆盖 Nacos——需要远端强约束时，应把配置下沉到 Nacos 且本地不声明同名键，或使用占位符机制。

## 优雅停机配置的配合关系

本次事故中的两个配置项其实是同一个功能的两个面：

- `server.shutdown: graceful`：开启优雅停机——Tomcat 停止时先等待进行中的请求处理完成，而不是立刻掐断
- `spring.lifecycle.timeout-per-shutdown-phase: 30s`：每个关闭阶段（如 Web 容器、Bean 销毁）等待的**时间上限**，防止某个请求或任务无限期拖住停机

**面试延伸**：优雅停机的意义（无损发布/滚动部署时用户体验）、与 k8s preStop hook 的配合、为什么超时上限必须有（否则优雅变"永远停不下来"）。
