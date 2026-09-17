package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long submitterId;
    private Integer version;
    private String originalFilename;
    private String objectName;
    private String status;
    private Boolean finalVersion;
    private LocalDateTime createTime;
}
