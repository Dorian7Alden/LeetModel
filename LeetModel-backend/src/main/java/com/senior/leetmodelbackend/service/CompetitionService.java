package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.mapper.CompetitionMapper;
import com.senior.leetmodelbackend.pojo.entity.Competition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompetitionService {

    @Autowired
    private CompetitionMapper competitionMapper;


    public List<Competition> getAllCompetitions() {
        return competitionMapper.getAllCompetition();
    }

    public List<Competition> getCompetitionsLatest3() {
        return competitionMapper.getCompetitionsLatest3();
    }
}
