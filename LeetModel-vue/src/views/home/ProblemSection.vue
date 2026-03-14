<template>

  <section class="problem-section">

    <h2>热门题目</h2>

    <div class="problem-list">

      <div
        class="problem-card"
        v-for="problem in problems"
        :key="problem.id"
      >

        <div class="problem-info">

          <h3>{{ problem.title }}</h3>

          <p>{{ problem.desc }}</p>

        </div>

        <button class="view-btn">
          查看题目
        </button>

      </div>

    </div>

  </section>

</template>

<script setup>

const problems = [
  {
    id:1,
    title:"城市交通流量预测",
    desc:"利用时间序列模型预测未来交通流量"
  },
  {
    id:2,
    title:"物流配送路径优化",
    desc:"建立最短路径与运输成本优化模型"
  },
  {
    id:3,
    title:"疫情传播模型",
    desc:"建立 SEIR 模型模拟疫情传播"
  },
  {
    id:4,
    title:"新能源发电预测",
    desc:"建立风电发电量预测模型"
  }
]

</script>

<style scoped>

.problem-section{
  margin-top:40px;
}

/* 纵向排列 */

.problem-list{
  display:flex;
  flex-direction:column;
  gap:15px;
}

.problem-card{
  display:flex;
  justify-content:space-between;
  align-items:center;

  padding:18px;

  background:white;

  border-radius:8px;

  box-shadow:0 2px 6px rgba(0,0,0,0.1);
}

.problem-info h3{
  margin-bottom:6px;
}

.view-btn{
  padding:6px 14px;

  background:#409eff;

  color:white;

  border:none;

  border-radius:4px;
}

</style>