package com.senior.leetmodelbackend.pojo.entity;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records;
    private long total;

    private PageResult(List<T> records, long total) {
        this.records = records;
        this.total = total;
    }

    public static <T> PageResult<T> of(List<T> records, long total) {
        return new PageResult<>(records, total);
    }
}
