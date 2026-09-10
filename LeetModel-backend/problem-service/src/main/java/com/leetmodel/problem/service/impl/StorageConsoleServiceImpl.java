package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.core.config.MinioProperties;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.storage.StorageContentTypes;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.entity.ProblemAttachment;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ProblemAttachmentMapper;
import com.leetmodel.problem.mapper.ProblemMapper;
import com.leetmodel.problem.service.StorageConsoleService;
import com.leetmodel.problem.vo.StorageObjectVO;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 存储桶对象资产控制台服务实现类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageConsoleServiceImpl implements StorageConsoleService {

    private static final String DEFAULT_UPLOAD_PREFIX = "manual";
    private static final int MAX_PREFIX_LENGTH = 128;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final StorageService storageService;
    private final ProblemAttachmentMapper attachmentMapper;
    private final ProblemMapper problemMapper;

    @Override
    public List<StorageObjectVO> listObjects(String keyword, Boolean onlyOrphans) {
        // 1. 查出数据库中所有正在引用的题目附件
        List<ProblemAttachment> dbAttachments = attachmentMapper.selectList(null);
        Map<String, ProblemAttachment> attachmentMap = dbAttachments.stream()
                .filter(a -> a.getObjectKey() != null)
                .collect(Collectors.toMap(ProblemAttachment::getObjectKey, Function.identity(), (k1, k2) -> k1));

        // 2. 查出关联题目简要字典 (id -> title)
        List<Long> problemIds = dbAttachments.stream().map(ProblemAttachment::getProblemId).distinct().toList();
        Map<Long, String> problemTitleMap = problemIds.isEmpty() ? Collections.emptyMap()
                : problemMapper.selectBatchIds(problemIds).stream()
                .collect(Collectors.toMap(Problem::getId, Problem::getTitle, (t1, t2) -> t1));

        // 3. 扫描 MinIO 存储桶内的所有物理对象
        List<StorageObjectVO> objectList = new ArrayList<>();
        try {
            Iterable<Result<Item>> items = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .recursive(true)
                            .build()
            );
            for (Result<Item> res : items) {
                Item item = res.get();
                if (item.isDir()) continue;

                String objectKey = item.objectName();
                long size = item.size();
                LocalDateTime lastModified = item.lastModified() != null
                        ? LocalDateTime.ofInstant(item.lastModified().toInstant(), ZoneId.systemDefault())
                        : LocalDateTime.now();

                // 对账业务引用
                ProblemAttachment linkedAtt = attachmentMap.get(objectKey);
                int refCount = (linkedAtt != null) ? 1 : 0;
                Long refProblemId = linkedAtt != null ? linkedAtt.getProblemId() : null;
                String refProblemTitle = refProblemId != null ? problemTitleMap.get(refProblemId) : null;
                String fileName = linkedAtt != null ? linkedAtt.getFileName() : extractFileName(objectKey);

                // 未识别题目附件引用的对象过滤
                if (Boolean.TRUE.equals(onlyOrphans) && refCount > 0) {
                    continue;
                }

                // 关键词过滤
                if (keyword != null && !keyword.isBlank()) {
                    String kw = keyword.trim().toLowerCase();
                    boolean matchKey = objectKey.toLowerCase().contains(kw);
                    boolean matchName = fileName != null && fileName.toLowerCase().contains(kw);
                    boolean matchTitle = refProblemTitle != null && refProblemTitle.toLowerCase().contains(kw);
                    if (!matchKey && !matchName && !matchTitle) {
                        continue;
                    }
                }

                String downloadUrl = null;
                try {
                    downloadUrl = storageService.getUrl(objectKey);
                } catch (Exception e) {
                    log.warn("生成对象存储下载链接失败: objectKey={}", objectKey, e);
                }

                objectList.add(StorageObjectVO.builder()
                        .objectKey(objectKey)
                        .fileName(fileName)
                        .fileSize(size)
                        .lastModified(lastModified)
                        .refCount(refCount)
                        .refProblemId(refProblemId)
                        .refProblemTitle(refProblemTitle)
                        .downloadUrl(downloadUrl)
                        .build());
            }
        } catch (Exception e) {
            log.error("扫描 MinIO 存储桶对象清单失败", e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "读取对象存储列表失败");
        }

        // 按最后修改时间倒序排布
        objectList.sort((a, b) -> {
            if (a.getLastModified() == null) return 1;
            if (b.getLastModified() == null) return -1;
            return b.getLastModified().compareTo(a.getLastModified());
        });

        return objectList;
    }

    @Override
    public StorageObjectVO uploadObject(MultipartFile file, String prefix) {
        String safePrefix = normalizePrefix(prefix);
        String objectKey = storageService.upload(file, safePrefix, StorageContentTypes.ARCHIVE);
        String url = storageService.getUrl(objectKey);

        return StorageObjectVO.builder()
                .objectKey(objectKey)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .lastModified(LocalDateTime.now())
                .refCount(0)
                .refProblemId(null)
                .refProblemTitle(null)
                .downloadUrl(url)
                .build();
    }

    @Override
    public void deleteObjectSafely(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_INVALID, "待删除的对象路径不能为空");
        }
        String normalizedObjectKey = objectKey.trim();

        // 题目附件强依赖检查
        List<ProblemAttachment> linked = attachmentMapper.selectList(
                new LambdaQueryWrapper<ProblemAttachment>().eq(ProblemAttachment::getObjectKey, normalizedObjectKey)
        );
        if (!linked.isEmpty()) {
            ProblemAttachment att = linked.get(0);
            Problem problem = problemMapper.selectById(att.getProblemId());
            String title = (problem != null) ? problem.getTitle() : "ID=" + att.getProblemId();
            throw new BusinessException(
                    ProblemErrorCode.ATTACHMENT_IN_USE,
                    "该文件正在被题目【" + title + "】作为附件引用，禁止删除！如确需清理，请先在对应题目中删除该附件。"
            );
        }

        // 未被题目附件引用的对象由管理员确认后执行物理删除
        storageService.delete(normalizedObjectKey);
        log.info("物理删除存储对象成功: objectKey={}", normalizedObjectKey);
    }

    /**
     * 标准化并校验手动上传使用的对象前缀。
     *
     * @param prefix 管理员输入的对象前缀，允许为空
     * @return 可安全用于对象 Key 的前缀；为空时返回 manual
     * @throws BusinessException 当前缀包含空路径段、相对路径或不支持字符时抛出
     */
    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return DEFAULT_UPLOAD_PREFIX;

        String candidate = prefix.trim();
        boolean invalidLength = candidate.length() > MAX_PREFIX_LENGTH;
        boolean invalidPath = candidate.startsWith("/")
                || candidate.endsWith("/")
                || candidate.contains("//")
                || candidate.contains("..");
        boolean invalidCharacter = !candidate.matches("[A-Za-z0-9][A-Za-z0-9/_-]*");
        if (invalidLength || invalidPath || invalidCharacter) {
            throw new BusinessException(
                    ErrorCodeEnum.PARAM_INVALID,
                    "存储前缀仅支持字母、数字、/、_ 和 -，且不能包含空路径段或相对路径"
            );
        }
        return candidate;
    }

    private String extractFileName(String objectKey) {
        if (objectKey == null) return "unknown";
        int lastSlash = objectKey.lastIndexOf('/');
        return (lastSlash >= 0) ? objectKey.substring(lastSlash + 1) : objectKey;
    }
}
