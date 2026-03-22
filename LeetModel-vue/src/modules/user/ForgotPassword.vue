<template>
  <div class="page">
    <el-card class="card">
      <h2>找回密码</h2>

      <el-form :model="form" label-width="80px">
        <!-- 邮箱 -->
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>

        <!-- 验证码 -->
        <el-form-item label="验证码">
          <div class="code-row">
            <el-input v-model="form.code" />

            <el-button :disabled="countdown > 0" @click="handleSendCode">
              {{ countdown > 0 ? countdown + "s" : "发送验证码" }}
            </el-button>
          </div>
        </el-form-item>

        <!-- 新密码 -->
        <el-form-item label="新密码">
          <el-input v-model="form.password" type="password" />
        </el-form-item>

        <!-- 按钮 -->
        <el-form-item>
          <el-button type="primary" @click="handleReset"> 重置密码 </el-button>
        </el-form-item>
      </el-form>

      <div class="back">
        <router-link to="/login">返回登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import request from "@/api/request";

const router = useRouter();

const form = reactive({
  email: "",
  code: "",
  password: "",
});

const countdown = ref(0);
let timer = null;

// ✅ 发送验证码
const handleSendCode = async () => {
  if (!form.email) {
    ElMessage.error("请输入邮箱");
    return;
  }

  try {
    await request.post("/auth/verification-codes", {
      target: form.email,
    });

    ElMessage.success("验证码已发送");

    countdown.value = 60;
    timer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0) clearInterval(timer);
    }, 1000);
  } catch (err) {
    ElMessage.error("发送失败");
  }
};

// ✅ 重置密码
const handleReset = async () => {
  if (!form.email || !form.code || !form.password) {
    ElMessage.error("请填写完整信息");
    return;
  }

  try {
    await request.post("/auth/reset-password", {
      email: form.email,
      code: form.code,
      password: form.password,
    });

    ElMessage.success("重置成功，请登录");

    router.push("/login");
  } catch (err) {
    ElMessage.error(err.response?.data?.msg || "重置失败");
  }
};
</script>

<style scoped>
.page {
  display: flex;
  justify-content: center;
  margin-top: 80px;
}

.card {
  width: 400px;
  padding: 20px;
  border-radius: 12px;
}

.code-row {
  display: flex;
  gap: 10px;
}

.back {
  margin-top: 10px;
  text-align: right;
}
</style>
