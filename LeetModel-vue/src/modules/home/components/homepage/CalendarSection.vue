<template>
  <div class="calendar-card">
    <!-- 星期 -->

    <div class="week-row">
      <div
        class="day"
        v-for="day in weekDays"
        :key="day.date"
        :class="{
          active: day.isToday,
          weekend: day.isWeekend,
        }"
      >
        <span class="week">{{ day.week }}</span>
        <span class="date">
          {{ day.isToday ? "今" : day.date }}
        </span>
      </div>
    </div>

    <!-- 每日一题 -->

    <div class="daily-problem">
      <div class="daily-header">🔥 每日 1 题</div>

      <div class="problem-title">1878. 矩阵中最大的三个菱形和</div>

      <div class="circle"></div>
    </div>

    <div class="divider"></div>

    <div class="welcome">欢迎来到数学建模评测系统</div>

    <router-link v-if="!userStore.isLogin" to="/register" class="login-btn">
      登录 / 注册
    </router-link>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useUserStore } from "@/store/user";

const userStore = useUserStore();

const today = new Date();

const weekNames = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];

const weekDays = computed(() => {
  const current = new Date();

  const day = current.getDay() || 7;

  const monday = new Date(current);

  monday.setDate(current.getDate() - day + 1);

  const days = [];

  for (let i = 0; i < 7; i++) {
    const d = new Date(monday);

    d.setDate(monday.getDate() + i);

    const dateNum = d.getDate();

    days.push({
      week: weekNames[i],
      date: dateNum,
      isToday: dateNum === today.getDate(),
      isWeekend: i >= 5,
    });
  }

  return days;
});
</script>
<style scoped>
.calendar-card {
  background: #f7f7f7;

  border-radius: 18px;

  padding: 24px;

  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);

  display: flex;

  flex-direction: column;

  gap: 20px;
}

/* 星期栏 */

/* 星期栏 */

.week-row {
  display: flex;
  justify-content: space-between;
}

/* 每个日期 */

.day {
  width: 46px;
  height: 56px;

  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;

  border-radius: 12px;

  transition: all 0.2s;

  cursor: pointer;
}

/* hover 效果 */

.day:hover {
  background: #f1f5ff;
}

/* 星期 */

.week {
  font-size: 11px;
  color: #aaa;
  letter-spacing: 1px;
}

/* 日期 */

.date {
  font-size: 20px;
  font-weight: 600;
  color: #444;
}

/* 今天 */

.day.active {
  background: #409eff;
}

.day.active .week {
  color: rgba(255, 255, 255, 0.8);
}

.day.active .date {
  color: white;
}

/* 周末 */

.weekend .week {
  color: #ff4d4f;
}

.weekend .date {
  color: #ff4d4f;
}
/* 每日一题 */

.daily-problem {
  background: #ececec;

  border-radius: 12px;

  padding: 16px;

  position: relative;
}

.daily-header {
  color: #409eff;

  font-weight: 600;

  margin-bottom: 8px;
}

.problem-title {
  font-size: 16px;

  color: #333;
}

/* 圆圈 */

.circle {
  position: absolute;

  right: 16px;

  top: 50%;

  transform: translateY(-50%);

  width: 18px;

  height: 18px;

  border-radius: 50%;

  border: 2px solid #ddd;
}

/* 分割线 */

.divider {
  height: 1px;

  background: #ddd;
}

/* 欢迎 */

.welcome {
  text-align: center;

  color: #888;

  font-size: 16px;
}

/* 登录按钮 */

.login-btn {
  display: flex;
  align-items: center;
  justify-content: center;

  background: #222;
  color: white;

  border-radius: 10px;

  height: 48px;

  font-size: 16px;

  text-decoration: none;

  transition: 0.2s;
}

.login-btn:hover {
  background: #000;
}
</style>
