package com.leetmodel.file.storage;

import com.leetmodel.common.core.config.MinioProperties;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.file.enums.FileErrorCode;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MinioFileObjectInventory implements FileObjectInventory {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public List<PhysicalObject> listAll() {
        List<PhysicalObject> objects = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    LocalDateTime lastModified = item.lastModified() == null
                            ? null
                            : item.lastModified().withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                    objects.add(new PhysicalObject(item.objectName(), item.size(), lastModified));
                }
            }
            return objects;
        } catch (Exception exception) {
            throw new BusinessException(FileErrorCode.STORAGE_UNAVAILABLE, "读取存储桶对象清单失败");
        }
    }
}
