package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.pojo.entity.Competition;

import java.util.List;

public interface CompetitionService {

    List<Competition> getAllCompetitions();

    List<Competition> getCompetitionsLatest3();
}
