package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 最终提交的跨服务只读快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionSnapshotDTO {
    private Long id;
    private Long teamId;
    private Long problemId;
    private Long submitterId;
    private Integer version;
    private String originalFilename;
    private String objectName;
    private String status;
    private Boolean finalVersion;
    private LocalDateTime createTime;
}
