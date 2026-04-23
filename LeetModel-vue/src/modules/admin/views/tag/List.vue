<template>
  <div class="tag-list-page">
    <el-card shadow="never">
      <div class="action-bar">
        <el-select
          v-model="selectedCategoryId"
          placeholder="选择标签分类"
          style="width: 220px"
          @change="fetchTags"
        >
          <el-option
            v-for="item in categoryOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-input
          v-model="keyword"
          placeholder="按标签名搜索"
          clearable
          style="width: 260px"
        />

        <div class="actions-right">
          <el-button @click="fetchTags" :loading="loading">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增标签
          </el-button>
        </div>
      </div>

      <el-table :data="filteredTags" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="tagId" label="ID" width="90" />
        <el-table-column prop="name" label="标签名" min-width="220" />
        <el-table-column label="分类" width="160">
          <template #default="scope">
            <el-tag type="info">{{ getCategoryLabel(scope.row.categoryId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="openEditDialog(scope.row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增标签' : '编辑标签'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="90px">
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="formModel.categoryId" style="width: 100%" placeholder="请选择分类">
            <el-option
              v-for="item in categoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签名" prop="name">
          <el-input v-model="formModel.name" maxlength="30" show-word-limit placeholder="请输入标签名" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { createTag, deleteTag, getTagsByCategory, updateTag } from "@/api/tag";

const categoryOptions = [
  { value: 1, label: "难度" },
  { value: 4, label: "年份" },
  { value: 5, label: "题型" },
  { value: 6, label: "模型" },
  { value: 7, label: "行业" },
  { value: 8, label: "数据特征" },
];

const loading = ref(false);
const submitLoading = ref(false);
const tags = ref([]);
const keyword = ref("");
const selectedCategoryId = ref(1);

const dialogVisible = ref(false);
const dialogMode = ref("create");
const formRef = ref();
const formModel = reactive({
  tagId: null,
  name: "",
  categoryId: 1,
});

const formRules = {
  categoryId: [{ required: true, message: "请选择分类", trigger: "change" }],
  name: [{ required: true, message: "请输入标签名", trigger: "blur" }],
};

const getCategoryLabel = (id) => {
  return categoryOptions.find((item) => item.value === id)?.label || "未分类";
};

const normalizeTagList = (res) => {
  const list = Array.isArray(res) ? res : res?.data || [];
  return list.map((item) => ({
    ...item,
    categoryId: item.categoryId ?? selectedCategoryId.value,
  }));
};

const fetchTags = async () => {
  loading.value = true;
  try {
    const res = await getTagsByCategory(selectedCategoryId.value);
    tags.value = normalizeTagList(res);
  } catch (error) {
    console.error("加载标签失败", error);
    ElMessage.error("加载标签失败，请稍后重试");
  } finally {
    loading.value = false;
  }
};

const filteredTags = computed(() => {
  const key = keyword.value.trim().toLowerCase();
  if (!key) {
    return tags.value;
  }
  return tags.value.filter((item) => (item.name || "").toLowerCase().includes(key));
});

const resetFormModel = () => {
  formModel.tagId = null;
  formModel.name = "";
  formModel.categoryId = selectedCategoryId.value;
};

const openCreateDialog = () => {
  dialogMode.value = "create";
  resetFormModel();
  dialogVisible.value = true;
};

const openEditDialog = (row) => {
  dialogMode.value = "edit";
  formModel.tagId = row.tagId;
  formModel.name = row.name;
  formModel.categoryId = row.categoryId;
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  if (!formRef.value) {
    return;
  }

  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }

  submitLoading.value = true;
  try {
    const payload = {
      name: formModel.name.trim(),
      categoryId: formModel.categoryId,
    };

    if (dialogMode.value === "create") {
      await createTag(payload);
      ElMessage.success("标签创建成功");
    } else {
      await updateTag(formModel.tagId, payload);
      ElMessage.success("标签更新成功");
    }

    dialogVisible.value = false;
    selectedCategoryId.value = formModel.categoryId;
    await fetchTags();
  } catch (error) {
    console.error("保存标签失败", error);
    ElMessage.error("保存标签失败，请检查后重试");
  } finally {
    submitLoading.value = false;
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除标签“${row.name}”吗？`, "删除确认", {
      type: "warning",
      confirmButtonText: "确认",
      cancelButtonText: "取消",
    });

    await deleteTag(row.tagId);
    ElMessage.success("删除成功");
    await fetchTags();
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除标签失败", error);
      ElMessage.error("删除失败，请稍后重试");
    }
  }
};

onMounted(() => {
  fetchTags();
});
</script>

<style scoped>
.action-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.actions-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
</style>
