<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="title">登录账号</h2>

      <div class="form">
        <input v-model="form.email" placeholder="用户名 / 邮箱" />

        <input v-model="form.password" type="password" placeholder="密码" />

        <button class="login-btn" @click="handleLogin">登录</button>
      </div>

      <div class="register-link">
        暂无帐号？
        <router-link to="/register">去注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { login } from "@/api/user";
import { ElMessage } from "element-plus";

import { useUserStore } from "@/store/user";
const userStore = useUserStore();

const router = useRouter();

const form = ref({
  email: "",
  password: "",
});

async function handleLogin() {
  try {
    const res = await login(form.value);

    if (res.code === 200) {
      userStore.login(res.data.token, res.data.username);

      ElMessage.success("登录成功");

      router.push("/");
    } else {
      ElMessage({
        message: res.message || "登录失败",
        type: "error",
      });
    }
  } catch (error) {
    ElMessage({
      message: "登录失败，请检查账号或服务器",
      type: "error",
    });
  }
}
</script>

<style scoped>
/* 页面背景 */

.login-page {
  min-height: 100vh;

  display: flex;

  justify-content: center;

  align-items: center;

  background: #f5f7fa;
}

/* 登录卡片 */

.login-card {
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

/* 登录按钮 */

.login-btn {
  height: 42px;

  border: none;

  border-radius: 6px;

  background: #409eff;

  color: white;

  font-size: 15px;

  cursor: pointer;

  transition: 0.2s;
}

.login-btn:hover {
  background: #2f7de1;
}

/* 注册链接 */

.register-link {
  text-align: center;

  font-size: 14px;

  color: #666;
}

.register-link a {
  color: #409eff;

  margin-left: 4px;

  text-decoration: none;
}

.register-link a:hover {
  text-decoration: underline;
}
</style>
