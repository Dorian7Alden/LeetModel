package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.Competition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CompetitionMapper {

    @Select("select * from competition")
    List<Competition> getAllCompetition();

    /**
     * 获取与当前时间最接近的 3 个比赛。理想状态下可以涵盖 3 种情况
     *  1. 已经结束的  2. 正在进行的  3. 即将开始的
     *  比赛的状态： FINISHED, ONGOING, UPCOMING
     */
    @Select("select * from competition order by abs(TIMESTAMPDIFF(SECOND, NOW(), end_time)) limit 3")
    List<Competition> getCompetitionsLatest3();
}
