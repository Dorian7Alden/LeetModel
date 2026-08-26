<template>
  <div class="assistant-page">
    <PageHeader title="AI 客服" description="平台使用答疑与受控的题目/选题辅助">
    </PageHeader>

    <div class="chat-shell">
      <aside class="conversation-side">
        <div class="side-head">
          <span class="side-title">会话</span>
          <el-button type="primary" plain size="small" :disabled="creating" @click="handleCreateConversation">新建</el-button>
        </div>
        <div v-loading="loadingConversations" class="conversation-list">
          <button
            v-for="conv in conversations"
            :key="conv.id"
            type="button"
            class="conversation-item"
            :class="{ active: String(conv.id) === String(currentId) }"
            @click="selectConversation(conv.id)"
          >
            <span class="conv-title">{{ conv.title || '未命名会话' }}</span>
            <span class="conv-meta">{{ formatDate(conv.updateTime) }}</span>
          </button>
          <el-empty v-if="!loadingConversations && conversations.length === 0" description="暂无会话" :image-size="60" />
        </div>
      </aside>

      <section class="chat-main">
        <template v-if="conversation">
          <div class="chat-messages" ref="messagesRef">
            <div v-for="msg in conversation.messages" :key="msg.id" class="message-row" :class="msg.role">
              <div class="message-bubble">
                <p class="message-content">{{ msg.content || '（无内容）' }}</p>
                <div class="message-meta">
                  <el-tag v-if="msg.status === 'FAILED'" type="danger" size="small">{{ msg.errorMessage || '回复失败' }}</el-tag>
                  <el-tag v-else-if="msg.status === 'RUNNING'" type="warning" size="small">生成中</el-tag>
                  <el-tag v-if="msg.usedProblemTool" type="info" size="small">已参考题目</el-tag>
                  <span v-if="msg.modelName" class="model-name">{{ msg.modelName }}</span>
                  <span class="msg-time">{{ formatDate(msg.createTime) }}</span>
                </div>
                <el-button v-if="msg.role === 'assistant' && msg.status === 'FAILED'" type="primary" link size="small" @click="retry(msg.id)">重试</el-button>
              </div>
            </div>
            <el-empty v-if="conversation.messages.length === 0" description="开始提问吧，我会尽力解答平台相关问题" :image-size="60" />
          </div>

          <div class="chat-input">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="输入你的问题，例如：如何确定最终提交？"
              @keydown.enter.exact.prevent="send"
            />
            <div class="input-actions">
              <span class="input-tip">发送前可回车，Shift+Enter 换行</span>
              <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send">发送</el-button>
            </div>
          </div>
        </template>

        <el-empty v-else class="empty-main" description="新建或选择一个会话开始对话" :image-size="90" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/common/PageHeader.vue";
import { listConversations, createConversation, getConversation, sendMessage, retryMessage } from "@/api/assistant";

const loadingConversations = ref(false);
const creating = ref(false);
const sending = ref(false);
const conversations = ref([]);
const currentId = ref(null);
const conversation = ref(null);
const draft = ref("");
const messagesRef = ref(null);

function formatDate(value) {
  return value ? String(value).replace("T", " ").slice(5, 16) : "";
}

function uuid() {
  if (crypto?.randomUUID) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight;
  });
}

async function loadConversations() {
  loadingConversations.value = true;
  try {
    const res = await listConversations();
    conversations.value = res.data || [];
  } catch (error) {
    ElMessage.error(error.message || "会话列表加载失败");
  } finally {
    loadingConversations.value = false;
  }
}

async function selectConversation(id) {
  currentId.value = id;
  try {
    conversation.value = (await getConversation(id)).data;
    scrollToBottom();
  } catch (error) {
    ElMessage.error(error.message || "会话加载失败");
  }
}

async function handleCreateConversation() {
  creating.value = true;
  try {
    const res = await createConversation("新的咨询");
    conversations.value.unshift(res.data);
    currentId.value = res.data.id;
    await selectConversation(res.data.id);
  } catch (error) {
    ElMessage.error(error.message || "创建会话失败");
  } finally {
    creating.value = false;
  }
}

async function send() {
  const content = draft.value.trim();
  if (!content || sending.value || !currentId.value) return;
  sending.value = true;
  draft.value = "";
  try {
    const res = await sendMessage(currentId.value, content, uuid());
    const { userMessage, assistantMessage } = res.data || {};
    if (conversation.value) {
      if (userMessage) conversation.value.messages.push(userMessage);
      if (assistantMessage) conversation.value.messages.push(assistantMessage);
    }
    await loadConversations();
    scrollToBottom();
  } catch (error) {
    ElMessage.error(error.message || "发送失败");
    draft.value = content;
  } finally {
    sending.value = false;
  }
}

async function retry(messageId) {
  try {
    const res = await retryMessage(messageId);
    if (conversation.value) {
      const index = conversation.value.messages.findIndex((item) => String(item.id) === String(messageId));
      if (index >= 0) conversation.value.messages.splice(index, 1, res.data);
      else conversation.value.messages.push(res.data);
    }
    scrollToBottom();
  } catch (error) {
    ElMessage.error(error.message || "重试失败");
  }
}

onMounted(async () => {
  await loadConversations();
  if (conversations.value.length) await selectConversation(conversations.value[0].id);
});
</script>

<style scoped>
.assistant-page { max-width: 1120px; margin: 0 auto; padding: 20px 0; }
.chat-shell { display: grid; grid-template-columns: 280px 1fr; gap: 16px; min-height: 620px; }
.conversation-side { background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; display: flex; flex-direction: column; }
.side-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--lm-border); }
.side-title { font-weight: 600; color: var(--lm-text-primary); }
.conversation-list { overflow: auto; flex: 1; padding: 8px; }
.conversation-item { display: flex; width: 100%; flex-direction: column; gap: 4px; padding: 10px 12px; margin-bottom: 4px; text-align: left; background: transparent; border: 1px solid transparent; border-radius: 8px; cursor: pointer; }
.conversation-item:hover { background: var(--lm-bg-secondary); }
.conversation-item.active { border-color: var(--lm-primary); background: var(--lm-primary-bg); }
.conv-title { overflow: hidden; color: var(--lm-text-primary); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.conv-meta { color: var(--lm-text-muted); font-size: 12px; }
.chat-main { display: flex; flex-direction: column; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; overflow: hidden; }
.empty-main { flex: 1; }
.chat-messages { display: flex; flex-direction: column; gap: 14px; padding: 20px; overflow: auto; flex: 1; }
.message-row { display: flex; }
.message-row.user { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }
.message-bubble { max-width: 78%; padding: 10px 14px; border-radius: 12px; }
.message-row.user .message-bubble { background: var(--lm-primary); color: #fff; }
.message-row.assistant .message-bubble { background: var(--lm-bg-secondary); }
.message-content { margin: 0; white-space: pre-wrap; line-height: 1.6; }
.message-meta { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.message-row.user .message-meta { color: rgba(255, 255, 255, 0.85); }
.message-row.assistant .message-meta { color: var(--lm-text-muted); }
.model-name { font-size: 11px; }
.msg-time { font-size: 11px; }
.chat-input { padding: 14px 16px; border-top: 1px solid var(--lm-border); }
.input-actions { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; }
.input-tip { color: var(--lm-text-muted); font-size: 12px; }
@media (max-width: 800px) { .chat-shell { grid-template-columns: 1fr; min-height: 480px; } .conversation-side { max-height: 220px; } }
</style>
