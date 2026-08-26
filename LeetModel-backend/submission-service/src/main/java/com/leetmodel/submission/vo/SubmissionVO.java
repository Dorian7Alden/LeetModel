package com.leetmodel.submission.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Builder
public class SubmissionVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
    private Long submitterId;
    private Integer version; private String originalFilename; private Long fileSize;
    private String status; private Boolean finalVersion; private String downloadUrl; private LocalDateTime createTime;
}
