package com.leetmodel.submission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("submission_lock")
public class SubmissionLock {
    private Long id;
    private Long teamId;
    private Long submissionId;
    private LocalDateTime lockedAt;
}
