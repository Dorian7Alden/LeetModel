<template>

<div class="page">

<h2>我的成就</h2>

<div class="grid">

<div class="badge">
每日一题坚持者
</div>

<div class="badge">
建模新手
</div>

<div class="badge locked">
编程达人（未解锁）
</div>

</div>

</div>

</template>

<style scoped>

.page{
padding:30px;
}

.grid{

display:flex;
gap:20px;

}

.badge{

padding:20px;
background:#67c23a;
color:white;
border-radius:8px;

}

.locked{
background:#ccc;
}

</style>