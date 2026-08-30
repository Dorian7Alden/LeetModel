#!/usr/bin/env python3
"""
LeetModel 演示数据生成脚本：生成提交、评审与排行榜全量演示数据。

输出目标：
1. user-service:       V6__insert_more_mock_users.sql
2. team-service:       V11__insert_leaderboard_demo_teams.sql
3. submission-service: V2__insert_mock_submissions.sql
4. ai-review-service:  V3__insert_mock_reviews.sql
5. ranking-service:    V2__insert_mock_rankings.sql
"""
from __future__ import annotations

import json
import random
import sys
from datetime import datetime, timedelta
from pathlib import Path

# 将项目根目录加入 sys.path
ROOT = Path(__file__).resolve().parent.parent.parent
MOCK_ROOT = ROOT / "LeetModel-mock"
sys.path.insert(0, str(MOCK_ROOT))

from app.generators import (
    DEFAULT_SEED,
    gen_avatars,
    gen_chinese_names,
    gen_emails,
    gen_usernames,
)

DEFAULT_PASSWORD = "123456"
DEFAULT_PASSWORD_HASH = "$2b$12$RPvgstWnQDY.36RUN2UiH.ty5wPCDx6zPxghxfHz4qC3r5w1vryJW"
SEED = 20260830

# 8 道已发布的练习题目定义与领域关键词
PROBLEMS = [
    {
        "id": 51001,
        "title": "城市共享单车潮汐调度",
        "code": 1,
        "lang": "ZH",
        "domain": "潮汐调度",
        "file_prefix": "Bicycle_Tidal_Rebalancing",
        "keywords": ["时空网格", "混合整数线性规划", "动态供需预测", "启发式调度算法", "重平衡成本优化"],
    },
    {
        "id": 51002,
        "title": "湖泊水质变化预测",
        "code": 2,
        "lang": "ZH",
        "domain": "水质预测",
        "file_prefix": "Lake_Water_Quality_Prediction",
        "keywords": ["溶解氧时序回归", "高锰酸盐指数分析", "空间反距离插值", "随机森林回归", "污染溯源分析"],
    },
    {
        "id": 51003,
        "title": "社区医疗资源评价",
        "code": 3,
        "lang": "ZH",
        "domain": "医疗评价",
        "file_prefix": "Community_Healthcare_Evaluation",
        "keywords": ["AHP层次分析", "TOPSIS综合评价", "医疗服务可达性", "双向转诊承载力", "基尼系数均衡度"],
    },
    {
        "id": 51004,
        "title": "Urban Delivery Route Planning",
        "code": 4,
        "lang": "EN",
        "domain": "配送路径",
        "file_prefix": "Urban_Delivery_Route_Planning",
        "keywords": ["MDVRPTW Model", "Adaptive Large Neighborhood Search", "Dynamic Traffic Constraints", "Genetic Algorithm", "Carbon Emission Factor"],
    },
    {
        "id": 51005,
        "title": "Carbon Market Price Forecasting",
        "code": 5,
        "lang": "EN",
        "domain": "碳价预测",
        "file_prefix": "Carbon_Market_Price_Forecasting",
        "keywords": ["ARIMA-GARCH Volatility", "LSTM-Attention Network", "Policy Shock Analysis", "Carbon Allowance CEA", "Quantile Regression"],
    },
    {
        "id": 51006,
        "title": "生态保护区承载力评价",
        "code": 6,
        "lang": "ZH",
        "domain": "生态承载",
        "file_prefix": "Ecosystem_Carrying_Capacity",
        "keywords": ["旅游生态足迹", "环境承载力预警阈值", "主成分降维分析", "系统动力学仿真", "生态红线约束"],
    },
    {
        "id": 51007,
        "title": "应急物资配送优化",
        "code": 7,
        "lang": "ZH",
        "domain": "应急配送",
        "file_prefix": "Emergency_Logistics_Optimization",
        "keywords": ["时变受损路网", "多目标鲁棒优化", "应急物资时效惩罚", "NSGA-II算法", "次优路径备用机制"],
    },
    {
        "id": 51008,
        "title": "流感就诊人数预测",
        "code": 8,
        "lang": "ZH",
        "domain": "流感预测",
        "file_prefix": "Influenza_Outpatient_Forecasting",
        "keywords": ["SEIR动力学模型", "多源气象特征工程", "Prophet时序分解", "门诊就诊峰值预警", "交叉滞后相关分析"],
    },
]

