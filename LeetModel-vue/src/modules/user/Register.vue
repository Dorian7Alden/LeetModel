<template>
  <div class="register-page">
    <div class="register-card">
      <h2 class="title">创建账号</h2>

      <div class="form">
        <!-- 邮箱 -->
        <div class="input-group">
          <input v-model="email" placeholder="请输入邮箱" />
        </div>

        <!-- 验证码 -->
        <div class="code-row">
          <input v-model="code" placeholder="验证码" />
          <button class="code-btn" @click="getCode">获取验证码</button>
        </div>

        <!-- 密码 -->
        <div class="input-group">
          <input v-model="password" type="password" placeholder="请输入密码" />
        </div>

        <!-- 注册按钮 -->
        <button class="register-btn" @click="doRegister">注册</button>
      </div>

      <div class="login-link">
        已有账号？
        <router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref } from "vue";
import { sendCode, register } from "@/api/user";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";

const router = useRouter();

const email = ref("");
const password = ref("");
const code = ref("");

const getCode = async () => {
  await sendCode(email.value);
  alert("验证码已发送");
};

const doRegister = async () => {
  try {
    const res = await register({
      email: email.value,
      password: password.value,
      code: code.value,
    });

    ElMessage.success(res.msg);

    // ⭐ 延迟跳转（让用户看到提示）
    setTimeout(() => {
      router.push("/login");
    }, 1000);
  } catch (err) {
    ElMessage.error(err.response.msg);
  }
};
</script>

<style scoped>
/* 页面背景（渐变+居中） */
.register-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;

  background: linear-gradient(135deg, #eef2ff, #f8fafc);
}

/* 卡片 */
.register-card {
  width: 380px;
  padding: 40px;

  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);

  border-radius: 16px;

  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08);

  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 标题 */
.title {
  text-align: center;
  font-size: 24px;
  font-weight: 600;
  color: #222;
}

/* 表单 */
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 输入框容器 */
.input-group input {
  width: 100%;
  height: 42px;

  border: 1px solid #e5e7eb;
  border-radius: 8px;

  padding: 0 14px;
  font-size: 14px;

  transition: all 0.2s;
}

/* 输入框 focus */
.input-group input:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
}

/* 验证码行 */
.code-row {
  display: flex;
  gap: 10px;
}

.code-row input {
  flex: 1;
  height: 42px;

  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0 14px;
}

/* 验证码按钮 */
.code-btn {
  width: 120px;
  border: none;
  border-radius: 8px;

  background: #409eff;
  color: white;

  cursor: pointer;
  transition: 0.2s;
}

.code-btn:hover {
  background: #2f7de1;
}

/* 注册按钮 */
.register-btn {
  height: 44px;

  border: none;
  border-radius: 10px;

  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: white;

  font-size: 15px;
  font-weight: 500;

  cursor: pointer;
  transition: all 0.2s;
}

.register-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.3);
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
