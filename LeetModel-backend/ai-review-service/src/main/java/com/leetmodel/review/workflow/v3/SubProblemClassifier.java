package com.leetmodel.review.workflow.v3;

import com.leetmodel.common.api.dto.SubProblemCategoryDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数模小题题型分类与判定器。
 * 负责从赛题 Markdown 文本中自动识别离散小问，并依据内置启发式规则框定题型。
 */
@Component
public class SubProblemClassifier {

    private static final Map<String, SubProblemCategoryDTO> DEFAULT_TEMPLATES = Map.of(
            "OPTIMIZATION", SubProblemCategoryDTO.builder()
                    .categoryCode("OPTIMIZATION")
                    .categoryName("运筹优化类")
                    .focusAspects(List.of("决策变量物理意义", "目标函数标准表达", "约束条件完备性", "求解器收敛过程", "最优解现实可行性", "灵敏度扰动"))
                    .typicalMethods(List.of("线性规划", "整数规划", "0-1规划", "非线性规划", "多目标规划", "遗传算法", "模拟退火", "粒子群", "Cplex/Gurobi/Lingo"))
                    .expectedEvidenceTypes(List.of("FORMULA", "TABLE", "CODE", "FIGURE"))
                    .retrievalScene("SCENE_REVIEW_OPTIMIZATION")
                    .commonPitfalls(List.of("未列出三要素标准数学公式", "无代码支撑或伪造求解", "灵敏度仅文字敷衍无数据图表"))
                    .build(),

            "MECHANISM", SubProblemCategoryDTO.builder()
                    .categoryCode("MECHANISM")
                    .categoryName("机理分析类")
                    .focusAspects(List.of("物理/化学定律来源", "守恒方程推导完整性", "假设简化依据与边界", "参数标定真实性", "数值解法规范性", "误差分析"))
                    .typicalMethods(List.of("常微分方程ODE", "偏微分方程PDE", "动力系统", "元胞自动机", "蒙特卡洛模拟", "差分方程"))
                    .expectedEvidenceTypes(List.of("FORMULA", "FIGURE", "TABLE", "CODE"))
                    .retrievalScene("SCENE_REVIEW_MECHANISM")
                    .commonPitfalls(List.of("孤立拼凑公式无推导链条", "假设违背基本物理常识", "参数无标定依据拍脑袋给出"))
                    .build(),

            "PREDICTION", SubProblemCategoryDTO.builder()
                    .categoryCode("PREDICTION")
                    .categoryName("时序统计预测类")
                    .focusAspects(List.of("数据预处理与平稳性检验", "训练集测试集划分", "预测值明确报出", "误差指标量化(MAE/RMSE/MAPE)", "多模型横向对比"))
                    .typicalMethods(List.of("ARIMA", "指数平滑", "灰色预测GM(1,1)", "多元回归", "LSTM", "随机森林回归", "Prophet"))
                    .expectedEvidenceTypes(List.of("TABLE", "FIGURE", "FORMULA", "CODE"))
                    .retrievalScene("SCENE_REVIEW_PREDICTION")
                    .commonPitfalls(List.of("无具体数值报出空谈趋势", "无测试集验证自说自话", "直接拿原始带噪数据无脑拟合"))
                    .build(),

            "EVALUATION", SubProblemCategoryDTO.builder()
                    .categoryCode("EVALUATION")
                    .categoryName("综合评价类")
                    .focusAspects(List.of("评价指标体系完整性", "正向化与无量纲化处理", "主客观综合赋权依据", "一致性检验", "评价排序结果直观性"))
                    .typicalMethods(List.of("TOPSIS", "层次分析法AHP", "熵权法", "变异系数法", "模糊综合评价", "主成分分析PCA", "秩和比RSR"))
                    .expectedEvidenceTypes(List.of("TABLE", "FORMULA", "FIGURE"))
                    .retrievalScene("SCENE_REVIEW_EVALUATION")
                    .commonPitfalls(List.of("指标未做极性统一或标准化", "主观赋权随意无一致性检验", "结果无敏感性讨论"))
                    .build(),

            "DATA_MINING_STATISTICS", SubProblemCategoryDTO.builder()
                    .categoryCode("DATA_MINING_STATISTICS")
                    .categoryName("数据挖掘统计类")
                    .focusAspects(List.of("数据清洗与缺失异常处理", "特征选择与降维", "统计显著性检验p值", "分类/聚类有效性指标(AUC/F1/轮廓系数)", "可解释性"))
                    .typicalMethods(List.of("K-Means", "DBSCAN", "XGBoost", "SVM", "假设检验", "卡方检验", "ANOVA", "随机森林"))
                    .expectedEvidenceTypes(List.of("FIGURE", "TABLE", "CODE", "FORMULA"))
                    .retrievalScene("SCENE_REVIEW_STATISTICS")
                    .commonPitfalls(List.of("纯调包不解释特征意义", "样本不均衡无处理", "无独立测试集评估指标"))
                    .build(),

            "GRAPH_NETWORK", SubProblemCategoryDTO.builder()
                    .categoryCode("GRAPH_NETWORK")
                    .categoryName("图论网络流类")
                    .focusAspects(List.of("网络拓扑图定义清晰度", "节点与边权物理定义", "算法收敛性与复杂度", "瓶颈与流量平衡性", "方案可视化路线"))
                    .typicalMethods(List.of("Dijkstra", "Floyd", "Kruskal", "Prim", "最大流最小割", "动态规划", "网络单纯形法"))
                    .expectedEvidenceTypes(List.of("FIGURE", "FORMULA", "CODE", "TABLE"))
                    .retrievalScene("SCENE_REVIEW_GRAPH")
                    .commonPitfalls(List.of("网络边权无现实物理意义", "图规模过大时无复杂度分析导致算力崩溃"))
                    .build(),

            "GENERAL_MODELING", SubProblemCategoryDTO.builder()
                    .categoryCode("GENERAL_MODELING")
                    .categoryName("通用建模兜底类")
                    .focusAspects(List.of("对题意的准确理解", "前后逻辑自洽性", "模型优缺点自评客观性", "结论有据可查"))
                    .typicalMethods(List.of("数学建模综合方法", "对比分析", "定性与定量结合"))
                    .expectedEvidenceTypes(List.of("FORMULA", "TABLE", "FIGURE", "CODE"))
                    .retrievalScene("SCENE_REVIEW_GENERAL")
                    .commonPitfalls(List.of("严重跑题答非所问", "纯文科空谈无任何定量分析"))
                    .build()
    );

