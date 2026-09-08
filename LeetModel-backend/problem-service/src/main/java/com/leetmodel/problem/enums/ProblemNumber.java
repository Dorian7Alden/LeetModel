package com.leetmodel.problem.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 赛事内题号分类。
 */
public enum ProblemNumber {
    A,
    B,
    C,
    D,
    E,
    F,
    X;

    public static final String PATTERN = "A|B|C|D|E|F|X";

    private static final Set<String> CODES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isSupported(String value) {
        return value != null && CODES.contains(value);
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
