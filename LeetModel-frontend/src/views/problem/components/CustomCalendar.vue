<template>
  <div class="custom-calendar">
    <div class="calendar-header">
      <button class="nav-btn" @click="prevMonth">
        <el-icon :size="14"><ArrowLeft /></el-icon>
      </button>
      <span class="year-month">{{ currentYear }}年{{ currentMonth }}月</span>
      <button class="nav-btn" @click="nextMonth">
        <el-icon :size="14"><ArrowRight /></el-icon>
      </button>
    </div>

    <div class="weekdays">
      <span class="weekday" v-for="w in weeks" :key="w">{{ w }}</span>
    </div>

    <div class="days-grid">
      <div v-for="i in prevMonthDaysCount" :key="'prev-' + i" class="day empty" />
      <div
        v-for="day in totalDays" :key="'cur-' + day"
        class="day"
        :class="{ today: isToday(day), completed: completedDays.includes(day) }"
      >{{ day }}</div>
    </div>

    <div class="calendar-footer">
      <span class="legend">
        <span class="dot completed-dot"></span> 已完成
      </span>
      <span class="legend">
        <span class="dot today-dot"></span> 今日
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

const now = new Date()
const currentYear = ref(now.getFullYear())
const currentMonth = ref(now.getMonth() + 1)
const today = now.getDate()
const weeks = ['日', '一', '二', '三', '四', '五', '六']
const completedDays = ref([3, 7, 10, 14, 18, 21, 24, 28])

const totalDays = computed(() => new Date(currentYear.value, currentMonth.value, 0).getDate())
const firstDayOfWeek = computed(() => new Date(currentYear.value, currentMonth.value - 1, 1).getDay())
const prevMonthDaysCount = computed(() => firstDayOfWeek.value)

const prevMonth = () => {
  if (currentMonth.value === 1) { currentYear.value--; currentMonth.value = 12 }
  else currentMonth.value--
}
const nextMonth = () => {
  if (currentMonth.value === 12) { currentYear.value++; currentMonth.value = 1 }
  else currentMonth.value++
}
const isToday = (day) => currentYear.value === now.getFullYear() && currentMonth.value === now.getMonth() + 1 && day === today
</script>

<style scoped>
.custom-calendar {
  width: 100%;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 14px;
}

.calendar-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px;
}

.year-month {
  font-size: 14px; font-weight: 600; color: var(--lm-text-primary);
}

.nav-btn {
  border: none; background: var(--lm-bg-secondary);
  border-radius: var(--lm-radius-sm); cursor: pointer;
  padding: 4px 6px; display: flex; align-items: center;
  color: var(--lm-text-secondary);
}

.nav-btn:hover { background: var(--lm-border); }

.weekdays {
  display: grid; grid-template-columns: repeat(7, 1fr);
  margin-bottom: 6px;
}

.weekday {
  text-align: center; font-size: 11px; color: var(--lm-text-muted); font-weight: 500;
}

.days-grid {
  display: grid; grid-template-columns: repeat(7, 1fr);
  gap: 3px;
}

.day {
  width: 28px; height: 28px; line-height: 28px;
  text-align: center; border-radius: 6px;
  margin: 0 auto; font-size: 12px; color: var(--lm-text-primary);
}

.day.empty { background: transparent; }

.day.today {
  background: var(--lm-primary); color: #ffffff; font-weight: 600;
}

.day.completed {
  background: var(--lm-primary-bg); color: var(--lm-primary); font-weight: 500;
}

.calendar-footer {
  display: flex; justify-content: center; gap: 16px;
  margin-top: 10px; font-size: 11px; color: var(--lm-text-secondary);
}

.legend { display: flex; align-items: center; gap: 4px; }

.dot {
  width: 8px; height: 8px; border-radius: 50%;
}

.completed-dot { background: var(--lm-primary); }
.today-dot { background: var(--lm-primary); opacity: 0.4; }
</style>
