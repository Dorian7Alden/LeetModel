package com.leetmodel.team.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.team.dto.AddMemberRequest;
import com.leetmodel.team.dto.JoinApplicationCreateRequest;
import com.leetmodel.team.dto.JoinApplicationReviewRequest;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.RecruitmentUpdateRequest;
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

    TeamVO createTeam(TeamCreateRequest request, Long leaderId);

    PageResult<TeamVO> pagePublicTeams(TeamPublicPageQuery query, Long currentUserId);

    TeamVO getTeamDetail(Long teamId, Long currentUserId);

    TeamVO getTeamDetail(Long teamId);

    TeamVO updateTeam(Long teamId, TeamUpdateRequest request, Long operatorId);

    TeamVO updateRecruitment(Long teamId, RecruitmentUpdateRequest request, Long operatorId);

    void dissolveTeam(Long teamId, Long operatorId);

    List<TeamVO> listMyTeams(Long userId, Integer status);

    List<TeamVO> listMyTeams(Long userId);

    void addMember(Long teamId, AddMemberRequest request, Long operatorId);

    void removeMember(Long teamId, Long memberId, Long operatorId);

    TeamMemberVO updateMemberRoles(Long teamId, Long memberId,
                                   MemberRolesUpdateRequest request, Long operatorId);

    void leaveTeam(Long teamId, Long userId);

    JoinApplicationVO submitApplication(Long teamId, JoinApplicationCreateRequest request,
                                        Long applicantId);

    void cancelMyApplication(Long teamId, Long applicantId);

    List<JoinApplicationVO> listApplications(Long teamId, Long operatorId);

    JoinApplicationVO reviewApplication(Long teamId, Long applicationId,
                                        JoinApplicationReviewRequest request, Long operatorId);
}
