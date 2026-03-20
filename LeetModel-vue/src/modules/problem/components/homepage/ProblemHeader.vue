<template>
  <div class="problem-header">
    <!-- 🔍 搜索 -->
    <div class="search-box">
      <input v-model="keyword" placeholder="搜索题目" class="search-input" />

      <button class="search-btn" @click="emitChange">搜索</button>
    </div>

    <!-- 🎯 难度 -->
    <select v-model="difficulty" @change="emitChange">
      <option value="">全部难度</option>
      <option value="Easy">简单</option>
      <option value="Medium">中等</option>
      <option value="Hard">困难</option>
    </select>

    <!-- 🌐 语言筛选 -->
    <select v-model="language" @change="emitChange">
      <option value="">全部语言</option>
      <option value="CN">CN</option>
      <option value="EN">EN</option>
    </select>

    <!-- 📊 分数范围 -->
    <div class="score-range">
      <input v-model.number="minAveScore" type="number" placeholder="最低分" />
      <span> - </span>
      <input v-model.number="maxAveScore" type="number" placeholder="最高分" />

      <button class="score-btn" @click="handleScoreSearch">确定</button>
    </div>

    <!-- 🔃 排序 -->
    <select v-model="sortOrder" @change="emitChange">
      <option value="asc">正序</option>
      <option value="desc">逆序</option>
    </select>

    <!-- 🏷 标签 -->
    <div class="tag-container">
      <span
        v-for="tag in tagList"
        :key="tag"
        :class="['tag-item', selectedTags.includes(tag) ? 'active' : '']"
        @click="toggleTag(tag)"
      >
        {{ tag }}
      </span>
    </div>

    <!-- 🧹 清空 -->
    <button class="reset-btn" @click="reset">清空筛选</button>
  </div>
</template>

<script setup>
import { ref } from "vue";
const handleScoreSearch = () => {
  emitChange();
};

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

// 清空
const reset = () => {
  keyword.value = "";
  difficulty.value = "";
  language.value = "";
  selectedTags.value = [];
  minAveScore.value = "";
  maxAveScore.value = "";
  sortOrder.value = "asc";

  emitChange();
};
</script>

<style scoped>
.problem-header {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 16px;
}

/* 输入 */
.search-box {
  display: inline-flex;
  align-items: center;
  margin-right: 10px;
}

.search-input {
  width: 180px;
  padding: 6px;
  border: 1px solid #ddd;
  border-radius: 4px 0 0 4px;
}

.search-btn {
  padding: 6px 12px;
  border: 1px solid #409eff;
  background: #409eff;
  color: #fff;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
}

.search-btn:hover {
  background: #66b1ff;
}

/* 分数范围 */
.score-range {
  display: inline-flex;
  align-items: center;
  margin: 10px;
}

.score-range input {
  width: 80px;
  padding: 4px;
  margin-right: 4px;
}

.score-btn {
  margin-left: 6px;
  padding: 4px 10px;
  background: #67c23a;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.score-btn:hover {
  background: #85ce61;
}

/* 标签 */
.tag-container {
  margin-top: 10px;
}

.tag-item {
  display: inline-block;
  padding: 4px 10px;
  margin: 4px;
  background: #eee;
  border-radius: 6px;
  cursor: pointer;
}

.tag-item.active {
  background: #409eff;
  color: #fff;
}

/* 按钮 */
.reset-btn {
  margin-top: 10px;
  padding: 6px 12px;
}
</style>
