<template>
  <div class="settings-page">
    <div class="page-header-row">
      <h2 class="page-title">编辑个人资料</h2>
      <router-link to="/profile" class="back-link">
        <el-icon :size="16"><ArrowLeft /></el-icon>
        <span>返回</span>
      </router-link>
    </div>

    <el-card class="card" shadow="never">
      <div class="avatar-section">
        <div class="avatar-upload" @click="triggerUpload">
          <img v-if="avatarUrl" :src="avatarUrl" class="avatar-img" />
          <el-icon v-else :size="40" class="avatar-placeholder"><UserFilled /></el-icon>
          <div class="avatar-overlay">
            <el-icon :size="20"><Camera /></el-icon>
            <span>更换头像</span>
          </div>
          <input ref="avatarInput" type="file" accept="image/*" class="file-hidden" @change="handleAvatarChange" />
        </div>
        <p class="avatar-hint">点击上传头像，支持 JPG、PNG、GIF 等格式</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="settings-form">
        <!-- 用户名 -->
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" disabled />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" maxlength="32" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" maxlength="64" />
        </el-form-item>

        <!-- 学校 -->
        <el-form-item label="学校" prop="school">
          <el-input v-model="form.school" maxlength="100" disabled placeholder="后端暂未支持" />
        </el-form-item>

        <!-- 手机号 -->
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" disabled placeholder="后端暂未支持" />
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

    <el-card class="card password-card" shadow="never">
      <h3 class="section-title">修改密码</h3>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px" class="settings-form">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleChangePassword">修改密码</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="danger-zone">
      <h3 class="danger-title">危险操作</h3>
      <p class="danger-desc">注销账号后所有数据将被永久删除且无法恢复。</p>
      <el-button type="danger" disabled>注销账号</el-button>
      <span class="unavailable-hint">后端暂未支持</span>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { ArrowLeft, Camera, UserFilled } from '@element-plus/icons-vue'
import { changePassword, getCurrentUser, updateCurrentUser, uploadCurrentAvatar } from "@/api/user";
import { useUserStore } from "@/store/user";

const userStore = useUserStore();

const formRef = ref(null);
const passwordFormRef = ref(null);
const avatarInput = ref(null);
const avatarUrl = ref("");

const form = reactive({
  username: "",
  nickname: "",
  email: "",
  createTime: "",
  school: "",
  phone: "",
});

const rules = {
  nickname: [
    { max: 32, message: "昵称最多32个字符", trigger: "blur" },
  ],
  email: [
    { type: "email", message: "邮箱格式不正确", trigger: ["blur", "change"] },
  ],
  school: [
    { max: 100, message: "学校名称不能超过100个字符", trigger: "blur" },
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" },
  ],
};

const passwordForm = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});

const validateConfirmPassword = (_rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error("两次输入的新密码不一致"));
    return;
  }
  callback();
};

const passwordRules = {
  oldPassword: [{ required: true, message: "请输入旧密码", trigger: "blur" }],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, max: 32, message: "密码长度为6-32位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    { validator: validateConfirmPassword, trigger: "blur" },
  ],
};

async function loadUser() {
  try {
    const res = await getCurrentUser();

    function normalizeUser(user) {
      return {
        username: user.username ?? "",
        nickname: user.nickname ?? "",
        email: user.email ?? "",
        createTime: user.createTime ?? "",
        school: user.school ?? "",
        phone: user.phone ?? "",
      };
    }
    const user = normalizeUser(res.data);
    Object.assign(form, user);
    avatarUrl.value = res.data.avatarUrl ?? "";
  } catch (e) {
    ElMessage.error("加载用户失败");
  }
}

function triggerUpload() {
  avatarInput.value?.click();
}

async function handleAvatarChange(e) {
  const file = e.target.files?.[0];
  if (!file) return;
  try {
    const res = await uploadCurrentAvatar(file);
    avatarUrl.value = res.data.avatarUrl;
    userStore.updateProfile({ avatarUrl: res.data.avatarUrl });
    ElMessage.success("头像更新成功");
  } catch {
    ElMessage.error("头像上传失败");
  } finally {
    e.target.value = "";
  }
}

onMounted(() => {
  loadUser();
});

async function handleSubmit() {
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  try {
    const res = await updateCurrentUser({
      nickname: form.nickname || null,
      email: form.email || null,
    });
    userStore.updateProfile(res.data);
    ElMessage.success("修改成功");
  } catch (e) {
    ElMessage.error("修改失败");
  }
}

async function handleChangePassword() {
  try {
    await passwordFormRef.value.validate();
  } catch {
    return;
  }

  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    });
    passwordFormRef.value.resetFields();
    ElMessage.success("密码修改成功");
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message || "密码修改失败");
  }
}
</script>

<style scoped>
.settings-page {
  max-width: 620px;
  margin: 0 auto;
  padding: 24px 30px 40px;
}

.page-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--lm-text-secondary);
  text-decoration: none;
  font-size: 14px;
  padding: 6px 10px;
  border-radius: var(--lm-radius-sm);
  transition: color var(--lm-transition), background var(--lm-transition);
}

.back-link:hover {
  color: var(--lm-primary);
  background: var(--lm-primary-bg);
}

.card {
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  overflow: hidden;
}

.card :deep(.el-card__body) {
  padding: 28px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--lm-border-light, #f0f0f0);
}

.avatar-upload {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: var(--lm-surface-secondary, #f5f6f8);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  border: 2px dashed var(--lm-border, #d0d5dd);
  transition: border-color 0.2s;
}

.avatar-upload:hover {
  border-color: var(--lm-primary, #2563eb);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  color: var(--lm-text-muted, #bbb);
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #fff;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-upload:hover .avatar-overlay {
  opacity: 1;
}

.avatar-hint {
  font-size: 12px;
  color: var(--lm-text-muted, #999);
  margin: 10px 0 0;
}

.file-hidden {
  display: none;
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

.password-card {
  margin-top: 24px;
}

.section-title {
  margin: 0 0 24px;
  font-size: 18px;
  color: var(--lm-text-primary);
}

.unavailable-hint {
  margin-left: 12px;
  color: var(--lm-text-muted);
  font-size: 13px;
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
