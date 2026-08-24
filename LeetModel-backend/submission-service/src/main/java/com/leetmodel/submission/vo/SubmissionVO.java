package com.leetmodel.submission.vo;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Builder
public class SubmissionVO {
    private Long id; private Long teamId; private Long problemId; private Long submitterId;
    private Integer version; private String originalFilename; private Long fileSize;
    private String status; private String downloadUrl; private LocalDateTime createTime;
}
