<template>
  <section class="community-section">
    <h2 class="section-title">🔥 热门帖子</h2>

    <div class="post-list">
      <div class="post-card" v-for="post in posts" :key="post.id">
        <div class="post-left">
          <div class="hot-icon">🔥</div>

          <div class="post-info">
            <h3 class="post-title">{{ post.title }}</h3>

            <p class="post-desc">{{ post.desc }}</p>

            <div class="post-meta">
              <span>👍 {{ post.likes }}</span>

              <span>💬 {{ post.comments }}</span>
            </div>
          </div>
        </div>

        <router-link class="view-btn" :to="'/post/' + post.id">
          查看帖子
        </router-link>
      </div>
    </div>
  </section>
</template>

<script setup>
const posts = [
  {
    id: 1,
    title: "城市交通流量预测建模思路",
    desc: "利用时间序列模型预测未来交通流量",
    likes: 124,
    comments: 36,
  },
  {
    id: 2,
    title: "数学建模国赛一等奖经验分享",
    desc: "完整复盘建模流程与论文写作技巧",
    likes: 98,
    comments: 21,
  },
  {
    id: 3,
    title: "SEIR 疫情传播模型推导",
    desc: "一步一步建立完整的疫情传播模型",
    likes: 76,
    comments: 15,
  },
  {
    id: 4,
    title: "新能源发电预测模型",
    desc: "利用机器学习预测风电发电量",
    likes: 63,
    comments: 12,
  },
];
</script>

<style scoped>
/* 整体 */

.community-section {
  margin-top: 40px;
}

/* 标题 */

.section-title {
  font-size: 20px;
  margin-bottom: 18px;
  color: #333;
}

/* 帖子列表 */

.post-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 帖子卡片 */

.post-card {
  display: flex;

  justify-content: space-between;

  align-items: center;

  padding: 18px 20px;

  background: white;

  border-radius: 12px;

  border: 1px solid #eee;

  transition: all 0.2s;
}

.post-card:hover {
  transform: translateY(-3px);

  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}

/* 左侧 */

.post-left {
  display: flex;

  gap: 14px;

  align-items: flex-start;
}

/* 热度图标 */

.hot-icon {
  font-size: 20px;

  margin-top: 2px;
}

/* 标题 */

.post-title {
  font-size: 16px;

  font-weight: 600;

  color: #333;

  margin-bottom: 6px;
}

/* 描述 */

.post-desc {
  font-size: 14px;

  color: #777;

  margin-bottom: 8px;
}

/* 元信息 */

.post-meta {
  font-size: 13px;

  color: #999;

  display: flex;

  gap: 14px;
}

/* 按钮 */

.view-btn {
  padding: 8px 16px;

  background: #409eff;

  color: white;

  border: none;

  border-radius: 6px;

  cursor: pointer;

  font-size: 14px;

  transition: 0.2s;

  text-decoration: none;
}

.view-btn:hover {
  background: #2f7de1;
}
</style>
