package com.senior.leetmodelbackend.pojo.dto.problem;

import com.senior.leetmodelbackend.pojo.dto.tag.CreateProblemTagDTO;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class ProblemUploadDTO {

    /**
     * 题目标题
     */
    private String title;

    /**
     * Markdown 内容文件
     */
    private MultipartFile contentMarkdownFile;


    /**
     * 附件
     */
    private List<AttachmentLinkDTO> attachmentLinks;

    /**
     * 待关联的标签
     */
    private List<CreateProblemTagDTO> tagList;

}