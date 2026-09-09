package com.leetmodel.file.storage;

import java.time.LocalDateTime;

public record PhysicalObject(String objectKey, long size, LocalDateTime lastModified) {
}
