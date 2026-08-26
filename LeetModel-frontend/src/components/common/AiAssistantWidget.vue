<template>
  <transition name="fade">
    <div v-if="userStore.isLogin" class="ai-widget">
      <transition name="slide-up">
        <div v-if="opened" class="ai-panel">
          <!-- Header -->
          <header class="ai-header">
            <button v-if="view === 'history'" type="button" class="ai-icon-btn" title="返回对话" @click="view = 'chat'">
              <el-icon :size="17"><ArrowLeft /></el-icon>
            </button>
            <div class="ai-avatar">
              <el-icon :size="17"><ChatDotRound /></el-icon>
            </div>
            <div class="ai-title-wrap">
              <div class="ai-title-row">
                <strong class="ai-title">AI 客服</strong>
                <span class="ai-online"><i></i>在线</span>
              </div>
              <span class="ai-subtitle">{{ view === 'history' ? '历史会话' : '平台操作与选题辅助' }}</span>
            </div>
            <div class="ai-header-actions">
              <button v-if="view === 'chat'" type="button" class="ai-icon-btn" title="历史会话" @click="view = 'history'">
                <el-icon :size="17"><Clock /></el-icon>
              </button>
              <button type="button" class="ai-close" title="收起" @click="opened = false">×</button>
            </div>
          </header>

          <!-- History overlay -->
          <template v-if="view === 'history'">
            <div class="ai-history-head">
              <span>全部会话</span>
              <el-button size="small" plain @click="newConversation">新建会话</el-button>
            </div>
            <div class="ai-history" v-loading="loadingConvs">
              <button
                v-for="conv in conversations"
                :key="conv.id"
                type="button"
                class="ai-history-item"
                :class="{ active: String(conv.id) === String(currentId) }"
                @click="selectConversation(conv.id)"
              >
                <div class="ai-history-icon"><el-icon :size="15"><ChatDotRound /></el-icon></div>
                <div class="ai-history-text">
                  <span class="ai-history-name">{{ conv.title || '未命名对话' }}</span>
                  <span class="ai-history-time">{{ shortTime(conv.updateTime) }}<template v-if="conv.messageCount"> · {{ conv.messageCount }} 条</template></span>
                </div>
                <span class="ai-history-arrow"><el-icon :size="14"><ArrowRight /></el-icon></span>
              </button>
              <div v-if="!loadingConvs && conversations.length === 0" class="ai-history-empty">
                <el-icon :size="22"><Message /></el-icon>
                <p>还没有历史会话</p>
              </div>
            </div>
          </template>

          <!-- Chat -->
          <template v-else>
            <div ref="messagesRef" class="ai-messages">
              <div v-for="msg in messages" :key="msg.id" class="ai-msg" :class="msg.role">
                <div class="ai-bubble">
                  <p class="ai-content">{{ msg.content || '（无内容）' }}</p>
                  <div class="ai-meta">
                    <el-tag v-if="msg.status === 'FAILED'" type="danger" size="small" effect="light">{{ msg.errorMessage || '回复失败' }}</el-tag>
                    <el-button v-if="msg.role === 'assistant' && msg.status === 'FAILED'" link type="primary" size="small" @click="retry(msg.id)">重新回答</el-button>
                    <span v-else class="ai-msg-time">{{ shortTime(msg.createTime) }}</span>
                  </div>
                </div>
              </div>

              <div v-if="sending" class="ai-msg assistant">
                <div class="ai-bubble typing">
                  <span class="ai-typing-dot"></span><span class="ai-typing-dot"></span><span class="ai-typing-dot"></span>
                </div>
              </div>

              <div v-if="messages.length === 0 && !sending" class="ai-empty">
                <div class="ai-empty-icon"><el-icon :size="22"><ChatDotRound /></el-icon></div>
                <p>你好，我是 LeetModel 客服。</p>
                <p>可以问我平台操作、组队、提交或评审问题。</p>
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

            <footer class="ai-input">
              <el-input
                v-model="draft"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 3 }"
                resize="none"
                placeholder="输入你的问题…"
                @keydown.enter.exact.prevent="send()"
              />
              <button type="button" class="ai-send" :disabled="!draft.trim() || sending" @click="send()" aria-label="发送">
                <el-icon :size="17"><Promotion /></el-icon>
              </button>
            </footer>
          </template>
        </div>
      </transition>

      <button type="button" class="ai-bubble-btn" @click="toggleOpen" :aria-label="opened ? '收起 AI 客服' : '打开 AI 客服'">
        <el-icon :size="20"><ChatDotRound /></el-icon>
        <span v-if="!opened" class="ai-bubble-label">AI 客服</span>
      </button>
    </div>
  </transition>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from "vue";
