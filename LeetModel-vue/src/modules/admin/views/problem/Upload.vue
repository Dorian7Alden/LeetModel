<template>
  <div class="problem-upload">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>上传或录入题目</span>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-divider content-position="left">基础信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属赛事" prop="competition_id">
              <el-select v-model="form.competition_id" placeholder="请选择赛事" style="width: 100%;">
                <el-option
                  v-for="comp in competitions"
                  :key="comp.competition_id"
                  :label="comp.competition_name"
                  :value="comp.competition_id"
                ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="题目标题" prop="problem_title">
              <el-input v-model="form.problem_title" placeholder="请输入题目标题"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题目标签" prop="tags">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="请选择或创建题目标签"
            style="width: 100%;"
          >
            <el-option label="数据分析" value="数据分析"></el-option>
            <el-option label="机理建模" value="机理建模"></el-option>
            <el-option label="优化模型" value="优化模型"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="Markdown简介" prop="problem_markdown">
          <el-input
            type="textarea"
            :rows="6"
            v-model="form.problem_markdown"
            placeholder="请输入题目的 Markdown 格式简介..."
          ></el-input>
        </el-form-item>

        <el-divider content-position="left">附件与链接管理 (一对多)</el-divider>
        <el-form-item label="管理链接">
          <el-button type="primary" plain @click="addLink" icon="Plus">手动添加链接</el-button>
          <el-upload
            class="upload-demo"
            action="https://run.mocky.io/v3/9d059bf9-4660-45f2-925d-ce80ad6c4d15"
            :show-file-list="false"
            :on-success="handleUploadSuccess"
            style="display: inline-block; margin-left: 10px;"
          >
            <el-button type="success" plain icon="Upload">模拟文件上传并生成链接</el-button>
          </el-upload>
        </el-form-item>

        <el-table :data="form.links" border style="width: 100%; margin-bottom: 20px;">
          <el-table-column label="链接标题" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.link_title" placeholder="如: 官网PDF"></el-input>
            </template>
          </el-table-column>
          <el-table-column label="链接类型" width="120">
            <template #default="{ row }">
              <el-select v-model="row.link_type" placeholder="类型">
                <el-option label="pdf" value="pdf"></el-option>
                <el-option label="zip" value="zip"></el-option>
                <el-option label="docx" value="docx"></el-option>
                <el-option label="csv" value="csv"></el-option>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="远程URL" min-width="200">
            <template #default="{ row }">
              <el-input v-model="row.link_url" placeholder="http://..."></el-input>
            </template>
          </el-table-column>
          <el-table-column label="展示优先" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.display_priority" :min="0" :max="99" controls-position="right" style="width: 100%"></el-input-number>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button type="danger" icon="Delete" circle @click="removeLink($index)"></el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-form-item>
          <el-button type="primary" @click="onSubmit" size="large">保存并发布</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";

const formRef = ref(null);
const form = reactive({
  competition_id: "",
  problem_title: "",
  tags: [],
  problem_markdown: "",
  links: []
});
const rules = {
  competition_id: [{ required: true, message: "请选择所属赛事", trigger: "change" }],
  problem_title: [{ required: true, message: "请输入题目标题", trigger: "blur" }]
};

const competitions = ref([
  { competition_id: 1, competition_name: "高教社杯全国大学生数学建模竞赛(国赛)" },
  { competition_id: 2, competition_name: "美国大学生数学建模竞赛(MCM/ICM)" }
]);

const addLink = () => {
  form.links.push({ link_title: "", link_type: "pdf", link_url: "", display_priority: 0 });
};
const removeLink = (index) => { form.links.splice(index, 1); };

const handleUploadSuccess = (response, file) => {
  const mockUrl = "https://example.com/files/" + file.name;
  form.links.push({ link_title: file.name, link_type: file.name.endsWith('.zip') ? 'zip' : 'pdf', link_url: mockUrl, display_priority: form.links.length });
  ElMessage.success("上传成功并生成附件链接");
};

const onSubmit = () => {
  formRef.value.validate((v) => {
    if (v) {
      console.log(JSON.stringify(form, null, 2));
      ElMessage.success("保存成功 (控制台已输出待发送的数据 payload)");
    } else {
      ElMessage.error('请完善表单必填信息');
    }
  });
};
</script>

<style scoped>
.problem-upload {
  max-width: 1000px;
  margin: 0 auto;
}
</style>