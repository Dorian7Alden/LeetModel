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
          <p class="form-subtitle">使用注册时的邮箱登录您的账号以继续</p>

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
import { Message, Lock, CircleCheck, ArrowLeft } from "@element-plus/icons-vue";
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
        data.role || "MEMBER"
      );
      localStorage.setItem("userId", data.id);
      ElMessage.success("登录成功");
      router.push("/");
    } else {
      ElMessage.error(res.msg || "登录失败");
    }
  } catch (error) {
    console.warn("后端未连接，使用离线模式登录");
    // Mock login for development without backend
    const mockToken = "dev-token-" + Date.now();
    const role = form.email.includes("admin") ? "SUPER_ADMIN" : "MEMBER";
    userStore.login(mockToken, form.email.split("@")[0], form.email, role);
    localStorage.setItem("userId", "1001");
    ElMessage.success("离线模式登录成功（Role: " + role + "）");
    router.push("/");
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
@import './style.css';
</style>

<style scoped>
.auth-decor {
  background: linear-gradient(135deg, #1d4ed8 0%, #2563eb 40%, #3b82f6 70%, #60a5fa 100%);
}
</style>
