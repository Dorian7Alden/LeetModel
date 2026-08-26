<template>
  <transition name="fade">
    <div v-if="userStore.isLogin" class="ai-widget">
      <transition name="slide-up">
        <div v-if="opened" class="ai-panel">
          <div class="ai-header">
            <div class="ai-header-title">
              <span class="ai-dot"></span>
              <strong>AI 客服</strong>
              <span class="ai-sub">对话记录自动保存</span>
            </div>
            <div class="ai-header-actions">
              <el-button size="small" plain :disabled="creating" @click="newConversation">新建会话</el-button>
              <button type="button" class="ai-close" aria-label="收起客服" @click="opened = false">×</button>
            </div>
          </div>

          <div class="ai-body">
            <aside class="ai-history">
              <div class="ai-history-title">会话</div>
              <div class="ai-history-list" v-loading="loadingConvs">
                <button
                  v-for="conv in conversations"
                  :key="conv.id"
                  type="button"
                  class="ai-history-item"
                  :class="{ active: String(conv.id) === String(currentId) }"
                  @click="selectConversation(conv.id)"
                >
                  <span class="ai-history-name">{{ conv.title || '未命名对话' }}</span>
                  <span class="ai-history-time">{{ shortTime(conv.updateTime) }}</span>
                </button>
                <p v-if="!loadingConvs && conversations.length === 0" class="ai-history-empty">暂无历史对话</p>
              </div>
            </aside>

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
          </div>

          <div class="ai-quick">
            <button
              v-for="q in quickQuestions"
              :key="q"
              type="button"
              class="ai-quick-chip"
              :disabled="sending"
              @click="send(q)"
            >{{ q }}</button>
          </div>

          <div class="ai-input">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="输入你的问题…"
              @keydown.enter.exact.prevent="send()"
            />
            <div class="ai-input-bar">
              <span class="ai-tip">Enter 发送</span>
              <el-button type="primary" size="small" :loading="sending" :disabled="!draft.trim()" @click="send()">发送</el-button>
            </div>
          </div>
        </div>
      </transition>

      <button type="button" class="ai-bubble-btn" @click="toggleOpen" :aria-label="opened ? '收起 AI 客服' : '打开 AI 客服'">
        <el-icon :size="22"><ChatDotRound /></el-icon>
        <span v-if="!opened" class="ai-bubble-label">AI 客服</span>
      </button>
    </div>
  </transition>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref } from "vue";
import { ElMessage } from "element-plus";
import { ChatDotRound } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/user";
import { listConversations, createConversation, getConversation, sendMessage, retryMessage } from "@/api/assistant";

const userStore = useUserStore();
const opened = ref(false);
const sending = ref(false);
const creating = ref(false);
const loadingConvs = ref(false);
const conversations = ref([]);
const messages = ref([]);
const currentId = ref(null);
const draft = ref("");
const messagesRef = ref(null);

// 固定的快捷提问（简洁、可预期；如需扩展可改为配置项）
const quickQuestions = [
  "如何创建队伍？",
  "如何提交论文？",
  "如何查看评审结果？",
  "如何确定最终提交？",
  "平台有哪些功能？",
];

function uuid() {
  if (crypto?.randomUUID) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function shortTime(value) {
  return value ? String(value).replace("T", " ").slice(5, 16) : "";
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight;
  });
}

async function loadConversations() {
  loadingConvs.value = true;
  try {
    conversations.value = (await listConversations()).data || [];
  } catch (error) {
    ElMessage.error(error.message || "对话记录加载失败");
  } finally {
    loadingConvs.value = false;
  }
}

async function open() {
  opened.value = true;
  await loadConversations();
  if (conversations.value.length) {
    await selectConversation(conversations.value[0].id);
  } else {
    await newConversation();
  }
}

function toggleOpen() {
  if (opened.value) {
    opened.value = false;
  } else {
    open();
  }
}

async function selectConversation(id) {
  currentId.value = id;
  try {
    const detail = (await getConversation(id)).data;
    messages.value = detail.messages || [];
    scrollToBottom();
  } catch (error) {
    ElMessage.error(error.message || "对话加载失败");
  }
}

async function newConversation() {
  creating.value = true;
  try {
    const res = await createConversation("AI 客服咨询");
    conversations.value.unshift(res.data);
    currentId.value = res.data.id;
    messages.value = [];
  } catch (error) {
    ElMessage.error(error.message || "新建会话失败");
  } finally {
    creating.value = false;
  }
}

