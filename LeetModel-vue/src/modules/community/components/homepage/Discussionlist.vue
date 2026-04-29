<template>
  <div class="discussion-page">
    <!-- 🔍 筛选区 -->
    <div class="filter-bar">
      <input v-model="query.keyword" placeholder="搜索帖子..." class="input" />

      <!-- 类型 -->
      <select v-model="query.type" @change="fetchList" class="select">
        <option value="">全部类型</option>
        <option value="experience">经验</option>
        <option value="skill">技巧</option>
        <option value="discuss">讨论</option>
      </select>

      <!-- 排序 -->
      <select v-model="query.sortField" @change="fetchList" class="select">
        <option value="createTime">最新</option>
        <option value="likeCnt">点赞最多</option>
        <option value="viewCnt">浏览最多</option>
        <option value="commentCnt">评论最多</option>
        <option value="heat">最热</option>
      </select>

      <button class="btn" @click="handleSearch">搜索</button>
      <button class="btn reset-btn" @click="handleReset">清空</button>
    </div>

    <!-- 📋 列表 -->
    <div v-if="list.length > 0">
      <div v-for="item in list" :key="item.id" class="post-card">
        <h3 class="title">{{ item.title }}</h3>

        <p class="content">
          {{ formatContent(item.content) }}
        </p>

        <div class="meta">
          <span class="meta-item">{{ item.likeCnt }} 赞</span>
          <span class="meta-item">
            <el-icon><View /></el-icon>
            {{ item.viewCnt }}
          </span>
          <span class="meta-item">
            <el-icon><ChatDotSquare /></el-icon>
            {{ item.commentCnt }}
          </span>
          <span class="meta-item">
            <el-icon><TrendCharts /></el-icon>
            {{ item.heat }}
          </span>
        </div>

        <div class="footer">
          <span>类型：{{ item.type }}</span>
          <span>发布时间：{{ item.createTime }}</span>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty">暂无数据</div>

    <!-- 📄 分页 -->
    <div class="pagination">
      <button @click="prevPage" :disabled="query.pageNum === 1">上一页</button>

      <span>第 {{ query.pageNum }} 页</span>

      <button @click="nextPage">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { getPostList } from "@/api/post";
import { View, ChatDotSquare, TrendCharts } from "@element-plus/icons-vue";

// 📦 列表数据
const list = ref([]);

// // 🔍 查询参数（完全对齐后端）
// const query = ref({
//   pageNum: 1,
//   pageSize: 10,
//   keyword: "",
//   type: "",
//   sortField: "createTime",
//   sortOrder: "desc",
// });

// 🚀 获取数据
const fetchList = async () => {
  try {
    const res = await getPostList(query.value);

    console.log("帖子数据：", res);

    // 🔥 核心：正确取值
    list.value = res.data.list || [];
  } catch (err) {
    console.error("获取帖子失败：", err);
  }
};

// 🔍 搜索
const handleSearch = () => {
  query.value.pageNum = 1;
  fetchList();
};

// ➡️ 下一页
const nextPage = () => {
  query.value.pageNum++;
  fetchList();
};

// ⬅️ 上一页
const prevPage = () => {
  if (query.value.pageNum > 1) {
    query.value.pageNum--;
    fetchList();
  }
};

// 🧹 简单处理内容（去掉 markdown 符号）
const formatContent = (content) => {
  if (!content) return "";
  return (
    content
      .replace(/[#>*`]/g, "")
      .replace(/\n/g, " ")
      .slice(0, 100) + "..."
  );
};

// 初始化
onMounted(() => {
  fetchList();
});
const defaultQuery = {
  pageNum: 1,
  pageSize: 10,
  keyword: "",
  type: "",
  sortField: "createTime",
  sortOrder: "desc",
};

const query = ref({ ...defaultQuery });

// ✅ 清空 + 刷新
const handleReset = () => {
  query.value = { ...defaultQuery }; // 🔥 重新赋值（不要逐个改）

  fetchList(); // 重新请求
};
</script>

<style scoped>
.discussion-page {
  max-width: 800px;
  margin: 0 auto;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.input {
  flex: 1;
  padding: 6px;
}

.select {
  padding: 6px;
}

.btn {
  padding: 6px 12px;
  cursor: pointer;
}

/* 卡片 */
.post-card {
  border: 1px solid #eee;
  padding: 15px;
  margin-bottom: 15px;
  border-radius: 8px;
}

.title {
  margin-bottom: 10px;
}

.content {
  color: #666;
  margin-bottom: 10px;
}

.meta {
  display: flex;
  gap: 15px;
  font-size: 14px;
  color: #999;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.footer {
  margin-top: 10px;
  font-size: 12px;
  color: #bbb;
}

/* 分页 */
.pagination {
  margin-top: 20px;
  text-align: center;
}

.empty {
  text-align: center;
  color: #999;
}
</style>
