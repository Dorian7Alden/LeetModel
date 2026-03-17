<template>
  <div class="register-page">
    <div class="register-card">
      <h2 class="title">注册账号</h2>

      <div class="form">
        <input placeholder="用户名" />

        <input placeholder="邮箱" />

        <input type="password" placeholder="密码" />

        <input type="password" placeholder="确认密码" />

        <button class="register-btn">注册</button>
      </div>

      <div class="login-link">
        已有账号？
        <router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 页面背景 */

.register-page {
  min-height: 100vh;

  display: flex;

  justify-content: center;

  align-items: center;

  background: #f5f7fa;
}

/* 注册卡片 */

.register-card {
  width: 360px;

  background: white;

  padding: 40px;

  border-radius: 14px;

  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);

  display: flex;

  flex-direction: column;

  gap: 20px;
}

/* 标题 */

.title {
  text-align: center;

  font-size: 22px;

  font-weight: 600;

  color: #333;
}

/* 表单 */

.form {
  display: flex;

  flex-direction: column;

  gap: 14px;
}

/* 输入框 */

input {
  height: 40px;

  border: 1px solid #ddd;

  border-radius: 6px;

  padding: 0 12px;

  font-size: 14px;

  outline: none;

  transition: 0.2s;
}

input:focus {
  border-color: #409eff;
}

/* 注册按钮 */

.register-btn {
  height: 42px;

  border: none;

  border-radius: 6px;

  background: #409eff;

  color: white;

  font-size: 15px;

  cursor: pointer;

  transition: 0.2s;
}

.register-btn:hover {
  background: #2f7de1;
}

/* 登录链接 */

.login-link {
  text-align: center;

  font-size: 14px;

  color: #666;
}

.login-link a {
  color: #409eff;

  margin-left: 4px;

  text-decoration: none;
}

.login-link a:hover {
  text-decoration: underline;
}
</style>
