<template>
  <div class="submit-work">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>提交作品测试点</span>
        </div>
      </template>

      <el-form :model="form" ref="formRef" label-width="120px">
        <el-form-item label="关联题目ID" prop="problemId" required>
          <el-input v-model="form.problemId" placeholder="例如：12"></el-input>
        </el-form-item>
        
        <el-form-item label="作者或队伍名" prop="author">
          <el-input v-model="form.author" placeholder="参赛队伍 / 个人姓名"></el-input>
        </el-form-item>

        <el-form-item label="论文附件" prop="paper">
          <el-upload
            class="upload-demo"
            drag
            action="https://run.mocky.io/v3/9d059bf9-4660-45f2-925d-ce80ad6c4d15"
            :limit="1"
            accept=".pdf,.doc,.docx"
          >
             <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              点击或拖拽上传论文 (PDF / DOC)
            </div>
          </el-upload>
        </el-form-item>

        <el-form-item label="代码附件" prop="code">
           <el-upload
            class="upload-demo"
            action="https://run.mocky.io/v3/9d059bf9-4660-45f2-925d-ce80ad6c4d15"
            accept=".zip,.rar,.tar.gz"
          >
            <el-button type="primary">上传代码包</el-button>
          </el-upload>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="onSubmit">确认提交</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';

const formRef = ref(null);
const form = reactive({
  problemId: '',
  author: '',
  paper: null,
  code: null
});

const onSubmit = () => {
  if (!form.problemId) {
    ElMessage.error('题目ID必填');
    return;
  }
  ElMessage.success('测试作品已提交，可前往列表查看状态');
};
</script>

<style scoped>
.submit-work {
  max-width: 800px;
}
</style>
