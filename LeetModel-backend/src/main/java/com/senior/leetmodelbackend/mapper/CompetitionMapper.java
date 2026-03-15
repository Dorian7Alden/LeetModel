package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.Competition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CompetitionMapper {

    @Select("select * from competition")
    List<Competition> getAllCompetition();

    @Select("select * from competition where end_time > NOW() order by end_time limit 3")
    List<Competition> getCompetitionsLatest3();
}
