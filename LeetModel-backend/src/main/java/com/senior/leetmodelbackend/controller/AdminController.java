package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.common.annotation.RequirePermission;
import com.senior.leetmodelbackend.common.utils.OssUtils;
import com.senior.leetmodelbackend.mapper.OssFileMapper;
import com.senior.leetmodelbackend.pojo.dto.admin.AssignIdsDTO;
import com.senior.leetmodelbackend.pojo.dto.admin.PermissionDTO;
import com.senior.leetmodelbackend.pojo.dto.admin.ProblemDTO;
import com.senior.leetmodelbackend.pojo.dto.admin.RoleDTO;
import com.senior.leetmodelbackend.pojo.dto.admin.SubmissionDTO;
import com.senior.leetmodelbackend.pojo.dto.admin.UserBatchUpdateDTO;
import com.senior.leetmodelbackend.pojo.entity.PageResult;
import com.senior.leetmodelbackend.pojo.entity.OssFile;
import com.senior.leetmodelbackend.pojo.entity.Permission;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.Role;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.pojo.vo.UserVO;
import com.senior.leetmodelbackend.pojo.vo.admin.FileUploadVO;
import com.senior.leetmodelbackend.pojo.vo.admin.PermissionVO;
import com.senior.leetmodelbackend.pojo.vo.admin.ProblemVO;
import com.senior.leetmodelbackend.pojo.vo.admin.RoleVO;
import com.senior.leetmodelbackend.pojo.vo.admin.SubmissionVO;
import com.senior.leetmodelbackend.service.PermissionService;
import com.senior.leetmodelbackend.service.ProblemService;
import com.senior.leetmodelbackend.service.RoleService;
import com.senior.leetmodelbackend.service.SubmissionService;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.validator.admin.AssignIdsParamValidator;
import com.senior.leetmodelbackend.validator.admin.PermissionParamValidator;
import com.senior.leetmodelbackend.validator.admin.ProblemParamValidator;
import com.senior.leetmodelbackend.validator.admin.RoleParamValidator;
import com.senior.leetmodelbackend.validator.admin.SubmissionParamValidator;
import com.senior.leetmodelbackend.validator.admin.UserBatchUpdateValidator;
import com.senior.leetmodelbackend.validator.user.UserIdParamValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@AllArgsConstructor
public class AdminController {

    private final UserService userService;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final ProblemService problemService;
    private final SubmissionService submissionService;
    private final OssUtils ossUtils;
    private final OssFileMapper ossFileMapper;

    private final UserIdParamValidator userIdParamValidator;
    private final RoleParamValidator roleParamValidator;
    private final PermissionParamValidator permissionParamValidator;
    private final ProblemParamValidator problemParamValidator;
    private final SubmissionParamValidator submissionParamValidator;
    private final AssignIdsParamValidator assignIdsParamValidator;
    private final UserBatchUpdateValidator userBatchUpdateValidator;

    // ==================== User Management ====================

    /**
     * 获取全部用户列表
     */
    @RequirePermission("USER_VIEW")
    @GetMapping("/users")
    public Result<List<UserVO>> getUserList() {
        List<User> users = userService.getAllUsers();
        List<UserVO> voList = users.stream().map(UserVO::createVO).toList();
        for (UserVO vo : voList) {
            if (vo.getAvatarFileId() != null) {
                OssFile ossFile = ossFileMapper.getOssFileById(vo.getAvatarFileId());
                if (ossFile != null) {
                    vo.setAvatarUrl(ossFile.getFileUrl());
                }
            }
        }
        return Result.success(voList);
    }

    /**
     * 查看用户持有的角色
     */
    @RequirePermission("USER_VIEW")
    @GetMapping("/users/{userId}/roles")
    public Result<List<RoleVO>> getUserRoles(@PathVariable Integer userId) {
        userIdParamValidator.validate(userId);
        List<Role> roles = userService.getUserRoles(userId);
        List<RoleVO> voList = roles != null
                ? roles.stream().map(RoleVO::createVO).toList()
                : List.of();
        return Result.success(voList);
    }

    /**
     * 分配用户角色（全量替换）
     */
    @RequirePermission("AUTH_MANAGE")
    @PutMapping("/users/{userId}/roles")
    public Result<Void> assignUserRoles(@PathVariable Integer userId, @RequestBody AssignIdsDTO request) {
        userIdParamValidator.validate(userId);
        assignIdsParamValidator.validate(request);
        userService.assignUserRoles(userId, request.getIds());
        return Result.success("分配成功");
    }

    /**
     * 批量更新用户信息（仅更新传入的非空字段）
     */
    @RequirePermission("USER_UPDATE")
    @PutMapping("/users/batch")
    public Result<Void> batchUpdateUsers(@RequestBody UserBatchUpdateDTO dto) {
        userBatchUpdateValidator.validate(dto);
        userService.batchUpdateUsers(dto);
        return Result.success();
    }

