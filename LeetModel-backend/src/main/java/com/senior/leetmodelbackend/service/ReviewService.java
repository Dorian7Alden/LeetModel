package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.mapper.OssFileMapper;
import com.senior.leetmodelbackend.mapper.ProblemMapper;
import com.senior.leetmodelbackend.mapper.ReviewMapper;
import com.senior.leetmodelbackend.mapper.SubmissionMapper;
import com.senior.leetmodelbackend.pojo.dto.review.DimensionScoreDTO;
import com.senior.leetmodelbackend.pojo.entity.OssFile;
import com.senior.leetmodelbackend.pojo.entity.Problem;
import com.senior.leetmodelbackend.pojo.entity.Review;
import com.senior.leetmodelbackend.pojo.entity.Submission;
import com.senior.leetmodelbackend.pojo.enums.ReviewDimensionEnum;
import com.senior.leetmodelbackend.pojo.enums.ReviewStatusEnum;
import com.senior.leetmodelbackend.pojo.enums.SubmissionStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class ReviewService {

    private static final int MAX_RETRY = 3;
    private static final String STRICT_JSON_INSTRUCTION = """

            请仅返回可被 JSON 解析器直接解析的 JSON 对象：
            1) 不要输出 Markdown 代码块（如 ```json）。
            2) 不要输出任何解释文字、前后缀、注释。
            3) 所有字符串内引号必须正确转义。""";

    private final ChatClient chatClient;
    private final PromptService promptService;
    private final ReviewMapper reviewMapper;
    private final ReviewLogService reviewLogService;
    private final SubmissionMapper submissionMapper;
    private final OssFileMapper ossFileMapper;
    private final ProblemMapper problemMapper;
    private final HttpClient httpClient;
    private final Executor reviewExecutor;

    public ReviewService(ChatClient.Builder chatClientBuilder,
                         PromptService promptService,
                         ReviewMapper reviewMapper,
                         ReviewLogService reviewLogService,
                         SubmissionMapper submissionMapper,
                         OssFileMapper ossFileMapper,
                         ProblemMapper problemMapper,
                         @Qualifier("reviewExecutor") Executor reviewExecutor) {
        this.chatClient = chatClientBuilder.build();
        this.promptService = promptService;
        this.reviewMapper = reviewMapper;
        this.reviewLogService = reviewLogService;
        this.submissionMapper = submissionMapper;
        this.ossFileMapper = ossFileMapper;
        this.problemMapper = problemMapper;
        this.reviewExecutor = reviewExecutor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 异步评审提交的作品（主入口）
     */
    @Async("reviewExecutor")
    public void evaluateSubmission(Integer submissionId) {
        log.info("开始评审作品: submissionId={}", submissionId);
        try {
            submissionMapper.updateSubmissionStatus(submissionId, SubmissionStatusEnum.EVALUATING.name());

            Submission submission = submissionMapper.getSubmissionById(submissionId);
            if (submission == null) {
                log.error("作品不存在: submissionId={}", submissionId);
                return;
            }

            String problemContent = readFileContent(submission.getProblemId());
            String submissionContent = submission.getContentFileId() != null
                    ? readOssFileContent(submission.getContentFileId())
                    : "";

            List<CompletableFuture<Review>> futures = new ArrayList<>();
            for (ReviewDimensionEnum dimension : ReviewDimensionEnum.values()) {
                CompletableFuture<Review> future = CompletableFuture.supplyAsync(
                        () -> evaluateDimension(submission, problemContent, submissionContent, dimension),
                        reviewExecutor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<Review> completedReviews = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(r -> ReviewStatusEnum.COMPLETED.name().equals(r.getStatus()))
                    .toList();

            if (completedReviews.isEmpty()) {
                submissionMapper.updateSubmissionStatus(submissionId, SubmissionStatusEnum.FAILED.name());
                reviewLogService.logFailure(submissionId, null,
                        "所有维度评审均失败", "5个维度均未成功完成评审");
            } else {
                BigDecimal totalScore = calculateWeightedScore(completedReviews);
                submissionMapper.updateSubmissionScore(submissionId, totalScore, SubmissionStatusEnum.COMPLETED.name());
                log.info("作品评审完成: submissionId={}, totalScore={}", submissionId, totalScore);
            }
        } catch (Exception e) {
            log.error("评审作品异常: submissionId={}", submissionId, e);
            submissionMapper.updateSubmissionStatus(submissionId, SubmissionStatusEnum.FAILED.name());
            reviewLogService.logFailure(submissionId, null,
                    "评审系统异常", e.getMessage());
        }
    }

    /**
     * 对单个维度进行评审（含3次重试）
     */
    private Review evaluateDimension(Submission submission,
                                     String problemContent,
                                     String submissionContent,
                                     ReviewDimensionEnum dimension) {
        Review review = createReviewRecord(submission.getSubmissionId(), dimension);
        BeanOutputConverter<DimensionScoreDTO> converter = new BeanOutputConverter<>(DimensionScoreDTO.class);

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                reviewMapper.updateReviewStatus(review.getReviewId(),
                        ReviewStatusEnum.RUNNING.name(), attempt);

                String systemPrompt = promptService.buildSystemMessage(dimension.getName()).getText()
                        + "\n\n" + converter.getFormat();
                if (attempt > 1) {
                    systemPrompt += STRICT_JSON_INSTRUCTION;
                    if (lastError != null) {
                        String errorMsg = lastError.getMessage();
                        if (errorMsg != null && errorMsg.length() > 200) {
                            errorMsg = errorMsg.substring(0, 200) + "...";
                        }
                        systemPrompt += "\n上次输出解析失败原因：" + errorMsg;
                    }
                }

                String userPrompt = promptService.buildUserMessage(problemContent, submissionContent).getText();

                String responseContent = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();

                DimensionScoreDTO result = parseWithRepair(responseContent, converter);

                if (result == null) {
                    throw new BusinessException(ResponseCode.AI_REVIEW_PARSE_FAILED,
                            "AI响应解析失败");
                }

                if (result.score() < 0 || result.score() > 100) {
                    throw new BusinessException(ResponseCode.AI_REVIEW_PARSE_FAILED,
                            "维度得分越界: score=" + result.score());
                }

                reviewMapper.updateReviewResult(review.getReviewId(),
                        BigDecimal.valueOf(result.score()),
                        result.feedback(),
                        ReviewStatusEnum.COMPLETED.name());

                review.setScore(BigDecimal.valueOf(result.score()));
                review.setFeedback(result.feedback());
                review.setStatus(ReviewStatusEnum.COMPLETED.name());

                reviewLogService.logSuccess(submission.getSubmissionId(), review.getReviewId(),
                        "维度评审成功: " + dimension.getName(),
                        "得分=" + result.score() + ", 反馈=" + result.feedback());

                return review;

            } catch (Exception e) {
                lastError = e;
                if (attempt < MAX_RETRY) {
                    log.warn("维度评审失败，准备重试: submissionId={}, dimension={}, attempt={}/{}, error={}",
                            submission.getSubmissionId(), dimension.getCode(), attempt, MAX_RETRY, e.getMessage());
                } else {
                    log.error("维度评审最终失败: submissionId={}, dimension={}, attempts={}, error={}",
                            submission.getSubmissionId(), dimension.getCode(), MAX_RETRY, e.getMessage());
                }
            }
        }

        reviewMapper.updateReviewStatus(review.getReviewId(),
                ReviewStatusEnum.FAILED.name(), MAX_RETRY);
        review.setStatus(ReviewStatusEnum.FAILED.name());

        reviewLogService.logFailure(submission.getSubmissionId(), review.getReviewId(),
                "维度评审失败（已达最大重试次数）: " + dimension.getName(),
                lastError != null ? lastError.getMessage() : "unknown");

        return review;
    }

    private Review createReviewRecord(Integer submissionId, ReviewDimensionEnum dimension) {
        Review review = new Review();
        review.setSubmissionId(submissionId);
        review.setDimensionCode(dimension.getCode());
        review.setDimensionName(dimension.getName());
        review.setWeight(dimension.getWeight());
        review.setStatus(ReviewStatusEnum.PENDING.name());
        review.setRetryCount(0);
        reviewMapper.insertReview(review);
        return review;
    }

    /**
     * 解析 AI 响应，失败时尝试修复 JSON 未转义引号
     */
    private DimensionScoreDTO parseWithRepair(String content, BeanOutputConverter<DimensionScoreDTO> converter) {
        try {
            return converter.convert(content);
        } catch (Exception firstError) {
            String repaired = repairUnescapedQuotesInJsonStrings(content);
            if (!repaired.equals(content)) {
                try {
                    DimensionScoreDTO result = converter.convert(repaired);
                    log.warn("JSON 存在未转义引号，本地修复后解析成功");
                    return result;
                } catch (Exception repairError) {
                    firstError.addSuppressed(repairError);
                }
            }
            throw new RuntimeException("AI响应解析失败: " + firstError.getMessage(), firstError);
        }
    }

    /**
     * 修复 JSON 字符串中未转义的引号
     */
    private String repairUnescapedQuotesInJsonStrings(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        StringBuilder repaired = new StringBuilder(content.length() + 16);
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (!inString) {
                if (ch == '"') inString = true;
                repaired.append(ch);
                continue;
            }
            if (escaping) {
                repaired.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                repaired.append(ch);
                escaping = true;
                continue;
            }
            if (ch == '"') {
                if (isJsonStringTerminator(content, i + 1)) {
                    inString = false;
                    repaired.append(ch);
                } else {
                    repaired.append("\\\"");
                }
                continue;
            }
            repaired.append(ch);
        }
        return repaired.toString();
    }

    private boolean isJsonStringTerminator(String content, int start) {
        for (int i = start; i < content.length(); i++) {
            char next = content.charAt(i);
            if (Character.isWhitespace(next)) continue;
            return next == ',' || next == '}' || next == ']' || next == ':';
        }
        return true;
    }

    private String readFileContent(Integer problemId) {
        Problem problem = problemMapper.getProblemById(problemId);
        if (problem == null || problem.getContentFileId() == null) {
            return "";
        }
        return readOssFileContent(problem.getContentFileId());
    }

    private String readOssFileContent(Integer fileId) {
        OssFile ossFile = ossFileMapper.getOssFileById(fileId);
        if (ossFile == null || ossFile.getFileUrl() == null) {
            return "";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ossFile.getFileUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.body();
        } catch (Exception e) {
            log.error("读取OSS文件内容失败: fileId={}, url={}", fileId, ossFile.getFileUrl(), e);
            return "";
        }
    }

    /**
     * 计算加权平均分。仅计入成功完成的维度，按实际权重归一化。
     */
    private BigDecimal calculateWeightedScore(List<Review> reviews) {
        BigDecimal totalWeightedScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (Review r : reviews) {
            if (ReviewStatusEnum.COMPLETED.name().equals(r.getStatus())
                    && r.getScore() != null && r.getWeight() != null) {
                totalWeightedScore = totalWeightedScore.add(r.getScore().multiply(r.getWeight()));
                totalWeight = totalWeight.add(r.getWeight());
            }
        }
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalWeightedScore.divide(totalWeight, 1, RoundingMode.HALF_UP);
    }
}
