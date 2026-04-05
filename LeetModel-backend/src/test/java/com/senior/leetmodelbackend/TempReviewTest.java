package com.senior.leetmodelbackend;

import com.senior.leetmodelbackend.temp.Review;
import com.senior.leetmodelbackend.temp.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TempReviewTest {

    @Autowired
    private ReviewService reviewService;

    private Review getA() {
        Review review = new Review();
        review.setId(1);
        review.setTotalScore(92.6);
        review.setSubmissionId(1);
        review.setDimension1Score(93);
        review.setDimension2Score(94);
        review.setDimension3Score(95);
        review.setDimension4Score(90);
        review.setDimension5Score(88);
        review.setDimension1Weight(0.15);
        review.setDimension2Weight(0.30);
        review.setDimension3Weight(0.25);
        review.setDimension4Weight(0.15);
        review.setDimension5Weight(0.15);
        review.setDimension1Name("问题拆解与假设合理性");
        review.setDimension2Name("模型构建与方法适配性");
        review.setDimension3Name("求解实现与结果可靠性");
        review.setDimension4Name("结果分析与模型拓展性");
        review.setDimension5Name("文档规范与逻辑完整性");
        review.setDimension1Review("亮点：1. 精准识别题目核心目标、三项任务要求与刚性约束条件，将复杂的水质管控问题拆解为时序特征分析、多步动态预测、约束优化求解三个逻辑连贯的可求解子问题，与题目需求高度匹配；2. 提出的6条模型假设均具备明确的现实场景依据或水环境机理理论支撑，每条假设均标注了设立理由与作用，无冗余无效假设，完整覆盖了数据可靠性、模型边界、机理支撑、约束条件等影响模型有效性的核心要素。不足：假设中未纳入突发污染、闸门调度等极端扰动情景的边界条件，存在轻微的场景覆盖不足。");
        review.setDimension2Review("亮点：1. 模型设计与问题特征高度适配，针对非平稳时序水质数据的特征分析需求，选用STL时序分解+灰色关联分析GRA方法，有效规避了传统线性相关分析的局限性；针对多变量时序多步预测需求，选用LSTM神经网络，通过门控结构解决长时序依赖问题，完全匹配连续动态预测的核心要求；针对达标管控需求，耦合一维水质机理模型与单目标非线性规划模型，精准匹配最小减排的优化目标；2. 所有方法均有完整的理论公式与原理支撑，模型逻辑自洽，无原理性、概念性错误，方法链形成完整闭环，具备较强的工程适配性。不足：未设置多模型对比实验（如ARIMA、GRU等）来验证所选LSTM模型的优越性，方法创新性的支撑略有不足。");
        review.setDimension3Review("亮点：1. 求解算法逻辑完整正确，数据预处理、模型训练、优化求解的全流程步骤清晰，附录提供了可直接复现的完整Python代码，计算过程可复现性强；2. 求解结果完整覆盖题目要求的全部三项任务，精准完成了水质时序规律分析、关键驱动因子识别与影响量化、未来7天水质指标预测、超标场景最小减排比例求解，所有结果均符合水环境现实逻辑与题目要求；3. 规范采用题目指定的MAPE、R²指标评估预测精度，同时完成了残差检验、回代检验、误差来源分析，充分验证了结果的正确性与可靠性。不足：未开展旱季、雨季分时段的预测精度对比分析，结果可靠性的验证维度可进一步拓展。");
        review.setDimension4Review("亮点：1. 对所有计算结果均开展了贴合城市内河水质管控业务场景的深度解读与现实解释，而非单纯罗列数值，分析结论具备实际指导意义；2. 完成了核心参数的灵敏度分析，验证了模型鲁棒性，同时开展了系统的模型有效性检验，符合数学建模评审的核心要求；3. 客观全面地分析了模型的优势与局限性，提出了场景推广、维度扩展、工程应用三个明确的可落地推广与改进方向，具备较强的应用价值。不足：灵敏度分析仅选取2个核心参数，参数覆盖度不足，未开展多情景的稳健性检验，模型拓展性的验证可进一步完善。");
        review.setDimension5Review("亮点：1. 文档结构完整，严格遵循数学建模竞赛论文的规范格式，包含摘要、问题重述、模型假设、符号说明、问题分析、模型建立与求解、灵敏度分析、模型检验、模型评价与推广、参考文献、附录核心代码等全部必要模块，前后逻辑连贯通顺，形成完整的研究闭环；2. 符号说明规范统一，全文无歧义，图表均有规范的编号与标注，参考文献格式符合学术规范，引用内容标注准确，语言严谨专业，完全符合数学建模论文的学术要求。不足：文中2张核心配图的链接出现link dead、网页解析失败的问题，无法正常查看图表内容，存在格式规范上的瑕疵。");
        review.setStatus(0);
        return review;
    }

    @Test
    public void test() {

        Review review = getA();

//        reviewService.insertReview(review);

        System.out.println("All reviews: " + reviewService.getReviews());


    }


}
