<template>
  <div class="post-editor-container">
    <!-- 顶部操作栏 -->
    <div class="editor-header">
      <input
        type="text"
        class="title-input"
        placeholder="请输入标题"
        v-model="postTitle"
      />
      <div class="header-actions">
        <button class="cancel-btn">取消</button>
        <button class="publish-btn" :disabled="!postTitle.trim()">
          <el-icon class="publish-icon"><Promotion /></el-icon>
          发布
        </button>
      </div>
    </div>

    <!-- 话题选择栏 -->
    <div class="topic-bar">
      <div class="topic-selector">
        <el-icon class="topic-icon"><UserFilled /></el-icon>
        <button class="add-topic-btn">+ 话题</button>
      </div>
    </div>

    <!-- 编辑器区域 -->
    <div class="editor-content">
      <div class="editor-input-area" @click="focusEditor">
        <span class="add-icon">+</span>
        <input
          type="text"
          class="editor-placeholder"
          placeholder="输入 “/” 快速添加内容"
          readonly
          ref="editorRef"
        />
      </div>
      <!-- 右侧滚动条示意 -->
      <div class="scrollbar">
        <div class="scrollbar-thumb top"></div>
        <div class="scrollbar-thumb bottom"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { Promotion, UserFilled } from "@element-plus/icons-vue";

// 响应式数据
const postTitle = ref("");
const editorRef = ref(null);

// 聚焦编辑器
const focusEditor = () => {
  editorRef.value?.focus();
};
</script>

<style scoped>
.post-editor-container {
  width: 100%;
  max-width: 1200px;
  min-height: 90vh;
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  font-family:
    -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue",
    Arial, sans-serif;
  display: flex;
  flex-direction: column;
}

/* 顶部操作栏 */
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.title-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 24px;
  font-weight: 500;
  color: #333;
  padding: 8px 0;
}

.title-input::placeholder {
  color: #b0b0b0;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.cancel-btn {
  background-color: #f5f5f5;
  color: #666;
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
}

.publish-btn {
  background-color: #00b42a;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.publish-btn:disabled {
  background-color: #a5d9a7;
  cursor: not-allowed;
}

.publish-icon {
  font-size: 14px;
}

/* 话题选择栏 */
.topic-bar {
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.topic-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topic-icon {
  font-size: 18px;
  color: #999;
}

.add-topic-btn {
  background: none;
  border: none;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  padding: 4px 8px;
}

/* 编辑器区域 */
.editor-content {
  flex: 1;
  padding: 20px;
  display: flex;
  position: relative;
}

.editor-input-area {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.add-icon {
  font-size: 20px;
  color: #ccc;
}

.editor-placeholder {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  color: #999;
  background: transparent;
}

/* 右侧滚动条 */
.scrollbar {
  width: 8px;
  background-color: #f0f0f0;
  border-radius: 4px;
  position: absolute;
  right: 8px;
  top: 20px;
  bottom: 20px;
}

.scrollbar-thumb {
  width: 8px;
  background-color: #ccc;
  border-radius: 4px;
  position: absolute;
}

.scrollbar-thumb.top {
  top: 0;
  height: 20px;
}

.scrollbar-thumb.bottom {
  bottom: 0;
  height: 20px;
}
</style>
