# file-service 资产列表返回“系统内部错误”

> 影响范围：file-service、管理端存储资产列表

## 报错现象

管理端打开“存储资产”或点击“刷新”时，列表为空并提示 `系统内部错误`，接口返回业务码 `50001`。

## 根因分析

`FileAssetServiceImpl.toVO` 会根据 MIME 类型和文件扩展名计算预览类型。历史盘点或手动上传记录允许 `content_type` 为空，但压缩包判断直接调用 `StorageContentTypes.ARCHIVE.contains(contentType)`。`Set.of(...).contains(null)` 会抛出 `NullPointerException`，最终被全局异常处理器转换成 `50001`。

## 修复方案

在访问压缩包 MIME 集合前先判断 MIME 类型非空；扩展名判断仍然保留，因此即使客户端未提供 MIME 类型，仍可依据 `.zip`、`.rar` 等扩展名识别压缩包。

修复后需重新编译并重启 file-service，使运行中的 JVM 加载最新 class：

```bash
mvn -f LeetModel-backend/pom.xml -pl file-service -am test -q
```

## 回归验证

- `FileAssetServiceTest.uploadAllowsMissingContentType` 覆盖 MIME 类型为空的上传路径。
- file-service 测试套件通过（8 tests，0 failures，0 errors）。
- 前端 `npm --prefix LeetModel-frontend run build` 通过。
