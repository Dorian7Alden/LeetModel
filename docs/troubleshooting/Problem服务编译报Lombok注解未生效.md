# Problem 服务编译报 Lombok 注解未生效

---

## 报错现象

```text
[ERROR] cannot find symbol
  symbol:   method getTitle()
  location: variable p of type Problem
[ERROR] cannot find symbol
  symbol:   method builder()
  location: class ProblemVO
```

执行 `mvn clean compile -pl problem-service -am` 后，所有 Lombok 生成的 getter、setter、builder 方法全部报 `cannot find symbol`。

---

## 根因分析

父 POM 在 `<pluginManagement>` 中为 `maven-compiler-plugin` 配置了 `annotationProcessorPaths`（Lombok → MapStruct → Configuration Processor），但 `pluginManagement` 仅提供插件配置的默认值，**不会自动应用到子模块**。

子模块 POM 中未显式声明 `maven-compiler-plugin`，Maven 使用自己的默认编译器插件，不会加载父 POM 中配置的注解处理器路径。Lombok 注解处理器未被激活，导致 `@Data`、`@Builder` 等注解不会生成对应代码。

---

## 修复方案

在子模块 POM 的 `<build><plugins>` 中显式声明 `maven-compiler-plugin`，不配置任何参数——配置自动从父 POM 的 `pluginManagement` 继承：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
        </plugin>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

---

## 知识点

**pluginManagement vs plugins 的语义区别**：`pluginManagement` 定义插件的版本和配置基线，子模块必须显式引用才会生效。`plugins` 直接应用到自身模块。父 POM 用 `pluginManagement` 统一管理插件版本是正确的，但每个需要该插件的子模块都必须声明它。
