package com.senior.leetmodelbackend.temp;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ReviewMapper {

    @Select("select \n" +
            "     r.id, r.total_score, r.status, \n" +
            "     r.dimension1_name, r.dimension1_score, r.dimension1_weight, r.dimension1_review, \n" +
            "     r.dimension2_name, r.dimension2_score, r.dimension2_weight, r.dimension2_review, \n" +
            "     r.dimension3_name, r.dimension3_score, r.dimension3_weight, r.dimension3_review, \n" +
            "     r.dimension4_name, r.dimension4_score, r.dimension4_weight, r.dimension4_review, \n" +
            "     r.dimension5_name, r.dimension5_score, r.dimension5_weight, r.dimension5_review, \n" +
            "     p.title \n" +
            " from \n" +
            "     temp_review r \n" +
            " left join \n" +
            "     submission s \n" +
            "     on r.submission_id = s.id \n" +
            " left join \n" +
            "     problem p \n" +
            "     on s.problem_id = p.id")
    @Results({
            @Result(property = "id", column = "id", id = true),
            @Result(property = "totalScore", column = "total_score"),
            @Result(property = "status", column = "status"),
            @Result(property = "dimension1Name", column = "dimension1_name"),
            @Result(property = "dimension1Score", column = "dimension1_score"),
            @Result(property = "dimension1Weight", column = "dimension1_weight"),
            @Result(property = "dimension1Review", column = "dimension1_review"),
            @Result(property = "dimension2Name", column = "dimension2_name"),
            @Result(property = "dimension2Score", column = "dimension2_score"),
            @Result(property = "dimension2Weight", column = "dimension2_weight"),
            @Result(property = "dimension2Review", column = "dimension2_review"),
            @Result(property = "dimension3Name", column = "dimension3_name"),
            @Result(property = "dimension3Score", column = "dimension3_score"),
            @Result(property = "dimension3Weight", column = "dimension3_weight"),
            @Result(property = "dimension3Review", column = "dimension3_review"),
            @Result(property = "dimension4Name", column = "dimension4_name"),
            @Result(property = "dimension4Score", column = "dimension4_score"),
            @Result(property = "dimension4Weight", column = "dimension4_weight"),
            @Result(property = "dimension4Review", column = "dimension4_review"),
            @Result(property = "dimension5Name", column = "dimension5_name"),
            @Result(property = "dimension5Score", column = "dimension5_score"),
            @Result(property = "dimension5Weight", column = "dimension5_weight"),
            @Result(property = "dimension5Review", column = "dimension5_review"),
            @Result(property = "title", column = "title")
    })
    public List<Review> findAll();

    /**
     * 插入一条数据
     * @param review
     */
    @Insert("INSERT INTO temp_review(total_score, submission_id, " +
            "dimension1_score, dimension2_score, dimension3_score, dimension4_score, dimension5_score, " +
            "dimension1_weight, dimension2_weight, dimension3_weight, dimension4_weight, dimension5_weight, " +
            "dimension1_name, dimension2_name, dimension3_name, dimension4_name, dimension5_name," +
            "dimension1_review, dimension2_review, dimension3_review, dimension4_review, dimension5_review," +
            "status) " +
            "VALUES(#{totalScore}, #{submissionId}, " +
            "#{dimension1Score}, #{dimension2Score}, #{dimension3Score}, #{dimension4Score}, #{dimension5Score}, " +
            "#{dimension1Weight}, #{dimension2Weight}, #{dimension3Weight}, #{dimension4Weight}, #{dimension5Weight}, " +
            "#{dimension1Name}, #{dimension2Name}, #{dimension3Name}, #{dimension4Name}, #{dimension5Name}," +
            "#{dimension1Review}, #{dimension2Review}, #{dimension3Review}, #{dimension4Review}, #{dimension5Review}," +
            "#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    public void insertReview(Review review);

    /**
     * 更新评审状态
     */
    @Update("UPDATE temp_review SET status = #{status} WHERE id = #{id}")
    public void updateStatus(@Param("id") Integer id, @Param("status") Integer status);
}
