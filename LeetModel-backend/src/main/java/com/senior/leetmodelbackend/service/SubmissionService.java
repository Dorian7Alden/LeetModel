package com.senior.leetmodelbackend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.mapper.OssFileMapper;
import com.senior.leetmodelbackend.mapper.ReviewMapper;
import com.senior.leetmodelbackend.mapper.SubmissionMapper;
import com.senior.leetmodelbackend.pojo.dto.admin.SubmissionDTO;
import com.senior.leetmodelbackend.pojo.entity.OssFile;
import com.senior.leetmodelbackend.pojo.entity.PageResult;
import com.senior.leetmodelbackend.pojo.entity.Review;
import com.senior.leetmodelbackend.pojo.entity.Submission;
import com.senior.leetmodelbackend.pojo.enums.SubmissionStatusEnum;
import com.senior.leetmodelbackend.pojo.vo.admin.ReviewVO;
import com.senior.leetmodelbackend.pojo.vo.admin.SubmissionVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final ReviewMapper reviewMapper;
    private final OssFileMapper ossFileMapper;
    private final ReviewService reviewService;

    public PageResult<SubmissionVO> getSubmissionList(int page, int pageSize, String keyword, String status) {
        PageHelper.startPage(page, pageSize);
        var list = submissionMapper.getSubmissionList(keyword, status);
        PageInfo<SubmissionVO> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getList(), pageInfo.getTotal());
    }

    public SubmissionVO getSubmissionDetail(Integer submissionId) {
        Submission submission = submissionMapper.getSubmissionById(submissionId);
        if (submission == null) {
            throw new BusinessException(ResponseCode.SUBMISSION_NOT_FOUND);
        }
        SubmissionVO vo = SubmissionVO.from(submission);

        List<Review> reviews = reviewMapper.getReviewsBySubmissionId(submissionId);
        if (reviews != null && !reviews.isEmpty()) {
            vo.setReviews(reviews.stream().map(ReviewVO::from).toList());
        }
        return vo;
    }

    public SubmissionVO createSubmission(SubmissionDTO dto, Integer userId) {
        OssFile ossFile = ossFileMapper.getOssFileById(dto.getContentFileId());
        if (ossFile == null) {
            throw new BusinessException(ResponseCode.FILE_NOT_FOUND);
        }

        Submission submission = new Submission();
        submission.setProblemId(dto.getProblemId());
        submission.setUserId(userId);
        submission.setTitle(dto.getTitle());
        submission.setContentFileId(dto.getContentFileId());
        submission.setStatus(SubmissionStatusEnum.PENDING.name());
        submissionMapper.insertSubmission(submission);
        log.info("创建作品: {} [ID: {}]", submission.getTitle(), submission.getSubmissionId());

        reviewService.evaluateSubmission(submission.getSubmissionId());

        return SubmissionVO.from(submission);
    }

    public void reEvaluate(Integer submissionId) {
        Submission submission = submissionMapper.getSubmissionById(submissionId);
        if (submission == null) {
            throw new BusinessException(ResponseCode.SUBMISSION_NOT_FOUND);
        }

        submissionMapper.updateSubmissionStatus(submissionId, SubmissionStatusEnum.PENDING.name());
        reviewService.evaluateSubmission(submissionId);
        log.info("重新评审作品: {}", submissionId);
    }

    public void deleteSubmission(Integer submissionId) {
        Submission submission = submissionMapper.getSubmissionById(submissionId);
        if (submission == null) {
            throw new BusinessException(ResponseCode.SUBMISSION_NOT_FOUND);
        }
        submissionMapper.deleteSubmission(submissionId);
        log.info("删除作品: {}", submissionId);
    }
}
