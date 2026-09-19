package com.leetmodel.problem.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.problem.dto.ProblemPageQuery;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProblemSearchServiceTest {
    private RestClient client;
    private ProblemSearchService service;

    @BeforeEach
    void setUp() {
        client = mock(RestClient.class);
        ProblemSearchProperties properties = new ProblemSearchProperties();
        properties.setIndexName("leetmodel-problem-v1");
        properties.setMaxResultWindow(100);
        service = new ProblemSearchService(client, properties, new ObjectMapper());
    }

    @Test
    @DisplayName("检索成功时按相关度返回题目标识与总数")
    void returnsHitsWhenSearchSucceeds() throws Exception {
        Response response = mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity("""
                {"hits":{"total":{"value":2},"hits":[{"_id":"11"},{"_id":"12"}]}}
                """, ContentType.APPLICATION_JSON));
        when(client.performRequest(any(Request.class))).thenReturn(response);

        ProblemSearchPage page = service.search("线性规划", query(1, 10));

        assertThat(page).isNotNull();
        assertThat(page.problemIds()).containsExactly(11L, 12L);
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("检索不可用时返回 null 以便降级")
    void returnsNullWhenSearchUnavailable() throws Exception {
        when(client.performRequest(any(Request.class))).thenThrow(new IOException("es down"));

        assertThat(service.search("线性规划", query(1, 10))).isNull();
    }

    @Test
    @DisplayName("超出检索窗口时直接降级")
    void returnsNullWhenBeyondResultWindow() {
        assertThat(service.search("线性规划", query(20, 10))).isNull();
    }

    private ProblemPageQuery query(int page, int pageSize) {
        ProblemPageQuery query = new ProblemPageQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setStatus(1);
        query.setTagIds(List.of(6001L));
        return query;
    }
}
