package com.leetmodel.common.ai.model;

/**
 * 调用方声明的优先级分类。网关仍需按 feature/operation 校验，不能信任调用方任意提权。
 */
public enum AiCallPriority {
    P0,
    P1,
    P2,
    P3,
    P4
}