# 队伍命名词库
TEAM_PREFIXES = [
    "矩阵", "求索", "极值", "泰勒", "梯度", "图灵", "纳什", "高斯", "欧拉", "拉格朗日",
    "蒙特卡洛", "凸优化", "时序", "聚类", "马尔可夫", "贝叶斯", "灵敏度", "拟合", "启发式",
    "超平面", "谱聚类", "分支定界", "动态规划", "退火", "粒子群", "深度洞察", "多目标", "前沿建模"
]
TEAM_SUFFIXES = [
    "先锋队", "研究组", "攻坚组", "突击队", "研析队", "实战队", "冲刺小队", "探索小组",
    "精算队", "决策实验室", "分析队", "建模队", "智囊组", "先导小队", "创新队"
]

def sql_escape(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def generate_review_json(score: float, problem: dict, team_name: str) -> str:
    """生成与 BasicReviewV1Output 结构一致的结构化评审结果 JSON"""
    dim_base = score / 100.0
    dim_ar = round(min(100.0, max(30.0, score + random.uniform(-4.0, 3.5))), 1)
    dim_mc = round(min(100.0, max(30.0, score + random.uniform(-5.0, 4.0))), 1)
    dim_rc = round(min(100.0, max(30.0, score + random.uniform(-3.5, 3.5))), 1)
    dim_ec = round(min(100.0, max(30.0, score + random.uniform(-4.0, 4.0))), 1)

    kw1 = random.choice(problem["keywords"])
    kw2 = random.choice([k for k in problem["keywords"] if k != kw1])

    if score >= 90.0:
        summary = f"《{team_name}》针对【{problem['title']}】的建模与求解表现出极高的专业水准，全面运用了{kw1}，论证严密且数值实验充分。"
        strengths = [
            f"模型构建深入，核心采用{kw1}并对边界条件做出了细致的数学刻画",
            f"实验与算例分析详实，灵敏度检验与鲁棒性验证充分",
            "论文结构工整，图表规范美观，推导过程逻辑清晰",
        ]
        weaknesses = ["在极端边界扰动下的计算开销分析还可进一步精简阐述"]
        suggestions = [f"可进一步探讨{kw2}在大规模实时系统中的在线动态微调方案"]
    elif score >= 80.0:
        summary = f"论文整体结构完整，对【{problem['title']}】关键机理分析透彻，构建的{kw1}模型具有较强的实用性与可落地性。"
        strengths = [
            f"问题抽象合理，对{kw1}的参数标定过程叙述清楚",
            "算例对比直观，有效验证了优化方案相比基准策略的提升",
        ]
        weaknesses = ["部分模型假设对实际复杂环境的约束简化略多"]
        suggestions = [f"建议结合{kw2}补充更全面的误差容忍度分析"]
    elif score >= 70.0:
        summary = f"完成了【{problem['title']}】的基本建模要求，能运用{kw1}完成主要指标的计算与分析，具备良好的工程实践性。"
        strengths = [
            "完成了完整的端到端建模、求解与结果讨论流程",
            "提供了必要的算例输入与输出对比数据",
        ]
        weaknesses = [
            "算法收敛性分析不够深入，缺少更充分的对照实验",
            "部分图表排版略显紧凑，标注信息可更规范",
        ]
        suggestions = [f"建议补充与经典基准模型的对照实验，并引入{kw2}进行交叉检验"]
    elif score >= 60.0:
        summary = f"基本涵盖了【{problem['title']}】的核心问题，但在算法设计与结果分析环节存在一定局限性。"
        strengths = ["建立了基础的数学模型，完成了核心变量的定义与初步求解"]
        weaknesses = [
            f"模型对{kw1}的实现较为基础，关键参数缺乏敏感性讨论",
            "结论部分分析偏简略，缺少对实际业务场景落地的深入探讨",
        ]
        suggestions = ["建议规范公式排版与变量说明表，加强算法复杂度与误差分布的定量分析"]
    else:
        summary = f"对【{problem['title']}】进行了初步探索，但整体数学严谨性不足，核心机理与推导需要大幅修正。"
        strengths = ["初步尝试了问题的数学形式化表达"]
        weaknesses = [
            "关键假设与问题实际物理背景存在脱节",
            "求解算法未完全收敛，算例数据缺乏充分的说服力",
            "论文排版与学术规范有较大改进空间",
        ]
        suggestions = ["建议重新梳理问题假设与目标函数，遵循标准论文规范重构实验与图表"]

    output = {
        "score": round(score, 1),
        "summary": summary,
        "dimensions": {
            "assumptionRationality": {
                "score": dim_ar,
                "comment": f"假设合理性评分 {dim_ar}。主要假设与问题背景的契合度符合当前分段水平。",
            },
            "modelCreativity": {
                "score": dim_mc,
                "comment": f"模型创新性评分 {dim_mc}。在{kw1}的设计与求解机制上有相应展现。",
            },
            "resultCorrectness": {
                "score": dim_rc,
                "comment": f"结果正确性评分 {dim_rc}。算例计算与数值验证基本自洽。",
            },
            "expressionClarity": {
                "score": dim_ec,
                "comment": f"表达清晰度评分 {dim_ec}。图表、公式推导及学术规范整体达标。",
            },
        },
        "strengths": strengths,
        "weaknesses": weaknesses,
        "suggestions": suggestions,
    }
    return json.dumps(output, ensure_ascii=False)


def main():
    random.seed(SEED)
    rng = random.Random(SEED)

    # 预设的通用密码哈希
    # Flyway migrations are immutable after application. Reuse a fixed BCrypt
    # hash so regenerating deterministic demo SQL does not change its checksum.
    bcrypt_hash = DEFAULT_PASSWORD_HASH

    # 1. 规划题目分配与分数阶梯（确保每个题目覆盖 90-100, 80-89, 70-79, 60-69, 0-59 五个分段）
    # 分数规划（每个题目配置 8 ~ 14 支队伍）
    problem_plans = [
        {
            "problem": PROBLEMS[0],  # 51001 城市共享单车潮汐调度
            "scores": [95.5, 93.0, 91.2, 88.5, 86.0, 84.2, 81.5, 78.5, 76.0, 73.5, 71.0, 67.5, 63.8, 54.0],
        },
        {
            "problem": PROBLEMS[1],  # 51002 湖泊水质变化预测
            "scores": [94.0, 91.5, 87.5, 85.0, 82.0, 77.0, 74.5, 68.0, 63.5, 52.0],
        },
        {
            "problem": PROBLEMS[2],  # 51003 社区医疗资源评价
            "scores": [92.5, 89.0, 85.5, 81.0, 76.5, 72.0, 66.0, 58.0],
        },
        {
            "problem": PROBLEMS[3],  # 51004 Urban Delivery Route Planning
            "scores": [96.5, 93.0, 90.5, 88.0, 85.2, 82.5, 78.0, 75.0, 71.5, 67.0, 62.5, 55.5],
        },
        {
            "problem": PROBLEMS[4],  # 51005 Carbon Market Price Forecasting
            "scores": [95.0, 90.8, 87.0, 83.5, 79.0, 74.0, 66.5, 57.0],
        },
        {
            "problem": PROBLEMS[5],  # 51006 生态保护区承载力评价
            "scores": [93.5, 91.0, 88.0, 84.5, 81.0, 77.5, 73.0, 68.5, 64.0, 53.5],
        },
        {
            "problem": PROBLEMS[6],  # 51007 应急物资配送优化 (最高热度)
            "scores": [97.5, 95.0, 92.0, 89.5, 87.5, 85.0, 83.0, 80.5, 78.0, 75.5, 73.0, 70.5, 67.5, 63.0, 59.0, 48.0],
        },
        {
            "problem": PROBLEMS[7],  # 51008 流感就诊人数预测
            "scores": [94.5, 91.5, 88.5, 85.0, 82.0, 78.0, 74.5, 69.0, 65.0, 56.0],
        },
    ]

    total_teams_count = sum(len(p["scores"]) for p in problem_plans)
    print(f"规划生成 {len(problem_plans)} 道题目的 {total_teams_count} 支队伍及其多版本提交与评审...")

    # 2. 生成新增用户（从 1013 开始）
    user_id_start = 1013
    user_names = gen_chinese_names(total_teams_count * 2, seed=SEED)
    usernames = gen_usernames(total_teams_count * 2, seed=SEED)
    emails = gen_emails(total_teams_count * 2, seed=SEED)
    avatars = gen_avatars(total_teams_count * 2, style="micah", seed=SEED)

    users_sql = []
    user_roles_sql = []
    current_user_id = user_id_start
    user_role_id = 13

    for i in range(total_teams_count * 2):
        u_id = current_user_id + i
        u_name = usernames[i]
        n_name = user_names[i]
        mail = emails[i]
        ava = avatars[i]
        users_sql.append(
            f"({u_id}, '{sql_escape(u_name)[1:-1]}', '{bcrypt_hash}', "
            f"'{sql_escape(n_name)[1:-1]}', '{sql_escape(mail)[1:-1]}', '{ava}', "
            f"1, NOW(), NOW(), 0)"
        )
        user_roles_sql.append(f"({user_role_id}, {u_id}, 3)")
        user_role_id += 1

    # 3. 组织各微服务实体数据
    teams_sql = []
    team_members_sql = []
    submissions_sql = []
    submission_locks_sql = []
    review_tasks_sql = []
    review_results_sql = []
    ranking_snapshots_sql = []

    team_id_seq = 2006
    member_id_seq = 3100
    submission_id_seq = 7001
    lock_id_seq = 8001
    review_task_id_seq = 9001
    review_result_id_seq = 10001
    ranking_snapshot_id_seq = 11001

    base_time = datetime(2026, 8, 18, 9, 0, 0)
    user_cursor = 0

    # 用于确保队伍名称不重复
    used_team_names = {"星河建模队", "应急优化实战队", "潮汐调度复盘队", "湖泊研究历史队", "生态承载力小组"}

    for plan in problem_plans:
        problem = plan["problem"]
        scores = plan["scores"]
        problem_id = problem["id"]
        batch_id = f"batch-demo-{problem_id}-{SEED}"
        computed_at = datetime(2026, 8, 29, 12, 0, 0)

        # 队伍数据和排名数据暂存
        ranked_team_drafts = []

        for score_idx, score in enumerate(scores):
            # 生成队伍名称
            while True:
                p_name = rng.choice(TEAM_PREFIXES)
                s_name = rng.choice(TEAM_SUFFIXES)
                t_name = f"{p_name}{problem['domain']}{s_name}"
                if t_name not in used_team_names:
                    used_team_names.add(t_name)
                    break

            team_id = team_id_seq
            team_id_seq += 1

            leader_id = user_id_start + user_cursor
            user_cursor += 1
            mate_id = user_id_start + user_cursor
            user_cursor += 1

            t_desc = f"专注于【{problem['title']}】的算法设计、仿真实验与报告撰写。"
            start_dt = base_time + timedelta(days=rng.randint(0, 3), hours=rng.randint(0, 6))
            end_dt = start_dt + timedelta(days=3, hours=rng.randint(-2, 4))
            deadline_dt = start_dt + timedelta(days=3)

            # 队伍记录
            teams_sql.append(
                f"({team_id}, {sql_escape(t_name)}, {sql_escape(t_desc)}, {leader_id}, {problem_id}, 1, "
                f"'ENDED', {sql_escape(start_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(deadline_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(end_dt.strftime('%Y-%m-%d %H:%M:%S'))}, NOW(), NOW(), 0)"
            )

            # 成员记录
            team_members_sql.append(
                f"({member_id_seq}, {team_id}, {leader_id}, 'leader', 1, 1, 0, 1, {sql_escape(start_dt.strftime('%Y-%m-%d %H:%M:%S'))}, NOW())"
            )
            member_id_seq += 1
            team_members_sql.append(
                f"({member_id_seq}, {team_id}, {mate_id}, 'member', 0, 0, 1, 0, {sql_escape(start_dt.strftime('%Y-%m-%d %H:%M:%S'))}, NOW())"
            )
            member_id_seq += 1

            # 版本数量（1 到 3 个版本）
            # 高分作品往往迭代 2-3 次，中低分可能 1-2 次
            ver_count = 3 if score >= 88.0 else (2 if score >= 70.0 else rng.choice([1, 2]))

            final_sub_id = None
            final_task_id = None
            final_sub_time = None
            final_review_time = None

            for v in range(1, ver_count + 1):
                sub_id = submission_id_seq
                submission_id_seq += 1
                task_id = review_task_id_seq
                review_task_id_seq += 1

                sub_time = start_dt + timedelta(hours=20 * v + rng.randint(1, 5))
                review_finish_time = sub_time + timedelta(minutes=rng.randint(8, 25))

                filename = f"{problem['file_prefix']}_Team{team_id}_V{v}.pdf"
                obj_name = f"submissions/{team_id}/{problem['file_prefix']}_v{v}_{sub_id}.pdf"
                file_size = rng.randint(1800000, 6500000)

                # 提交记录
                submissions_sql.append(
                    f"({sub_id}, {team_id}, {problem_id}, {leader_id}, {v}, "
                    f"{sql_escape(filename)}, {sql_escape(obj_name)}, {file_size}, 'SUCCESS', "
                    f"{sql_escape(sub_time.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(sub_time.strftime('%Y-%m-%d %H:%M:%S'))}, 0)"
                )

                # 历史版本的评审分数略低于终版
                if v == ver_count:
                    v_score = score
                    final_sub_id = sub_id
                    final_task_id = task_id
                    final_sub_time = sub_time
                    final_review_time = review_finish_time
                else:
                    v_score = round(max(35.0, score - (ver_count - v) * rng.uniform(4.0, 9.0)), 1)

                # 评审任务
                review_tasks_sql.append(
                    f"({task_id}, {sub_id}, 1, {team_id}, {problem_id}, 'COMPLETED', 'BASIC_REVIEW_V1', "
                    f"NULL, 0, 1, {sql_escape(sub_time.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(sub_time.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(review_finish_time.strftime('%Y-%m-%d %H:%M:%S'))}, NULL, "
                    f"{sql_escape(sub_time.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(review_finish_time.strftime('%Y-%m-%d %H:%M:%S'))}, 0)"
                )

                # 评审结果
                review_json = generate_review_json(v_score, problem, t_name)
                res_id = review_result_id_seq
                review_result_id_seq += 1

                review_results_sql.append(
                    f"({res_id}, {task_id}, {sub_id}, {team_id}, {problem_id}, 'BASIC_REVIEW_V1', "
                    f"{v_score:.2f}, {sql_escape(review_json)}, 'deepseek-chat', "
                    f"{sql_escape(f'call-mock-{res_id}')}, "
                    f"{sql_escape(review_finish_time.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(review_finish_time.strftime('%Y-%m-%d %H:%M:%S'))}, 0)"
                )

            # 锁定最终提交
            lock_id = lock_id_seq
            lock_id_seq += 1
            submission_locks_sql.append(
                f"({lock_id}, {team_id}, {final_sub_id}, {sql_escape(end_dt.strftime('%Y-%m-%d %H:%M:%S'))})"
            )

            ranked_team_drafts.append({
                "team_id": team_id,
                "team_name": t_name,
                "submission_id": final_sub_id,
                "review_task_id": final_task_id,
                "score": score,
                "submitted_at": final_sub_time,
                "review_finished_at": final_review_time,
            })

        # 4. 计算排行榜（按得分倒序、提交时间升序、队伍ID升序）
        ranked_team_drafts.sort(key=lambda x: (-x["score"], x["submitted_at"], x["team_id"]))

        current_rank = 0
        prev_score = None
        for r_idx, draft in enumerate(ranked_team_drafts):
            if prev_score is None or abs(prev_score - draft["score"]) > 1e-6:
                current_rank = r_idx + 1
                prev_score = draft["score"]

            snap_id = ranking_snapshot_id_seq
            ranking_snapshot_id_seq += 1

            ranking_snapshots_sql.append(
                f"({snap_id}, {sql_escape(batch_id)}, {problem_id}, {draft['team_id']}, "
                f"{sql_escape(draft['team_name'])}, {draft['submission_id']}, {draft['review_task_id']}, "
                f"'BASIC_REVIEW_V1', {draft['score']:.2f}, {current_rank}, "
                f"{sql_escape(draft['submitted_at'].strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(draft['review_finished_at'].strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(computed_at.strftime('%Y-%m-%d %H:%M:%S'))}, 1, "
                f"{sql_escape(computed_at.strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(computed_at.strftime('%Y-%m-%d %H:%M:%S'))}, 0)"
            )

    # 5. 生成各个服务的 Flyway Migration SQL 文件

    # (A) user-service: V6__insert_more_mock_users.sql
    user_out = ROOT / "LeetModel-backend/user-service/src/main/resources/db/migration/V6__insert_more_mock_users.sql"
    user_sql_text = (
        "-- ==================== 扩展演示用户数据 ====================\n"
        "-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成\n"
        f"-- 统一密码：{DEFAULT_PASSWORD}，角色：user=3\n\n"
        "INSERT INTO user (id, username, password, nickname, email, avatar_path, status, create_time, update_time, deleted)\n"
        "VALUES\n"
        + ",\n".join(users_sql)
        + "\nON DUPLICATE KEY UPDATE username = VALUES(username);\n\n"
        "INSERT INTO user_role (id, user_id, role_id)\n"
        "VALUES\n"
        + ",\n".join(user_roles_sql)
        + "\nON DUPLICATE KEY UPDATE role_id = VALUES(role_id);\n"
    )
    user_out.parent.mkdir(parents=True, exist_ok=True)
    user_out.write_text(user_sql_text, encoding="utf-8")
    print(f"[OK] 生成 user-service 迁移: {user_out} (新增 {len(users_sql)} 个用户)")

    # (B) team-service: V11__insert_leaderboard_demo_teams.sql
    team_out = ROOT / "LeetModel-backend/team-service/src/main/resources/db/migration/V11__insert_leaderboard_demo_teams.sql"
    team_sql_text = (
        "-- ==================== 扩展排行榜队伍与成员数据 ====================\n"
        "-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成\n"
        "-- 覆盖 8 道已发布题目的多队伍建模场景\n\n"
        "DELETE FROM `team_member` WHERE `team_id` BETWEEN 2006 AND 2099;\n"
        "DELETE FROM `team` WHERE `id` BETWEEN 2006 AND 2099;\n\n"
        "INSERT INTO `team`\n"
        "(`id`, `name`, `description`, `leader_id`, `problem_id`, `status`, `practice_status`, `started_at`, `deadline_at`, `ended_at`, `create_time`, `update_time`, `deleted`)\n"
        "VALUES\n"
        + ",\n".join(teams_sql) + ";\n\n"
        "INSERT INTO `team_member`\n"
        "(`id`, `team_id`, `user_id`, `role`, `modeler`, `programmer`, `writer`, `can_submit`, `joined_at`, `create_time`)\n"
        "VALUES\n"
        + ",\n".join(team_members_sql) + ";\n"
    )
    team_out.parent.mkdir(parents=True, exist_ok=True)
    team_out.write_text(team_sql_text, encoding="utf-8")
    print(f"[OK] 生成 team-service 迁移: {team_out} (新增 {len(teams_sql)} 支队伍)")

    # (C) submission-service: V2__insert_mock_submissions.sql
    sub_out = ROOT / "LeetModel-backend/submission-service/src/main/resources/db/migration/V2__insert_mock_submissions.sql"
    sub_sql_text = (
        "-- ==================== 演示论文提交与最终版锁定 ====================\n"
        "-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成\n"
        "-- 包含多版本迭代提交和最终锁定快照\n\n"
        "DELETE FROM `submission_lock` WHERE `id` BETWEEN 8001 AND 8199;\n"
        "DELETE FROM `submission` WHERE `id` BETWEEN 7001 AND 7299;\n\n"
        "INSERT INTO `submission`\n"
        "(`id`, `team_id`, `problem_id`, `submitter_id`, `version`, `original_filename`, `object_name`, `file_size`, `status`, `create_time`, `update_time`, `deleted`)\n"
        "VALUES\n"
        + ",\n".join(submissions_sql) + ";\n\n"
        "INSERT INTO `submission_lock`\n"
        "(`id`, `team_id`, `submission_id`, `locked_at`)\n"
        "VALUES\n"
        + ",\n".join(submission_locks_sql) + ";\n"
    )
    sub_out.parent.mkdir(parents=True, exist_ok=True)
    sub_out.write_text(sub_sql_text, encoding="utf-8")
    print(f"[OK] 生成 submission-service 迁移: {sub_out} ({len(submissions_sql)} 条提交, {len(submission_locks_sql)} 条最终锁定)")

    # (D) ai-review-service: V3__insert_mock_reviews.sql
    review_out = ROOT / "LeetModel-backend/ai-review-service/src/main/resources/db/migration/V3__insert_mock_reviews.sql"
    review_sql_text = (
        "-- ==================== 演示 AI 评审任务与结果 ====================\n"
        "-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成\n"
        "-- 覆盖 0-59, 60-69, 70-79, 80-89, 90-100 全分段与多维度评分\n\n"
        "DELETE FROM `review_v1_result` WHERE `id` BETWEEN 10001 AND 10299;\n"
        "DELETE FROM `review_task` WHERE `id` BETWEEN 9001 AND 9299;\n\n"
        "INSERT INTO `review_task`\n"
        "(`id`, `submission_id`, `version_id`, `team_id`, `problem_id`, `status`, `workflow_version`, `prompt_snapshot`, `retry_count`, `attempt_no`, `next_run_at`, `started_at`, `finished_at`, `error_message`, `create_time`, `update_time`, `deleted`)\n"
        "VALUES\n"
        + ",\n".join(review_tasks_sql) + ";\n\n"
        "INSERT INTO `review_v1_result`\n"
        "(`id`, `task_id`, `submission_id`, `team_id`, `problem_id`, `workflow_version`, `score`, `result_json`, `model_name`, `ai_call_id`, `create_time`, `update_time`, `deleted`)\n"
        "VALUES\n"
        + ",\n".join(review_results_sql) + ";\n"
    )
    review_out.parent.mkdir(parents=True, exist_ok=True)
    review_out.write_text(review_sql_text, encoding="utf-8")
    print(f"[OK] 生成 ai-review-service 迁移: {review_out} ({len(review_tasks_sql)} 条评审任务, {len(review_results_sql)} 条评审结果)")

    # (E) ranking-service: V2__insert_mock_rankings.sql
    rank_out = ROOT / "LeetModel-backend/ranking-service/src/main/resources/db/migration/V2__insert_mock_rankings.sql"
    rank_sql_text = (
        "-- ==================== 演示排行榜快照数据 ====================\n"
        "-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成\n"
        "-- 覆盖 8 道题目的当前有效榜单与可追溯快照\n\n"
        "DELETE FROM `ranking_snapshot` WHERE `id` BETWEEN 11001 AND 11199;\n\n"
        "INSERT INTO `ranking_snapshot`\n"
        "(`id`, `batch_id`, `problem_id`, `team_id`, `team_name`, `submission_id`, `review_task_id`, `workflow_version`, `score`, `rank_no`, `submitted_at`, `review_finished_at`, `computed_at`, `current_marker`, `create_time`, `update_time`, `deleted`)\n"
        "VALUES\n"
        + ",\n".join(ranking_snapshots_sql) + ";\n"
    )
    rank_out.parent.mkdir(parents=True, exist_ok=True)
    rank_out.write_text(rank_sql_text, encoding="utf-8")
    print(f"[OK] 生成 ranking-service 迁移: {rank_out} ({len(ranking_snapshots_sql)} 条当前上榜快照)")


if __name__ == "__main__":
    main()
