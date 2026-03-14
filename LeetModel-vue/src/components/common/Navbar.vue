<template>

<nav class="navbar">

<div class="logo">
MathModel
</div>

<ul class="menu">

<li>
<router-link to="/">首页</router-link>
</li>

<li>
<router-link to="/training">训练</router-link>
</li>

<li>
<router-link to="/problem">题库</router-link>
</li>

<li>
<router-link to="/contest">赛事</router-link>
</li>

<li>
<router-link to="/community">社区</router-link>
</li>

<li>
<router-link to="/team">组队</router-link>
</li>

<li>
<router-link to="/profile">个人中心</router-link>
</li>

</ul>

</nav>

</template>

<style>
.navbar{
display:flex;
align-items:center;
padding:0 40px;
height:55px;
background:#2c3e50;
}

.logo{
color:white;
font-weight:bold;
margin-right:40px;
}

.menu{
display:flex;
gap:30px;
list-style:none;
}

.menu a{
color:white;
text-decoration:none;
font-size:15px;
}

.menu a:hover{
color:#409eff;
}
</style>