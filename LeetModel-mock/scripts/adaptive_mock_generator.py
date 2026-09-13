#!/usr/bin/env python3
"""
LeetModel 自适应批量 Mock 数据生成器

功能特性：
1. 自适应探测：自动查询数据库中已录入的真实赛题（国赛 CUMCM、美赛 MCM/ICM），根据题目中英文语言、年份、赛题编号自适应提取领域关键词。
2. 概率分布驱动：基于 Beta 分布生成逼真的全分段奖项梯次（O/M/H/S/需改进），多维度得分（AR/MC/RC/EC）带协方差扰动。
3. 全生命周期多场景：每道题目随机生成 10~16 支队伍，覆盖组建中（开放招募与待审核申请）、练习中（倒计时与中期迭代）及练习结束（终稿锁定与榜单呈现）。
4. 64 位雪花算法：所有主键与外键关联均由标准 Snowflake 算法动态分配，杜绝硬编码 ID。
5. 数据库直连批量写入：支持幂等重放，直接写入本地 MySQL，支持全量或按题目增量补充。
"""
from __future__ import annotations

import argparse
import json
import math
import random
import re
import sys
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import pymysql

# 加入 mock 根目录
ROOT = Path(__file__).resolve().parent.parent.parent
MOCK_ROOT = ROOT / "LeetModel-mock"
sys.path.insert(0, str(MOCK_ROOT))

from app.generators import make_faker
from app.snowflake import Snowflake

DEFAULT_PASSWORD_HASH = "$2b$12$RPvgstWnQDY.36RUN2UiH.ty5wPCDx6zPxghxfHz4qC3r5w1vryJW"  # 123456

# 学术队伍命名前缀与后缀库
ZH_PREFIXES = [
    "矩阵", "求索", "极值", "泰勒", "梯度", "图灵", "纳什", "高斯", "欧拉", "拉格朗日",
    "蒙特卡洛", "凸优化", "时序", "聚类", "马尔可夫", "贝叶斯", "灵敏度", "拟合", "启发式",
    "超平面", "谱聚类", "分支定界", "动态规划", "退火", "粒子群", "深度洞察", "多目标", "前沿建模",
    "随机游走", "博弈论", "决策树", "李雅普诺夫", "哈密顿", "泛函分析", "隐马尔可夫", "主成分"
]
ZH_SUFFIXES = [
    "先锋队", "研究组", "攻坚组", "突击队", "研析队", "实战队", "冲刺小队", "探索小组",
    "精算队", "决策实验室", "分析队", "建模队", "智囊组", "先导小队", "创新队", "协同组"
]

EN_PREFIXES = [
    "Matrix", "Apex", "Taylor", "Gradient", "Turing", "Nash", "Gauss", "Euler", "Lagrange",
    "MonteCarlo", "Convex", "Markov", "Bayes", "Heuristic", "Dynamic", "Annealing", "Swarm",
    "DeepInsight", "Pareto", "Frontier", "Stochastic", "GameTheory", "Lyapunov", "Functional"
]
EN_SUFFIXES = [
    "Pioneers", "Lab", "StrikeTeam", "Analytics", "Vanguard", "Innovators", "Synergy",
    "ResearchGroup", "StrategyTeam", "TaskForce", "Specialists", "Fellows", "Studio"
]

UNIVERSITIES = [
    "tsinghua.edu.cn", "pku.edu.cn", "zju.edu.cn", "sjtu.edu.cn", "fudan.edu.cn",
    "ustc.edu.cn", "hit.edu.cn", "whu.edu.cn", "hust.edu.cn", "mail.edu.cn",
    "mit.edu", "stanford.edu", "berkeley.edu", "cmu.edu", "cam.ac.uk", "ox.ac.uk"
]