async function send(text) {
  const content = (text || draft.value).trim();
  if (!content || sending.value) return;
  if (!currentId.value) {
    ElMessage.warning("请先创建会话");
    return;
  }
  sending.value = true;
  if (!text) draft.value = "";
  try {
    const res = await sendMessage(currentId.value, content, uuid());
    const { userMessage, assistantMessage } = res.data || {};
    if (userMessage) messages.value.push(userMessage);
    if (assistantMessage) messages.value.push(assistantMessage);
    await loadConversations();
    scrollToBottom();
  } catch (error) {
    ElMessage.error(error.message || "发送失败");
    if (!text) draft.value = content;
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

onBeforeUnmount(() => {
  opened.value = false;
});
</script>

<style scoped>
.ai-widget { position: fixed; right: 24px; bottom: 24px; z-index: 4000; display: flex; flex-direction: column; align-items: flex-end; gap: 12px; }
.ai-bubble-btn { display: inline-flex; align-items: center; gap: 8px; padding: 14px 18px; border: 0; border-radius: 999px; background: var(--lm-primary); color: #fff; font: inherit; font-size: 15px; font-weight: 600; cursor: pointer; box-shadow: 0 8px 24px rgba(37, 99, 235, 0.32); transition: transform .2s, box-shadow .2s; }
.ai-bubble-btn:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(37, 99, 235, 0.4); }
.ai-panel { display: flex; width: 460px; max-width: calc(100vw - 32px); height: 560px; max-height: calc(100vh - 150px); flex-direction: column; overflow: hidden; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 16px; box-shadow: var(--lm-shadow-xl); }
.ai-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; border-bottom: 1px solid var(--lm-border); }
.ai-header-title { display: flex; align-items: center; gap: 8px; color: var(--lm-text-primary); }
.ai-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--lm-success); }
.ai-sub { color: var(--lm-text-muted); font-size: 12px; }
.ai-header-actions { display: flex; align-items: center; gap: 8px; }
.ai-close { padding: 0 6px; border: 0; background: transparent; color: var(--lm-text-muted); font-size: 22px; line-height: 1; cursor: pointer; }
.ai-close:hover { color: var(--lm-text-primary); }
.ai-body { display: flex; flex: 1; min-height: 0; }
.ai-history { display: flex; width: 148px; flex-direction: column; border-right: 1px solid var(--lm-border-light); background: var(--lm-bg-secondary); }
.ai-history-title { padding: 10px 12px 6px; color: var(--lm-text-muted); font-size: 12px; font-weight: 600; }
.ai-history-list { overflow: auto; flex: 1; padding: 4px 6px; }
.ai-history-item { display: flex; width: 100%; flex-direction: column; gap: 3px; padding: 8px 10px; margin-bottom: 3px; text-align: left; border: 1px solid transparent; border-radius: 8px; background: transparent; cursor: pointer; }
.ai-history-item:hover { background: var(--lm-surface); }
.ai-history-item.active { border-color: var(--lm-primary); background: var(--lm-primary-bg); }
.ai-history-name { overflow: hidden; color: var(--lm-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.ai-history-time { color: var(--lm-text-muted); font-size: 11px; }
.ai-history-empty { padding: 8px 10px; color: var(--lm-text-muted); font-size: 12px; }
.ai-messages { display: flex; flex-direction: column; gap: 10px; padding: 14px; overflow: auto; flex: 1; min-width: 0; }
.ai-msg { display: flex; }
.ai-msg.user { justify-content: flex-end; }
.ai-msg.assistant { justify-content: flex-start; }
.ai-bubble { max-width: 82%; padding: 8px 12px; border-radius: 12px; }
.ai-msg.user .ai-bubble { background: var(--lm-primary); color: #fff; }
.ai-msg.assistant .ai-bubble { background: var(--lm-bg-secondary); }
.ai-content { margin: 0; white-space: pre-wrap; line-height: 1.6; font-size: 13px; }
.ai-meta { display: flex; align-items: center; gap: 6px; margin-top: 6px; flex-wrap: wrap; }
.ai-msg.user .ai-meta { color: rgba(255,255,255,.85); }
.ai-msg.assistant .ai-meta { color: var(--lm-text-muted); }
.ai-retry { padding: 0; border: 0; background: transparent; color: var(--lm-primary); font-size: 12px; cursor: pointer; }
.ai-empty { margin: auto; padding: 24px 24px; text-align: center; color: var(--lm-text-muted); font-size: 13px; line-height: 1.7; }
.ai-quick { display: flex; flex-wrap: wrap; gap: 6px; padding: 0 12px 8px; }
.ai-quick-chip { padding: 5px 10px; border: 1px solid var(--lm-border); border-radius: 999px; background: var(--lm-surface); color: var(--lm-text-secondary); font: inherit; font-size: 12px; cursor: pointer; transition: border-color .2s, color .2s; }
.ai-quick-chip:hover { color: var(--lm-primary); border-color: var(--lm-primary); }
.ai-quick-chip:disabled { cursor: not-allowed; opacity: .6; }
.ai-input { padding: 12px; border-top: 1px solid var(--lm-border); }
.ai-input-bar { display: flex; align-items: center; justify-content: space-between; margin-top: 6px; }
.ai-tip { color: var(--lm-text-muted); font-size: 11px; }
.slide-up-enter-active, .slide-up-leave-active { transition: transform .2s ease, opacity .2s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(12px); opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
@media (max-width: 520px) { .ai-widget { right: 12px; bottom: 12px; } .ai-panel { width: calc(100vw - 24px); height: 72vh; } .ai-history { width: 116px; } }
</style>
