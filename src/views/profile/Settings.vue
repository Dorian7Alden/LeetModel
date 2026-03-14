<template>

<div class="page">

<h2>个人设置</h2>

<div class="card">

<label>用户名</label>
<input placeholder="输入用户名"/>

<label>邮箱</label>
<input placeholder="输入邮箱"/>

<button>保存</button>

</div>

</div>

</template>

<style scoped>

.page{
padding:30px;
}

.card{

width:400px;
background:white;
padding:20px;
border-radius:8px;
box-shadow:0 2px 8px rgba(0,0,0,0.1);

display:flex;
flex-direction:column;
gap:10px;

}

input{
padding:8px;
border:1px solid #ddd;
border-radius:4px;
}

button{

margin-top:10px;
background:#409eff;
color:white;
border:none;
padding:8px;
border-radius:6px;

}

</style>