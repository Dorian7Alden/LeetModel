#!/usr/bin/env python3
"""
LeetModel 赛题级规模提交流水线生成脚本 (Problem-level Submissions & Reviews Generator)

核心目标：
让每个题目拥有约 500 个提交与评审结果（例如 480~520 条），将这约 500 次提交自适应分配给该题目的各个参赛队伍，
形成逼真的队伍版本迭代轨迹与模型学习演进曲线，并确保最终版本受锁且排行榜快照完整对齐。
"""
from __future__ import annotations

import argparse
import json
import math
import random
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import pymysql

ROOT = Path(__file__).resolve().parent.parent.parent
MOCK_ROOT = ROOT / "LeetModel-mock"
sys.path.insert(0, str(MOCK_ROOT))

from app.snowflake import Snowflake

# 2025 美赛题目 ID
PROBLEMS_2025_IDS = [
    2097509647494889474,  # A 楼梯磨损
    2097509740369362945,  # B 朱诺旅游
    2097509845520564225,  # C 奥运奖牌
    2097509961371435009,  # D 城市路线
    2097510037481275393,  # E 农业空间
    2097510114111209473,  # F 网络强国
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


def partition_submissions(teams_count: int, total_subs: int, rng: random.Random) -> list[int]:
    """将题目的总提交数（约500次）自适应、有梯次地分配给各个队伍"""
    if teams_count <= 0:
        return []
    min_per_team = max(2, min(15, total_subs // (teams_count * 2)))
    remaining = total_subs - (teams_count * min_per_team)
    if remaining < 0:
        return [total_subs // teams_count] * teams_count

    # 使用 Pareto 权重产生不同活跃度的队伍（头部队伍提交频繁，尾部适度）
    weights = [rng.paretovariate(1.4) for _ in range(teams_count)]
    total_w = sum(weights)
    shares = [int(remaining * w / total_w) for w in weights]
    diff = remaining - sum(shares)
    for i in range(diff):
        shares[i % teams_count] += 1
    return [min_per_team + s for s in shares]


def generate_score_samples(count: int, rng: random.Random) -> list[float]:
    """使用 Beta 分布生成拟真竞赛分数梯次 (覆盖 O/M/H/S/需改进)"""
    scores = []
    for _ in range(count):
        raw = rng.betavariate(4.0, 2.0)
        score = round(48.0 + raw * 50.0, 1)
        score = min(98.5, max(46.0, score))
        scores.append(score)
    scores.sort(reverse=True)
    return scores


def generate_learning_curve(num_subs: int, final_score: float, rng: random.Random) -> list[float]:
    """生成单支队伍从初版到终版的模型演进提升曲线"""
    start_score = max(35.0, final_score - rng.uniform(25.0, 42.0))
    scores = []
    for v in range(1, num_subs + 1):
        progress = v / float(num_subs)
        s_factor = 1.0 / (1.0 + math.exp(-6.0 * (progress - 0.4)))
        base = start_score + (final_score - start_score) * s_factor
        noise = rng.uniform(-2.5, 2.5) * (1.0 - progress * 0.75)
        sc = round(min(final_score, max(30.0, base + noise)), 1)
        scores.append(sc)
    scores[-1] = final_score
    return scores


def build_review_json(score: float, problem_title: str, version: int, total_versions: int, team_name: str, rng: random.Random) -> str:
    dim_ar = round(min(100.0, max(30.0, score + rng.uniform(-3.0, 2.5))), 1)
    dim_mc = round(min(100.0, max(30.0, score + rng.uniform(-4.0, 3.0))), 1)
    dim_rc = round(min(100.0, max(30.0, score + rng.uniform(-2.5, 2.5))), 1)
    dim_ec = round(min(100.0, max(30.0, score + rng.uniform(-3.0, 3.0))), 1)

    if score >= 90.0:
        tier = "卓越特等奖水平"
        summary = f"《{team_name}》第 {version} 版提交在【{problem_title}】上表现卓越，推导严密自洽，数值仿真与敏感性检验充分。"
    elif score >= 80.0:
        tier = "一等奖水平"
        summary = f"第 {version} 版模型整体结构完整，核心机理刻画清晰，相比前序版本在算法收敛性与实验对比上有明显提升。"
    elif score >= 70.0:
        tier = "二等奖水平"
        summary = f"第 {version} 版基本满足端到端建模求解要求，各小问解答自洽，图表与文字表达具备良好工程水准。"
    elif score >= 60.0:
        tier = "成功参赛水平"
        summary = f"第 {version} 版初步完成了基础变量与关系式的推导，但关键参数仍缺少充分的敏感性讨论与对比实验。"
    else:
        tier = "初级探索阶段"
        summary = f"第 {version} 版处于早期探索阶段，核心物理/数学假设有待进一步校验，代码收敛性需要持续优化。"

    output = {
        "score": round(score, 1),
        "version": version,
        "tier": tier,
        "summary": summary,
        "dimensions": {
            "assumptionRationality": {"score": dim_ar, "comment": f"假设评分 {dim_ar}"},
            "modelCreativity": {"score": dim_mc, "comment": f"模型评分 {dim_mc}"},
            "resultCorrectness": {"score": dim_rc, "comment": f"结果评分 {dim_rc}"},
            "expressionClarity": {"score": dim_ec, "comment": f"清晰度评分 {dim_ec}"},
        },
        "strengths": [f"第 {version} 次迭代重点优化了模型求解效率与参数精度"],
        "weaknesses": ["极端边界扰动下的鲁棒性区间仍可进一步拓宽"],
        "suggestions": ["继续强化多组对比实验，增强结论的实际工程指导意义"],
    }
    return json.dumps(output, ensure_ascii=False)


def main():
    parser = argparse.ArgumentParser(description="按题目维度生成约 500 个提交与评审结果")
    parser.add_argument("--target", choices=["2025", "all"], default="all", help="目标题目范围：2025=2025美赛, all=全平台真实题目(默认)")
    parser.add_argument("--problem-id", type=int, help="指定题目 ID")
    parser.add_argument("--count", type=int, default=500, help="每个题目目标提交总量 (默认约 500 次)")
    parser.add_argument("--dry-run", action="store_true", help="仅演练统计不实际写入")
    args = parser.parse_args()

    conn = get_db_connection()
    try:
        # 1. 查询目标题目
        prob_query = """
            SELECT p.id, p.code, p.contest_id, c.code AS contest_code, p.year, p.problem_number, p.title
            FROM lm_problem.problem p
            JOIN lm_problem.contest c ON p.contest_id = c.id
            WHERE p.contest_id IN (1, 2) AND p.status = 1
        """
        if args.problem_id:
            prob_query += f" AND p.id = {args.problem_id}"
        elif args.target == "2025":
            prob_query += f" AND p.id IN ({','.join(str(i) for i in PROBLEMS_2025_IDS)})"

        prob_query += " ORDER BY p.contest_id, p.year, p.problem_number, p.code;"

        with conn.cursor() as cur:
            cur.execute(prob_query)
            problems = cur.fetchall()

        print("=== 赛题级规模提交流水线生成 ===")
        print(f"匹配目标题目: {len(problems)} 道")
        print(f"单题目标提交量: 约 {args.count} 条")
        print(f"全量预计生成提交与评审: 约 {len(problems) * args.count:,} 条")
        if args.dry_run:
            print("[DRY-RUN 模式：不实际写入数据库]\n")
            return

        sub_sf = Snowflake(datacenter_id=3, worker_id=11)
        task_sf = Snowflake(datacenter_id=3, worker_id=12)
        res_sf = Snowflake(datacenter_id=3, worker_id=13)
        lock_sf = Snowflake(datacenter_id=3, worker_id=14)
        rank_sf = Snowflake(datacenter_id=3, worker_id=15)

        t_start_all = time.time()
        total_inserted = 0

        for p_idx, prob in enumerate(problems, start=1):
            p_id = prob["id"]
            p_title = prob["title"]
            p_year = prob["year"]
            p_letter = prob["problem_number"]
            c_code = prob["contest_code"]
            rng = random.Random(p_id)

            # 查询该题目下的队伍
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT id, name, leader_id, started_at, ended_at "
                    "FROM lm_team.team t WHERE t.problem_id = %s AND t.status = 1 ORDER BY t.id",
                    (p_id,),
                )
                teams = cur.fetchall()

            if not teams:
                print(f"[{p_idx}/{len(problems)}] 题目 {p_id} ({p_title[:16]}...): 无队伍，跳过")
                continue

            # 单题总提交量围绕目标值随机微浮动（如 485~515）
            target_subs_for_problem = rng.randint(max(50, args.count - 15), args.count + 15)
            team_sub_counts = partition_submissions(len(teams), target_subs_for_problem, rng)
            team_target_scores = generate_score_samples(len(teams), rng)

            # 基础时间
            contest_month = 2 if c_code == "MCM_ICM" else 9
            base_start = datetime(p_year, contest_month, 18, 8, 0, 0)

            prob_subs = []
            prob_tasks = []
            prob_results = []
            prob_locks = []
            rankings_data = []

            for t_idx, team in enumerate(teams):
                t_id = team["id"]
                t_name = team["name"]
                t_leader = team["leader_id"]
                final_score = team_target_scores[t_idx]
                n_subs = team_sub_counts[t_idx]

                scores = generate_learning_curve(n_subs, final_score, rng)
                t_start = team["started_at"] or base_start + timedelta(hours=rng.randint(0, 8))
                time_step = (85.0 * 60) / float(n_subs)  # 85 小时完成所有提交

                final_sub_id = None
                final_task_id = None
                final_sub_time = None
                final_rev_time = None

                for v in range(1, n_subs + 1):
                    sid = sub_sf.next_id()
                    tid = task_sf.next_id()
                    rid = res_sf.next_id()
                    v_sc = scores[v - 1]

                    sub_dt = t_start + timedelta(minutes=int((v - 1) * time_step + rng.randint(0, 3)))
                    dur = rng.randint(15, 45)
                    rev_dt = sub_dt + timedelta(seconds=dur)

                    f_bytes = rng.randint(9 * 1024 * 1024, 18 * 1024 * 1024)
                    orig_fn = f"{c_code}_{p_year}_{p_letter}_Team{t_id}_v{v}.pdf"
                    obj_name = f"submissions/{t_id}/{c_code}_{p_year}_{p_letter}_v{v}_{sid}.pdf"

                    prob_subs.append((
                        sid, t_id, p_id, t_leader, v, orig_fn, obj_name, f_bytes, "SUCCESS", sub_dt, sub_dt
                    ))

                    tr_id = f"mass-trace-{tid}"
                    idm_k = f"mass-idm-{sid}"
                    prob_tasks.append((
                        tid, sid, 1, t_id, p_id, "COMPLETED", 100, tr_id, "BASIC_REVIEW_V1",
                        "mass-worker", "mass-token", rev_dt, rev_dt, idm_k, sub_dt, sub_dt, rev_dt
                    ))

                    r_json = build_review_json(v_sc, p_title, v, n_subs, t_name, rng)
                    prob_results.append((
                        rid, tid, sid, t_id, p_id, "BASIC_REVIEW_V1", v_sc, r_json, "gemini-3.8-flash-high",
                        f"call-{tid}", sub_dt, rev_dt
                    ))

                    if v == n_subs:
                        final_sub_id = sid
                        final_task_id = tid
                        final_sub_time = sub_dt
                        final_rev_time = rev_dt

                # 终稿锁定
                lid = lock_sf.next_id()
                prob_locks.append((lid, t_id, final_sub_id, final_sub_time))
                rankings_data.append({
                    "team_id": t_id,
                    "team_name": t_name,
                    "submission_id": final_sub_id,
                    "review_task_id": final_task_id,
                    "score": final_score,
                    "submitted_at": final_sub_time,
                    "review_finished_at": final_rev_time,
                })

            # 对本题所有队伍按终稿分数降序排序并赋榜单名次
            rankings_data.sort(key=lambda x: x["score"], reverse=True)
            batch_id = f"batch-demo-{p_id}"
            computed_at = (base_start + timedelta(days=5)).strftime("%Y-%m-%d %H:%M:%S")
            prob_rankings = []
            for rank_no, rk in enumerate(rankings_data, start=1):
                rank_id = rank_sf.next_id()
                prob_rankings.append((
                    rank_id, batch_id, p_id, rk["team_id"], rk["team_name"],
                    rk["submission_id"], rk["review_task_id"], "BASIC_REVIEW_V1",
                    rk["score"], rank_no, rk["submitted_at"], rk["review_finished_at"],
                    computed_at, 1, computed_at, computed_at
                ))

            # 执行该题目的数据库批量刷新
            with conn.cursor() as cur:
                # 清理该题历史提交与评审
                cur.execute("DELETE FROM lm_submission.submission_lock WHERE submission_id IN (SELECT id FROM lm_submission.submission WHERE problem_id = %s)", (p_id,))
                cur.execute("DELETE FROM lm_review.review_v1_result WHERE problem_id = %s", (p_id,))
                cur.execute("DELETE FROM lm_review.review_task WHERE problem_id = %s", (p_id,))
                cur.execute("DELETE FROM lm_submission.submission WHERE problem_id = %s", (p_id,))
                cur.execute("DELETE FROM lm_ranking.ranking_snapshot WHERE problem_id = %s", (p_id,))

                cur.executemany(
                    "INSERT INTO lm_submission.submission (id, team_id, problem_id, submitter_id, version, original_filename, object_name, file_size, status, create_time, update_time, deleted) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0)",
                    prob_subs,
                )
                cur.executemany(
                    "INSERT INTO lm_review.review_task (id, submission_id, version_id, team_id, problem_id, status, priority, trace_id, workflow_version, lease_owner, lease_token, lease_expires_at, heartbeat_at, ai_idempotency_key, next_run_at, started_at, finished_at, create_time, update_time, deleted) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW(), 0)",
                    prob_tasks,
                )
                cur.executemany(
                    "INSERT INTO lm_review.review_v1_result (id, task_id, submission_id, team_id, problem_id, workflow_version, score, result_json, model_name, ai_call_id, create_time, update_time, deleted) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0)",
                    prob_results,
                )
                cur.executemany(
                    "INSERT INTO lm_submission.submission_lock (id, team_id, submission_id, locked_at) VALUES (%s, %s, %s, %s)",
                    prob_locks,
                )
                cur.executemany(
                    "INSERT INTO lm_ranking.ranking_snapshot (id, batch_id, problem_id, team_id, team_name, submission_id, review_task_id, workflow_version, score, rank_no, submitted_at, review_finished_at, computed_at, current_marker, create_time, update_time, deleted) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0)",
                    prob_rankings,
                )
                for rk in rankings_data:
                    cur.execute(
                        "UPDATE lm_team.team SET practice_status = 'ENDED', ended_at = %s WHERE id = %s",
                        (rk["submitted_at"], rk["team_id"]),
                    )

                conn.commit()

            total_inserted += len(prob_subs)
            print(f"[{p_idx}/{len(problems)}] 题目 {p_id} [{c_code} {p_year} {p_letter}]: 成功写入 {len(prob_subs)} 条提交与评审 (覆盖 {len(teams)} 支队伍, 累计: {total_inserted:,} 条)")

        t_cost = time.time() - t_start_all
        print(f"\n=== 全量赛题提交流水线生成完毕 ===")
        print(f"覆盖题目: {len(problems)} 道")
        print(f"累计生成提交与评审: {total_inserted:,} 条")
        print(f"总耗时: {t_cost:.2f} 秒 (平均单题约 {total_inserted / max(1, len(problems)):.0f} 条提交)")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
