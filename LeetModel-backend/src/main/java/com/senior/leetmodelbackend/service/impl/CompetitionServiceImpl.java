package com.senior.leetmodelbackend.service.impl;

import com.senior.leetmodelbackend.mapper.CompetitionMapper;
import com.senior.leetmodelbackend.pojo.Competition;
import com.senior.leetmodelbackend.service.CompetitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompetitionServiceImpl implements CompetitionService {

    @Autowired
    private CompetitionMapper competitionMapper;


    @Override
    public List<Competition> getAllCompetitions() {
        return competitionMapper.getAllCompetition();
    }

    @Override
    public List<Competition> getCompetitionsLatest3() {
        return competitionMapper.getCompetitionsLatest3();
    }
}
