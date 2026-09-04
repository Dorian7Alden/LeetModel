package com.leetmodel.team.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
import com.leetmodel.team.dto.JoinApplicationCreateRequest;
import com.leetmodel.team.dto.JoinApplicationPageQuery;
import com.leetmodel.team.dto.JoinApplicationReviewRequest;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.MyTeamPageQuery;
import com.leetmodel.team.dto.RecruitmentUpdateRequest;
import com.leetmodel.team.dto.SubmissionPermissionUpdateRequest;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamPublicPageQuery;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.vo.JoinApplicationVO;
import com.leetmodel.team.vo.TeamMemberVO;
import com.leetmodel.team.vo.TeamVO;

import java.util.List;

/**
 * 团队服务接口。
 */
public interface TeamService extends IService<Team> {

    /**
     * 创建新队伍并初始化队长成员记录。
     *
     * @param request  队伍创建参数对象，不能为 null
     * @param leaderId 创建者用户 ID，不能为 null
     * @return 创建后的队伍视图对象
     */
    TeamVO createTeam(TeamCreateRequest request, Long leaderId);

    /**
     * 分页查询开放招募的公共队伍列表。
     *
     * @param query         分页查询条件对象，不能为 null
     * @param currentUserId 当前登录用户 ID，可为空
     * @return 分页包装的队伍视图列表
     */
    PageResult<TeamVO> pagePublicTeams(TeamPublicPageQuery query, Long currentUserId);

    /**
     * 查询存在组建中缺人队伍的题目标识列表。
     *
     * @return 题目 ID 列表
     */
    List<Long> listPublicPreparingProblemIds();

    /**
     * 查询指定队伍的详细信息（含申请状态匹配）。
     *
     * @param teamId        目标队伍 ID，不能为 null
     * @param currentUserId 当前登录用户 ID，可为空
     * @return 队伍详细视图对象
     */
    TeamVO getTeamDetail(Long teamId, Long currentUserId);

    /**
     * 查询指定队伍的详细信息（匿名访问）。
     *
     * @param teamId 目标队伍 ID，不能为 null
     * @return 队伍详细视图对象
     */
    TeamVO getTeamDetail(Long teamId);

    /**
     * 更新队伍基本信息（名称、描述）。
     *
     * @param teamId     目标队伍 ID，不能为 null
     * @param request    更新请求对象，不能为 null
     * @param operatorId 操作人用户 ID，不能为 null
     * @return 更新后的队伍视图对象
     */
    TeamVO updateTeam(Long teamId, TeamUpdateRequest request, Long operatorId);

    /**
     * 队长发布新的队伍招募位置。
     *
     * @param teamId     目标队伍 ID，不能为 null
     * @param request    招募请求对象，不能为 null
     * @param operatorId 操作人用户 ID，不能为 null
     * @return 更新后的队伍视图对象
     */
    TeamVO publishRecruitment(Long teamId, RecruitmentUpdateRequest request, Long operatorId);

    /**
     * 队长编辑已有的开放招募位置。
     *
     * @param teamId        目标队伍 ID，不能为 null
     * @param recruitmentId 招募条目 ID，不能为 null
     * @param request       招募更新对象，不能为 null
     * @param operatorId    操作人用户 ID，不能为 null
     * @return 更新后的队伍视图对象
     */
    TeamVO updateRecruitment(Long teamId, Long recruitmentId, RecruitmentUpdateRequest request, Long operatorId);

    /**
     * 队长关闭指定的开放招募位置。
     *
     * @param teamId        目标队伍 ID，不能为 null
     * @param recruitmentId 待关闭的招募 ID，不能为 null
     * @param operatorId    操作人用户 ID，不能为 null
     */
    void closeRecruitment(Long teamId, Long recruitmentId, Long operatorId);

    /**
     * 队长解散指定的队伍。
     *
     * @param teamId     目标队伍 ID，不能为 null
     * @param operatorId 操作人用户 ID，不能为 null
     */
    void dissolveTeam(Long teamId, Long operatorId);

    /**
     * 根据状态筛选查询指定用户的队伍列表。
     *
     * @param userId 目标用户 ID，不能为 null
     * @param status 可选的状态过滤值
     * @return 队伍视图列表
     */
    List<TeamVO> listMyTeams(Long userId, Integer status);

    /**
     * 查询指定用户参与的全部队伍列表。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 队伍视图列表
     */
    List<TeamVO> listMyTeams(Long userId);

