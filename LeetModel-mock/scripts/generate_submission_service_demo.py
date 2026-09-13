#!/usr/bin/env python3
"""
LeetModel 演示数据生成脚本：基于 2025 年美赛真题全链路算法化生成测试数据。

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

# 将项目根目录与 mock 目录加入 sys.path
ROOT = Path(__file__).resolve().parent.parent.parent
MOCK_ROOT = ROOT / "LeetModel-mock"
sys.path.insert(0, str(MOCK_ROOT))

from app.snowflake import Snowflake
from app.generators import make_faker

DEFAULT_PASSWORD = "123456"
DEFAULT_PASSWORD_HASH = "$2b$12$RPvgstWnQDY.36RUN2UiH.ty5wPCDx6zPxghxfHz4qC3r5w1vryJW"
SEED = 20260830

# 2025 年美赛真题完整定义与领域知识图谱
PROBLEMS_2025 = [
    {
        "id": 2097509647494889474,
        "code": 38,
        "letter": "A",
        "title": "Testing Time: The Constant Wear On Stairs",
        "domain": "楼梯磨损反演",
        "file_prefix": "2025_MCM_Problem_A",
        "keywords": [
            "偏微分几何磨损方程",
            "微元受力与弹性接触分析",
            "随机人流动力学元胞自动机",
            "历史建造年代贝叶斯反演",
            "踏面凹陷三维点云重构",
            "材料抗磨损消耗系数标定",
        ],
    },
    {
        "id": 2097509740369362945,
        "code": 39,
        "letter": "B",
        "title": "Managing Sustainable Tourism",
        "domain": "朱诺可持续旅游",
        "file_prefix": "2025_MCM_Problem_B",
        "keywords": [
            "门登霍尔冰川环境承载力约束",
            "超大规模游轮时空人流再分配",
            "混合整数非线性规划(MINLP)",
            "系统动力学(System Dynamics)因果反馈",
            "旅游税收二次再投资优化",
            "生态足迹与多目标Pareto前沿",
        ],
    },
    {
        "id": 2097509845520564225,
        "code": 40,
        "letter": "C",
        "title": "Models for Olympic Medal Tables",
        "domain": "奥运奖牌榜时序预测",
        "file_prefix": "2025_MCM_Problem_C",
        "keywords": [
            "零膨胀负二项回归(ZINB)",
            "贝叶斯状态空间时序预测",
            "东道主主场优势倾向得分匹配",
            "小众优势项目突破概率分析",
            "蒙特卡洛随机扰动灵敏度检验",
            "奖牌产出转化效率综合评估",
        ],
    },
    {
        "id": 2097509961371435009,
        "code": 41,
        "letter": "D",
        "title": "A Roadmap to a Better City",
        "domain": "城市交通韧性与投资",
        "file_prefix": "2025_ICM_Problem_D",
        "keywords": [
            "多方式交通时空网络拓扑图论",
            "城市天然地理瓶颈通行能力评估",
            "复杂路网车流拥堵Wardrop平衡",
            "基础设施改造资金动态规划",
            "低碳出行与通达公平性评价",
        ],
    },
    {
        "id": 2097510037481275393,
        "code": 42,
        "letter": "E",
        "title": "Making Room for Agriculture",
        "domain": "农业生态演替仿真",
        "file_prefix": "2025_ICM_Problem_E",
        "keywords": [
            "森林向农田演替的Lotka-Volterra动力学",
            "农药化肥面源污染多孔介质淋溶模型",
            "农业生态食物网韧性拓扑评估",
            "害虫天敌生物平衡临界阈值分析",
            "农林复合间作多目标鲁棒优化",
        ],
    },
    {
        "id": 2097510114111209473,
        "code": 43,
        "letter": "F",
        "title": "Cyber Strong?",
        "domain": "国家网络安全韧性评估",
        "file_prefix": "2025_ICM_Problem_F",
        "keywords": [
            "国家网络安全能力TOPSIS-熵权法综合评价",
            "勒索软件跨国扩散的SEIR动力学模型",
            "攻防对抗的不完全信息演化博弈",
            "关键基础设施纵深防御最优预算分配",
            "国际网络犯罪管辖权协同博弈",
        ],
    },
]

# 队伍命名学术词库
TEAM_PREFIXES = [
    "矩阵", "求索", "极值", "泰勒", "梯度", "图灵", "纳什", "高斯", "欧拉", "拉格朗日",
    "蒙特卡洛", "凸优化", "时序", "聚类", "马尔可夫", "贝叶斯", "灵敏度", "拟合", "启发式",
    "超平面", "谱聚类", "分支定界", "动态规划", "退火", "粒子群", "深度洞察", "多目标", "前沿建模",
    "随机游走", "博弈论", "决策树", "李雅普诺夫", "哈密顿", "泛函分析"
]
TEAM_SUFFIXES = [
    "先锋队", "研究组", "攻坚组", "突击队", "研析队", "实战队", "冲刺小队", "探索小组",
    "精算队", "决策实验室", "分析队", "建模队", "智囊组", "先导小队", "创新队", "协同组"
]

def sql_escape(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def generate_review_json(score: float, problem: dict, team_name: str, rng: random.Random) -> str:
    """基于赛题领域图谱与分段标准，算法化生成结构化评审结果 JSON"""
    kw1 = rng.choice(problem["keywords"])
    kw2 = rng.choice([k for k in problem["keywords"] if k != kw1])

    # 模拟四维度得分与波动
    dim_ar = round(min(100.0, max(35.0, score + rng.uniform(-3.5, 3.0))), 1)
    dim_mc = round(min(100.0, max(35.0, score + rng.uniform(-4.5, 3.5))), 1)
    dim_rc = round(min(100.0, max(35.0, score + rng.uniform(-3.0, 3.0))), 1)
    dim_ec = round(min(100.0, max(35.0, score + rng.uniform(-3.5, 3.5))), 1)

    if score >= 90.0:
        summary = (
            f"《{team_name}》针对【{problem['title']}】的建模与求解表现出极高的专业水准。"
            f"团队全面深入地运用了{kw1}，论证严密、数学形式化优美，并进行了充分的数值实验与敏感性分析。"
        )
        strengths = [
            f"核心机理建模极为深入，创新引入{kw1}对问题边界做出了细致准确的数学刻画",
            f"数值实验与算例分析完备，鲁棒性检验充分验证了模型的有效性",
            "论文排版工整，图表直观精美，公式推导严谨且符合顶级学术期刊规范",
        ]
        weaknesses = ["在极端扰动条件下的算法计算复杂度讨论略显精炼，可补充大样本渐近性质"]
        suggestions = [f"建议进一步探讨{kw2}在大规模动态实时场景下的在线参数自适应机制"]
    elif score >= 80.0:
        summary = (
            f"论文整体结构完整，对【{problem['title']}】的核心机理把握准确。"
            f"构建的{kw1}模型具有较强的实用性与可落地性，数值仿真结果自洽可靠。"
        )
        strengths = [
            f"问题抽象贴切，对{kw1}的参数标定与求解步骤叙述清晰",
            "实验对比合理，有效验证了所提策略相比基准方案的性能提升",
        ]
        weaknesses = ["部分简化假设对实际复杂环境的非线性约束略有放宽"]
        suggestions = [f"建议结合{kw2}补充更全面的误差容忍度与置信区间分析"]
    elif score >= 70.0:
        summary = (
            f"完成了【{problem['title']}】的基本建模要求，能有效运用{kw1}完成核心指标的计算与分析，"
            "具备良好的数学建模素养与工程实践能力。"
        )
        strengths = [
            "完成了端到端的问题分析、公式推导、程序求解与结果讨论",
            "给出了清晰的算例输入输出对比与初步的灵敏度讨论",
        ]
        weaknesses = [
            "算法收敛性分析不够深入，对照实验维度相对单一",
            "部分图表排版略显紧凑，符号与变量说明表可进一步标准化",
        ]
        suggestions = [f"建议补充与经典基准模型的对照实验，并引入{kw2}进行交叉检验"]
    elif score >= 60.0:
        summary = (
            f"基本涵盖了【{problem['title']}】的主要问题要素，但在模型深度与结果分析环节存在一定局限性。"
        )
        strengths = ["建立了基础的数学模型，完成了核心决策变量的定义与初步求解"]
        weaknesses = [
            f"模型对{kw1}的实现较为基础，关键参数缺乏敏感性讨论",
            "结论部分分析偏简略，缺少对实际决策落地场景的深入思考",
        ]
        suggestions = ["建议规范 LaTeX 公式排版与变量说明表，加强算法复杂度与误差分布的定量分析"]
    else:
        summary = (
            f"对【{problem['title']}】进行了初步探索，但整体数学严谨性不足，核心机理与推导需要大幅修正。"
        )
        strengths = ["初步尝试了问题的形式化表达与代码编程求解"]
        weaknesses = [
            "关键假设与问题实际物理/业务背景存在脱节",
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
                "comment": f"假设合理性评分 {dim_ar}。主要假设与问题背景契合。",
            },
            "modelCreativity": {
                "score": dim_mc,
                "comment": f"模型创新性评分 {dim_mc}。在{kw1}的应用与机制设计上有清晰展现。",
            },
            "resultCorrectness": {
                "score": dim_rc,
                "comment": f"结果正确性评分 {dim_rc}。算例计算与数值验证基本自洽。",
            },
            "expressionClarity": {
                "score": dim_ec,
                "comment": f"表达清晰度评分 {dim_ec}。图表与学术排版整体达标。",
            },
        },
        "strengths": strengths,
        "weaknesses": weaknesses,
        "suggestions": suggestions,
    }
    return json.dumps(output, ensure_ascii=False)


def main():
    rng = random.Random(SEED)
    fake = make_faker("zh_CN", seed=SEED)

    # 使用不同数据中心与机器节点的独立雪花生成器，保障多实体全局唯一且连续有序
    user_sf = Snowflake(datacenter_id=1, worker_id=3)
    team_sf = Snowflake(datacenter_id=1, worker_id=4)
    member_sf = Snowflake(datacenter_id=1, worker_id=5)
    sub_sf = Snowflake(datacenter_id=1, worker_id=6)
    lock_sf = Snowflake(datacenter_id=1, worker_id=7)
    review_task_sf = Snowflake(datacenter_id=1, worker_id=8)
    review_result_sf = Snowflake(datacenter_id=1, worker_id=9)
    rank_sf = Snowflake(datacenter_id=1, worker_id=10)

    # 1. 规划 6 道真题的参赛队伍与分数分布梯队
    # 每道题规划 13 ~ 15 支队伍，覆盖 O奖(90+), M奖(80+), H奖(70+), S奖(60+), 需改进(<60)
    score_plans = {
        # Problem A: 楼梯磨损 (14 支队伍)
        2097509647494889474: [96.2, 93.5, 91.0, 88.5, 86.2, 83.8, 81.0, 78.5, 75.8, 73.0, 70.5, 66.8, 63.2, 54.5],
        # Problem B: 朱诺旅游 (14 支队伍)
        2097509740369362945: [95.8, 92.6, 89.2, 87.0, 84.5, 82.0, 79.2, 76.5, 74.0, 71.2, 68.0, 64.5, 61.8, 52.0],
        # Problem C: 奥运奖牌 (13 支队伍)
        2097509845520564225: [97.0, 94.0, 90.8, 88.0, 85.5, 82.5, 79.5, 76.0, 72.8, 69.5, 65.2, 62.0, 56.5],
        # Problem D: 更好城市路线图 (13 支队伍)
        2097509961371435009: [95.0, 91.8, 88.2, 85.0, 82.8, 79.0, 75.5, 72.2, 69.0, 66.0, 63.5, 59.5, 51.0],
        # Problem E: 为农业腾出空间 (13 支队伍)
        2097510037481275393: [96.5, 93.0, 89.5, 86.5, 83.0, 80.5, 77.0, 73.5, 70.0, 67.2, 64.0, 60.5, 53.0],
        # Problem F: 网络强国？ (13 支队伍)
        2097510114111209473: [94.8, 91.2, 87.8, 84.0, 81.5, 78.0, 74.8, 71.5, 68.5, 65.0, 62.5, 58.0, 49.5],
    }

    total_teams_count = sum(len(s) for s in score_plans.values())
    print(f"规划生成 2025 美赛 6 道题目共 {total_teams_count} 支队伍...")

    # 2. 生成大量高质量用户
    # 为每支队伍准备 3 名队员，总共需要约 240 个用户
    users_needed = total_teams_count * 3
    user_ids = user_sf.next_ids(users_needed)
    users_sql = []
    user_roles_sql = []
    user_role_sf = Snowflake(datacenter_id=1, worker_id=11)
    user_role_ids = user_role_sf.next_ids(users_needed)

    universities = ["tsinghua.edu.cn", "pku.edu.cn", "zju.edu.cn", "sjtu.edu.cn", "fudan.edu.cn", "ustc.edu.cn", "hit.edu.cn", "whu.edu.cn", "hust.edu.cn", "mail.edu.cn"]

    user_index = 0
    for i in range(users_needed):
        uid = user_ids[i]
        uname = f"{fake.user_name()}_{i + 1}"
        nname = fake.name()
        univ = universities[i % len(universities)]
        email = f"{uname}@{univ}"
        avatar = f"https://api.dicebear.com/9.x/micah/svg?seed={uname}-{i}"
        status = 1

        users_sql.append(
            f"({uid}, {sql_escape(uname)}, '{DEFAULT_PASSWORD_HASH}', "
            f"{sql_escape(nname)}, {sql_escape(email)}, '{avatar}', "
            f"{status}, NOW(), NOW(), 0)"
        )
        user_roles_sql.append(f"({user_role_ids[i]}, {uid}, 3)")

    # 3. 逐题构建队伍、提交、评审与排行榜
    teams_sql = []
    team_members_sql = []
    submissions_sql = []
    submission_locks_sql = []
    review_tasks_sql = []
    review_results_sql = []
    ranking_snapshots_sql = []

    base_start_date = datetime(2026, 8, 20, 8, 0, 0)
    batch_id = "batch-2025-mcm-demo"

    used_team_names = set()

    for problem in PROBLEMS_2025:
        prob_id = problem["id"]
        scores = score_plans[prob_id]
        prob_letter = problem["letter"]
        prob_prefix = problem["file_prefix"]

        problem_drafts = []

        for idx, target_score in enumerate(scores):
            # 生成唯美学术队伍名
            while True:
                pfx = rng.choice(TEAM_PREFIXES)
                sfx = rng.choice(TEAM_SUFFIXES)
                tname = f"{pfx}{problem['domain'][:2]}{sfx}"
                if tname not in used_team_names:
                    used_team_names.add(tname)
                    break

            team_id = team_sf.next_id()

            leader_id = user_ids[user_index]
            user_index += 1
            member1_id = user_ids[user_index]
            user_index += 1
            member2_id = user_ids[user_index]
            user_index += 1

            # 时间线模拟
            start_dt = base_start_date + timedelta(hours=rng.randint(0, 8), minutes=rng.randint(0, 59))
            deadline_dt = start_dt + timedelta(hours=96)
            ended_dt = start_dt + timedelta(hours=rng.randint(70, 92), minutes=rng.randint(0, 59))

            desc = f"聚焦于 2025 MCM/ICM Problem {prob_letter}《{problem['title']}》的全流程数学建模、算法设计与论文撰写。"
            teams_sql.append(
                f"({team_id}, {sql_escape(tname)}, {sql_escape(desc)}, {leader_id}, {prob_id}, 1, "
                f"'ENDED', {sql_escape(start_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(deadline_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(ended_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"NOW(), NOW(), 0)"
            )

            # 成员配置（队长全能/建模，队员编程与论文）
            m1_id = member_sf.next_id()
            m2_id = member_sf.next_id()
            m3_id = member_sf.next_id()
            team_members_sql.append(
                f"({m1_id}, {team_id}, {leader_id}, 'leader', 1, 0, 0, 1, "
                f"{sql_escape(start_dt.strftime('%Y-%m-%d %H:%M:%S'))}, NOW())"
            )
            team_members_sql.append(
                f"({m2_id}, {team_id}, {member1_id}, 'member', 0, 1, 0, 1, "
                f"{sql_escape(start_dt.strftime('%Y-%m-%d %H:%M:%S'))}, NOW())"
            )
            team_members_sql.append(
                f"({m3_id}, {team_id}, {member2_id}, 'member', 0, 0, 1, 0, "
                f"{sql_escape(start_dt.strftime('%Y-%m-%d %H:%M:%S'))}, NOW())"
            )

            # 模拟 2~3 版本提交
            num_versions = 3 if target_score >= 70.0 else 2
            version_scores = []
            if num_versions == 3:
                version_scores = [
                    max(40.0, target_score - rng.uniform(10.0, 16.0)),
                    max(50.0, target_score - rng.uniform(3.0, 7.0)),
                    target_score,
                ]
            else:
                version_scores = [
                    max(35.0, target_score - rng.uniform(8.0, 14.0)),
                    target_score,
                ]

            final_sub_id = None
            final_review_task_id = None
            final_submit_dt = None
            final_review_finish_dt = None

            for v in range(1, num_versions + 1):
                sub_id = sub_sf.next_id()
                v_score = version_scores[v - 1]

                # 提交时间
                if v == 1:
                    sub_dt = start_dt + timedelta(hours=rng.randint(20, 32), minutes=rng.randint(0, 59))
                elif v == 2 and num_versions == 3:
                    sub_dt = start_dt + timedelta(hours=rng.randint(48, 60), minutes=rng.randint(0, 59))
                else:
                    sub_dt = ended_dt

                file_bytes = rng.randint(9 * 1024 * 1024, 18 * 1024 * 1024)
                orig_filename = f"{prob_prefix}_Team{team_id}_V{v}.pdf"
                obj_name = f"submissions/{team_id}/{prob_prefix}_v{v}_{sub_id}.pdf"

                submissions_sql.append(
                    f"({sub_id}, {team_id}, {prob_id}, {leader_id}, {v}, "
                    f"{sql_escape(orig_filename)}, {sql_escape(obj_name)}, {file_bytes}, 'SUCCESS', "
                    f"{sql_escape(sub_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(sub_dt.strftime('%Y-%m-%d %H:%M:%S'))}, 0)"
                )

                # 评审任务与结果
                task_id = review_task_sf.next_id()
                res_id = review_result_sf.next_id()

                duration_secs = rng.randint(25, 60)
                review_finish_dt = sub_dt + timedelta(seconds=duration_secs)
                trace_id = f"simulated-trace-{task_id}"
                idempotency_key = f"idempotency-review-{sub_id}"

                review_tasks_sql.append(
                    f"({task_id}, {sub_id}, 1, {team_id}, {prob_id}, 'COMPLETED', 100, "
                    f"{sql_escape(trace_id)}, 'BASIC_REVIEW_V1', NULL, 0, 1, 3, "
                    f"'mock-evaluator', 'lease-simulated-token', "
                    f"{sql_escape(review_finish_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(review_finish_dt.strftime('%Y-%m-%d %H:%M:%S'))}, 0, NULL, "
                    f"{sql_escape(idempotency_key)}, "
                    f"{sql_escape(sub_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(sub_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(review_finish_dt.strftime('%Y-%m-%d %H:%M:%S'))}, NULL, "
                    f"{sql_escape(sub_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(review_finish_dt.strftime('%Y-%m-%d %H:%M:%S'))}, 0)"
                )

                result_json_str = generate_review_json(v_score, problem, tname, rng)
                ai_call_id = f"aicall-mock-{task_id}"
                review_results_sql.append(
                    f"({res_id}, {task_id}, {sub_id}, {team_id}, {prob_id}, 'BASIC_REVIEW_V1', "
                    f"{v_score:.2f}, {sql_escape(result_json_str)}, 'gemini-3.8-flash-high', {sql_escape(ai_call_id)}, "
                    f"{sql_escape(sub_dt.strftime('%Y-%m-%d %H:%M:%S'))}, "
                    f"{sql_escape(review_finish_dt.strftime('%Y-%m-%d %H:%M:%S'))}, 0)"
                )

                if v == num_versions:
                    final_sub_id = sub_id
                    final_review_task_id = task_id
                    final_submit_dt = sub_dt
                    final_review_finish_dt = review_finish_dt

            # 锁定终稿
            lock_id = lock_sf.next_id()
            submission_locks_sql.append(
                f"({lock_id}, {team_id}, {final_sub_id}, {sql_escape(ended_dt.strftime('%Y-%m-%d %H:%M:%S'))})"
            )

            problem_drafts.append({
                "team_id": team_id,
                "team_name": tname,
                "submission_id": final_sub_id,
                "review_task_id": final_review_task_id,
                "score": target_score,
                "submitted_at": final_submit_dt,
                "review_finished_at": final_review_finish_dt,
            })

        # 按最终分数降序排序生成排行榜快照
        problem_drafts.sort(key=lambda x: x["score"], reverse=True)
        computed_at = datetime(2026, 8, 25, 12, 0, 0)

        for rank_idx, draft in enumerate(problem_drafts, start=1):
            rank_id = rank_sf.next_id()

            ranking_snapshots_sql.append(
                f"({rank_id}, {sql_escape(batch_id)}, {prob_id}, {draft['team_id']}, "
                f"{sql_escape(draft['team_name'])}, {draft['submission_id']}, {draft['review_task_id']}, "
                f"'BASIC_REVIEW_V1', {draft['score']:.2f}, {rank_idx}, "
                f"{sql_escape(draft['submitted_at'].strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(draft['review_finished_at'].strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(computed_at.strftime('%Y-%m-%d %H:%M:%S'))}, 1, "
                f"{sql_escape(computed_at.strftime('%Y-%m-%d %H:%M:%S'))}, "
                f"{sql_escape(computed_at.strftime('%Y-%m-%d %H:%M:%S'))}, 0)"
            )

    # 4. 生成各个微服务的 Flyway 迁移脚本
    prob_ids_str = ", ".join(str(p["id"]) for p in PROBLEMS_2025)

    # (A) user-service: V6__insert_more_mock_users.sql
    user_out = ROOT / "LeetModel-backend/user-service/src/main/resources/db/migration/V6__insert_more_mock_users.sql"
    user_sql_text = f"""-- ==================== 扩展演示用户数据 ====================
