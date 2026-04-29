<template>
  <div class="community-section">
    <h2 class="title">
      <el-icon><TrendCharts /></el-icon>
      热门帖子
    </h2>

    <!-- 列表 -->
    <div v-if="postList.length > 0">
      <div
        v-for="item in postList"
        :key="item.id"
        class="post-item"
        @click="goDetail(item.id)"
      >
        <div class="left">
          <h3 class="post-title">{{ item.title }}</h3>
          <p class="post-content">
            {{ formatContent(item.content) }}
          </p>
        </div>

        <div class="right">
          <div class="stat">
            <el-icon><TrendCharts /></el-icon>
            {{ item.heat }}
          </div>
          <div class="stat">{{ item.likeCnt }} 赞</div>
        </div>
      </div>
    </div>

    <div v-else class="empty">暂无热门帖子</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { getPostList } from "@/api/post";
import { TrendCharts } from "@element-plus/icons-vue";

const router = useRouter();

const postList = ref([]);

// 🚀 获取热门帖子
const fetchHotPosts = async () => {
  try {
    const res = await getPostList({
      pageNum: 1,
      pageSize: 5,
      sortField: "heat", // 🔥 核心：按热度
      sortOrder: "desc",
    });

    console.log("热门帖子：", res);

    postList.value = res.data.list || [];
  } catch (e) {
    console.error("获取热门帖子失败", e);
  }
};

// 👉 跳转详情页（后面你可以做）
const goDetail = (id) => {
  router.push(`/post/${id}`);
};

// 简单内容截断
const formatContent = (content) => {
  if (!content) return "";
  return (
    content
      .replace(/[#>*`]/g, "")
      .replace(/\n/g, " ")
      .slice(0, 60) + "..."
  );
};

onMounted(() => {
  fetchHotPosts();
});
</script>

<style scoped>
.community-section {
  padding: 20px;
  background: #fff;
  border-radius: 10px;
}

.title {
  margin-bottom: 15px;
}

.post-item {
  display: flex;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid #eee;
  cursor: pointer;
}

.post-item:hover {
  background: #f7f7f7;
}

.post-title {
  font-size: 16px;
  margin-bottom: 5px;
}

.post-content {
  font-size: 13px;
  color: #888;
}

.right {
  text-align: right;
  font-size: 13px;
  color: #999;
}

.empty {
  text-align: center;
  color: #aaa;
}
</style>
