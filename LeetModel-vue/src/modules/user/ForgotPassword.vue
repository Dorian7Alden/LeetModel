<template>
  <div class="auth-page">
    <div class="auth-container">
      <!-- Decorative side -->
      <div class="auth-decor">
        <div class="decor-content">
          <div class="decor-logo">
            <el-icon :size="48" color="#fff"><Key /></el-icon>
          </div>
          <h1 class="decor-title">找回密码</h1>
          <p class="decor-tagline">我们帮您重置账号密码</p>
          <div class="decor-features">
            <div class="feature-item">
              <el-icon :size="18"><CircleCheck /></el-icon>
              <span>验证邮箱身份</span>
            </div>
            <div class="feature-item">
              <el-icon :size="18"><CircleCheck /></el-icon>
              <span>输入验证码</span>
            </div>
            <div class="feature-item">
              <el-icon :size="18"><CircleCheck /></el-icon>
              <span>设置新密码</span>
            </div>
          </div>
        </div>
        <div class="decor-bg"></div>
      </div>

      <!-- Form side -->
      <div class="auth-form-wrapper">
        <div class="auth-form-card">
          <h2 class="form-title">重置密码</h2>
          <p class="form-subtitle">{{ stepTitles[currentStep] }}</p>

          <!-- Step indicator -->
          <el-steps
            :active="currentStep"
            align-center
            finish-status="success"
            class="step-indicator"
          >
            <el-step title="验证邮箱" />
            <el-step title="验证身份" />
            <el-step title="设置密码" />
          </el-steps>

          <!-- Step 0: Enter Email -->
          <el-form
            v-if="currentStep === 0"
            ref="emailFormRef"
            :model="form"
            :rules="emailRules"
            label-position="top"
            class="auth-form"
          >
            <el-form-item prop="email">
              <el-input
                v-model="form.email"
                placeholder="请输入注册邮箱"
                size="large"
                :prefix-icon="Message"
                clearable
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                class="submit-btn"
                :loading="emailLoading"
                @click="handleVerifyEmail"
              >
                下一步
              </el-button>
            </el-form-item>
          </el-form>

          <!-- Step 1: Enter Verification Code -->
          <el-form
            v-if="currentStep === 1"
            ref="codeFormRef"
            :model="form"
            :rules="codeRules"
            label-position="top"
            class="auth-form"
          >
            <div class="step-hint">
              验证码已发送至 <strong>{{ form.email }}</strong>
            </div>
            <el-form-item prop="code">
              <div class="code-row">
                <el-input
                  v-model="form.code"
                  placeholder="请输入6位验证码"
                  size="large"
                  :prefix-icon="Key"
                  class="code-input"
                />
                <el-button
                  class="code-btn"
                  :disabled="countdown > 0 || codeSending"
                  :loading="codeSending"
                  @click="resendCode"
                >
                  {{ countdown > 0 ? `${countdown}s 后重发` : '重新发送' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <div class="step-btns">
                <el-button size="large" class="prev-btn" @click="currentStep = 0">上一步</el-button>
                <el-button
                  type="primary"
                  size="large"
                  class="submit-btn flex-1"
                  :loading="codeLoading"
                  @click="handleVerifyCode"
                >
                  验证
                </el-button>
              </div>
            </el-form-item>
          </el-form>

          <!-- Step 2: Reset Password -->
          <el-form
            v-if="currentStep === 2"
            ref="passwordFormRef"
            :model="form"
            :rules="passwordRules"
            label-position="top"
            class="auth-form"
          >
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入新密码（至少6位）"
                size="large"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                size="large"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <el-form-item>
              <div class="step-btns">
                <el-button size="large" class="prev-btn" @click="currentStep = 1">上一步</el-button>
                <el-button
                  type="primary"
                  size="large"
                  class="submit-btn flex-1"
                  :loading="resetLoading"
                  @click="handleReset"
                >
                  重置密码
                </el-button>
              </div>
            </el-form-item>
          </el-form>

          <div class="switch-link">
            <router-link to="/login">返回登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onBeforeUnmount } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { sendCode, resetPassword } from "@/api/user";
import { Message, Lock, Key, CircleCheck } from "@element-plus/icons-vue";

const router = useRouter();

const stepTitles = ["请输入您的注册邮箱", "请输入邮箱收到的验证码", "请设置新的登录密码"];

const currentStep = ref(0);
const emailFormRef = ref(null);
const codeFormRef = ref(null);
const passwordFormRef = ref(null);

const emailLoading = ref(false);
const codeLoading = ref(false);
const resetLoading = ref(false);
const codeSending = ref(false);

const form = reactive({
  email: "",
  code: "",
  password: "",
  confirmPassword: "",
});

const emailRules = {
  email: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "请输入有效的邮箱地址", trigger: ["blur", "change"] },
  ],
};