-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成
-- 统一密码：{DEFAULT_PASSWORD}，角色：user=3
-- 主键使用标准 64 位雪花算法生成

INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `email`, `avatar_path`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
{',\n'.join(users_sql)}
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

INSERT INTO `user_role` (`id`, `user_id`, `role_id`)
VALUES
{',\n'.join(user_roles_sql)}
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
"""
    user_out.write_text(user_sql_text, encoding="utf-8")
    print(f"[OK] 生成 user-service 迁移: {user_out} (新增 {len(users_sql)} 个雪花 ID 用户)")

    # (B) team-service: V11__insert_leaderboard_demo_teams.sql
    team_out = ROOT / "LeetModel-backend/team-service/src/main/resources/db/migration/V11__insert_leaderboard_demo_teams.sql"
    team_sql_text = f"""-- ==================== 扩展排行榜队伍与成员数据 ====================
-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成
-- 覆盖 2025 美赛真题 6 道题目 (A-F) 的多队伍全流程建模场景

DELETE FROM `team_member` WHERE `team_id` IN (SELECT `id` FROM `team` WHERE `problem_id` IN ({prob_ids_str}));
DELETE FROM `team` WHERE `problem_id` IN ({prob_ids_str});
-- 清理历史遗留硬编码 ID 与老旧 mock 题目
DELETE FROM `team_member` WHERE `team_id` BETWEEN 2006 AND 2099;
DELETE FROM `team` WHERE `id` BETWEEN 2006 AND 2099;
DELETE FROM `team` WHERE `problem_id` BETWEEN 51001 AND 51008;

