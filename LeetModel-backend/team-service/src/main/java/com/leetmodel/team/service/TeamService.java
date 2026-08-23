package com.leetmodel.team.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.team.dto.AddMemberRequest;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.vo.TeamMemberVO;
import com.leetmodel.team.vo.TeamVO;

import java.util.List;

/**
 * 团队服务接口。
 */
public interface TeamService extends IService<Team> {

    /**
     * 创建团队（创建者自动成为队长）。
     *
     * @param request  创建信息
     * @param leaderId 队长用户 ID
     * @return 团队 VO
     */
    TeamVO createTeam(TeamCreateRequest request, Long leaderId);

    /**
     * 获取团队详情（含成员列表）。
     *
     * @param teamId 团队 ID
     * @return 团队 VO
     */
    TeamVO getTeamDetail(Long teamId);

    /**
     * 更新团队信息（仅队长可操作）。
     *
     * @param teamId     团队 ID
     * @param request    更新内容
     * @param operatorId 操作者 ID
     * @return 更新后的团队 VO
     */
    TeamVO updateTeam(Long teamId, TeamUpdateRequest request, Long operatorId);

    /**
     * 解散团队（标记 status=0，保留历史记录）。
     *
     * @param teamId     团队 ID
     * @param operatorId 操作者 ID
     */
    void dissolveTeam(Long teamId, Long operatorId);

    /**
     * 查询用户加入的团队列表。
     *
     * @param userId 用户 ID
     * @return 团队 VO 列表
     */
    List<TeamVO> listMyTeams(Long userId);

    /**
     * 添加成员（队长邀请）。
     *
     * @param teamId  团队 ID
     * @param request 要添加的用户
     * @param operatorId 操作者 ID
     */
    void addMember(Long teamId, AddMemberRequest request, Long operatorId);

    /**
     * 移除成员（队长踢人）。
     *
     * @param teamId     团队 ID
     * @param memberId   要移除的用户 ID
     * @param operatorId 操作者 ID
     */
    void removeMember(Long teamId, Long memberId, Long operatorId);

    /**
     * 更新团队成员的专业角色。
     *
     * @param teamId 团队 ID
     * @param memberId 成员用户 ID
     * @param request 专业角色状态
     * @param operatorId 操作者 ID
     * @return 更新后的成员视图
     */
    TeamMemberVO updateMemberRoles(Long teamId, Long memberId,
                                   MemberRolesUpdateRequest request, Long operatorId);

    /**
     * 退出团队（成员主动退出）。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     */
    void leaveTeam(Long teamId, Long userId);
}
