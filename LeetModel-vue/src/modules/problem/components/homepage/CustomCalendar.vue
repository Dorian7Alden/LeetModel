<template>
  <div class="leetcode-calendar">
    <!-- 头部 -->
    <div class="calendar-header">
      <button class="prev-btn" @click="prevMonth">‹</button>
      <span class="year-month">{{ currentYear }}年{{ currentMonth }}月</span>
      <button class="next-btn" @click="nextMonth">›</button>
    </div>

    <!-- 星期 -->
    <div class="weekdays">
      <span class="weekday" v-for="w in weeks" :key="w">{{ w }}</span>
    </div>

    <!-- 日期 -->
    <div class="days-grid">
      <!-- 上月占位 -->
      <div
        v-for="index in prevMonthDaysCount"
        :key="`prev-${index}`"
        class="day empty"
      />

      <!-- 本月 -->
      <div
        v-for="day in totalDays"
        :key="`current-${day}`"
        class="day"
        :class="{
          today: isToday(day),
          completed: completedDays.includes(day),
        }"
      >
        {{ day }}
      </div>
    </div>

    <!-- 底部 -->
    <div class="calendar-footer">
      <span class="legend">
        <span class="dot completed-dot"></span>
        已完成
      </span>
      <span class="legend">
        <span class="dot today-dot"></span>
        今日
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";

/* ---------------- 基础状态 ---------------- */
const now = new Date();

const currentYear = ref(now.getFullYear());
const currentMonth = ref(now.getMonth() + 1);
const today = now.getDate();

const weeks = ["日", "一", "二", "三", "四", "五", "六"];

// 模拟数据（后面可以换接口）
const completedDays = ref([5, 8, 12, 15, 18, 22, 25]);

/* ---------------- 计算属性 ---------------- */

// 当月总天数
const totalDays = computed(() => {
  return new Date(currentYear.value, currentMonth.value, 0).getDate();
});

// 当月1号星期几
const firstDayOfWeek = computed(() => {
  return new Date(currentYear.value, currentMonth.value - 1, 1).getDay();
});

// 上月占位
const prevMonthDaysCount = computed(() => {
  return firstDayOfWeek.value;
});

/* ---------------- 方法 ---------------- */

// 上一月
const prevMonth = () => {
  if (currentMonth.value === 1) {
    currentYear.value--;
    currentMonth.value = 12;
  } else {
    currentMonth.value--;
  }
};

// 下一月
const nextMonth = () => {
  if (currentMonth.value === 12) {
    currentYear.value++;
    currentMonth.value = 1;
  } else {
    currentMonth.value++;
  }
};

// 是否今天
const isToday = (day) => {
  return (
    currentYear.value === now.getFullYear() &&
    currentMonth.value === now.getMonth() + 1 &&
    day === today
  );
};
</script>

<style scoped>
.leetcode-calendar {
  width: 100%;
  max-width: 280px;
  background: #ffffff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

/* 头部 */
.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
}

.prev-btn,
.next-btn {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
}

.prev-btn:hover,
.next-btn:hover {
  background: #f5f5f5;
}

/* 星期 */
.weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 8px;
}

.weekday {
  text-align: center;
  font-size: 12px;
  color: #999;
}

/* 日期 */
.days-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.day {
  width: 32px;
  height: 32px;
  line-height: 32px;
  text-align: center;
  border-radius: 6px;
  margin: 0 auto;
}

.day.empty {
  background: transparent;
}

/* 今日 */
.day.today {
  background: #ffa726;
  color: white;
}

/* 已完成 */
.day.completed {
  background: #e8f5e9;
  color: #2e7d32;
}

/* 底部 */
.calendar-footer {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 12px;
  font-size: 12px;
}

.legend {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.completed-dot {
  background: #2e7d32;
}

.today-dot {
  background: #ffa726;
}
</style>
