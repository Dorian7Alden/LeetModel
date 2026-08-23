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

            <el-form-item prop="nickname">
              <el-input
                v-model="form.nickname"
                placeholder="请输入昵称（选填）"
                size="large"
                :prefix-icon="UserFilled"
                clearable
              />
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
import { register } from "@/api/user";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { User, UserFilled, Lock, CircleCheck, ArrowLeft } from "@element-plus/icons-vue";

const router = useRouter();
const formRef = ref(null);
const registering = ref(false);

const form = reactive({
  username: "",
  nickname: "",
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
    { min: 4, max: 32, message: "用户名长度为4-32个字符", trigger: "blur" },
  ],
  nickname: [
    { max: 32, message: "昵称最多32个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: "blur" },
  ],
};

const doRegister = async () => {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  registering.value = true;
  try {
    const res = await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || null,
    });
    ElMessage.success(res.message || "注册成功");
    setTimeout(() => {
      router.push("/login");
    }, 1200);
  } catch (err) {
    const msg = err?.response?.data?.message || err.message || "注册失败";
    ElMessage.error(msg);
  } finally {
    registering.value = false;
  }
};
</script>

<style scoped>
@import './style.css';
</style>

<style scoped>
.auth-decor {
  background: linear-gradient(135deg, #7c3aed 0%, #8b5cf6 40%, #a78bfa 70%, #c4b5fd 100%);
}
</style>
