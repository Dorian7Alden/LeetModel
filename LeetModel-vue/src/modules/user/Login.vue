<template>
  <div class="auth-page">
    <div class="auth-container">
      <!-- Decorative side -->
      <div class="auth-decor">
        <div class="decor-content">
          <div class="decor-logo">
            <el-icon :size="48" color="#fff"><DataAnalysis /></el-icon>
          </div>
          <h1 class="decor-title">LeetModel</h1>
          <p class="decor-tagline">数学建模学习与竞赛平台</p>
          <div class="decor-features">
            <div class="feature-item">
              <el-icon :size="18"><CircleCheck /></el-icon>
              <span>丰富的建模题目库</span>
            </div>
            <div class="feature-item">
              <el-icon :size="18"><CircleCheck /></el-icon>
              <span>AI 智能评审反馈</span>
            </div>
            <div class="feature-item">
              <el-icon :size="18"><CircleCheck /></el-icon>
              <span>竞赛交流社区</span>
            </div>
          </div>
        </div>
        <div class="decor-bg"></div>
      </div>

      <!-- Form side -->
      <div class="auth-form-wrapper">
        <div class="auth-form-card">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-subtitle">登录您的账号以继续</p>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="auth-form"
            @submit.prevent="handleLogin"
          >
            <el-form-item prop="email">
              <el-input
                v-model="form.email"
                placeholder="请输入邮箱"
                size="large"
                :prefix-icon="Message"
                clearable
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                :prefix-icon="Lock"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <div class="form-extra">
              <el-checkbox v-model="rememberMe" label="记住我" />
              <router-link to="/forgot-password" class="forgot-link">忘记密码？</router-link>
            </div>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                class="submit-btn"
                :loading="loading"
                @click="handleLogin"
              >
                {{ loading ? '登录中...' : '登 录' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="social-login">
            <div class="divider">
              <span>其他方式登录</span>
            </div>
            <div class="social-btns">
              <el-button class="social-btn" circle>
                <el-icon :size="20"><ChatDotSquare /></el-icon>
              </el-button>
              <el-button class="social-btn" circle>
                <el-icon :size="20"><UserFilled /></el-icon>
              </el-button>
              <el-button class="social-btn" circle>
                <el-icon :size="20"><Message /></el-icon>
              </el-button>
            </div>
          </div>

          <div class="switch-link">
            还没有账号？<router-link to="/register">立即注册</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { login } from "@/api/user";
import { ElMessage } from "element-plus";
import { Message, Lock, CircleCheck, ChatDotSquare, UserFilled } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/user";

const userStore = useUserStore();
const router = useRouter();
const formRef = ref(null);
const loading = ref(false);
const rememberMe = ref(false);

const form = reactive({
  email: "",
  password: "",
});

const rules = {
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "请输入有效的邮箱地址", trigger: ["blur", "change"] },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" },
  ],
};

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    const res = await login(form);
    if (res.code === 20000) {
      const data = res.data;
      userStore.login(
        data.token,
        data.username || data.email,
        data.email,
        data.role || "user"
      );
      localStorage.setItem("userId", data.id);
      ElMessage.success("登录成功");
      router.push("/");
    } else {
      ElMessage.error(res.msg || "登录失败");
    }
  } catch (error) {
    console.error(error);
    ElMessage.error("登录失败，请检查账号或服务器");
  } finally {
    loading.value = false;
  }
}
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
  min-height: 580px;
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
  background: linear-gradient(135deg, #1d4ed8 0%, #2563eb 40%, #3b82f6 70%, #60a5fa 100%);
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
  max-width: 380px;
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
  margin-bottom: 32px;
}

.auth-form {
  width: 100%;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 20px;
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

.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  font-size: 13px;
}

.forgot-link {
  color: var(--lm-primary);
  text-decoration: none;
  font-size: 13px;
}

.forgot-link:hover {
  color: var(--lm-primary-light);
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--lm-radius);
  letter-spacing: 1px;
}

/* ===== Social Login ===== */
.social-login {
  margin-top: 24px;
}

.divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.divider::before,
.divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background: var(--lm-border);
}

.divider span {
  font-size: 12px;
  color: var(--lm-text-muted);
  white-space: nowrap;
}

.social-btns {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.social-btn {
  width: 44px;
  height: 44px;
  border: 1px solid var(--lm-border);
  color: var(--lm-text-secondary);
  transition: all var(--lm-transition);
}

.social-btn:hover {
  border-color: var(--lm-primary);
  color: var(--lm-primary);
  background: var(--lm-primary-bg);
}

/* ===== Switch Link ===== */
.switch-link {
  text-align: center;
  margin-top: 28px;
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
    padding: 32px 20px;
  }
  .decor-features {
    display: none;
  }
  .auth-form-wrapper {
    padding: 32px 24px;
  }
}
</style>
