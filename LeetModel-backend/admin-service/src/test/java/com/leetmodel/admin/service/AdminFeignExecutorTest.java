package com.leetmodel.admin.service;

import com.leetmodel.common.core.result.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminFeignExecutorTest {
    private final AdminFeignExecutor executor = new AdminFeignExecutor();

    @Test
    void shouldPreserveDownstreamBusinessResponse() {
        Result<String> response = executor.forward("质量评价服务",
                () -> Result.fail(41101, "评价数据集不存在"));

        assertThat(response.getCode()).isEqualTo(41101);
        assertThat(response.getMessage()).isEqualTo("评价数据集不存在");
    }

    @Test
    void shouldSanitizeTransportExceptionAndNullResponse() {
        Result<String> exception = executor.forward("提交服务",
                () -> { throw new IllegalStateException("jdbc:mysql://root:secret@localhost/private"); });
        Result<String> nullResponse = executor.forward("队伍服务", () -> null);

        assertThat(exception.getCode()).isEqualTo(51001);
        assertThat(exception.getMessage()).isEqualTo("提交服务暂不可用");
        assertThat(nullResponse.getCode()).isEqualTo(51001);
        assertThat(nullResponse.getMessage()).isEqualTo("队伍服务暂不可用");
    }
}
