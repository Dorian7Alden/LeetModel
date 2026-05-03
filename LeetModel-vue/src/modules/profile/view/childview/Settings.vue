<template>
  <div class="settings-page">
    <el-card class="card">
      <h2>编辑个人资料</h2>

      <el-form :model="form" label-width="80px">
        <!-- 用户名 -->
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>

        <!-- 邮箱（只读） -->
        <el-form-item label="邮箱">
          <el-input v-model="form.email" disabled />
        </el-form-item>

        <!-- 学校 -->
        <el-form-item label="学校">
          <el-input v-model="form.school" />
        </el-form-item>

        <!-- 创建时间（只读） -->
        <el-form-item label="账号创建时间">
          <el-input v-model="form.createTime" disabled />
        </el-form-item>

        <!-- 按钮 -->
        <el-form-item>
          <el-button type="primary" @click="handleSubmit"> 保存修改 </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-button type="danger" @click="handleDelete"> 注销账号 </el-button>
  </div>
</template>

<script setup>
import { reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { updateUser } from "@/api/user";
import request from "..//..//..//..//api/request";
import { useRouter } from "vue-router";
import { deleteUser } from "@/api/user";

// ⚠️ 你需要从本地拿当前用户ID
// 示例：登录后存的
const userId = localStorage.getItem("userId");

const form = reactive({
  username: "",
  email: "",
  createTime: "",
  school: "",
});

// ✅ 加载用户信息（推荐）
async function loadUser() {
  try {
    const res = await request.get(`/users/${userId}`);

    function normalizeUser(user) {
      return {
        username: user.username ?? "",
        email: user.email ?? "",
        createTime: user.createTime ?? "",
        school: user.school ?? "",
      };
    }
    const user = normalizeUser(res.data);
    Object.assign(form, user);
  } catch (e) {
    ElMessage.error("加载用户失败");
  }
}

onMounted(() => {
  loadUser();
});

// 提交
async function handleSubmit() {
  try {
    await updateUser(userId, { username: form.username, school: form.school });

    ElMessage.success("修改成功");
  } catch (e) {
    ElMessage.error("修改失败");
  }
}
const router = useRouter();

async function handleDelete() {
  try {
    // ⚠️ 二次确认（非常重要）
    await ElMessageBox.confirm("注销账号后将无法恢复，是否继续？", "警告", {
      confirmButtonText: "确定注销",
      cancelButtonText: "取消",
      type: "warning",
    });

    // 1️⃣ 调后端删除用户
    await deleteUser(userId);

    // 2️⃣ 清本地登录信息
    localStorage.removeItem("token");
    localStorage.removeItem("userId");

    ElMessage.success("账号已注销");

    // 3️⃣ 跳首页
    router.push("/");

    // 4️⃣ 强制刷新
    setTimeout(() => {
      location.reload();
    }, 100);
  } catch (e) {
    if (e !== "cancel") {
      console.error(e);
      ElMessage.error("注销失败");
    }
  }
}
</script>

<style scoped>
.settings-page {
  max-width: 600px;
  margin: 40px auto;
}

.card {
  padding: 20px;
  border-radius: 12px;
}
</style>