    private static final Map<String, List<Pattern>> TYPE_KEYWORD_PATTERNS = Map.of(
            "OPTIMIZATION", List.of(
                    Pattern.compile("(最[优佳省多大小时]|最大化|最小化|成本最低|利润最大|规划|调度|排班|选址|配置|路线)"),
                    Pattern.compile("(linear programming|integer programming|optimization|schedule)", Pattern.CASE_INSENSITIVE)
            ),
            "MECHANISM", List.of(
                    Pattern.compile("(微分方程|动力学|受力|守恒|物理过程|热传导|机理|流体|仿真模拟)"),
                    Pattern.compile("(differential equation|mechanics|dynamic system|conservation)", Pattern.CASE_INSENSITIVE)
            ),
            "PREDICTION", List.of(
                    Pattern.compile("(预测|估计|趋势|未来.*值|外推|时间序列|时序)"),
                    Pattern.compile("(predict|forecast|time series|estimate|trend)", Pattern.CASE_INSENSITIVE)
            ),
            "EVALUATION", List.of(
                    Pattern.compile("(评价|评估|排序|选优|指标体系|综合打分|权重|优劣)"),
                    Pattern.compile("(evaluation|ranking|topsis|ahp|assessment)", Pattern.CASE_INSENSITIVE)
            ),
            "DATA_MINING_STATISTICS", List.of(
                    Pattern.compile("(分类|聚类|关联|相关性|显著性|异常检测|回归分析|统计检验)"),
                    Pattern.compile("(classification|clustering|anomaly|correlation|regression)", Pattern.CASE_INSENSITIVE)
            ),
            "GRAPH_NETWORK", List.of(
                    Pattern.compile("(最短路径|网络流|最大流|生成树|图论|连通|节点|拓扑)"),
                    Pattern.compile("(shortest path|network flow|graph|topology|mst)", Pattern.CASE_INSENSITIVE)
            )
    );

