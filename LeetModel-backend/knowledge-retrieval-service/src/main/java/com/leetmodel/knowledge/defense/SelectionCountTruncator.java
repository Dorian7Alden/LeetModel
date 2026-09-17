package com.leetmodel.knowledge.defense;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 防线 3: 数量硬截断器。
 * 严禁依赖模型的自觉性，服务端强制封顶截取，防止下游主模型输入爆炸。
 */
@Slf4j
@Component
public class SelectionCountTruncator {

    public List<String> truncate(List<String> paths, int maxSelected) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, maxSelected);
        if (paths.size() > limit) {
            log.info("模型选拔数量({})超过服务端硬限制({})，已执行硬截断", paths.size(), limit);
            return paths.subList(0, limit);
        }
        return paths;
    }
}