INSERT INTO `team`
(`id`, `name`, `description`, `leader_id`, `problem_id`, `status`, `practice_status`, `started_at`, `deadline_at`, `ended_at`, `create_time`, `update_time`, `deleted`)
VALUES
{',\n'.join(teams_sql)};

INSERT INTO `team_member`
(`id`, `team_id`, `user_id`, `role`, `modeler`, `programmer`, `writer`, `can_submit`, `joined_at`, `create_time`)
VALUES
{',\n'.join(team_members_sql)};
"""
    team_out.write_text(team_sql_text, encoding="utf-8")
    print(f"[OK] 生成 team-service 迁移: {team_out} (新增 {len(teams_sql)} 支队伍, {len(team_members_sql)} 条成员关系)")

    # (C) submission-service: V2__insert_mock_submissions.sql
    sub_out = ROOT / "LeetModel-backend/submission-service/src/main/resources/db/migration/V2__insert_mock_submissions.sql"
    sub_sql_text = f"""-- ==================== 演示论文提交与最终版锁定 ====================
-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成
-- 覆盖 2025 美赛 6 道题目的多版本迭代提交和最终锁定快照

DELETE FROM `submission_lock` WHERE `submission_id` IN (SELECT `id` FROM `submission` WHERE `problem_id` IN ({prob_ids_str}));
DELETE FROM `submission` WHERE `problem_id` IN ({prob_ids_str});
-- 清理历史遗留硬编码 ID 与老旧 mock 题目
DELETE FROM `submission_lock` WHERE `id` BETWEEN 8001 AND 8199;
DELETE FROM `submission` WHERE `id` BETWEEN 7001 AND 7299;
DELETE FROM `submission` WHERE `problem_id` BETWEEN 51001 AND 51008;