import { ElMessage } from "element-plus";
import { ArrowLeft, ArrowRight, ChatDotRound, Clock, Message, Promotion } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/user";
import { listConversations, createConversation, getConversation, sendMessage, retryMessage } from "@/api/assistant";

const userStore = useUserStore();
const opened = ref(false);
const view = ref("chat");
const sending = ref(false);
const creating = ref(false);
const loadingConvs = ref(false);
const conversations = ref([]);
const messages = ref([]);
const currentId = ref(null);
const draft = ref("");
const messagesRef = ref(null);

const userInitial = computed(() => (userStore.nickname || userStore.username || "我").charAt(0));

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
  if (!value) return "";
  const s = String(value).replace("T", " ").slice(5, 16);
  return s;
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
  view.value = "chat";
  await loadConversations();
  if (conversations.value.length) {
    await selectConversation(conversations.value[0].id);
  } else {
    await newConversation();
  }
}

function toggleOpen() {
  opened.value ? (opened.value = false) : open();
}

async function selectConversation(id) {
  currentId.value = id;
  try {
    const detail = (await getConversation(id)).data;
    messages.value = detail.messages || [];
    view.value = "chat";
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
    view.value = "chat";
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
    scrollToBottom();
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
.ai-widget { position: fixed; right: 22px; bottom: 22px; z-index: 4000; display: flex; flex-direction: column; align-items: flex-end; gap: 12px; }

.ai-bubble-btn { display: inline-flex; align-items: center; gap: 8px; padding: 13px 18px; border: 0; border-radius: 999px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; font: inherit; font-size: 15px; font-weight: 600; cursor: pointer; box-shadow: 0 8px 24px rgba(37, 99, 235, 0.34); transition: transform .2s, box-shadow .2s; }
.ai-bubble-btn:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(37, 99, 235, 0.42); }

.ai-panel { display: flex; width: 380px; max-width: calc(100vw - 24px); height: 520px; max-height: calc(100vh - 130px); flex-direction: column; overflow: hidden; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 16px; box-shadow: 0 24px 64px rgba(15, 23, 42, 0.16); }

.ai-header { display: flex; align-items: center; gap: 10px; padding: 11px 12px; border-bottom: 1px solid var(--lm-border-light); }
.ai-avatar { display: flex; width: 32px; height: 32px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 10px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; }
.ai-title-wrap { min-width: 0; flex: 1; }
.ai-title-row { display: flex; align-items: center; gap: 7px; }
.ai-title { font-size: 14px; color: var(--lm-text-primary); }
.ai-online { display: inline-flex; align-items: center; gap: 4px; padding: 1px 7px; border-radius: 999px; background: var(--lm-success-bg); font-size: 10px; color: var(--lm-success); }
.ai-online i { width: 5px; height: 5px; border-radius: 50%; background: var(--lm-success); }
.ai-subtitle { display: block; margin-top: 1px; font-size: 11px; color: var(--lm-text-muted); }
.ai-header-actions { display: flex; align-items: center; gap: 2px; }
.ai-icon-btn { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border: 0; border-radius: 8px; background: transparent; color: var(--lm-text-secondary); cursor: pointer; transition: background .18s, color .18s; }
.ai-icon-btn:hover { background: var(--lm-bg-secondary); color: var(--lm-primary); }
.ai-close { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border: 0; border-radius: 8px; background: transparent; color: var(--lm-text-muted); font-size: 20px; line-height: 1; cursor: pointer; }
.ai-close:hover { background: var(--lm-danger-bg); color: var(--lm-danger); }

.ai-history-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; }
.ai-history-head span { color: var(--lm-text-secondary); font-size: 13px; font-weight: 600; }
.ai-history { overflow: auto; flex: 1; padding: 2px 8px 10px; }
.ai-history-item { display: flex; width: 100%; align-items: center; gap: 10px; padding: 9px 10px; margin-bottom: 2px; text-align: left; border: 1px solid transparent; border-radius: 10px; background: transparent; cursor: pointer; transition: background .15s, border-color .15s; }
.ai-history-item:hover { background: var(--lm-bg-secondary); }
.ai-history-item.active { border-color: var(--lm-border); background: var(--lm-primary-bg); }
.ai-history-icon { display: flex; width: 30px; height: 30px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 9px; background: var(--lm-bg-secondary); color: var(--lm-text-secondary); }
.ai-history-item.active .ai-history-icon { background: var(--lm-primary-bg); color: var(--lm-primary); }
.ai-history-text { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 2px; }
.ai-history-name { overflow: hidden; color: var(--lm-text-primary); font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.ai-history-time { color: var(--lm-text-muted); font-size: 11px; }
.ai-history-arrow { color: var(--lm-text-muted); }
.ai-history-empty { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 48px 0; color: var(--lm-text-muted); font-size: 13px; }
.ai-history-empty p { margin: 0; }

.ai-messages { display: flex; flex-direction: column; gap: 10px; padding: 14px 14px 10px; overflow: auto; flex: 1; background: var(--lm-bg); }
.ai-msg { display: flex; }
.ai-msg.assistant { justify-content: flex-start; }
.ai-msg.user { justify-content: flex-end; }
.ai-bubble { max-width: 82%; padding: 8px 11px; border-radius: 12px; font-size: 13px; }
.ai-msg.assistant .ai-bubble { border-bottom-left-radius: 4px; background: var(--lm-surface); border: 1px solid var(--lm-border); color: var(--lm-text-primary); }
.ai-msg.user .ai-bubble { border-bottom-right-radius: 4px; background: var(--lm-primary); color: #fff; }
.ai-content { margin: 0; white-space: pre-wrap; line-height: 1.6; }
.ai-meta { display: flex; align-items: center; gap: 6px; margin-top: 5px; flex-wrap: wrap; }
.ai-msg.user .ai-meta { color: rgba(255,255,255,0.8); }
.ai-msg.assistant .ai-meta { color: var(--lm-text-muted); }
.ai-msg-time { font-size: 10px; }

.ai-bubble.typing { display: inline-flex; gap: 4px; padding: 12px 14px; }
.ai-typing-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--lm-text-muted); animation: ai-blink 1.2s infinite ease-in-out; }
.ai-typing-dot:nth-child(2) { animation-delay: .2s; }
.ai-typing-dot:nth-child(3) { animation-delay: .4s; }
@keyframes ai-blink { 0%, 80%, 100% { opacity: .3; } 40% { opacity: 1; } }

