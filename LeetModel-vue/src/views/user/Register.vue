<template>
  <div class="auth-container">

    <h2>用户注册</h2>

    <input placeholder="用户名" />
    <input placeholder="邮箱" />
    <input placeholder="密码" type="password"/>

    <button class="register-btn">注册</button>

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

.register-btn{
  padding:8px;
  background:#67c23a;
  color:white;
  border:none;
  border-radius:4px;
}

</style>