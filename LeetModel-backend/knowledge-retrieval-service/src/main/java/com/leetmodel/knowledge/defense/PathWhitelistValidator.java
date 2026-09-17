package com.leetmodel.knowledge.defense;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 防线 2: 服务端绝对白名单校验器。
 * 比对内存中合法知识库相对路径白名单，严禁使用大模型臆造、拼错或越权路径。
 */
@Slf4j
@Component
public class PathWhitelistValidator {

    public List<String> validate(List<String> rawPaths, Set<String> validPaths) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return List.of();
        }
        Set<String> filtered = new LinkedHashSet<>();

        for (String raw : rawPaths) {
            if (raw == null || raw.isBlank()) continue;
            String normalized = raw.trim().replace('\\', '/').replaceAll("^/+", "");
            if (normalized.contains("..")) {
                log.warn("检测到非法路径穿透尝试，已被防线剔除: {}", raw);
                continue;
            }
            if (validPaths != null && !validPaths.isEmpty()) {
                if (validPaths.contains(normalized)) {
                    filtered.add(normalized);
                } else {
                    log.warn("模型输出路径不在受控白名单内，已被静默剔除: {}", normalized);
                }
            } else {
                filtered.add(normalized);
            }
        }
        return new ArrayList<>(filtered);
    }
}
