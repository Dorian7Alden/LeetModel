<template>

  <section class="contest-wrapper">


    <div class="contest-area">

      <h2>进行中的竞赛</h2>

      <div class="contest-list">

        <div
          class="contest-card"
          v-for="contest in sortedContests"
          :key="contest.id"
        >

          <h3>{{contest.title}}</h3>

          <p>{{contest.desc}}</p>

          <div class="countdown">
            剩余时间：{{getRemainingTime(contest.endTime)}}
          </div>

          <button class="enter-btn">
            进入竞赛
          </button>

        </div>

      </div>

    </div>


    

  </section>

</template>

<script setup>

import { computed, ref, onMounted } from "vue"


/* 模拟竞赛数据 */

const contests = [
  {
    id:1,
    title:"数学建模挑战赛 A",
    desc:"基于真实数据的城市交通建模问题",
    endTime:"2026-06-03 18:00:00"
  },
  {
    id:2,
    title:"AI 建模竞赛",
    desc:"机器学习预测模型构建",
    endTime:"2026-06-01 18:00:00"
  },
  {
    id:3,
    title:"工业优化挑战赛",
    desc:"生产调度与资源优化问题",
    endTime:"2026-06-05 18:00:00"
  }
]

/* 当前时间 */

const now = ref(Date.now())

onMounted(()=>{
  setInterval(()=>{
    now.value = Date.now()
  },1000)
})

/* 计算剩余时间 */

function getRemainingTime(endTime){

  const diff = new Date(endTime) - now.value

  if(diff <= 0) return "已结束"

  const day = Math.floor(diff / 86400000)
  const hour = Math.floor(diff % 86400000 / 3600000)
  const minute = Math.floor(diff % 3600000 / 60000)
  const second = Math.floor(diff % 60000 / 1000)

  return `${day}天 ${hour}:${minute}:${second}`
}

/* 按结束时间排序 */

const sortedContests = computed(()=>{
  return [...contests].sort((a,b)=>{
    return new Date(a.endTime) - new Date(b.endTime)
  })
})

</script>

<style scoped>

.contest-section{
  margin-bottom:40px;
}

.contest-list{
  display:flex;
  gap:20px;
}

.contest-card{
  flex:1;
  background:white;
  padding:20px;
  border-radius:8px;
  box-shadow:0 2px 6px rgba(0,0,0,0.1);
}

.countdown{
  margin:10px 0;
  color:#f56c6c;
}

.enter-btn{
  padding:6px 12px;
  background:#409eff;
  border:none;
  color:white;
  border-radius:4px;
}

</style>