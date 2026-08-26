# 业务服务启动报 MinioClient 类缺失

## 现象

submission-service 或 ai-review-service 启动失败。最外层异常可能指向某个条件装配方法，完整异常链最终包含：

```text
NoClassDefFoundError: io/minio/MinioClient
ClassNotFoundException: io.minio.MinioClient
```

## 根因

common-core 提供 MinioConfig 和 MinioStorageServiceImpl，但 MinIO SDK 没有自动成为业务服务的运行时依赖。

业务服务启动时会扫描 common-core 的配置类。Spring 反射解析 MinioConfig 方法签名时需要加载 MinioClient。业务服务的运行时 classpath 中不存在该类，因此应用在容器初始化阶段失败。

只依赖 StorageService 接口的业务代码仍可通过编译，使用 Mock 的单元测试也不会加载 MinIO 实现，所以该问题通常在真实启动时才暴露。

## 修复

所有启用 MinIO 存储实现的业务服务必须显式声明 MinIO SDK：

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
</dependency>
```

多模块工程新增公共 DTO 或 Feign 契约后，直接在单个子模块执行 spring-boot:run 可能继续加载本地 Maven 仓库中的旧公共模块快照。此时先从聚合工程安装相关模块：

```bash
mvn -pl submission-service,review-service -am install -DskipTests
```

## 验证

先确认两个服务的依赖树都包含 MinIO SDK：

```bash
mvn -pl submission-service,review-service dependency:tree -Dincludes=io.minio:minio
```

以上命令中的 `review-service` 是 ai-review-service 当前真实 Maven artifactId，不是领域服务的统一文档名称。

再分别启动服务。启动日志应出现 Tomcat started 和 Started Application，不再出现 MinioClient 类缺失异常。
