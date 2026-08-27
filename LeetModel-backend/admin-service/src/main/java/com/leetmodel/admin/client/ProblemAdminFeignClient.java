package com.leetmodel.admin.client;

import com.leetmodel.admin.dto.AdminProblemPageQuery;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 题目领域既有管理接口的无状态转发契约。 */
@FeignClient(name = "problem-service", contextId = "problemAdminFeignClient")
public interface ProblemAdminFeignClient {
    @GetMapping("/api/problems") Result<Object> page(@SpringQueryMap AdminProblemPageQuery query);
    @GetMapping("/api/problems/{id}") Result<Object> detail(@PathVariable("id") Long id);
    @PostMapping("/api/problems") Result<Object> create(@RequestBody Map<String, Object> request);
    @PutMapping("/api/problems/{id}") Result<Object> update(@PathVariable("id") Long id,
                                                            @RequestBody Map<String, Object> request);
    @DeleteMapping("/api/problems/{id}") Result<Void> delete(@PathVariable("id") Long id);

    @GetMapping("/api/tags") Result<Object> listTags();
    @PostMapping("/api/tags") Result<Object> createTag(@RequestBody Map<String, Object> request);
    @PutMapping("/api/tags/{id}") Result<Object> updateTag(@PathVariable("id") Long id,
                                                           @RequestBody Map<String, Object> request);
    @DeleteMapping("/api/tags/{id}") Result<Void> deleteTag(@PathVariable("id") Long id);
    @GetMapping("/api/contests") Result<Object> listContests();

    @PostMapping(value = "/api/problems/{id}/attachments", consumes = "multipart/form-data")
    Result<Object> uploadAttachment(@PathVariable("id") Long id,
                                    @RequestPart("file") MultipartFile file,
                                    @RequestParam(value = "description", required = false) String description,
                                    @RequestParam(value = "sortOrder", required = false) Integer sortOrder);

    @DeleteMapping("/api/problems/{problemId}/attachments/{attachmentId}")
    Result<Void> deleteAttachment(@PathVariable("problemId") Long problemId,
                                  @PathVariable("attachmentId") Long attachmentId);
}
