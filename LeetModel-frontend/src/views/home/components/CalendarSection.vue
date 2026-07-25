<template>
  <div class="calendar-card">
    <!-- Week Row -->
    <div class="week-row">
      <div
        v-for="day in weekDays"
        :key="day.date"
        class="day-cell"
        :class="{ active: day.isToday, weekend: day.isWeekend }"
      >
        <span class="day-week">{{ day.week }}</span>
        <span class="day-date">{{ day.isToday ? '今' : day.date }}</span>
      </div>
    </div>

    <!-- Streak Counter -->
    <div class="streak-box">
      <div class="streak-flame">
        <el-icon :size="28" color="#f59e0b"><Present /></el-icon>
      </div>
      <div class="streak-info">
        <span class="streak-count">{{ streak }} 天</span>
        <span class="streak-label">连续打卡</span>
      </div>
      <div class="streak-total">
        <span class="total-count">{{ totalCheckins }}</span>
        <span class="total-label">累计打卡</span>
      </div>
    </div>

    <!-- Daily Problem -->
    <div class="daily-problem" @click="$router.push(`/problem/${dailyProblem.id}`)">
      <div class="daily-header">
        <el-icon :size="16"><Sunny /></el-icon>
        <span>每日一题</span>
        <el-tag size="small" :type="difficultyType(dailyProblem.difficulty)">{{ dailyProblem.difficulty }}</el-tag>
      </div>
      <p class="daily-title">{{ dailyProblem.title }}</p>
      <div class="daily-tags">
        <span v-for="tag in dailyProblem.tags" :key="tag" class="daily-tag">{{ tag }}</span>
      </div>
    </div>

    <!-- Welcome / Login -->
    <div class="divider"></div>

    <div v-if="!userStore.isLogin" class="welcome-box">
      <p class="welcome-text">欢迎来到数学建模评测系统</p>
      <router-link to="/register" class="login-btn">
        登录 / 注册
      </router-link>
    </div>
    <div v-else class="user-greeting">
      <p class="greeting-text">{{ greeting }}, <strong>{{ userStore.username }}</strong></p>
      <router-link to="/profile" class="profile-link">查看个人主页</router-link>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useUserStore } from '@/store/user'
import { Present, Sunny } from '@element-plus/icons-vue'
import { mockProblems } from '@/mock/data.js'

const userStore = useUserStore()

const streak = ref(15)
const totalCheckins = ref(128)

const dailyProblem = ref({
  id: 10,
  title: '蛋白质结构预测入门',
  difficulty: '入门',
  tags: ['神经网络', '生物医学'],
})

const today = new Date()
const weekNames = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

const weekDays = computed(() => {
  const current = new Date()
  const day = current.getDay() || 7
  const monday = new Date(current)
  monday.setDate(current.getDate() - day + 1)
  const days = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(monday)
    d.setDate(monday.getDate() + i)
    days.push({
      week: weekNames[i],
      date: d.getDate(),
      isToday: d.getDate() === today.getDate() && d.getMonth() === today.getMonth(),
      isWeekend: i >= 5,
    })
  }
  return days
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

function difficultyType(d) {
  const map = { '入门': 'success', '中等': 'warning', '困难': 'danger', '挑战': 'danger' }
  return map[d] || 'info'
}
</script>

<style scoped>
.calendar-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* Week Row */
.week-row {
  display: flex;
  justify-content: space-between;
}

.day-cell {
  width: 44px;
  height: 54px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border-radius: var(--lm-radius-sm);
  transition: all var(--lm-transition);
  cursor: pointer;
}

.day-cell:hover {
  background: var(--lm-primary-bg);
}

.day-week {
  font-size: 10px;
  color: var(--lm-text-muted);
  letter-spacing: 0.5px;
  font-weight: 500;
}

.day-date {
  font-size: 18px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin-top: 2px;
}

.day-cell.active {
  background: var(--lm-primary);
}

.day-cell.active .day-week {
  color: rgba(255, 255, 255, 0.8);
}

.day-cell.active .day-date {
  color: #ffffff;
}

.day-cell.weekend .day-date {
  color: var(--lm-danger);
}

.day-cell.active.weekend .day-date {
  color: #ffffff;
}

/* Streak Box */
.streak-box {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: linear-gradient(135deg, #fffbeb, #fef3c7);
  border-radius: var(--lm-radius);
}

.streak-flame {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #fef3c7;
  display: flex;
  align-items: center;
  justify-content: center;
}

.streak-info {
  flex: 1;
}

.streak-count {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #d97706;
  line-height: 1.2;
}

.streak-label {
  font-size: 12px;
  color: #92400e;
}

.streak-total {
  text-align: right;
}

.total-count {
  display: block;
  font-size: 18px;
  font-weight: 600;
  color: var(--lm-text-primary);
  line-height: 1.2;
}

.total-label {
  font-size: 12px;
  color: var(--lm-text-secondary);
}

/* Daily Problem */
.daily-problem {
  background: var(--lm-primary-bg);
  border: 1px solid #bfdbfe;
  border-radius: var(--lm-radius);
  padding: 16px;
  cursor: pointer;
  transition: all var(--lm-transition);
}

.daily-problem:hover {
  border-color: var(--lm-primary);
  box-shadow: var(--lm-shadow-sm);
}

.daily-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--lm-primary);
}

.daily-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin: 0 0 10px;
}

.daily-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.daily-tag {
  font-size: 11px;
  color: var(--lm-text-secondary);
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 8px;
  border-radius: 4px;
}

/* Divider */
.divider {
  height: 1px;
  background: var(--lm-border-light);
}

/* Welcome / Login */
.welcome-box {
  text-align: center;
}

.welcome-text {
  font-size: 15px;
  color: var(--lm-text-secondary);
  margin: 0 0 14px;
}

.login-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lm-text-primary);
  color: #ffffff;
  border-radius: var(--lm-radius);
  height: 44px;
  font-size: 15px;
  text-decoration: none;
  font-weight: 500;
  transition: background var(--lm-transition);
}

.login-btn:hover {
  background: #334155;
}

.user-greeting {
  text-align: center;
}

.greeting-text {
  font-size: 14px;
  color: var(--lm-text-secondary);
  margin: 0 0 10px;
}

.profile-link {
  font-size: 13px;
  color: var(--lm-primary);
  text-decoration: none;
  font-weight: 500;
}

.profile-link:hover {
  color: var(--lm-primary-dark);
}
</style>
