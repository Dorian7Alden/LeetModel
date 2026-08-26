<template>
  <transition name="fade">
    <div v-if="userStore.isLogin" class="ai-widget">
      <transition name="slide-up">
        <div v-if="opened" class="ai-panel">
          <header class="ai-header">
            <div class="ai-avatar">
              <el-icon :size="18"><ChatDotRound /></el-icon>
            </div>
            <div class="ai-title-wrap">
              <div class="ai-title-row">
                <strong class="ai-title">LeetModel 客服</strong>
                <span class="ai-online"><i></i>在线</span>
              </div>
              <span class="ai-subtitle">对话记录自动保存</span>
            </div>
            <div class="ai-header-actions">
              <button type="button" class="ai-icon-btn" title="新建会话" :disabled="creating" @click="newConversation">
                <el-icon :size="16"><Plus /></el-icon>
              </button>
              <button type="button" class="ai-icon-btn" title="收起" @click="opened = false">
                <el-icon :size="16"><Close /></el-icon>
              </button>
            </div>
          </header>

          <div class="ai-body">
            <aside class="ai-history">
              <div class="ai-history-title">近期对话</div>
              <div class="ai-history-list" v-loading="loadingConvs">
                <button
                  v-for="conv in conversations"
                  :key="conv.id"
                  type="button"
                  class="ai-history-item"
                  :class="{ active: String(conv.id) === String(currentId) }"
                  @click="selectConversation(conv.id)"
                >
                  <span class="ai-history-dot"></span>
                  <span class="ai-history-text">
                    <span class="ai-history-name">{{ conv.title || '未命名对话' }}</span>
                    <span class="ai-history-time">{{ shortTime(conv.updateTime) }}</span>
                  </span>
                </button>
                <p v-if="!loadingConvs && conversations.length === 0" class="ai-history-empty">暂无历史对话</p>
              </div>
            </aside>

            <div ref="messagesRef" class="ai-messages">
              <div v-for="msg in messages" :key="msg.id" class="ai-msg" :class="msg.role">
                <div v-if="msg.role === 'assistant'" class="ai-bubble-avatar">
                  <el-icon :size="14"><ChatDotRound /></el-icon>
                </div>
                <div class="ai-bubble">
                  <p class="ai-content">{{ msg.content || '（无内容）' }}</p>
                  <div class="ai-meta">
                    <el-tag v-if="msg.status === 'FAILED'" type="danger" size="small" effect="light">{{ msg.errorMessage || '回复失败' }}</el-tag>
                    <el-tag v-else-if="msg.status === 'RUNNING'" type="warning" size="small" effect="light">生成中</el-tag>
                    <el-tag v-if="msg.usedProblemTool" type="info" size="small" effect="light">已参考题目</el-tag>
                    <span class="ai-msg-time">{{ shortTime(msg.createTime) }}</span>
                    <button v-if="msg.role === 'assistant' && msg.status === 'FAILED'" type="button" class="ai-retry" @click="retry(msg.id)">重新回答</button>
                  </div>
                </div>
                <div v-if="msg.role === 'user'" class="ai-bubble-avatar user">
                  <span>{{ userInitial }}</span>
                </div>
              </div>

              <div v-if="sending" class="ai-msg assistant">
                <div class="ai-bubble-avatar"><el-icon :size="14"><ChatDotRound /></el-icon></div>
                <div class="ai-bubble typing">
                  <span class="ai-typing-dot"></span><span class="ai-typing-dot"></span><span class="ai-typing-dot"></span>
                </div>
              </div>

              <div v-if="messages.length === 0 && !sending" class="ai-empty">
                <div class="ai-empty-icon"><el-icon :size="26"><ChatDotRound /></el-icon></div>
                <p>你好，我是 LeetModel 客服。</p>
                <p>可以询问平台操作、组队、提交或评审相关问题。</p>
              </div>
            </div>
          </div>

          <div class="ai-quick">
            <span class="ai-quick-label">快捷提问</span>
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
              :rows="1"
              resize="none"
              autosize
              placeholder="输入你的问题…"
              @keydown.enter.exact.prevent="send()"
            />
            <button type="button" class="ai-send" :disabled="!draft.trim() || sending" @click="send()" aria-label="发送">
              <el-icon :size="18"><Promotion /></el-icon>
            </button>
          </footer>
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
import { computed, nextTick, onBeforeUnmount, ref } from "vue";
import { ElMessage } from "element-plus";
import { ChatDotRound, Close, Plus, Promotion } from "@element-plus/icons-vue";
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