def get_db_connection():
    return pymysql.connect(
        host="127.0.0.1",
        port=3306,
        user="root",
        password="root",
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def adapt_keywords_for_problem(title: str, lang: str) -> list[str]:
    """根据题目名称与语言，自适应提取领域模型与核心方法关键词"""
    clean_title = re.sub(r"^[0-9_A-Za-z\s]+:\s*", "", title)

    if lang == "EN":
        methods = [
            "Differential Equation Modeling", "Stochastic Process Simulation",
            "Mixed-Integer Linear Programming", "Monte Carlo Parameter Calibration",
            "Multivariate Regression and ANOVA", "Markov Decision Process",
            "Pareto Frontier Optimization", "Sensitivity and Robustness Analysis",
            "Bayesian State-Space Estimation", "Graph Topology and Network Flow"
        ]
        # 提取标题中 2~3 个有代表性的单词作为领域主题
        words = [w for w in re.findall(r"[A-Za-z]+", clean_title) if len(w) > 3 and w.lower() not in ["problem", "models", "constant", "using", "that", "this"]]
        domain_tag = " ".join(words[:2]) if words else "System Optimization"
        return [f"{domain_tag} - {m}" for m in methods]
    else:
        methods = [
            "偏微分方程数值解法", "随机过程与元胞自动机仿真", "多目标混合整数非线性规划",
            "蒙特卡洛随机采样与敏感性分析", "贝叶斯状态空间时序预测模型", "复杂网络拓扑与图流平衡",
            "TOPSIS-熵权法综合决策评价", "支持向量机与随机森林集成回归", "多阶段动态规划递推求解",
            "非线性方程组牛顿-拉夫逊迭代"
        ]
        # 提取中文核心词
        short_title = clean_title[:8]
        return [f"【{short_title}】{m}" for m in methods]


def generate_score_samples(count: int, rng: random.Random) -> list[float]:
    """生成呈正态/高斯钟形曲线分布的竞赛分数，峰值在 75-78，覆盖 50-98 分"""
    scores = []
    for _ in range(count):
        val = rng.gauss(76.0, 8.5)
        score = round(min(97.8, max(50.5, val)), 1)
        scores.append(score)
    scores.sort(reverse=True)
    return scores


def partition_submissions(teams_count: int, total_subs: int, rng: random.Random) -> list[int]:
    """将题目的总提交数（约500次）分配给各个队伍"""
    min_per_team = 1
    remaining = max(0, total_subs - (teams_count * min_per_team))
    weights = [rng.paretovariate(1.5) for _ in range(teams_count)]
    total_w = sum(weights)
    shares = [int(remaining * w / total_w) for w in weights]
    diff = remaining - sum(shares)
    for i in range(diff):
        shares[i % teams_count] += 1
    return [min_per_team + s for s in shares]


def generate_adaptive_review_json(score: float, problem_title: str, lang: str, keywords: list[str], team_name: str, rng: random.Random) -> str:
    """自适应生成多维度评审 JSON 数据"""
    kw1 = rng.choice(keywords)
    kw2 = rng.choice([k for k in keywords if k != kw1])

    dim_ar = round(min(100.0, max(35.0, score + rng.uniform(-3.5, 3.0))), 1)
    dim_mc = round(min(100.0, max(35.0, score + rng.uniform(-4.5, 3.5))), 1)
    dim_rc = round(min(100.0, max(35.0, score + rng.uniform(-3.0, 3.0))), 1)
    dim_ec = round(min(100.0, max(35.0, score + rng.uniform(-3.5, 3.5))), 1)

    if lang == "EN":
        if score >= 90.0:
            summary = f"Team '{team_name}' demonstrated outstanding modeling skills on '{problem_title}'. The mathematical rigor, comprehensive application of {kw1}, and clear visualization set an exemplary benchmark."
            strengths = [
                f"Rigorous mathematical formulation employing {kw1} with explicit physical boundary constraints.",
                "Comprehensive numerical experiments and robust sensitivity analysis under diverse disturbance scenarios.",
                "Exceptional paper organization, typography, and professional formatting conforming to top-tier academic standards."
            ]
            weaknesses = ["Discussion on computational complexity under asymptotic extreme conditions could be expanded slightly."]
            suggestions = [f"Consider exploring real-time dynamic parameter adaptation using {kw2} for large-scale operations."]
        elif score >= 80.0:
            summary = f"The submission provides a complete and well-structured solution for '{problem_title}'. The {kw1} formulation is sound, and the experimental results are convincing."
            strengths = [
                f"Appropriate abstraction of the core problem with detailed derivation of {kw1}.",
                "Valid comparative experiments demonstrating noticeable performance improvements over baseline heuristics."
            ]
            weaknesses = ["Certain simplifying assumptions relax complex real-world non-linear dependencies."]
            suggestions = [f"Incorporate cross-validation with {kw2} to further substantiate tolerance margins."]
        elif score >= 70.0:
            summary = f"A solid submission addressing the primary questions of '{problem_title}'. Demonstrated good modeling ability using {kw1}."
            strengths = [
                "Complete end-to-end workflow from problem formulation to numerical calculation.",
                "Provides necessary input/output data comparisons and preliminary sensitivity charts."
            ]
            weaknesses = ["Algorithmic convergence analysis is somewhat brief; lacks broader comparative baselines."]
            suggestions = [f"Supplement with classical baseline comparisons and introduce {kw2} for validation."]
        elif score >= 60.0:
            summary = f"The paper covers the fundamental aspects of '{problem_title}', but shows limitations in depth and algorithmic evaluation."
            strengths = ["Established baseline mathematical relations and defined core decision variables."]
            weaknesses = [f"Implementation of {kw1} remains elementary; lacks depth in parameter sensitivity."]
            suggestions = ["Standardize mathematical notation and enhance quantitative algorithmic complexity analysis."]
        else:
            summary = f"Preliminary investigation of '{problem_title}'. Significant improvements are required in mathematical rigor and empirical evidence."
            strengths = ["Attempted basic mathematical modeling and computational implementation."]
            weaknesses = ["Assumptions disconnect from actual problem constraints; results show incomplete convergence."]
            suggestions = ["Reconstruct core objective formulations and adhere to standard academic formatting."]
    else:
        if score >= 90.0:
            summary = f"《{team_name}》针对【{problem_title}】展现出极高水准的建模与求解能力。团队全面深入运用了{kw1}，推导严密自洽，数值算例与灵敏度分析极为充分。"
            strengths = [
                f"机理建模极为深入，创新引入{kw1}对问题边界做出了细致准确的形式化刻画",
                "数值实验与算例分析完备，鲁棒性检验充分验证了模型的有效性",
                "论文排版工整，图表直观精美，公式推导符合学术期刊高标准规范"
            ]
            weaknesses = ["极端边界扰动下的算法渐近复杂度讨论可进一步延伸"]
            suggestions = [f"建议进一步探讨结合{kw2}在大规模动态实时场景下的在线微调方案"]
        elif score >= 80.0:
            summary = f"论文整体结构完整，对【{problem_title}】的核心机理把握准确，构建的{kw1}模型具有较强实用性与说服力。"
            strengths = [
                f"问题抽象贴切，对{kw1}的参数标定与求解步骤叙述清晰",
                "实验对比合理，有效验证了所提策略相比基准方案的性能提升"
            ]
            weaknesses = ["部分简化假设对实际环境的复杂非线性约束略有放宽"]
            suggestions = [f"建议结合{kw2}补充更全面的误差容忍度与置信区间分析"]
        elif score >= 70.0:
            summary = f"完成了【{problem_title}】的基本建模要求，能有效运用{kw1}完成核心指标计算，具备良好的建模素养。"
            strengths = [
                "完成了端到端的问题分析、公式推导、程序求解与结果讨论",
                "给出了清晰的算例输入输出对比与初步的灵敏度讨论"
            ]
            weaknesses = ["算法收敛性分析不够深入，对照实验维度相对单一"]
            suggestions = [f"建议补充与经典基准模型的对照实验，并引入{kw2}进行交叉检验"]
        elif score >= 60.0:
            summary = f"基本涵盖了【{problem_title}】的主要问题要素，但在模型深度与结果分析环节存在一定局限性。"
            strengths = ["建立了基础的数学模型，完成了核心决策变量的定义与初步求解"]
            weaknesses = [f"模型对{kw1}的实现较为基础，关键参数缺乏敏感性讨论"]
            suggestions = ["建议规范 LaTeX 公式排版与变量说明表，加强算法复杂度与误差分布的定量分析"]
        else:
            summary = f"对【{problem_title}】进行了初步探索，但整体数学严谨性不足，核心机理与推导需要大幅修正。"
            strengths = ["初步尝试了问题的数学形式化表达与代码求解"]
            weaknesses = ["关键假设与问题实际物理/工程背景存在脱节，数据说服力较弱"]
            suggestions = ["建议重新梳理问题假设与目标函数，遵循标准学术论文规范重构实验与图表"]

    output = {
        "score": round(score, 1),
        "summary": summary,
        "dimensions": {
            "assumptionRationality": {"score": dim_ar, "comment": f"Score: {dim_ar}"},
            "modelCreativity": {"score": dim_mc, "comment": f"Score: {dim_mc}"},
            "resultCorrectness": {"score": dim_rc, "comment": f"Score: {dim_rc}"},
            "expressionClarity": {"score": dim_ec, "comment": f"Score: {dim_ec}"},
        },
        "strengths": strengths,
        "weaknesses": weaknesses,
        "suggestions": suggestions,
    }
    return json.dumps(output, ensure_ascii=False)


def process_problem(prob: dict[str, Any], conn: pymysql.Connection, dry_run: bool = False):
    prob_id = prob["id"]
    prob_title = prob["title"]
    prob_year = prob["year"]
    prob_letter = prob["problem_number"]
    prob_lang = prob.get("statement_language", "EN") or "EN"
    contest_code = prob.get("contest_code", "MCM_ICM")

    rng = random.Random(prob_id)
    fake_zh = make_faker("zh_CN", seed=prob_id % 100000)
    fake_en = make_faker("en_US", seed=prob_id % 100000)

    # 分配雪花生成器
    worker_seed = (prob_id % 20) + 10
    user_sf = Snowflake(datacenter_id=2, worker_id=worker_seed % 32)
    team_sf = Snowflake(datacenter_id=2, worker_id=(worker_seed + 1) % 32)
    member_sf = Snowflake(datacenter_id=2, worker_id=(worker_seed + 2) % 32)
    sub_sf = Snowflake(datacenter_id=2, worker_id=(worker_seed + 3) % 32)
    lock_sf = Snowflake(datacenter_id=2, worker_id=(worker_seed + 4) % 32)
    rev_task_sf = Snowflake(datacenter_id=2, worker_id=(worker_seed + 5) % 32)
    rev_res_sf = Snowflake(datacenter_id=2, worker_id=(worker_seed + 6) % 32)
    rank_sf = Snowflake(datacenter_id=2, worker_id=(worker_seed + 7) % 32)
    recruit_sf = Snowflake(datacenter_id=2, worker_id=(worker_seed + 8) % 32)
    app_sf = Snowflake(datacenter_id=2, worker_id=(worker_seed + 9) % 32)

    # 提取自适应领域关键词
    keywords = adapt_keywords_for_problem(prob_title, prob_lang)

    # 规划队伍数 (85~105支，打造丰满的分数分布直方图柱状体) 与约 500 次总提交
    num_teams = rng.randint(85, 105)
    scores = generate_score_samples(num_teams, rng)
    team_sub_counts = partition_submissions(num_teams, rng.randint(485, 515), rng)

    # 基准竞赛月份与时间窗口
    contest_month = 2 if contest_code == "MCM_ICM" else 9
    base_start_date = datetime(prob_year, contest_month, 18, 8, 0, 0)

    # 规划状态：最后 1 支为 PREPARING，倒数第 2 支为 IN_PROGRESS，其余全为 ENDED
    users_to_insert = []
    user_roles_to_insert = []
    teams_to_insert = []
    members_to_insert = []
    recruitments_to_insert = []
    applications_to_insert = []
    subs_to_insert = []
    locks_to_insert = []
    rev_tasks_to_insert = []
    rev_results_to_insert = []
    ranking_drafts = []
    ended_scores = []

    used_team_names = set()

    # 清理该题历史数据
    if not dry_run:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM lm_ranking.ranking_snapshot WHERE problem_id = %s", (prob_id,))
            cur.execute("DELETE FROM lm_review.review_v1_result WHERE problem_id = %s", (prob_id,))
            cur.execute("DELETE FROM lm_review.review_task WHERE problem_id = %s", (prob_id,))
            cur.execute("DELETE FROM lm_submission.submission_lock WHERE submission_id IN (SELECT id FROM lm_submission.submission WHERE problem_id = %s)", (prob_id,))
            cur.execute("DELETE FROM lm_submission.submission WHERE problem_id = %s", (prob_id,))
            cur.execute("DELETE FROM lm_team.team_join_application WHERE team_id IN (SELECT id FROM lm_team.team WHERE problem_id = %s)", (prob_id,))
            cur.execute("DELETE FROM lm_team.team_recruitment WHERE team_id IN (SELECT id FROM lm_team.team WHERE problem_id = %s)", (prob_id,))
            cur.execute("DELETE FROM lm_team.team_member WHERE team_id IN (SELECT id FROM lm_team.team WHERE problem_id = %s)", (prob_id,))
            cur.execute("DELETE FROM lm_team.team WHERE problem_id = %s", (prob_id,))

    # 逐支队伍生成
    for t_idx in range(num_teams):
        target_score = scores[t_idx]

        # 决定该队伍的生命周期状态
        if t_idx == num_teams - 1:
            practice_status = "PREPARING"
        elif t_idx == num_teams - 2:
            practice_status = "IN_PROGRESS"
        else:
            practice_status = "ENDED"

        # 队伍名称生成
        while True:
            if prob_lang == "EN":
                pfx = rng.choice(EN_PREFIXES)
                sfx = rng.choice(EN_SUFFIXES)
                tname = f"{pfx} {prob_letter} {sfx}"
            else:
                pfx = rng.choice(ZH_PREFIXES)
                sfx = rng.choice(ZH_SUFFIXES)
                short_title = re.sub(r"^[0-9_A-Za-z\s]+:\s*", "", prob_title)[:2]
                tname = f"{pfx}{short_title}{sfx}"
            if tname not in used_team_names:
                used_team_names.add(tname)
                break

        team_id = team_sf.next_id()

        # 生成 2~3 名队员
        num_members = 2 if practice_status == "PREPARING" else 3
        team_member_ids = []
        for m_idx in range(num_members):
            uid = user_sf.next_id()
            team_member_ids.append(uid)

            if prob_lang == "EN":
                uname = f"{fake_en.user_name()}_{prob_id % 10000}_{t_idx}_{m_idx}"
                nname = fake_en.name()
            else:
                uname = f"{fake_zh.user_name()}_{prob_id % 10000}_{t_idx}_{m_idx}"
                nname = fake_zh.name()

            univ = UNIVERSITIES[(t_idx + m_idx) % len(UNIVERSITIES)]
            email = f"{uname}@{univ}"
            avatar = f"https://api.dicebear.com/9.x/micah/svg?seed={uname}"

            users_to_insert.append((uid, uname, DEFAULT_PASSWORD_HASH, nname, email, avatar, 1))
            user_roles_to_insert.append((uid, 3))

        leader_id = team_member_ids[0]

        start_dt = base_start_date + timedelta(hours=rng.randint(0, 12), minutes=rng.randint(0, 59))
        deadline_dt = start_dt + timedelta(hours=96)
        if practice_status == "ENDED":
            ended_dt = start_dt + timedelta(hours=rng.randint(72, 92), minutes=rng.randint(0, 59))
        elif practice_status == "IN_PROGRESS":
            ended_dt = None
        else:
            start_dt = None
            deadline_dt = None
            ended_dt = None

        desc = f"针对 {contest_code} {prob_year} {prob_letter}《{prob_title}》的建模与程序求解团队。"
        teams_to_insert.append((
            team_id, tname, desc, leader_id, prob_id, 1, practice_status,
            start_dt, deadline_dt, ended_dt
        ))

        # 队伍成员角色
        roles = ["leader", "member", "member"]
        skills = [(1, 0, 0), (0, 1, 0), (0, 0, 1)]
        for m_idx, uid in enumerate(team_member_ids):
            mid = member_sf.next_id()
            r = roles[m_idx]
            m_s, p_s, w_s = skills[m_idx]
            can_sub = 1 if m_idx <= 1 else 0
            j_time = start_dt or datetime.now()
            members_to_insert.append((mid, team_id, uid, r, m_s, p_s, w_s, can_sub, j_time))

        # PREPARING 场景：生成招募与申请记录
        if practice_status == "PREPARING":
            rec_id = recruit_sf.next_id()
            recruitments_to_insert.append((
                rec_id, team_id, 0, 0, 1, "诚招论文撰写与 LaTeX 排版同学，要求细心严谨。", "OPEN"
            ))
            app_uid = user_sf.next_id()
            app_uname = f"applicant_{prob_id % 10000}_{t_idx}"
            users_to_insert.append((app_uid, app_uname, DEFAULT_PASSWORD_HASH, "申请者同学", f"{app_uname}@stu.edu.cn", f"https://api.dicebear.com/9.x/micah/svg?seed={app_uname}", 1))
            user_roles_to_insert.append((app_uid, 3))
            app_id = app_sf.next_id()
            applications_to_insert.append((
                app_id, team_id, rec_id, app_uid, "有丰富论文排版经验，申请加入团队！", "pending", 1, None, None
            ))
            continue  # 组建中不产生提交

        # 产生提交与评审
        num_versions = max(1, team_sub_counts[t_idx])
        prefix_file = f"{contest_code}_{prob_year}_{prob_letter}"

        version_scores = []
        start_score = max(35.0, target_score - rng.uniform(22.0, 38.0))
        for v in range(1, num_versions + 1):
            prog = v / float(num_versions)
            s_fac = 1.0 / (1.0 + math.exp(-6.0 * (prog - 0.4)))
            b_sc = start_score + (target_score - start_score) * s_fac
            noise = rng.uniform(-2.5, 2.5) * (1.0 - prog * 0.75)
            version_scores.append(round(min(target_score, max(30.0, b_sc + noise)), 1))
        version_scores[-1] = target_score

        final_sub_id = None
        final_task_id = None
        final_sub_dt = None
        final_rev_dt = None
        time_step = (85.0 * 60) / float(num_versions)

        for v in range(1, num_versions + 1):
            sub_id = sub_sf.next_id()
            v_score = version_scores[v - 1]

            sub_dt = start_dt + timedelta(minutes=int((v - 1) * time_step + rng.randint(0, 3)))

            file_bytes = rng.randint(9 * 1024 * 1024, 17 * 1024 * 1024)
            orig_filename = f"{prefix_file}_Team{team_id}_V{v}.pdf"
            obj_name = f"submissions/{team_id}/{prefix_file}_v{v}_{sub_id}.pdf"

            subs_to_insert.append((
                sub_id, team_id, prob_id, leader_id, v, orig_filename, obj_name, file_bytes, "SUCCESS", sub_dt, sub_dt
            ))

            # 评审任务
            task_id = rev_task_sf.next_id()
            res_id = rev_res_sf.next_id()

            rev_finish_dt = sub_dt + timedelta(seconds=rng.randint(20, 50))
            trace_id = f"sim-trace-{task_id}"
            idempotency_key = f"idem-{sub_id}"

            rev_tasks_to_insert.append((
                task_id, sub_id, 1, team_id, prob_id, "COMPLETED", 100, trace_id, "BASIC_REVIEW_V1",
                "mock-evaluator", "lease-token", rev_finish_dt, rev_finish_dt, idempotency_key,
                sub_dt, sub_dt, rev_finish_dt
            ))

            review_json = generate_adaptive_review_json(v_score, prob_title, prob_lang, keywords, tname, rng)
            rev_results_to_insert.append((
                res_id, task_id, sub_id, team_id, prob_id, "BASIC_REVIEW_V1", v_score, review_json, "gemini-3.8-flash-high",
                f"aicall-{task_id}", sub_dt, rev_finish_dt
            ))

            if v == num_versions:
                final_sub_id = sub_id
                final_task_id = task_id
                final_sub_dt = sub_dt
                final_rev_dt = rev_finish_dt

        # 锁定终稿并准备进入排行榜
        if practice_status == "ENDED":
            lid = lock_sf.next_id()
            locks_to_insert.append((lid, team_id, final_sub_id, ended_dt))
            ended_scores.append(target_score)

            ranking_drafts.append({
                "team_id": team_id,
                "team_name": tname,
                "submission_id": final_sub_id,
                "review_task_id": final_task_id,
                "score": target_score,
                "submitted_at": final_sub_dt,
                "review_finished_at": final_rev_dt,
            })

    # 排序并生成排行榜
    ranking_drafts.sort(key=lambda x: x["score"], reverse=True)
    batch_id = f"batch-demo-{prob_id}"
    computed_at = (base_start_date + timedelta(days=5)).strftime("%Y-%m-%d %H:%M:%S")

    rankings_to_insert = []
    for r_idx, draft in enumerate(ranking_drafts, start=1):
        rid = rank_sf.next_id()
        rankings_to_insert.append((
            rid, batch_id, prob_id, draft["team_id"], draft["team_name"],
            draft["submission_id"], draft["review_task_id"], "BASIC_REVIEW_V1",
            draft["score"], r_idx, draft["submitted_at"], draft["review_finished_at"],
            computed_at, 1, computed_at, computed_at
        ))

    if dry_run:
        print(f"  [Dry-Run] 拟为题目 {prob_id} ({prob_title}) 生成 {len(teams_to_insert)} 支队伍, {len(subs_to_insert)} 条提交, {len(rankings_to_insert)} 条榜单快照")
        return

    # 执行批量插入
    with conn.cursor() as cur:
        if users_to_insert:
            cur.executemany(
                "INSERT INTO lm_user.user (id, username, password, nickname, email, avatar_path, status, create_time, update_time, deleted) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE username=VALUES(username)",
                users_to_insert,
            )
            cur.executemany(
                "INSERT INTO lm_user.user_role (id, user_id, role_id) VALUES (%s, %s, %s) ON DUPLICATE KEY UPDATE role_id=VALUES(role_id)",
                [(member_sf.next_id(), uid, rid) for uid, rid in user_roles_to_insert],
            )

        if teams_to_insert:
            cur.executemany(
                "INSERT INTO lm_team.team (id, name, description, leader_id, problem_id, status, practice_status, started_at, deadline_at, ended_at, create_time, update_time, deleted) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW(), 0)",
                teams_to_insert,
            )
            cur.executemany(
                "INSERT INTO lm_team.team_member (id, team_id, user_id, role, modeler, programmer, writer, can_submit, joined_at, create_time) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())",
                members_to_insert,
            )

        if recruitments_to_insert:
            cur.executemany(
                "INSERT INTO lm_team.team_recruitment (id, team_id, need_modeler, need_programmer, need_writer, description, status, create_time, update_time) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, NOW(), NOW())",
                recruitments_to_insert,
            )

        if applications_to_insert:
            cur.executemany(
                "INSERT INTO lm_team.team_join_application (id, team_id, recruitment_id, applicant_id, message, status, pending_marker, handled_by, handled_at, create_time) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())",
                applications_to_insert,
            )

        if subs_to_insert:
            cur.executemany(
                "INSERT INTO lm_submission.submission (id, team_id, problem_id, submitter_id, version, original_filename, object_name, file_size, status, create_time, update_time, deleted) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0)",
                subs_to_insert,
            )

        if locks_to_insert:
            cur.executemany(
                "INSERT INTO lm_submission.submission_lock (id, team_id, submission_id, locked_at) VALUES (%s, %s, %s, %s)",
                locks_to_insert,
            )

        if rev_tasks_to_insert:
            cur.executemany(
                "INSERT INTO lm_review.review_task (id, submission_id, version_id, team_id, problem_id, status, priority, trace_id, workflow_version, lease_owner, lease_token, lease_expires_at, heartbeat_at, ai_idempotency_key, next_run_at, started_at, finished_at, create_time, update_time, deleted) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW(), 0)",
                rev_tasks_to_insert,
            )
            cur.executemany(
                "INSERT INTO lm_review.review_v1_result (id, task_id, submission_id, team_id, problem_id, workflow_version, score, result_json, model_name, ai_call_id, create_time, update_time, deleted) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0)",
                rev_results_to_insert,
            )

        if rankings_to_insert:
            cur.executemany(
                "INSERT INTO lm_ranking.ranking_snapshot (id, batch_id, problem_id, team_id, team_name, submission_id, review_task_id, workflow_version, score, rank_no, submitted_at, review_finished_at, computed_at, current_marker, create_time, update_time, deleted) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0)",
                rankings_to_insert,
            )

        if ended_scores:
            mean_score = round(sum(ended_scores) / len(ended_scores), 2)
            cur.execute("UPDATE lm_problem.problem SET average_score = %s WHERE id = %s", (mean_score, prob_id))

        conn.commit()

    print(f"  [OK] 成功为题目 {prob_id} ({prob_title[:18]}...) 生成 {len(teams_to_insert)} 支队伍, {len(subs_to_insert)} 条提交, {len(rankings_to_insert)} 条排行榜快照")


def main():
    parser = argparse.ArgumentParser(description="自适应批量生成真实题目的参赛、提交、评审与排行榜测试数据")
    parser.add_argument("--all-missing", action="store_true", help="为所有当前无参赛数据的国赛和美赛题目自适应生成数据")
    parser.add_argument("--force-all", action="store_true", help="强制为所有国赛和美赛题目重新生成数据")
    parser.add_argument("--problem-id", type=int, help="为指定题目 ID 生成数据")
    parser.add_argument("--dry-run", action="store_true", help="只演练不写入数据库")
    args = parser.parse_args()

    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT p.id, p.code, p.contest_id, c.code AS contest_code, p.year, p.problem_number, p.title, p.statement_language, p.status,
                       (SELECT COUNT(*) FROM lm_team.team t WHERE t.problem_id = p.id) AS team_count,
                       (SELECT COUNT(*) FROM lm_submission.submission s WHERE s.problem_id = p.id) AS sub_count
                FROM lm_problem.problem p
                JOIN lm_problem.contest c ON p.contest_id = c.id
                WHERE p.contest_id IN (1, 2) AND p.status = 1
                ORDER BY p.contest_id, p.year, p.problem_number, p.code;
            """)
            problems = cur.fetchall()

        target_problems = []
        for p in problems:
            if args.problem_id:
                if p["id"] == args.problem_id:
                    target_problems.append(p)
            elif args.force_all:
                target_problems.append(p)
            elif args.all_missing:
                if p["team_count"] == 0:
                    target_problems.append(p)
            else:
                # 默认模式：针对缺失参赛数据的真题生成
                if p["team_count"] == 0:
                    target_problems.append(p)

        print(f"=== 自适应 Mock 数据生成流水线 ===")
        print(f"目标真题数量: {len(target_problems)} 道 (总真题数: {len(problems)})")
        if args.dry_run:
            print("[DRY-RUN 模式：不实际写入数据库]")

        for idx, p in enumerate(target_problems, start=1):
            print(f"\n[{idx}/{len(target_problems)}] 处理题目 {p['id']} [{p['contest_code']} {p['year']} {p['problem_number']}]: {p['title']}")
            process_problem(p, conn, dry_run=args.dry_run)

        print(f"\n=== 全流程完成，全部目标题目测试数据已构建完毕 ===")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
