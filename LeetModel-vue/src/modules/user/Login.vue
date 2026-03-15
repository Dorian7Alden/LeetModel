<template>
  <div class="auth-container">

    <h2>用户登录</h2>

    <input placeholder="用户名" />
    <input placeholder="密码" type="password"/>

    <button class="login-btn">登录</button>

  </div>
</template>

<style scoped>

.auth-container{
  width:300px;
  margin:120px auto;
  display:flex;
  flex-direction:column;
  gap:15px;
}

input{
  padding:8px;
  border:1px solid #ddd;
  border-radius:4px;
}

.login-btn{
  padding:8px;
  background:#409eff;
  color:white;
  border:none;
  border-radius:4px;
}

</style>