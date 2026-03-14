<template>
  <div class="topbar">

    <div class="logo">
      数学建模在线评测系统
    </div>

    <div class="auth-buttons">
      <router-link to="/login">
        <button class="btn">登录</button>
      </router-link>

      <router-link to="/register">
        <button class="btn register">注册</button>
      </router-link>
    </div>

  </div>
</template>

<style scoped>

.topbar{
  height:60px;
  display:flex;
  justify-content:space-between;
  align-items:center;
  padding:0 40px;
  background:#2c3e50;
  color:white;
}

.logo{
  font-size:18px;
  font-weight:bold;
}

.auth-buttons{
  display:flex;
  gap:10px;
}

.btn{
  padding:6px 14px;
  border:none;
  border-radius:4px;
  cursor:pointer;
}

.register{
  background:#409eff;
  color:white;
}

</style>