.ai-empty { margin: auto; padding: 16px; text-align: center; color: var(--lm-text-muted); font-size: 13px; line-height: 1.7; }
.ai-empty-icon { display: inline-flex; width: 48px; height: 48px; align-items: center; justify-content: center; margin-bottom: 8px; border-radius: 14px; background: var(--lm-primary-bg); color: var(--lm-primary); }
.ai-empty p { margin: 0; }

.ai-quick { display: flex; gap: 6px; padding: 8px 12px 2px; overflow-x: auto; scrollbar-width: none; }
.ai-quick::-webkit-scrollbar { display: none; }
.ai-quick-chip { flex: 0 0 auto; padding: 5px 10px; border: 1px solid var(--lm-border); border-radius: 999px; background: var(--lm-surface); color: var(--lm-text-secondary); font: inherit; font-size: 12px; cursor: pointer; transition: border-color .16s, color .16s; }
.ai-quick-chip:hover { color: var(--lm-primary); border-color: var(--lm-primary); }
.ai-quick-chip:disabled { opacity: .5; cursor: not-allowed; }

.ai-input { display: flex; align-items: flex-end; gap: 8px; padding: 9px 12px 12px; }
.ai-input :deep(.el-textarea__inner) { padding: 8px 11px; border: 1px solid var(--lm-border); border-radius: 12px; background: var(--lm-bg-secondary); box-shadow: none; font-size: 13px; }
.ai-input :deep(.el-textarea__inner:focus) { border-color: var(--lm-primary); background: var(--lm-surface); }
.ai-send { display: inline-flex; width: 36px; height: 36px; align-items: center; justify-content: center; flex-shrink: 0; border: 0; border-radius: 11px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; cursor: pointer; transition: transform .16s, opacity .16s; }
.ai-send:hover { transform: translateY(-1px); }
.ai-send:disabled { opacity: .45; cursor: not-allowed; transform: none; }

.slide-up-enter-active, .slide-up-leave-active { transition: transform .2s ease, opacity .2s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(12px); opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 520px) {
  .ai-widget { right: 12px; bottom: 12px; gap: 10px; }
  .ai-panel { width: calc(100vw - 24px); height: 74vh; border-radius: 14px; }
}
</style>
