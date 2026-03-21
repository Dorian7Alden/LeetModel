<template>
  <div class="problem-header">
    <!-- 标签 -->
    <div class="tag-container">
      <span
        v-for="tag in tagList"
        :key="tag"
        :class="['tag', selectedTags.includes(tag) ? 'active' : '']"
        @click="toggleTag(tag)"
      >
        {{ tag }}
      </span>
    </div>
    <!-- 🔥 一整行筛选 -->
    <div class="filter-row">
      <!-- 搜索 -->
      <div class="search-box">
        <input v-model="keyword" placeholder="搜索题目..." />
        <button @click="emitChange">搜索</button>
      </div>

      <!-- 难度 -->
      <select v-model="difficulty" @change="emitChange">
        <option value="">难度</option>
        <option value="Easy">简单</option>
        <option value="Medium">中等</option>
        <option value="Hard">困难</option>
      </select>

      <!-- 语言 -->
      <select v-model="language" @change="emitChange">
        <option value="">语言</option>
        <option value="CN">中文</option>
        <option value="EN">英文</option>
      </select>

      <!-- 排序 -->
      <select v-model="sortOrder" @change="emitChange">
        <option value="asc">正序</option>
        <option value="desc">倒序</option>
      </select>

      <!-- 分数 -->
      <div class="score-row">
        <span class="label">评分：</span>

        <!-- ⭐ 下拉选择 -->
        <select v-model="selectedRange" @change="handleRangeChange">
          <option value="">区间选择</option>
          <option
            v-for="range in scoreRanges"
            :key="range.label"
            :value="range.label"
          >
            {{ range.label }}
          </option>
        </select>

        <!-- ⭐ 手动输入 -->
        <input
          v-model.number="minAveScore"
          type="number"
          placeholder="最低"
          @input="clearRange"
        />
        <span>-</span>
        <input
          v-model.number="maxAveScore"
          type="number"
          placeholder="最高"
          @input="clearRange"
        />
      </div>
      <!-- 清空 -->
      <button class="reset-btn" @click="reset">清空</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
// const handleScoreSearch = () => {
//   emitChange();
// };

// 标签

const tagList = [
  "国赛",
  "美赛",
  "评价模型",
  "预测模型",
  "动态规划",
  "数据分析",
  "神经网络",
  "遗传算法",
  "模拟退火",
  "时间序列",
  "回归分析",
  "图像处理",
  "文本挖掘",
  "优化问题",
  "分类问题",
];

// 条件
const keyword = ref("");
const difficulty = ref("");
const language = ref("");
const selectedTags = ref([]);
const minAveScore = ref("");
const maxAveScore = ref("");
const sortOrder = ref("asc");

const emit = defineEmits(["change"]);

// 标签切换
const toggleTag = (tag) => {
  const index = selectedTags.value.indexOf(tag);

  if (index > -1) {
    selectedTags.value.splice(index, 1);
  } else {
    selectedTags.value.push(tag);
  }

  emitChange();
};

// ⭐ 核心：统一抛出所有条件
const emitChange = () => {
  emit("change", {
    keyword: keyword.value,
    difficulty: difficulty.value,
    language: language.value,
    tags: selectedTags.value,
    minAveScore: minAveScore.value, // ⭐改名
    maxAveScore: maxAveScore.value, // ⭐改名
    sortOrder: sortOrder.value,
  });
};
// ⭐ 下拉选中值
const selectedRange = ref("");

// 固定区间
const scoreRanges = [
  { label: "0-20", min: 0, max: 20 },
  { label: "20-40", min: 20, max: 40 },
  { label: "40-60", min: 40, max: 60 },
  { label: "60-80", min: 60, max: 80 },
  { label: "80-100", min: 80, max: 100 },
];

// ⭐ 选择下拉
const handleRangeChange = () => {
  const range = scoreRanges.find((r) => r.label === selectedRange.value);

  if (range) {
    minAveScore.value = range.min;
    maxAveScore.value = range.max;
  } else {
    minAveScore.value = "";
    maxAveScore.value = "";
  }

  emitChange();
};

// ⭐ 手动输入时取消下拉选中
const clearRange = () => {
  selectedRange.value = "";
  emitChange();
};

// 清空
const reset = () => {
  keyword.value = "";
  difficulty.value = "";
  language.value = "";
  selectedTags.value = [];
  minAveScore.value = "";
  maxAveScore.value = "";
  sortOrder.value = "asc";
  selectedRange.value = "";

  emitChange();
};
</script>

<style scoped>
/* 一整行 */
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;

  flex-wrap: nowrap; /* ❗禁止换行 */
  overflow-x: auto; /* ⭐ 超出可横向滚动 */
}

/* 统一高度 */
/* 全部统一 */
.filter-row select,
.score-row input,
.search-box input,
.search-box button {
  height: 36px;
}
.filter-row > * {
  flex-shrink: 0; /* ❗不允许被压缩 */
}
.problem-header {
  background: #fff;
  padding: 18px;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: relative;
  top: 0;
  z-index: 10;
}
.problem-header::after {
  content: "";
  position: absolute;
  right: 0;
  top: 0;
  width: 40px;
  height: 100%;
  background: linear-gradient(to right, transparent, #fff);
  pointer-events: none;
}

/* 搜索 */
.search-box {
  display: flex;
}

.search-box input {
  width: 150px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid #ddd;
  border-radius: 6px 0 0 6px;
  outline: none;
}

.search-box button {
  height: 36px;
  padding: 0 14px;
  border: none;
  background: #409eff;
  color: #fff;
  border-radius: 0 6px 6px 0;
  cursor: pointer;
}

.search-box button:hover {
  background: #2f7de1;
}

/* 筛选 */
.filters {
  display: flex;
  gap: 10px;
}

.filters select {
  height: 36px;
  padding: 0 10px;
  border: 1px solid #ddd;
  border-radius: 6px;
  cursor: pointer;
}

/* 分数 */
.score-row {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.score-row select {
  height: 34px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid #ddd;
  cursor: pointer;
}

.score-row input {
  width: 60px;
  height: 34px;
  padding: 0 6px;
  border: 1px solid #ddd;
  border-radius: 6px;
}

/* 分数标签 */
.score-tag {
  padding: 4px 10px;
  background: #f2f3f5;
  border-radius: 16px;
  font-size: 13px;
  cursor: pointer;
  transition: 0.2s;
}

.score-tag:hover {
  background: #e4e7ed;
}

/* 选中 */
.score-tag.active {
  background: #67c23a;
  color: #fff;
}
.label {
  color: #666;
  font-size: 14px;
}

.dash {
  color: #999;
}

.confirm {
  padding: 6px 12px;
  background: #67c23a;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.confirm:hover {
  background: #5daf34;
}

/* 标签 */
.tag-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 6px 12px;
  border-radius: 20px;
  background: #f2f3f5;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.tag:hover {
  background: #e4e7ed;
}

.tag.active {
  background: #409eff;
  color: white;
}

/* 底部 */
.bottom-row {
  display: flex;
  justify-content: flex-end;
}

.reset-btn {
  padding: 6px 12px;
  background: #f56c6c;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.reset-btn:hover {
  background: #dd6161;
}
</style>
