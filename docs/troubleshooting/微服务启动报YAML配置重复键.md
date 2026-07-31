# 微服务启动报 YAML 配置重复键

## 现象

任意微服务启动时在配置解析阶段立即失败：

```
ERROR org.springframework.boot.SpringApplication -- Application run failed
org.yaml.snakeyaml.constructor.DuplicateKeyException: while constructing a mapping
 in 'reader', line 3, column 1:
    spring:
    ^
found duplicate key spring
 in 'reader', line 20, column 1:
    spring:
    ^
```

## 根因

YAML 规范要求**同一文档内顶层键唯一**。snakeyaml（Spring Boot 默认 YAML 解析器）遇到重复键直接抛 `DuplicateKeyException`；而 PyYAML 等宽松解析器会静默覆盖，所以用脚本校验时发现不了问题。

本项目 6 个配置文件的重复模式完全一致：把"优雅关闭"的 `spring.lifecycle.timeout-per-shutdown-phase` 拆成了**独立的顶层 `spring:` 块**，与第一个 `spring:` 块（application/profiles/config/cloud）重复：

```yaml
spring:                 # 第一个 spring 块
  application: ...
  cloud: ...

server:
  shutdown: graceful

spring:                 # ← 重复键：与第一个 spring 块冲突
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

涉及文件（5 个服务的 application.yml + gateway 的 application-dev.yml，共 6 个）：

- `LeetModel-user / -admin / -team / -gateway / -problem` 的 `application.yml`：`lifecycle` 独立成块
- `LeetModel-gateway/application-dev.yml`：`spring.data.redis` 独立成块

## 修复

把第二个 `spring:` 块的内容**合并进第一个 `spring:` 块**，顶层键去重，配置语义不变：

```yaml
spring:
  application:
    name: leetmodel-xxx
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
  # 优雅关闭：单阶段等待时间上限（配合下方 server.shutdown: graceful）
  lifecycle:
    timeout-per-shutdown-phase: 30s

# ==================== 优雅关闭 ====================
server:
  shutdown: graceful
```

## 验证方法

普通 `yaml.safe_load` 发现不了重复键（静默覆盖），需自定义构造器复刻 snakeyaml 的严格行为：

```python
class StrictLoader(yaml.SafeLoader):
    def construct_mapping(self, node, deep=False):
        mapping = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            if key in mapping:
                raise yaml.constructor.ConstructorError(
                    None, None, f"found duplicate key {key!r}", key_node.start_mark)
            mapping[key] = self.construct_object(value_node, deep=deep)
        return mapping

StrictLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, StrictLoader.construct_mapping)
```

逐个解析所有 `src/main/resources/*.yml` 全部通过后，实际启动一个服务确认越过配置解析阶段。

## 踩坑记录

**Maven 多模块下 `spring-boot:run -pl <module>` 不会重新构建公共模块**：修改 `LeetModel-common/*` 源码后，直接跑 `-pl LeetModel-admin` 用的仍是本地仓库里的旧 jar。必须先 `mvn install -pl <module> -am`（-am 连带构建依赖模块），修改才生效。判断依据：改动源码后报错栈完全不变，检查本地仓库 jar 时间戳或直接重新 install。

## 面试考点

- **YAML 键唯一性**：YAML 本质是 Map 的嵌套，同一层级键必须唯一；解析器行为分两派——严格（snakeyaml/Go yaml.v3 抛错）与宽松（PyYAML/JS 覆盖），Spring Boot 用 snakeyaml 所以抛错
- **Spring Boot 配置加载顺序**：`application.yml`（公共）→ `application-{profile}.yml`（环境覆盖），同名键后者覆盖前者——**跨文件可以覆盖，同文件重复则是错误**
- **优雅停机**：`server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase` 配合使用，后者是单阶段等待时间上限，防止无限等待未完成任务
