<template>
  <transition name="fade">
    <div v-if="userStore.isLogin" class="ai-widget">
      <transition name="slide-up">
        <div v-if="opened" class="ai-panel">
          <div class="ai-header">
            <div class="ai-header-title">
              <span class="ai-dot"></span>
              <strong>AI 客服</strong>
            </div>
            <button type="button" class="ai-close" aria-label="收起客服" @click="opened = false">×</button>
          </div>

          <div ref="messagesRef" class="ai-messages">
            <div v-for="msg in messages" :key="msg.id" class="ai-msg" :class="msg.role">
              <div class="ai-bubble">
                <p class="ai-content">{{ msg.content || '（无内容）' }}</p>
                <div class="ai-meta">
                  <el-tag v-if="msg.status === 'FAILED'" type="danger" size="small">{{ msg.errorMessage || '回复失败' }}</el-tag>
                  <el-tag v-else-if="msg.status === 'RUNNING'" type="warning" size="small">生成中</el-tag>
                  <el-tag v-if="msg.usedProblemTool" type="info" size="small">已参考题目</el-tag>
                  <button v-if="msg.role === 'assistant' && msg.status === 'FAILED'" type="button" class="ai-retry" @click="retry(msg.id)">重试</button>
                </div>
              </div>
            </div>
            <div v-if="messages.length === 0" class="ai-empty">
              <p>你好，我是 LeetModel 客服。</p>
              <p>可以询问平台操作、组队、提交或评审相关问题。</p>
            </div>
          </div>

          <div class="ai-input">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="输入你的问题…"
              @keydown.enter.exact.prevent="send"
            />
            <div class="ai-input-bar">
              <span class="ai-tip">Enter 发送</span>
              <el-button type="primary" size="small" :loading="sending" :disabled="!draft.trim()" @click="send">发送</el-button>
            </div>
          </div>
        </div>
      </transition>

      <button type="button" class="ai-bubble-btn" @click="opened = !opened" :aria-label="opened ? '收起 AI 客服' : '打开 AI 客服'">
        <el-icon :size="22"><ChatDotRound /></el-icon>
        <span v-if="!opened" class="ai-bubble-label">AI 客服</span>
      </button>
    </div>
  </transition>
</template>

<script setup>
import { nextTick, ref } from "vue";
import { ElMessage } from "element-plus";
import { ChatDotRound } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/user";
import { listConversations, createConversation, getConversation, sendMessage, retryMessage } from "@/api/assistant";

const userStore = useUserStore();
const opened = ref(false);
const sending = ref(false);
const messages = ref([]);
const currentId = ref(null);
const draft = ref("");
const messagesRef = ref(null);

function uuid() {
  if (crypto?.randomUUID) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight;
  });
}

async function open() {
  opened.value = true;
  try {
    const res = await listConversations();
    const conversations = res.data || [];
    if (conversations.length) {
      currentId.value = conversations[0].id;
      const detail = (await getConversation(currentId.value)).data;
      messages.value = detail.messages || [];
    } else {
      currentId.value = (await createConversation("AI 客服咨询")).data.id;
      messages.value = [];
    }
    scrollToBottom();
  } catch (error) {
    ElMessage.error(error.message || "AI 客服加载失败");
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
    if (userMessage) messages.value.push(userMessage);
    if (assistantMessage) messages.value.push(assistantMessage);
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
    const index = messages.value.findIndex((item) => String(item.id) === String(messageId));
    if (index >= 0) messages.value.splice(index, 1, res.data);
    else messages.value.push(res.data);
    scrollToBottom();
  } catch (error) {
    ElMessage.error(error.message || "重试失败");
  }
}
</script>

<style scoped>
.ai-widget { position: fixed; right: 24px; bottom: 24px; z-index: 4000; display: flex; flex-direction: column; align-items: flex-end; gap: 12px; }
.ai-bubble-btn { display: inline-flex; align-items: center; gap: 8px; padding: 14px 18px; border: 0; border-radius: 999px; background: var(--lm-primary); color: #fff; font: inherit; font-size: 15px; font-weight: 600; cursor: pointer; box-shadow: 0 8px 24px rgba(37, 99, 235, 0.32); transition: transform .2s, box-shadow .2s; }
.ai-bubble-btn:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(37, 99, 235, 0.4); }
.ai-panel { display: flex; width: 380px; max-width: calc(100vw - 32px); height: 520px; max-height: calc(100vh - 150px); flex-direction: column; overflow: hidden; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 16px; box-shadow: var(--lm-shadow-xl); }
.ai-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--lm-border); }
.ai-header-title { display: flex; align-items: center; gap: 8px; color: var(--lm-text-primary); }
.ai-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--lm-success); }
.ai-close { padding: 0 6px; border: 0; background: transparent; color: var(--lm-text-muted); font-size: 22px; line-height: 1; cursor: pointer; }
.ai-close:hover { color: var(--lm-text-primary); }
.ai-messages { display: flex; flex-direction: column; gap: 10px; padding: 14px; overflow: auto; flex: 1; }
.ai-msg { display: flex; }
.ai-msg.user { justify-content: flex-end; }
.ai-msg.assistant { justify-content: flex-start; }
.ai-bubble { max-width: 80%; padding: 8px 12px; border-radius: 12px; }
.ai-msg.user .ai-bubble { background: var(--lm-primary); color: #fff; }
.ai-msg.assistant .ai-bubble { background: var(--lm-bg-secondary); }
.ai-content { margin: 0; white-space: pre-wrap; line-height: 1.6; font-size: 13px; }
.ai-meta { display: flex; align-items: center; gap: 6px; margin-top: 6px; flex-wrap: wrap; }
.ai-msg.user .ai-meta { color: rgba(255,255,255,.85); }
.ai-msg.assistant .ai-meta { color: var(--lm-text-muted); }
.ai-retry { padding: 0; border: 0; background: transparent; color: var(--lm-primary); font-size: 12px; cursor: pointer; }
.ai-empty { margin: auto; padding: 24px 32px; text-align: center; color: var(--lm-text-muted); font-size: 13px; line-height: 1.7; }
.ai-input { padding: 12px; border-top: 1px solid var(--lm-border); }
.ai-input-bar { display: flex; align-items: center; justify-content: space-between; margin-top: 6px; }
.ai-tip { color: var(--lm-text-muted); font-size: 11px; }
.slide-up-enter-active, .slide-up-leave-active { transition: transform .2s ease, opacity .2s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(12px); opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
@media (max-width: 520px) { .ai-widget { right: 12px; bottom: 12px; } .ai-panel { height: 72vh; } }
</style>
