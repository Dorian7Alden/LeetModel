# 题库 ES 索引写入报 document_parsing_exception

> 影响范围：problem-service 题库全文检索索引写入与重建

## 报错现象

题库检索索引创建成功，但文档写入全部失败。实时日志只记录“题库索引写入失败，等待重建补偿”，补充响应细节后可以看到 Elasticsearch 返回：

```json
{
  "error": {
    "type": "document_parsing_exception",
    "reason": "failed to parse field [updateTime] of type [date]",
    "caused_by": {
      "reason": "failed to parse date field [2026-09-15 16:26:07] with format [strict_date_optional_time||epoch_millis]"
    }
  },
  "status": 400
}
```

同一时间全量重建接口返回的写入数量仍然是完整的题目总数，看起来“重建成功”，但索引里实际一条文档也没有。

## 根因分析

两个独立缺陷叠加：

1. `updateTime` 在索引映射中按 `date` 默认格式解析，只接受 `strict_date_optional_time` 与 `epoch_millis`。平台统一的 Jackson 配置把 `LocalDateTime` 序列化为 `yyyy-MM-dd HH:mm:ss`，带空格的格式不在默认解析范围内，导致每条文档都被 Elasticsearch 拒绝。
2. 单条写入方法把 `IOException` 全部吞掉只记日志，而全量重建按“循环是否抛异常”累加成功数量，于是失败被计成成功，掩盖了真实失败。

## 修复方案

- 在索引映射中为 `updateTime` 显式声明 `yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis`，与平台时间序列化格式对齐。
- 单条写入改为返回写入结果，全量重建按真实成功数累加。
- 写入失败日志追加响应状态码与响应体，直接暴露 Elasticsearch 的拒绝原因。

修改映射后必须重建索引才能生效：调用 `POST /api/problems/search-index/rebuild`，或删除索引后重启服务触发启动自举。

## 回归验证

- 重建接口返回写入数量与数据库中未删除题目数一致，`GET /leetmodel-problem-v1/_count` 数量相同。
- 关键词检索能命中题面内容，说明文档已真正写入索引。
- problem-service 测试套件通过（72 tests，0 failures，0 errors）。