const codeRules = {
  code: [
    { required: true, message: "请输入验证码", trigger: "blur" },
    { len: 6, message: "验证码为6位", trigger: "blur" },
  ],
};

const validateConfirmPassword = (_rule, value, callback) => {
  if (!value) {
    callback(new Error("请再次输入新密码"));
  } else if (value !== form.password) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

const passwordRules = {
  password: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: "blur" },
  ],
};

// Countdown
const countdown = ref(0);
let timer = null;

const clearTimer = () => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
};

onBeforeUnmount(() => {
  clearTimer();
});

const startCountdown = () => {
  clearTimer();
  countdown.value = 60;
  timer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      clearTimer();
    }
  }, 1000);
};

const doSendCode = async () => {
  codeSending.value = true;
  try {
    await sendCode(form.email);
    ElMessage.success("验证码已发送，请查收邮件");
    startCountdown();
  } catch (err) {
    ElMessage.error("验证码发送失败");
  } finally {
    codeSending.value = false;
  }
};

// Step 0: Verify email and send code
const handleVerifyEmail = async () => {
  const valid = await emailFormRef.value.validate().catch(() => false);
  if (!valid) return;

  emailLoading.value = true;
  try {
    await doSendCode();
    currentStep.value = 1;
  } finally {
    emailLoading.value = false;
  }
};

// Step 1: Retry send code (from the resend button)
const resendCode = async () => {
  await doSendCode();
};

// Step 1: Verify code
const handleVerifyCode = async () => {
  const valid = await codeFormRef.value.validate().catch(() => false);
  if (!valid) return;

  codeLoading.value = true;
  try {
    // Code verification happens implicitly on password reset;
    // we just validate the field is filled and advance.
    currentStep.value = 2;
  } finally {
    codeLoading.value = false;
  }
};

// Step 2: Reset password
const handleReset = async () => {
  const valid = await passwordFormRef.value.validate().catch(() => false);
  if (!valid) return;

  resetLoading.value = true;
  try {
    await resetPassword({
      email: form.email,
      code: form.code,
      password: form.password,
    });
    ElMessage.success("密码重置成功，请使用新密码登录");
    router.push("/login");
  } catch (err) {
    ElMessage.error(err?.response?.data?.msg || "重置失败，请检查验证码是否正确");
  } finally {
    resetLoading.value = false;
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
  min-height: 600px;
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
  background: linear-gradient(135deg, #0891b2 0%, #0e7490 40%, #14b8a6 70%, #2dd4bf 100%);
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
  max-width: 420px;
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
  margin-bottom: 24px;
}

/* ===== Steps ===== */
.step-indicator {
  margin-bottom: 32px;
}

.step-indicator :deep(.el-step__title) {
  font-size: 13px;
  font-weight: 500;
}

.step-indicator :deep(.el-step__head.is-success) {
  color: var(--lm-success);
  border-color: var(--lm-success);
}

/* ===== Form ===== */
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

.step-hint {
  font-size: 13px;
  color: var(--lm-text-secondary);
  margin-bottom: 16px;
  padding: 10px 14px;
  background: var(--lm-primary-bg);
  border-radius: var(--lm-radius-sm);
}

.step-hint strong {
  color: var(--lm-text-primary);
}

/* ===== Code Row ===== */
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
  min-width: 100px;
  font-size: 13px;
  border-radius: var(--lm-radius);
}

/* ===== Step Buttons ===== */
.step-btns {
  display: flex;
  gap: 12px;
  width: 100%;
}

.prev-btn {
  border-radius: var(--lm-radius);
  min-width: 100px;
  border: 1px solid var(--lm-border);
  color: var(--lm-text-secondary);
}

.prev-btn:hover {
  border-color: var(--lm-primary);
  color: var(--lm-primary);
}

/* ===== Submit Button ===== */
.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--lm-radius);
  letter-spacing: 1px;
}

.submit-btn.flex-1 {
  flex: 1;
}

/* ===== Switch Link ===== */
.switch-link {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
}

.switch-link a {
  color: var(--lm-primary);
  text-decoration: none;
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