const userInitial = computed(() => (userStore.nickname || userStore.username || "我").charAt(0).toUpperCase());

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
  return String(value).replace("T", " ").slice(5, 16);
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
  opened.value ? (opened.value = false) : open();
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
.ai-widget { position: fixed; right: 24px; bottom: 24px; z-index: 4000; display: flex; flex-direction: column; align-items: flex-end; gap: 14px; }

.ai-bubble-btn { display: inline-flex; align-items: center; gap: 9px; padding: 15px 20px; border: 0; border-radius: 999px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; font: inherit; font-size: 15px; font-weight: 600; cursor: pointer; box-shadow: 0 10px 26px rgba(37, 99, 235, 0.36); transition: transform .2s, box-shadow .2s; }
.ai-bubble-btn:hover { transform: translateY(-2px); box-shadow: 0 14px 32px rgba(37, 99, 235, 0.44); }

.ai-panel { display: flex; width: 500px; max-width: calc(100vw - 32px); height: 600px; max-height: calc(100vh - 140px); flex-direction: column; overflow: hidden; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 18px; box-shadow: var(--lm-shadow-xl); }

.ai-header { display: flex; align-items: center; gap: 12px; padding: 14px 16px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-dark)); color: #fff; }
.ai-avatar { display: flex; width: 38px; height: 38px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 12px; background: rgba(255,255,255,0.2); border: 1px solid rgba(255,255,255,0.3); }
.ai-title-wrap { min-width: 0; flex: 1; }
.ai-title-row { display: flex; align-items: center; gap: 8px; }
.ai-title { font-size: 15px; color: #fff; }
.ai-online { display: inline-flex; align-items: center; gap: 5px; padding: 2px 8px; border-radius: 999px; background: rgba(255,255,255,0.18); font-size: 11px; color: #eafcff; }
.ai-online i { width: 6px; height: 6px; border-radius: 50%; background: #4ade80; box-shadow: 0 0 0 3px rgba(74,222,128,0.25); }
.ai-subtitle { display: block; margin-top: 2px; font-size: 11px; color: rgba(255,255,255,0.78); }
.ai-header-actions { display: flex; align-items: center; gap: 4px; }
.ai-icon-btn { display: inline-flex; width: 30px; height: 30px; align-items: center; justify-content: center; border: 0; border-radius: 8px; background: rgba(255,255,255,0.14); color: #fff; cursor: pointer; transition: background .2s; }
.ai-icon-btn:hover { background: rgba(255,255,255,0.26); }
.ai-icon-btn:disabled { opacity: .5; cursor: not-allowed; }

.ai-body { display: flex; flex: 1; min-height: 0; }
.ai-history { display: flex; width: 150px; flex-direction: column; border-right: 1px solid var(--lm-border-light); background: var(--lm-bg-secondary); }
.ai-history-title { padding: 12px 12px 8px; color: var(--lm-text-muted); font-size: 12px; font-weight: 700; letter-spacing: .02em; }
.ai-history-list { overflow: auto; flex: 1; padding: 4px 6px; }
.ai-history-item { display: flex; width: 100%; align-items: center; gap: 8px; padding: 9px 9px; margin-bottom: 3px; text-align: left; border: 1px solid transparent; border-radius: 10px; background: transparent; cursor: pointer; transition: background .18s, border-color .18s; }
.ai-history-item:hover { background: var(--lm-surface); }
.ai-history-item.active { border-color: var(--lm-primary); background: var(--lm-primary-bg); box-shadow: inset 3px 0 0 var(--lm-primary); }
.ai-history-dot { width: 7px; height: 7px; flex-shrink: 0; border-radius: 50%; background: var(--lm-border); }
.ai-history-item.active .ai-history-dot { background: var(--lm-primary); }
.ai-history-text { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.ai-history-name { overflow: hidden; color: var(--lm-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.ai-history-time { color: var(--lm-text-muted); font-size: 11px; }
.ai-history-empty { padding: 10px; color: var(--lm-text-muted); font-size: 12px; }

.ai-messages { display: flex; flex-direction: column; gap: 12px; padding: 16px; overflow: auto; flex: 1; min-width: 0; background: linear-gradient(180deg, #f8fafc, #f1f5f9); }
.ai-msg { display: flex; align-items: flex-start; gap: 8px; }
.ai-msg.user { justify-content: flex-end; }
.ai-bubble-avatar { display: flex; width: 26px; height: 26px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; box-shadow: var(--lm-shadow-xs); }
.ai-bubble-avatar.user { background: linear-gradient(135deg, #475569, #64748b); font-size: 13px; font-weight: 700; }
.ai-bubble { max-width: 76%; padding: 9px 12px; border-radius: 14px; box-shadow: var(--lm-shadow-xs); }
.ai-msg.assistant .ai-bubble { border-top-left-radius: 4px; background: var(--lm-surface); border: 1px solid var(--lm-border); }
.ai-msg.user .ai-bubble { border-top-right-radius: 4px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; }
.ai-content { margin: 0; white-space: pre-wrap; line-height: 1.65; font-size: 13px; }
.ai-meta { display: flex; align-items: center; gap: 7px; margin-top: 6px; flex-wrap: wrap; }
.ai-msg.user .ai-meta { color: rgba(255,255,255,0.85); }
.ai-msg.assistant .ai-meta { color: var(--lm-text-muted); }
.ai-msg-time { font-size: 11px; opacity: .8; }
.ai-retry { padding: 0; border: 0; background: transparent; color: var(--lm-primary); font-size: 12px; cursor: pointer; }
.ai-retry:hover { text-decoration: underline; }

.ai-bubble.typing { display: inline-flex; gap: 5px; padding: 12px 14px; }
.ai-typing-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--lm-text-muted); animation: ai-blink 1.2s infinite ease-in-out; }
.ai-typing-dot:nth-child(2) { animation-delay: .2s; }
.ai-typing-dot:nth-child(3) { animation-delay: .4s; }
@keyframes ai-blink { 0%, 80%, 100% { opacity: .3; } 40% { opacity: 1; } }

.ai-empty { margin: auto; padding: 24px; text-align: center; color: var(--lm-text-muted); font-size: 13px; line-height: 1.7; }
.ai-empty-icon { display: inline-flex; width: 56px; height: 56px; align-items: center; justify-content: center; margin-bottom: 10px; border-radius: 16px; background: var(--lm-primary-bg); color: var(--lm-primary); }

.ai-quick { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; padding: 10px 14px 6px; }
.ai-quick-label { color: var(--lm-text-muted); font-size: 11px; font-weight: 600; }
.ai-quick-chip { padding: 6px 11px; border: 1px solid var(--lm-border); border-radius: 999px; background: var(--lm-surface); color: var(--lm-text-secondary); font: inherit; font-size: 12px; cursor: pointer; transition: border-color .18s, color .18s, transform .18s, box-shadow .18s; }
.ai-quick-chip:hover { color: var(--lm-primary); border-color: var(--lm-primary); transform: translateY(-1px); box-shadow: var(--lm-shadow-xs); }
.ai-quick-chip:disabled { cursor: not-allowed; opacity: .55; }

.ai-input { display: flex; align-items: flex-end; gap: 8px; padding: 12px 14px; border-top: 1px solid var(--lm-border-light); background: var(--lm-surface); }
.ai-input :deep(.el-textarea__inner) { padding: 9px 12px; border: 1px solid var(--lm-border); border-radius: 14px; background: var(--lm-bg-secondary); box-shadow: none; }
.ai-input :deep(.el-textarea__inner:focus) { border-color: var(--lm-primary); background: var(--lm-surface); }
.ai-send { display: inline-flex; width: 38px; height: 38px; align-items: center; justify-content: center; flex-shrink: 0; border: 0; border-radius: 12px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; cursor: pointer; transition: transform .18s, box-shadow .18s, opacity .18s; }
.ai-send:hover { transform: translateY(-1px); box-shadow: 0 6px 14px rgba(37,99,235,.3); }
.ai-send:disabled { opacity: .5; cursor: not-allowed; transform: none; box-shadow: none; }

.slide-up-enter-active, .slide-up-leave-active { transition: transform .22s ease, opacity .22s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(14px) scale(.98); opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 520px) {
  .ai-widget { right: 12px; bottom: 12px; gap: 10px; }
  .ai-panel { width: calc(100vw - 24px); height: 76vh; border-radius: 14px; }
  .ai-history { width: 122px; }
  .ai-bubble { max-width: 84%; }
}
</style>
