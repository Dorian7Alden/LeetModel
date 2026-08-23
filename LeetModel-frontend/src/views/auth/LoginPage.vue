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
          <p class="form-subtitle">使用用户名登录您的账号以继续</p>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="auth-form"
            @submit.prevent="handleLogin"
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
import { getCurrentUser, login } from "@/api/user";
import { ElMessage } from "element-plus";
import { User, Lock, CircleCheck, ArrowLeft } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/user";

const userStore = useUserStore();
const router = useRouter();
const formRef = ref(null);
const loading = ref(false);

const form = reactive({
  username: "",
  password: "",
});

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
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
      userStore.login(data);

      try {
        const profileRes = await getCurrentUser();
        userStore.updateProfile(profileRes.data);
      } catch (error) {
        console.warn("登录成功，但用户资料加载失败", error);
      }

      ElMessage.success("登录成功");
      router.push("/");
    } else {
      ElMessage.error(res.message || "登录失败");
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || "登录失败");
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
