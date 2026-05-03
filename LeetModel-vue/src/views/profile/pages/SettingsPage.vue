<template>
  <div class="settings-page">
    <h2 class="page-title">编辑个人资料</h2>

    <el-card class="card" shadow="never">
      <el-form :model="form" label-width="100px" class="settings-form">
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
        <el-form-item label="注册时间">
          <el-input v-model="form.createTime" disabled />
        </el-form-item>

        <!-- 按钮 -->
        <el-form-item>
          <el-button type="primary" @click="handleSubmit">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="danger-zone">
      <h3 class="danger-title">危险操作</h3>
      <p class="danger-desc">注销账号后所有数据将被永久删除且无法恢复。</p>
      <el-button type="danger" @click="handleDelete">注销账号</el-button>
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { updateUser } from "@/api/user";
import request from "@/api/request";
import { useRouter } from "vue-router";
import { deleteUser } from "@/api/user";

const userId = localStorage.getItem("userId");

const form = reactive({
  username: "",
  email: "",
  createTime: "",
  school: "",
});

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
    await ElMessageBox.confirm("注销账号后将无法恢复，是否继续？", "警告", {
      confirmButtonText: "确定注销",
      cancelButtonText: "取消",
      type: "warning",
    });

    await deleteUser(userId);

    localStorage.removeItem("token");
    localStorage.removeItem("userId");

    ElMessage.success("账号已注销");

    router.push("/");

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
  max-width: 620px;
  margin: 0 auto;
  padding: 24px 30px 40px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 24px;
}

.card {
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  overflow: hidden;
}

.card :deep(.el-card__body) {
  padding: 28px;
}

.settings-form .el-form-item {
  margin-bottom: 22px;
}

.settings-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--lm-text-primary, #1a1a2e);
}

.danger-zone {
  margin-top: 32px;
  padding: 24px;
  background: var(--lm-surface, #fff);
  border: 1px solid #fbc4c4;
  border-radius: 12px;
}

.danger-title {
  font-size: 16px;
  font-weight: 700;
  color: #f56c6c;
  margin: 0 0 8px;
}

.danger-desc {
  font-size: 13px;
  color: var(--lm-text-muted, #999);
  margin: 0 0 16px;
  line-height: 1.5;
}
</style>
