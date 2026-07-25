<template>
  <div class="auth-page">
    <div class="auth-container">
      <router-link to="/" class="auth-back-link">
        <el-icon :size="14"><ArrowLeft /></el-icon>
        <span>返回首页</span>
      </router-link>
      <!-- Decorative side -->
      <div class="auth-decor">
        <div class="decor-content">
          <div class="decor-logo">
            <img src="@/assets/images/logo-with-en.png" alt="LeetModel" />
          </div>
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
import { Message, Lock, CircleCheck, ArrowLeft } from "@element-plus/icons-vue";

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
@import './style.css';
</style>

<style scoped>
.auth-decor {
  background: linear-gradient(135deg, #0891b2 0%, #0e7490 40%, #14b8a6 70%, #2dd4bf 100%);
}
</style>