    // ==================== Role Management ====================

    /**
     * 获取全部角色列表
     */
    @RequirePermission("ROLE_VIEW")
    @GetMapping("/roles")
    public Result<List<RoleVO>> getRoleList() {
        List<Role> roles = roleService.getRoleList();
        List<RoleVO> voList = roles.stream().map(RoleVO::createVO).toList();
        return Result.success(voList);
    }

    /**
     * 获取角色详情
     */
    @RequirePermission("ROLE_VIEW")
    @GetMapping("/roles/{roleId}")
    public Result<RoleVO> getRoleDetail(@PathVariable Integer roleId) {
        Role role = roleService.getRoleById(roleId);
        return Result.success(RoleVO.createVO(role));
    }

    /**
     * 创建角色
     */
    @RequirePermission("ROLE_MANAGE")
    @PostMapping("/roles")
    public Result<Void> createRole(@RequestBody RoleDTO request) {
        roleParamValidator.validate(request);
        roleService.createRole(request);
        return Result.success("创建成功");
    }

    /**
     * 更新角色
     */
    @RequirePermission("ROLE_MANAGE")
    @PutMapping("/roles/{roleId}")
    public Result<Void> updateRole(@PathVariable Integer roleId, @RequestBody RoleDTO request) {
        roleParamValidator.validate(request);
        roleService.updateRole(roleId, request);
        return Result.success("更新成功");
    }

    /**
     * 删除角色
     */
    @RequirePermission("ROLE_MANAGE")
    @DeleteMapping("/roles/{roleId}")
    public Result<Void> deleteRole(@PathVariable Integer roleId) {
        roleService.deleteRole(roleId);
        return Result.success("删除成功");
    }

    /**
     * 查看角色持有的权限
     */
    @RequirePermission("ROLE_VIEW")
    @GetMapping("/roles/{roleId}/permissions")
    public Result<List<PermissionVO>> getRolePermissions(@PathVariable Integer roleId) {
        List<Permission> permissions = roleService.getRolePermissions(roleId);
        List<PermissionVO> voList = permissions.stream().map(PermissionVO::createVO).toList();
        return Result.success(voList);
    }

    /**
     * 分配角色权限（全量替换）
     */
    @RequirePermission("AUTH_MANAGE")
    @PutMapping("/roles/{roleId}/permissions")
    public Result<Void> assignRolePermissions(@PathVariable Integer roleId, @RequestBody AssignIdsDTO request) {
        assignIdsParamValidator.validate(request);
        roleService.assignRolePermissions(roleId, request.getIds());
        return Result.success("分配成功");
    }

    // ==================== Permission Management ====================

    /**
     * 获取全部权限列表
     */
    @RequirePermission("PERMISSION_VIEW")
    @GetMapping("/permissions")
    public Result<List<PermissionVO>> getPermissionList() {
        List<Permission> permissions = permissionService.getPermissionList();
        List<PermissionVO> voList = permissions.stream().map(PermissionVO::createVO).toList();
        return Result.success(voList);
    }

    /**
     * 获取权限详情
     */
    @RequirePermission("PERMISSION_VIEW")
    @GetMapping("/permissions/{permissionId}")
    public Result<PermissionVO> getPermissionById(@PathVariable Integer permissionId) {
        Permission permission = permissionService.getPermissionById(permissionId);
        return Result.success(PermissionVO.createVO(permission));
    }

    /**
     * 创建权限
     */
    @RequirePermission("PERMISSION_MANAGE")
    @PostMapping("/permissions")
    public Result<Void> createPermission(@RequestBody PermissionDTO request) {
        permissionParamValidator.validate(request);
        permissionService.createPermission(request);
        return Result.success("创建成功");
    }

    /**
     * 更新权限
     */
    @RequirePermission("PERMISSION_MANAGE")
    @PutMapping("/permissions/{permissionId}")
    public Result<Void> updatePermission(@PathVariable Integer permissionId, @RequestBody PermissionDTO request) {
        permissionParamValidator.validate(request);
        permissionService.updatePermission(permissionId, request);
        return Result.success("更新成功");
    }

    /**
     * 删除权限
     */
    @RequirePermission("PERMISSION_MANAGE")
    @DeleteMapping("/permissions/{permissionId}")
    public Result<Void> deletePermission(@PathVariable Integer permissionId) {
        permissionService.deletePermission(permissionId);
        return Result.success("删除成功");
    }

    // ==================== Problem Management ====================

