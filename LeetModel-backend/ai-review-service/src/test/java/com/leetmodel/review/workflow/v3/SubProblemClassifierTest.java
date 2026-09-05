package com.leetmodel.review.workflow.v3;

import com.leetmodel.common.api.dto.SubProblemCategoryDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubProblemClassifierTest {

    private final SubProblemClassifier classifier = new SubProblemClassifier();

    @Test
    void shouldClassifyOptimizationQuestion() {
        String text = "针对问题一，建立整数规划模型，以总运输成本最小化为目标，合理安排车队的配送路线与调度方案。";
        SubProblemCategoryDTO result = classifier.classify(1, text);

        assertThat(result.getQuestionNo()).isEqualTo(1);
        assertThat(result.getCategoryCode()).isEqualTo("OPTIMIZATION");
        assertThat(result.getCategoryName()).isEqualTo("运筹优化类");
        assertThat(result.getTypicalMethods()).contains("整数规划");
    }

    @Test
    void shouldClassifyMechanismQuestion() {
        String text = "建立微分方程动力学模型，分析热传导过程中介质受力与能量守恒规律，模拟温度变化轨迹。";
        SubProblemCategoryDTO result = classifier.classify(2, text);

        assertThat(result.getQuestionNo()).isEqualTo(2);
        assertThat(result.getCategoryCode()).isEqualTo("MECHANISM");
        assertThat(result.getCategoryName()).isEqualTo("机理分析类");
    }

    @Test
    void shouldClassifyPredictionQuestion() {
        String text = "根据附件提供的历史时间序列数据，预测未来30天各商超品类的销售量趋势。";
        SubProblemCategoryDTO result = classifier.classify(3, text);

        assertThat(result.getQuestionNo()).isEqualTo(3);
        assertThat(result.getCategoryCode()).isEqualTo("PREDICTION");
        assertThat(result.getCategoryName()).isEqualTo("时序统计预测类");
    }

    @Test
    void shouldSplitAndClassifyMultipleQuestionsFromMarkdown() {
        String markdown = """
                # 赛题背景与任务
                
                ### 问题一
                请建立数学模型，预测未来一年的市场需求量。
                
                ### 问题二
                在问题一预测的基础上，建立生产计划的线性规划模型，实现总利润最大化。
                
                ### 问题三
                构建综合评价指标体系，对不同供应商的供货稳定性进行排序选优。
                """;

        List<SubProblemCategoryDTO> list = classifier.parseAndClassifyQuestions(markdown);

        assertThat(list).hasSize(3);
        assertThat(list.get(0).getQuestionNo()).isEqualTo(1);
        assertThat(list.get(0).getCategoryCode()).isEqualTo("PREDICTION");

        assertThat(list.get(1).getQuestionNo()).isEqualTo(2);
        assertThat(list.get(1).getCategoryCode()).isEqualTo("OPTIMIZATION");

        assertThat(list.get(2).getQuestionNo()).isEqualTo(3);
        assertThat(list.get(2).getCategoryCode()).isEqualTo("EVALUATION");
    }
}
