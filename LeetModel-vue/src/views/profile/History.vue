<template>

<div class="page">

<h2>提交历史</h2>

<ul class="list">

<li>

<span>两数之和</span>

<span class="status">通过</span>

<span class="time">1天前</span>

</li>

<li>

<span>最短路径</span>

<span class="status fail">失败</span>

<span class="time">3天前</span>

</li>

</ul>

</div>

</template>

<style scoped>

.page{
padding:30px;
}

.list{
list-style:none;
padding:0;
}

li{

display:flex;
justify-content:space-between;
padding:15px;
border-bottom:1px solid #eee;

}

.status{
color:#67c23a;
}

.fail{
color:#f56c6c;
}

.time{
color:#888;
}

</style>