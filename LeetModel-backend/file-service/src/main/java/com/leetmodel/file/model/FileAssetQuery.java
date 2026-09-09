package com.leetmodel.file.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FileAssetQuery {
    @Min(1)
    private int page = 1;
    @Min(1)
    @Max(100)
    private int size = 20;
    @Size(max = 128)
    private String groupPath;
    @Size(max = 100)
    private String keyword;
    @Size(max = 24)
    private String lifecycleStatus;
}
