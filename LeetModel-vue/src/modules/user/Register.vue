<template>
  <div class="auth-page">
    <div class="auth-container">
      <!-- Decorative side -->
      <div class="auth-decor">
        <div class="decor-content">
          <div class="decor-logo">
            <el-icon :size="48" color="#fff"><UserFilled /></el-icon>
          </div>
          <h1 class="decor-title">加入我们</h1>
          <p class="decor-tagline">开启您的数学建模之旅</p>
          <div class="decor-features">
            <div class="feature-item">
              <el-icon :size="18"><CircleCheck /></el-icon>
              <span>海量题目实战练习</span>
            </div>
            <div class="feature-item">
              <el-icon :size="18"><CircleCheck /></el-icon>
              <span>AI 驱动的智能评审</span>
            </div>
            <div class="feature-item">
              <el-icon :size="18"><CircleCheck /></el-icon>
              <span>与全站建模爱好者交流</span>
            </div>
          </div>
        </div>
        <div class="decor-bg"></div>
      </div>

      <!-- Form side -->
      <div class="auth-form-wrapper">
        <div class="auth-form-card">
          <h2 class="form-title">创建账号</h2>
          <p class="form-subtitle">填写信息完成注册</p>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="auth-form"
            @submit.prevent="doRegister"
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                size="large"
                :prefix-icon="User"
                clearable
              />
            </el-form-item>

            <el-form-item prop="email">
              <el-input
                v-model="form.email"
                placeholder="请输入邮箱"
                size="large"
                :prefix-icon="Message"
                clearable
              />
            </el-form-item>

            <el-form-item prop="code">
              <div class="code-row">
                <el-input
                  v-model="form.code"
                  placeholder="验证码"
                  size="large"
                  :prefix-icon="Key"
                  class="code-input"
                />
                <el-button
                  type="primary"
                  class="code-btn"
                  :disabled="countdown > 0 || codeSending"
                  :loading="codeSending"
                  @click="getCode"
                >
                  {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码（至少6位）"
                size="large"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <el-form-item prop="confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                size="large"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                class="submit-btn"
                :loading="registering"
                @click="doRegister"
              >
                {{ registering ? '注册中...' : '注 册' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="switch-link">
            已有账号？<router-link to="/login">立即登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import { sendCode, register } from "@/api/user";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { User, Message, Lock, Key, UserFilled, CircleCheck } from "@element-plus/icons-vue";

const router = useRouter();
const formRef = ref(null);
const registering = ref(false);
const codeSending = ref(false);

const form = reactive({
  username: "",
  email: "",
  code: "",
  password: "",
  confirmPassword: "",
});

const validateConfirmPassword = (_rule, value, callback) => {
  if (!value) {
    callback(new Error("请再次输入密码"));
  } else if (value !== form.password) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 2, max: 20, message: "用户名长度为2-20个字符", trigger: "blur" },
  ],
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "请输入有效的邮箱地址", trigger: ["blur", "change"] },
  ],
  code: [
    { required: true, message: "请输入验证码", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: "blur" },
  ],
};

const countdown = ref(0);
let timer = null;

const getCode = async () => {
  if (!form.email) {
    ElMessage.warning("请先输入邮箱");
    return;
  }
  // Validate email format before sending
  try {
    await formRef.value.validateField("email");
  } catch {
    return;
  }

  codeSending.value = true;
  try {
    await sendCode(form.email);
    ElMessage.success("验证码已发送，请查收邮件");
    countdown.value = 60;
    timer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0) {
        clearInterval(timer);
        timer = null;
      }
    }, 1000);
  } catch (err) {
    ElMessage.error("验证码发送失败，请稍后重试");
  } finally {
    codeSending.value = false;
  }
};

const doRegister = async () => {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  registering.value = true;
  try {
    const res = await register({
      username: form.username,
      email: form.email,
      password: form.password,
      code: form.code,
    });
    ElMessage.success(res.msg || "注册成功");
    setTimeout(() => {
      router.push("/login");
    }, 1200);
  } catch (err) {
    const msg = err?.response?.data?.msg || err?.response?.msg || "注册失败";
    ElMessage.error(msg);
  } finally {
    registering.value = false;
  }
};
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lm-bg);
  padding: 20px;
}

.auth-container {
  display: flex;
  width: 100%;
  max-width: 960px;
  min-height: 660px;
  background: var(--lm-surface);
  border-radius: var(--lm-radius-xl);
  box-shadow: var(--lm-shadow-xl);
  overflow: hidden;
}

/* ===== Decorative Side ===== */
.auth-decor {
  width: 420px;
  flex-shrink: 0;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #7c3aed 0%, #8b5cf6 40%, #a78bfa 70%, #c4b5fd 100%);
  overflow: hidden;
}

.decor-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 80%, rgba(255,255,255,0.08) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255,255,255,0.06) 0%, transparent 50%);
}

.decor-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 40px;
  color: #fff;
}

.decor-logo {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(10px);
}

.decor-title {
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -0.5px;
  margin-bottom: 8px;
}

.decor-tagline {
  font-size: 14px;
  opacity: 0.85;
  margin-bottom: 36px;
}

.decor-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
  padding: 0 20px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  opacity: 0.9;
}

/* ===== Form Side ===== */
.auth-form-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.auth-form-card {
  width: 100%;
  max-width: 400px;
}

.form-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--lm-text-primary);
  margin-bottom: 4px;
}

.form-subtitle {
  font-size: 14px;
  color: var(--lm-text-secondary);
  margin-bottom: 28px;
}

.auth-form {
  width: 100%;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-form :deep(.el-form-item__label) {
  display: none;
}

.auth-form :deep(.el-input__wrapper) {
  border-radius: var(--lm-radius);
  box-shadow: 0 0 0 1px var(--lm-border) inset;
  padding: 0 12px;
  transition: box-shadow var(--lm-transition);
}

.auth-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--lm-text-muted) inset;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--lm-primary) inset, 0 0 0 3px var(--lm-primary-bg);
}

/* ===== Verification Code Row ===== */
.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-input {
  flex: 1;
}

.code-btn {
  height: 40px;
  flex-shrink: 0;
  min-width: 120px;
  font-size: 13px;
  border-radius: var(--lm-radius);
}

/* ===== Submit Button ===== */
.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--lm-radius);
  letter-spacing: 1px;
  margin-top: 4px;
}

/* ===== Switch Link ===== */
.switch-link {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: var(--lm-text-secondary);
}

.switch-link a {
  color: var(--lm-primary);
  font-weight: 500;
  text-decoration: none;
  margin-left: 4px;
}

.switch-link a:hover {
  color: var(--lm-primary-light);
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .auth-container {
    flex-direction: column;
    max-width: 420px;
  }
  .auth-decor {
    width: 100%;
    padding: 28px 20px;
  }
  .decor-features {
    display: none;
  }
  .auth-form-wrapper {
    padding: 32px 24px;
  }
}
</style>