    /**
     * 获取题目分页列表
     */
    @RequirePermission("PROBLEM_VIEW")
    @GetMapping("/problems")
    public Result<PageResult<ProblemVO>> getProblemList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<ProblemVO> pageResult = problemService.getProblemList(page, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 获取题目详情
     */
    @RequirePermission("PROBLEM_VIEW")
    @GetMapping("/problems/{problemId}")
    public Result<ProblemVO> getProblemDetail(@PathVariable Integer problemId) {
        var problem = problemService.getProblemById(problemId);
        ProblemVO vo = ProblemVO.createVO(problem);
        var ossFile = ossFileMapper.getOssFileById(problem.getContentFileId());
        if (ossFile != null) {
            vo.setContentFileUrl(ossFile.getFileUrl());
        }
        return Result.success(vo);
    }

    /**
     * 创建题目
     */
    @RequirePermission("PROBLEM_MANAGE")
    @PostMapping("/problems")
    public Result<Void> createProblem(@RequestBody ProblemDTO request, HttpServletRequest req) {
        problemParamValidator.validate(request);
        Integer userId = (Integer) req.getAttribute("userId");
        problemService.createProblem(request, userId);
        return Result.success("创建成功");
    }

    /**
     * 更新题目
     */
    @RequirePermission("PROBLEM_MANAGE")
    @PutMapping("/problems/{problemId}")
    public Result<Void> updateProblem(@PathVariable Integer problemId, @RequestBody ProblemDTO request) {
        problemParamValidator.validate(request);
        problemService.updateProblem(problemId, request);
        return Result.success("更新成功");
    }

    /**
     * 删除题目
     */
    @RequirePermission("PROBLEM_MANAGE")
    @DeleteMapping("/problems/{problemId}")
    public Result<Void> deleteProblem(@PathVariable Integer problemId) {
        problemService.deleteProblem(problemId);
        return Result.success("删除成功");
    }

    // ==================== Submission Management ====================

    /**
     * 获取作品分页列表
     */
    @RequirePermission("SUBMISSION_VIEW")
    @GetMapping("/submissions")
    public Result<PageResult<SubmissionVO>> getSubmissionList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        PageResult<SubmissionVO> pageResult = submissionService.getSubmissionList(page, pageSize, keyword, status);
        return Result.success(pageResult);
    }

    /**
     * 获取作品详情（含评审结果）
     */
    @RequirePermission("SUBMISSION_VIEW")
    @GetMapping("/submissions/{submissionId}")
    public Result<SubmissionVO> getSubmissionDetail(@PathVariable Integer submissionId) {
        SubmissionVO vo = submissionService.getSubmissionDetail(submissionId);
        return Result.success(vo);
    }

    /**
     * 创建作品（自动触发异步评审）
     */
    @RequirePermission("SUBMISSION_MANAGE")
    @PostMapping("/submissions")
    public Result<SubmissionVO> createSubmission(@RequestBody SubmissionDTO request, HttpServletRequest req) {
        submissionParamValidator.validate(request);
        Integer userId = (Integer) req.getAttribute("userId");
        SubmissionVO vo = submissionService.createSubmission(request, userId);
        return Result.success("作品提交成功，已加入评审队列", vo);
    }

    /**
     * 重新评审作品
     */
    @RequirePermission("SUBMISSION_MANAGE")
    @PostMapping("/submissions/{submissionId}/re-evaluate")
    public Result<Void> reEvaluateSubmission(@PathVariable Integer submissionId) {
        submissionService.reEvaluate(submissionId);
        return Result.success("已重新提交评审");
    }

    /**
     * 删除作品
     */
    @RequirePermission("SUBMISSION_MANAGE")
    @DeleteMapping("/submissions/{submissionId}")
    public Result<Void> deleteSubmission(@PathVariable Integer submissionId) {
        submissionService.deleteSubmission(submissionId);
        return Result.success("删除成功");
    }

    // ==================== File Upload ====================

    /**
     * 上传文件到 OSS 并保存记录
     */
    @RequirePermission("FILE_UPLOAD")
    @PostMapping("/upload")
    public Result<FileUploadVO> uploadFile2Oss(@RequestParam MultipartFile file, HttpServletRequest req) {
        Integer userId = (Integer) req.getAttribute("userId");
        String url = ossUtils.uploadFile(file);
        OssFile ossFile = new OssFile();
        ossFile.setFileName(file.getOriginalFilename());
        ossFile.setFileUrl(url);
        String suffix = "";
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            suffix = name.substring(name.lastIndexOf(".")).toLowerCase();
        }
        ossFile.setFileSuffix(suffix);
        ossFile.setContentType(file.getContentType());
        ossFile.setFileSize(file.getSize());
        ossFile.setUploaderId(userId);
        ossFileMapper.insertOssFile(ossFile);
        return Result.success(new FileUploadVO(ossFile.getFileId(), url));
    }
}