    private static final Pattern QUESTION_HEADER_PATTERN = Pattern.compile(
            "(?:^|\\n)(?:#{1,4}\\s*)?(?:问题\\s*([一二三四五六七八九十0-9]+)|(?:Question|Task)\\s*([0-9]+)|\\(([0-9]+)\\)|([0-9]+)[、.．])\\s*([^\n]*)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 对单个小问文本判定其所属题型。
     */
    public SubProblemCategoryDTO classify(int questionNo, String questionText) {
        String code = matchCategoryCode(questionText);
        SubProblemCategoryDTO template = DEFAULT_TEMPLATES.getOrDefault(code, DEFAULT_TEMPLATES.get("GENERAL_MODELING"));
        return SubProblemCategoryDTO.builder()
                .questionNo(questionNo)
                .categoryCode(template.getCategoryCode())
                .categoryName(template.getCategoryName())
                .focusAspects(template.getFocusAspects())
                .typicalMethods(template.getTypicalMethods())
                .expectedEvidenceTypes(template.getExpectedEvidenceTypes())
                .retrievalScene(template.getRetrievalScene())
                .commonPitfalls(template.getCommonPitfalls())
                .build();
    }

    /**
     * 从赛题完整 Markdown 中提取所有小问并完成判定。
     */
    public List<SubProblemCategoryDTO> parseAndClassifyQuestions(String problemMarkdown) {
        if (problemMarkdown == null || problemMarkdown.isBlank()) {
            return List.of(classify(1, ""));
        }
        Map<Integer, String> questionMap = splitQuestions(problemMarkdown);
        if (questionMap.isEmpty()) {
            return List.of(classify(1, problemMarkdown));
        }
        List<SubProblemCategoryDTO> result = new ArrayList<>();
        questionMap.forEach((no, text) -> result.add(classify(no, text)));
        return result;
    }

    public Map<Integer, String> splitQuestions(String problemMarkdown) {
        Map<Integer, String> questions = new LinkedHashMap<>();
        Matcher matcher = QUESTION_HEADER_PATTERN.matcher(problemMarkdown);
        List<int[]> matches = new ArrayList<>();
        List<Integer> questionNumbers = new ArrayList<>();

        int detectedIndex = 1;
        while (matcher.find()) {
            int qNo = parseQuestionNo(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), detectedIndex);
            matches.add(new int[]{matcher.start(), matcher.end()});
            questionNumbers.add(qNo);
            detectedIndex++;
        }

        for (int i = 0; i < matches.size(); i++) {
            int start = matches.get(i)[0];
            int end = (i + 1 < matches.size()) ? matches.get(i + 1)[0] : problemMarkdown.length();
            String content = problemMarkdown.substring(start, end).trim();
            questions.put(questionNumbers.get(i), content);
        }
        return questions;
    }

    private String matchCategoryCode(String text) {
        if (text == null || text.isBlank()) return "GENERAL_MODELING";
        for (Map.Entry<String, List<Pattern>> entry : TYPE_KEYWORD_PATTERNS.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(text).find()) {
                    return entry.getKey();
                }
            }
        }
        return "GENERAL_MODELING";
    }

    private int parseQuestionNo(String g1, String g2, String g3, String g4, int fallback) {
        if (g1 != null) {
            return parseChineseNumber(g1.trim(), fallback);
        }
        String raw = g2 != null ? g2 : (g3 != null ? g3 : g4);
        if (raw != null) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private int parseChineseNumber(String zh, int fallback) {
        return switch (zh) {
            case "一", "1" -> 1;
            case "二", "2" -> 2;
            case "三", "3" -> 3;
            case "四", "4" -> 4;
            case "五", "5" -> 5;
            case "六", "6" -> 6;
            case "七", "7" -> 7;
            case "八", "8" -> 8;
            case "九", "9" -> 9;
            case "十", "10" -> 10;
            default -> fallback;
        };
    }
}
