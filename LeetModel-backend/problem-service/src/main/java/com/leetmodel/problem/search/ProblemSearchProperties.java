package com.leetmodel.problem.search;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 题库全文检索配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "problem.search")
public class ProblemSearchProperties {

    /** 是否启用 Elasticsearch 全文检索；关闭时全部走数据库降级查询。 */
    private boolean enabled = true;

    /** Elasticsearch 地址。 */
    private String elasticsearchUri = "http://127.0.0.1:9200";

    /** 题库索引物理名称。 */
    private String indexName = "leetmodel-problem-v1";

    /** 单次检索最大返回条数，避免深度分页。 */
    private int maxResultWindow = 1000;
}
