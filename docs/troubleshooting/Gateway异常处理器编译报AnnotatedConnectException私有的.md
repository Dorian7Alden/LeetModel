# Gateway 异常处理器编译报 AnnotatedConnectException 私有

> 日期：2026-07-30

## 现象

在 Gateway 中编写 `JsonExceptionHandler`（实现 `ErrorWebExceptionHandler`）时，尝试捕获 `io.netty.channel.AbstractChannel.AnnotatedConnectException`，编译报错：

```
AnnotatedConnectException has private access in io.netty.channel.AbstractChannel
```

Maven 编译直接失败，无法通过。

## 根因

`AnnotatedConnectException` 是 Netty 内部类 `AbstractChannel` 的**私有内部类**，不对外开放。虽然在运行时可以通过反射访问，但编译期 Java 访问控制检查会拒绝它出现在 `instanceof` 或 `catch` 子句中。

## 修复

不直接引用私有内部类，改用 JDK 标准异常 + 消息内容匹配：

```java
// ❌ 错误写法
} else if (ex instanceof io.netty.channel.AbstractChannel.AnnotatedConnectException) {

// ✅ 正确写法
} else if (ex instanceof java.net.ConnectException
           || (ex.getMessage() != null && ex.getMessage().contains("Connection refused"))) {
```

`java.net.ConnectException` 是 JDK 标准类（公开 API），`"Connection refused"` 字符串匹配覆盖其他连接失败的变体。