INSERT INTO `submission`
(`id`, `team_id`, `problem_id`, `submitter_id`, `version`, `original_filename`, `object_name`, `file_size`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
{',\n'.join(submissions_sql)};

INSERT INTO `submission_lock`
(`id`, `team_id`, `submission_id`, `locked_at`)
VALUES
{',\n'.join(submission_locks_sql)};
"""
    sub_out.write_text(sub_sql_text, encoding="utf-8")
    print(f"[OK] 生成 submission-service 迁移: {sub_out} ({len(submissions_sql)} 条多版本提交, {len(submission_locks_sql)} 条终稿锁定)")

    # (D) ai-review-service: V3__insert_mock_reviews.sql
    review_out = ROOT / "LeetModel-backend/ai-review-service/src/main/resources/db/migration/V3__insert_mock_reviews.sql"
    review_sql_text = f"""-- ==================== 演示 AI 评审任务与结果 ====================
-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成
-- 覆盖 2025 美赛 6 道题目 0-59, 60-69, 70-79, 80-89, 90-100 全分段与领域语义评价

DELETE FROM `review_v1_result` WHERE `problem_id` IN ({prob_ids_str});
DELETE FROM `review_task` WHERE `problem_id` IN ({prob_ids_str});
-- 清理历史遗留硬编码 ID 与老旧 mock 题目
DELETE FROM `review_v1_result` WHERE `id` BETWEEN 10001 AND 10299;
DELETE FROM `review_task` WHERE `id` BETWEEN 9001 AND 9299;
DELETE FROM `review_v1_result` WHERE `problem_id` BETWEEN 51001 AND 51008;
DELETE FROM `review_task` WHERE `problem_id` BETWEEN 51001 AND 51008;

INSERT INTO `review_task`
(`id`, `submission_id`, `version_id`, `team_id`, `problem_id`, `status`, `priority`, `trace_id`, `workflow_version`, `prompt_snapshot`, `retry_count`, `attempt_no`, `max_attempts`, `lease_owner`, `lease_token`, `lease_expires_at`, `heartbeat_at`, `recovery_count`, `failure_type`, `ai_idempotency_key`, `next_run_at`, `started_at`, `finished_at`, `error_message`, `create_time`, `update_time`, `deleted`)
VALUES
{',\n'.join(review_tasks_sql)};

INSERT INTO `review_v1_result`
(`id`, `task_id`, `submission_id`, `team_id`, `problem_id`, `workflow_version`, `score`, `result_json`, `model_name`, `ai_call_id`, `create_time`, `update_time`, `deleted`)
VALUES
{',\n'.join(review_results_sql)};
"""
    review_out.write_text(review_sql_text, encoding="utf-8")
    print(f"[OK] 生成 ai-review-service 迁移: {review_out} ({len(review_tasks_sql)} 条评审任务, {len(review_results_sql)} 条多维评审结果)")

    # (E) ranking-service: V2__insert_mock_rankings.sql
    rank_out = ROOT / "LeetModel-backend/ranking-service/src/main/resources/db/migration/V2__insert_mock_rankings.sql"
    rank_sql_text = f"""-- ==================== 演示排行榜快照数据 ====================
-- 由 LeetModel-mock/scripts/generate_submission_service_demo.py 生成
-- 覆盖 2025 美赛 6 道题目 (A-F) 的当前有效榜单与可追溯快照

DELETE FROM `ranking_snapshot` WHERE `problem_id` IN ({prob_ids_str});
-- 清理历史遗留硬编码 ID 与老旧 mock 题目
DELETE FROM `ranking_snapshot` WHERE `id` BETWEEN 11001 AND 11199;
DELETE FROM `ranking_snapshot` WHERE `problem_id` BETWEEN 51001 AND 51008;

INSERT INTO `ranking_snapshot`
(`id`, `batch_id`, `problem_id`, `team_id`, `team_name`, `submission_id`, `review_task_id`, `workflow_version`, `score`, `rank_no`, `submitted_at`, `review_finished_at`, `computed_at`, `current_marker`, `create_time`, `update_time`, `deleted`)
VALUES
{',\n'.join(ranking_snapshots_sql)};
"""
    rank_out.write_text(rank_sql_text, encoding="utf-8")
    print(f"[OK] 生成 ranking-service 迁移: {rank_out} ({len(ranking_snapshots_sql)} 条当前上榜快照)")


if __name__ == "__main__":
    main()
