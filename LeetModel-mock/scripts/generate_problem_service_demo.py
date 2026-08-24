from __future__ import annotations

from pathlib import Path

TAGS = [
    (6001, "环境生态", "BACKGROUND_DOMAIN"), (6002, "交通物流", "BACKGROUND_DOMAIN"),
    (6003, "经济金融", "BACKGROUND_DOMAIN"), (6004, "公共健康", "BACKGROUND_DOMAIN"),
    (6101, "预测", "PROBLEM_TYPE"), (6102, "评价", "PROBLEM_TYPE"),
    (6103, "优化", "PROBLEM_TYPE"), (6201, "回归分析", "MODEL_ALGORITHM"),
    (6202, "层次分析法", "MODEL_ALGORITHM"), (6203, "线性规划", "MODEL_ALGORITHM"),
    (6204, "蒙特卡洛模拟", "MODEL_ALGORITHM"),
]

PROBLEMS = [
    (51001, "城市共享单车潮汐调度", 2, 2024, "ZH", 2, 86.50, 1, "# 城市共享单车潮汐调度\n\n建立需求预测与车辆调度模型，降低高峰期供需失衡。", [6002, 6103, 6203]),
    (51002, "湖泊水质变化预测", 2, 2023, "ZH", 2, 78.20, 1, "# 湖泊水质变化预测\n\n根据监测数据预测主要水质指标，并分析关键影响因素。", [6001, 6101, 6201]),
    (51003, "社区医疗资源评价", 2, 2022, "ZH", 1, 72.00, 1, "# 社区医疗资源评价\n\n构建社区医疗服务能力评价体系。", [6004, 6102, 6202]),
    (51004, "Urban Delivery Route Planning", 1, 2024, "EN", 3, 91.30, 1, "# Urban Delivery Route Planning\n\nDesign a robust routing plan under uncertain demand.", [6002, 6103, 6204]),
    (51005, "Carbon Market Price Forecasting", 1, 2023, "EN", 3, 88.80, 1, "# Carbon Market Price Forecasting\n\nForecast carbon allowance prices and quantify uncertainty.", [6003, 6101, 6201]),
    (51006, "生态保护区承载力评价", 3, 2026, "ZH", 2, 82.40, 1, "# 生态保护区承载力评价\n\n评价旅游活动对生态保护区承载力的影响。", [6001, 6102, 6202]),
    (51007, "应急物资配送优化", 3, 2026, "ZH", 3, 93.10, 1, "# 应急物资配送优化\n\n在道路通行能力动态变化时制定配送方案。", [6002, 6103, 6203]),
    (51008, "流感就诊人数预测", 3, 2025, "ZH", 1, 76.60, 1, "# 流感就诊人数预测\n\n根据历史就诊与气象数据预测短期就诊需求。", [6004, 6101, 6201]),
    (51009, "未发布的金融风险评价", 3, 2026, "ZH", 2, 80.00, 0, "# 未发布测试题\n\n用于验证公开题库不会返回草稿。", [6003, 6102, 6202]),
]

ROOT = Path(__file__).resolve().parent.parent.parent
OUTPUT_PATH = ROOT / "LeetModel-backend/problem-service/src/main/resources/db/migration/V6__insert_mock_problems.sql"

def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")

def main() -> None:
    tag_values = ",\n".join(f"({i}, '{sql_escape(n)}', '{t}')" for i, n, t in TAGS)
    problem_values = ",\n".join(
        f"({i}, '{sql_escape(title)}', '{sql_escape(md)}', {contest}, {year}, '{lang}', 4320, {difficulty}, {score:.2f}, {status}, 1, 0)"
        for i, title, contest, year, lang, difficulty, score, status, md, _ in PROBLEMS
    )
    relations, relation_id = [], 53001
    for problem in PROBLEMS:
        for tag_id in problem[-1]:
            relations.append(f"({relation_id}, {problem[0]}, {tag_id})")
            relation_id += 1
    sql = (
        "-- 由 LeetModel-mock/scripts/generate_problem_service_demo.py 生成\n"
        "-- 覆盖赛事、年份、语言、难度、三类标签组合、分数及草稿隔离场景\n\n"
        "INSERT INTO `tag` (`id`, `name`, `type`) VALUES\n" + tag_values
        + "\nON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `type` = VALUES(`type`);\n\n"
        "INSERT INTO `problem` (`id`, `title`, `content_markdown`, `contest_id`, `year`, `statement_language`, `duration_minutes`, `difficulty`, `average_score`, `status`, `creator_id`, `deleted`) VALUES\n"
        + problem_values
        + "\nON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `content_markdown` = VALUES(`content_markdown`), `contest_id` = VALUES(`contest_id`), `year` = VALUES(`year`), `statement_language` = VALUES(`statement_language`), `duration_minutes` = VALUES(`duration_minutes`), `difficulty` = VALUES(`difficulty`), `average_score` = VALUES(`average_score`), `status` = VALUES(`status`), `deleted` = 0;\n\n"
        "INSERT INTO `problem_tag` (`id`, `problem_id`, `tag_id`) VALUES\n" + ",\n".join(relations)
        + "\nON DUPLICATE KEY UPDATE `tag_id` = VALUES(`tag_id`);\n"
    )
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(sql, encoding="utf-8")
    print(f"已生成 {len(TAGS)} 个标签、{len(PROBLEMS)} 道题目和 {len(relations)} 条题目标签关系")
    print(f"输出文件：{OUTPUT_PATH}")

if __name__ == "__main__":
    main()