    /**
     * 分页查询当前用户参与的队伍列表。
     *
     * @param userId 目标用户 ID，不能为 null
     * @param query  分页查询参数对象，不能为 null
     * @return 分页包装的队伍视图列表
     */
    PageResult<TeamVO> pageMyTeams(Long userId, MyTeamPageQuery query);

    /**
     * 队长从队伍中移除指定队员。
     *
     * @param teamId     目标队伍 ID，不能为 null
     * @param memberId   待移除的队员记录 ID，不能为 null
     * @param operatorId 操作人用户 ID，不能为 null
     */
    void removeMember(Long teamId, Long memberId, Long operatorId);

    /**
     * 队长更新指定队员的专业角色职责。
     *
     * @param teamId     目标队伍 ID，不能为 null
     * @param memberId   目标队员记录 ID，不能为 null
     * @param request    包含角色选择的请求对象，不能为 null
     * @param operatorId 操作人用户 ID，不能为 null
     * @return 更新后的队员视图对象
     */
    TeamMemberVO updateMemberRoles(Long teamId, Long memberId,
                                   MemberRolesUpdateRequest request, Long operatorId);

    /**
     * 队员主动退出所在队伍。
     *
     * @param teamId 目标队伍 ID，不能为 null
     * @param userId 操作用户 ID，不能为 null
     */
    void leaveTeam(Long teamId, Long userId);

    /**
     * 用户向队伍提交入队申请。
     *
     * @param teamId      目标队伍 ID，不能为 null
     * @param request     申请请求对象，不能为 null
     * @param applicantId 申请人用户 ID，不能为 null
     * @return 入队申请视图对象
     */
    JoinApplicationVO submitApplication(Long teamId, JoinApplicationCreateRequest request,
                                        Long applicantId);

    /**
     * 申请人取消自己提交的入队申请。
     *
     * @param teamId      目标队伍 ID，不能为 null
     * @param applicantId 申请人用户 ID，不能为 null
     */
    void cancelMyApplication(Long teamId, Long applicantId);

    /**
     * 队长分页查询队伍收到的入队申请。
     *
     * @param teamId     目标队伍 ID，不能为 null
     * @param query      分页查询参数对象，不能为 null
     * @param operatorId 操作人用户 ID，不能为 null
     * @return 分页包装的申请视图列表
     */
    PageResult<JoinApplicationVO> pageApplications(Long teamId, JoinApplicationPageQuery query,
                                                   Long operatorId);

    /**
     * 队长审核入队申请。
     *
     * @param teamId        目标队伍 ID，不能为 null
     * @param applicationId 申请记录 ID，不能为 null
     * @param request       审核决定请求对象，不能为 null
     * @param operatorId    操作人用户 ID，不能为 null
     * @return 更新后的申请视图对象
     */
    JoinApplicationVO reviewApplication(Long teamId, Long applicationId,
                                        JoinApplicationReviewRequest request, Long operatorId);

    /**
     * 队长开启队伍限时建模练习。
     *
     * @param teamId     目标队伍 ID，不能为 null
     * @param operatorId 操作人用户 ID，不能为 null
     * @return 更新后的队伍视图对象
     */
    TeamVO startPractice(Long teamId, Long operatorId);

    /**
     * 队长提前结束当前进行中的限时练习。
     *
     * @param teamId     目标队伍 ID，不能为 null
     * @param operatorId 操作人用户 ID，不能为 null
     * @return 更新后的队伍视图对象
     */
    TeamVO endPractice(Long teamId, Long operatorId);

    /**
     * 队长更新指定成员的论文提交权限。
     *
     * @param teamId     目标队伍 ID，不能为 null
     * @param memberId   目标队员记录 ID，不能为 null
     * @param request    权限设置请求对象，不能为 null
     * @param operatorId 操作人用户 ID，不能为 null
     * @return 更新后的队员视图对象
     */
    TeamMemberVO updateSubmissionPermission(Long teamId, Long memberId,
                                            SubmissionPermissionUpdateRequest request, Long operatorId);

    /**
     * 定时自动结束已超截止时间的实训练习。
     */
    void endExpiredPractices();

    /**
     * 校验并获取指定用户在队伍中的作品提交权限。
     *
     * @param teamId 目标队伍 ID，不能为 null
     * @param userId 目标用户 ID，不能为 null
     * @return 队伍作品提交权限 DTO
     */
    TeamSubmissionAccessDTO getSubmissionAccess(Long teamId, Long userId);
}
