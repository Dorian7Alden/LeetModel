<template>
  <div class="oss-upload">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>OSS 文件上传 (公共图床/资源库)</span>
        </div>
      </template>

      <!-- 上传区域 -->
      <el-upload
        class="upload-area"
        drag
        action=""
        :http-request="customUpload"
        multiple
        :on-success="handleSuccess"
        :on-error="handleError"
        :before-upload="beforeUpload"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将文件拖入此处，或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持各种类型文件。上传后，后端会自动将文件转移到 OSS 并返回可直接访问的链接。
          </div>
        </template>
      </el-upload>

      <!-- 上传历史/结果展示表 -->
      <el-divider content-position="left" style="margin-top: 40px;">近期上传结果</el-divider>
      <el-table :data="uploadedFiles" stripe style="width: 100%">
        <el-table-column prop="name" label="文件名" min-width="200" />
        <el-table-column prop="url" label="外链地址 (点击复制)" min-width="300">
          <template #default="{ row }">
             <el-link type="primary" :underline="false" @click="copyUrl(row.url)">
                {{ row.url }} <el-icon class="el-icon--right"><CopyDocument /></el-icon>
             </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="time" label="上传时间" width="160" />
      </el-table>

    </el-card>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { uploadFile } from '@/api/file';

// 从本地缓存加载数据，默认为占位数据
const getInitialData = () => {
  const cachedData = localStorage.getItem('oss_uploaded_files');
  if (cachedData) {
    try {
      return JSON.parse(cachedData);
    } catch (e) {
      console.error('Failed to parse cached OSS files', e);
    }
  }
  return [];
};

const uploadedFiles = ref(getInitialData());

// 监听数组变化，持久化到本地缓存
watch(
  uploadedFiles,
  (newVal) => {
    localStorage.setItem('oss_uploaded_files', JSON.stringify(newVal));
  },
  { deep: true }
);

// 自定义上传行为
const customUpload = async (options) => {
  try {
    const res = await uploadFile(options.file);
    options.onSuccess(res, options.file);
  } catch (error) {
    options.onError(error, options.file);
  }
};

const beforeUpload = (file) => {
  // 做格式、大小校验等
  return true;
};

// 后端返回的 OSS 地址处理
const handleSuccess = (response, file, fileList) => {
  // 假定后端直接返回链接字符串（或者放在 data 字段中）
  const ossUrl = response.data || response || 'https://example-oss.com/' + file.name;

  uploadedFiles.value.unshift({
    name: file.name,
    url: ossUrl,
    time: new Date().toLocaleString()
  });

  ElMessage.success(`文件 ${file.name} 上传 OSS 成功！`);
};

const handleError = (error, file) => {
  ElMessage.error(`文件 ${file.name} 上传失败！`);
};

// 复制链接
const copyUrl = async (url) => {
  try {
    await navigator.clipboard.writeText(url);
    ElMessage.success('OSS链接已复制到剪贴板可以去 Markdown 粘贴了~');
  } catch (err) {
    ElMessage.error('复制失败');
  }
};
</script>

<style scoped>
.oss-upload {
  max-width: 1000px;
  margin: 0 auto;
}
.upload-area {
  margin-top: 20px;
}
</style>