<template>
  <div class="assistant-page">
    <header class="assistant-page-header">
      <div>
        <p class="assistant-eyebrow">智能支持</p>
        <h1>AI 客服</h1>
        <p class="assistant-description">查询平台操作、组队、提交与评审流程，也可以让客服协助筛选适合的练习题。</p>
      </div>
      <div class="assistant-capabilities" aria-label="AI 客服能力">
        <span>平台答疑</span>
        <span>选题辅助</span>
        <span>历史会话</span>
      </div>
    </header>

    <AiAssistantWidget embedded />
  </div>
</template>

<script setup>
import AiAssistantWidget from "@/components/common/AiAssistantWidget.vue";
</script>

<style scoped>
.assistant-page {
  width: min(100%, 1180px);
  margin: 0 auto;
}

.assistant-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.assistant-eyebrow {
  margin: 0 0 5px;
  color: var(--lm-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.assistant-page-header h1 {
  margin: 0;
  color: var(--lm-text-primary);
  font-size: 28px;
  line-height: 1.25;
}

.assistant-description {
  max-width: 720px;
  margin: 8px 0 0;
  color: var(--lm-text-muted);
  font-size: 14px;
  line-height: 1.7;
}

.assistant-capabilities {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.assistant-capabilities span {
  padding: 6px 10px;
  border: 1px solid var(--lm-border);
  border-radius: 999px;
  background: var(--lm-surface);
  color: var(--lm-text-secondary);
  font-size: 12px;
}

@media (max-width: 720px) {
  .assistant-page-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    margin-bottom: 14px;
  }

  .assistant-page-header h1 {
    font-size: 24px;
  }

  .assistant-capabilities {
    justify-content: flex-start;
  }
}
</style>
