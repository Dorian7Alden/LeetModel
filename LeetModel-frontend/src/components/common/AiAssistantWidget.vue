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
            <div class="ai-avatar"><el-icon :size="17"><ChatDotRound /></el-icon></div>
            <div class="ai-title-wrap">
              <div class="ai-title-row"><strong class="ai-title">AI 客服</strong><span class="ai-online"><i></i>在线</span></div>
              <span class="ai-subtitle">{{ view === 'history' ? '历史记录' : '平台操作与选题辅助' }}</span>
            </div>
            <div class="ai-header-actions">
              <button v-if="view === 'chat'" type="button" class="ai-text-btn" title="创建会话" :disabled="creating" @click="newConversation">
                <el-icon :size="15"><Plus /></el-icon><span>创建会话</span>
              </button>
              <button v-if="view === 'chat'" type="button" class="ai-text-btn" title="历史记录" @click="view = 'history'">
                <el-icon :size="15"><Clock /></el-icon><span>历史记录</span>
              </button>
              <button type="button" class="ai-close" title="收起" @click="opened = false">×</button>
            </div>
          </header>

          <!-- History -->
          <template v-if="view === 'history'">
            <div class="ai-history-head">
              <div>
                <div class="ai-history-title">历史会话</div>
                <div class="ai-history-count" v-if="conversations.length">仅展示最近 {{ conversations.length }} 条对话</div>
              </div>
              <el-button size="small" type="primary" plain @click="newConversation">创建会话</el-button>
            </div>
            <div class="ai-history" v-loading="loadingConvs">
              <div v-for="group in historyGroups" :key="group.label" class="ai-history-group">
                <div class="ai-history-group-label">{{ group.label }}</div>
                <button
                  v-for="conv in group.items"
                  :key="conv.id"
                  type="button"
                  class="ai-history-item"
                  :class="{ active: String(conv.id) === String(currentId) }"
                  @click="selectConversation(conv.id)"
                >
                  <div class="ai-history-avatar"><el-icon :size="16"><ChatDotRound /></el-icon></div>
                  <div class="ai-history-text">
                    <span class="ai-history-name">{{ conv.title || '未命名对话' }}</span>
                    <span class="ai-history-meta">{{ shortTime(conv.updateTime) }}<template v-if="conv.messageCount"> · {{ conv.messageCount }} 条</template></span>
                  </div>
                  <span class="ai-history-arrow"><el-icon :size="15"><ArrowRight /></el-icon></span>
                </button>
              </div>
              <div v-if="!loadingConvs && conversations.length === 0" class="ai-history-empty">
                <div class="ai-empty-icon"><el-icon :size="22"><Message /></el-icon></div>
                <p>还没有历史会话</p>
              </div>
            </div>
          </template>

          <!-- Chat -->
          <template v-else>
            <div ref="messagesRef" class="ai-messages">
              <div v-if="messages.length === 0 && !sending" class="ai-welcome">
                <div class="ai-welcome-avatar"><el-icon :size="24"><ChatDotRound /></el-icon></div>
                <p class="ai-welcome-title">你好，我是 LeetModel 客服</p>
                <p class="ai-welcome-desc">可以问我平台操作、组队、提交或评审相关问题。</p>
                <div class="ai-rec-head">
                  <span>试试这样问我</span>
                  <button type="button" class="ai-rec-refresh" @click="rotateRec"><el-icon :size="13"><Refresh /></el-icon>换一换</button>
                </div>
                <div class="ai-rec-list">
                  <button v-for="r in recQuestions" :key="r.q" type="button" class="ai-rec-card" :disabled="sending" @click="send(r.q)">
                    <span class="ai-rec-icon"><el-icon :size="15"><component :is="r.icon" /></el-icon></span>
                    <span class="ai-rec-body"><span class="ai-rec-cat">{{ r.category }}</span><span class="ai-rec-q">{{ r.q }}</span></span>
                  </button>
                </div>
              </div>

              <div v-for="msg in messages" :key="msg.id" class="ai-msg" :class="msg.role">
                <template v-if="msg.role === 'assistant'">
                  <div class="ai-msg-avatar support"><el-icon :size="15"><Service /></el-icon></div>
                  <div class="ai-msg-col">
                    <span class="ai-msg-name">AI 客服</span>
                    <div class="ai-bubble">
                      <div class="markdown-body ai-md" v-html="md(msg.content)"></div>
                      <div class="ai-meta">
                        <el-tag v-if="msg.status === 'FAILED'" type="danger" size="small" effect="light">{{ msg.errorMessage || '回复失败' }}</el-tag>
                        <span v-else class="ai-msg-time">{{ shortTime(msg.createTime) }}</span>
                      </div>
                      <div v-if="msg.status !== 'RUNNING'" class="ai-msg-actions">
                        <button type="button" title="复制" @click="copy(msg.content)"><el-icon :size="14"><CopyDocument /></el-icon></button>
                        <button v-if="msg.status === 'FAILED'" type="button" title="重新回答" @click="retry(msg.id)"><el-icon :size="14"><Refresh /></el-icon></button>
                      </div>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <div class="ai-msg-col user">
                    <span class="ai-msg-name">{{ userDisplayName }}</span>
                    <div class="ai-bubble">
                      <p class="ai-content">{{ msg.content || '（无内容）' }}</p>
                      <div class="ai-meta"><span class="ai-msg-time">{{ shortTime(msg.createTime) }}</span></div>
                    </div>
                  </div>
                  <div class="ai-msg-avatar user">
                    <img v-if="userAvatar" :src="userAvatar" alt="我" />
                    <span v-else>{{ userInitial }}</span>
                  </div>
                </template>
              </div>

              <div v-if="sending" class="ai-msg assistant">
                <div class="ai-msg-avatar support"><el-icon :size="15"><Service /></el-icon></div>
                <div class="ai-msg-col">
                  <span class="ai-msg-name">AI 客服</span>
                  <div class="ai-bubble typing"><span class="ai-typing-dot"></span><span class="ai-typing-dot"></span><span class="ai-typing-dot"></span></div>
                </div>
              </div>
            </div>

            <div v-if="suggestOpen && suggestions.length" class="ai-suggest">
              <button v-for="s in suggestions" :key="s.q" type="button" class="ai-suggest-item" @click="send(s.q)">
                <el-icon :size="14"><Message /></el-icon><span>{{ s.q }}</span>
              </button>
            </div>

            <div class="ai-quick">
              <button v-for="q in quickQuestions" :key="q" type="button" class="ai-quick-chip" :disabled="sending" @click="send(q)">{{ q }}</button>
            </div>

            <footer class="ai-input">
              <el-input
                v-model="draft"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 3 }"
                resize="none"
                placeholder="请将你的问题告诉我…"
                @focus="suggestOpen = true"
                @blur="delayCloseSuggest"
                @input="suggestOpen = true"
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
import { useUserStore } from "@/store/user";
import { renderSafeMarkdown } from "@/utils/markdown";
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
const recOffset = ref(0);
const suggestOpen = ref(false);
let suggestTimer = null;

const userInitial = computed(() => (userStore.nickname || userStore.username || "我").charAt(0));
const userDisplayName = computed(() => userStore.nickname || userStore.username || "我");
const userAvatar = computed(() => userStore.avatarUrl || "");

// 固定推荐问题池（可换一换轮换展示，提问内容固定）
const recPool = [
  { category: "组队", icon: "Trophy", q: "如何创建队伍？" },
  { category: "组队", icon: "Trophy", q: "如何招募成员？" },
  { category: "提交", icon: "Upload", q: "如何提交论文？" },
  { category: "提交", icon: "Upload", q: "如何确定最终提交？" },
  { category: "评审", icon: "DataAnalysis", q: "如何查看评审结果？" },
  { category: "评审", icon: "DataAnalysis", q: "评审多久出结果？" },
  { category: "平台", icon: "Collection", q: "平台有哪些功能？" },
  { category: "选题", icon: "Trophy", q: "如何选择适合的题目？" },
];
const recPageSize = 4;
const recQuestions = computed(() => {
  const start = recOffset.value % recPool.length;
  return Array.from({ length: recPageSize }, (_, i) => recPool[(start + i) % recPool.length]);
});

// 输入联想：仅当草稿与某个推荐问题匹配时展示
const suggestions = computed(() => {
  const k = draft.value.trim();
  if (!k) return [];
  return recPool.filter((r) => r.q.includes(k)).slice(0, 3);
});

const quickQuestions = recPool.slice(0, 5).map((r) => r.q);

const historyGroups = computed(() => {
  const now = new Date();
  const bands = [
    { label: "今天", fn: (t) => sameDay(t, now) },
    { label: "本周", fn: (t) => !sameDay(t, now) && withinDays(t, now, 7) },
    { label: "更早", fn: (t) => !withinDays(t, now, 7) },
  ];
  return bands
    .map((band) => ({ label: band.label, items: conversations.value.filter((c) => band.fn(parseDate(c.updateTime))) }))
    .filter((band) => band.items.length);
});

function parseDate(value) { return value ? new Date(String(value).replace(" ", "T")) : null; }
function sameDay(a, b) { return a && b && a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate(); }
function withinDays(a, b, days) { return a && a.getTime() >= b.getTime() - days * 86400000; }

const md = (value) => renderSafeMarkdown(value);

function uuid() {
  if (crypto?.randomUUID) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function shortTime(value) { return value ? String(value).replace("T", " ").slice(5, 16) : ""; }

function rotateRec() { recOffset.value += recPageSize; }
function delayCloseSuggest() { suggestTimer = window.setTimeout(() => { suggestOpen.value = false; }, 120); }

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text || "");
    ElMessage.success("已复制");
  } catch {
    ElMessage.error("复制失败");
  }
}

function scrollToBottom() {
  nextTick(() => { if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight; });
}

async function loadConversations() {
  loadingConvs.value = true;
  try { conversations.value = (await listConversations()).data || []; }
  catch (error) { ElMessage.error(error.message || "对话记录加载失败"); }
  finally { loadingConvs.value = false; }
}

async function open() {
  opened.value = true;
  view.value = "chat";
  await loadConversations();
  if (conversations.value.length) await selectConversation(conversations.value[0].id);
  else await newConversation();
}

function toggleOpen() { opened.value ? (opened.value = false) : open(); }

async function selectConversation(id) {
  currentId.value = id;
  try {
    const detail = (await getConversation(id)).data;
    messages.value = detail.messages || [];
    view.value = "chat";
    scrollToBottom();
  } catch (error) { ElMessage.error(error.message || "对话加载失败"); }
}

async function newConversation() {
  creating.value = true;
  try {
    const res = await createConversation("AI 客服咨询");
    conversations.value.unshift(res.data);
    currentId.value = res.data.id;
    messages.value = [];
    recOffset.value = 0;
    view.value = "chat";
  } catch (error) { ElMessage.error(error.message || "新建会话失败"); }
  finally { creating.value = false; }
}

async function send(text) {
  const content = (text || draft.value).trim();
  if (!content || sending.value) return;
  if (!currentId.value) { ElMessage.warning("请先创建会话"); return; }
  sending.value = true;
  suggestOpen.value = false;
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
  } catch (error) { ElMessage.error(error.message || "重试失败"); }
}

onBeforeUnmount(() => { opened.value = false; if (suggestTimer) clearTimeout(suggestTimer); });
</script>

<style>
.ai-bubble .markdown-body,
.ai-bubble .markdown-body *{box-sizing:border-box;}
.ai-bubble .markdown-body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans CJK SC","Microsoft YaHei",Arial,sans-serif;font-size:13px;line-height:1.65;color:inherit;word-break:break-word;padding:0;background:transparent;margin:0;}
.ai-bubble .markdown-body :is(p,ul,ol,pre,blockquote,table){margin:0 0 6px;}
.ai-bubble .markdown-body :is(ul,ol){padding-left:18px;}
.ai-bubble .markdown-body :is(h1,h2,h3,h4,h5,h6){margin:8px 0 4px;font-size:1em;font-weight:700;line-height:1.4;}
.ai-bubble .markdown-body strong{font-weight:800;}
.ai-bubble .markdown-body code{font-size:12px;padding:1px 5px;border-radius:4px;background:rgba(0,0,0,.06);}
.ai-bubble .markdown-body pre{overflow:auto;padding:8px 10px;border-radius:8px;background:rgba(0,0,0,.06);}
.ai-bubble .markdown-body pre code{background:transparent;padding:0;}
.ai-bubble .markdown-body a{color:var(--lm-primary);}
.ai-bubble .markdown-body blockquote{padding-left:10px;border-left:3px solid var(--lm-border);color:var(--lm-text-muted);}
.ai-bubble .markdown-body table{border-collapse:collapse;font-size:12px;}
.ai-bubble .markdown-body th,.ai-bubble .markdown-body td{padding:4px 8px;border:1px solid var(--lm-border);}
</style>

<style scoped>
.ai-widget { position: fixed; right: 22px; bottom: 22px; z-index: 4000; display: flex; flex-direction: column; align-items: flex-end; gap: 12px; }
.ai-bubble-btn { display: inline-flex; align-items: center; gap: 8px; padding: 13px 18px; border: 0; border-radius: 999px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; font: inherit; font-size: 15px; font-weight: 600; cursor: pointer; box-shadow: 0 8px 24px rgba(37, 99, 235, 0.34); transition: transform .2s, box-shadow .2s; }
.ai-bubble-btn:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(37, 99, 235, 0.42); }
.ai-panel { display: flex; width: 400px; max-width: calc(100vw - 24px); height: 560px; max-height: calc(100vh - 130px); flex-direction: column; overflow: hidden; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 16px; box-shadow: 0 24px 64px rgba(15, 23, 42, 0.18); }

/* Header */
.ai-header { display: flex; align-items: center; gap: 10px; padding: 11px 12px; border-bottom: 1px solid var(--lm-border-light); }
.ai-avatar { display: flex; width: 32px; height: 32px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 10px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; }
.ai-title-wrap { min-width: 0; flex: 1; }
.ai-title-row { display: flex; align-items: center; gap: 7px; }
.ai-title { font-size: 14px; color: var(--lm-text-primary); }
.ai-online { display: inline-flex; align-items: center; gap: 4px; padding: 1px 7px; border-radius: 999px; background: var(--lm-success-bg); font-size: 10px; color: var(--lm-success); }
.ai-online i { width: 5px; height: 5px; border-radius: 50%; background: var(--lm-success); }
.ai-subtitle { display: block; margin-top: 1px; font-size: 11px; color: var(--lm-text-muted); }
.ai-header-actions { display: flex; align-items: center; gap: 2px; }
.ai-text-btn { display: inline-flex; align-items: center; gap: 4px; padding: 5px 8px; border: 0; border-radius: 8px; background: transparent; color: var(--lm-text-secondary); font: inherit; font-size: 12px; cursor: pointer; transition: background .16s, color .16s; }
.ai-text-btn:hover { background: var(--lm-bg-secondary); color: var(--lm-primary); }
.ai-text-btn:disabled { opacity: .5; cursor: not-allowed; }
.ai-icon-btn { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border: 0; border-radius: 8px; background: transparent; color: var(--lm-text-secondary); cursor: pointer; transition: background .18s, color .18s; }
.ai-icon-btn:hover { background: var(--lm-bg-secondary); color: var(--lm-primary); }
.ai-close { display: inline-flex; width: 26px; height: 26px; align-items: center; justify-content: center; border: 0; border-radius: 8px; background: transparent; color: var(--lm-text-muted); font-size: 20px; line-height: 1; cursor: pointer; }
.ai-close:hover { background: var(--lm-danger-bg); color: var(--lm-danger); }

/* History */
.ai-history-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px 8px; }
.ai-history-title { color: var(--lm-text-primary); font-size: 14px; font-weight: 600; }
.ai-history-count { margin-top: 2px; color: var(--lm-text-muted); font-size: 11px; }
.ai-history { overflow: auto; flex: 1; padding: 2px 8px 12px; }
.ai-history-group-label { padding: 8px 6px 4px; color: var(--lm-text-muted); font-size: 11px; font-weight: 600; }
.ai-history-item { display: flex; width: 100%; align-items: center; gap: 10px; padding: 9px 10px; margin-bottom: 2px; text-align: left; border: 1px solid transparent; border-radius: 12px; background: transparent; cursor: pointer; transition: background .15s, border-color .15s; }
.ai-history-item:hover { background: var(--lm-bg-secondary); }
.ai-history-item.active { border-color: var(--lm-primary); background: var(--lm-primary-bg); }
.ai-history-avatar { display: flex; width: 32px; height: 32px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 9px; background: var(--lm-bg-secondary); color: var(--lm-text-secondary); }
.ai-history-item.active .ai-history-avatar { background: var(--lm-surface); color: var(--lm-primary); box-shadow: var(--lm-shadow-xs); }
.ai-history-text { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3px; }
.ai-history-name { overflow: hidden; color: var(--lm-text-primary); font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.ai-history-item.active .ai-history-name { color: var(--lm-primary); }
.ai-history-meta { color: var(--lm-text-muted); font-size: 11px; }
.ai-history-arrow { color: var(--lm-text-muted); }
.ai-history-empty { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 44px 0; color: var(--lm-text-muted); font-size: 13px; }
.ai-history-empty p { margin: 0; }

/* Welcome */
.ai-messages { display: flex; flex-direction: column; gap: 12px; padding: 16px 14px 10px; overflow: auto; flex: 1; background: var(--lm-bg); }
.ai-welcome { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.ai-welcome-avatar { display: inline-flex; width: 52px; height: 52px; align-items: center; justify-content: center; border-radius: 16px; background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; }
.ai-welcome-title { margin: 6px 0 0; color: var(--lm-text-primary); font-size: 17px; font-weight: 700; }
.ai-welcome-desc { margin: 0; color: var(--lm-text-muted); font-size: 13px; line-height: 1.6; }
.ai-rec-head { display: flex; align-items: center; justify-content: space-between; width: 100%; margin: 14px 0 8px; }
.ai-rec-head span { color: var(--lm-text-secondary); font-size: 12px; font-weight: 600; }
.ai-rec-refresh { display: inline-flex; align-items: center; gap: 3px; padding: 3px 6px; border: 0; border-radius: 6px; background: transparent; color: var(--lm-text-muted); font: inherit; font-size: 12px; cursor: pointer; }
.ai-rec-refresh:hover { color: var(--lm-primary); }
.ai-rec-list { display: flex; width: 100%; flex-direction: column; gap: 8px; }
.ai-rec-card { display: flex; width: 100%; align-items: center; gap: 10px; padding: 9px 10px; text-align: left; border: 1px solid var(--lm-border); border-radius: 12px; background: var(--lm-surface); cursor: pointer; transition: border-color .16s, box-shadow .16s, transform .16s; }
.ai-rec-card:hover { border-color: var(--lm-primary); box-shadow: var(--lm-shadow-sm); transform: translateY(-1px); }
.ai-rec-icon { display: flex; width: 30px; height: 30px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 9px; background: var(--lm-primary-bg); color: var(--lm-primary); }
.ai-rec-body { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.ai-rec-cat { color: var(--lm-text-muted); font-size: 11px; }
.ai-rec-q { color: var(--lm-text-primary); font-size: 13px; }

/* Chat */
.ai-msg { display: flex; align-items: flex-start; gap: 8px; }
.ai-msg.assistant { justify-content: flex-start; }
.ai-msg.user { justify-content: flex-end; }
.ai-msg-col { display: flex; min-width: 0; max-width: 78%; flex-direction: column; gap: 4px; }
.ai-msg-col.user { align-items: flex-end; }
.ai-msg-name { color: var(--lm-text-muted); font-size: 11px; line-height: 1; }
.ai-msg.user .ai-msg-name { text-align: right; }
.ai-msg-avatar { display: flex; width: 28px; height: 28px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 9px; }
.ai-msg-avatar.support { background: linear-gradient(135deg, var(--lm-primary), var(--lm-primary-light)); color: #fff; }
.ai-msg-avatar.user { background: linear-gradient(135deg, #475569, #64748b); color: #fff; font-size: 14px; font-weight: 700; text-transform: uppercase; }
.ai-msg-avatar.user { overflow: hidden; }
.ai-msg-avatar.user img { width: 100%; height: 100%; object-fit: cover; display: block; }
.ai-bubble { max-width: 100%; padding: 8px 11px; border-radius: 12px; font-size: 13px; }
.ai-msg.assistant .ai-bubble { border-top-left-radius: 4px; background: var(--lm-surface); border: 1px solid var(--lm-border); color: var(--lm-text-primary); }
.ai-msg.user .ai-bubble { border-top-right-radius: 4px; background: var(--lm-primary); color: #fff; }
.ai-content { margin: 0; white-space: pre-wrap; line-height: 1.6; }
.ai-meta { display: flex; align-items: center; gap: 6px; margin-top: 6px; flex-wrap: wrap; }
.ai-msg.user .ai-meta { color: rgba(255,255,255,0.8); }
.ai-msg.assistant .ai-meta { color: var(--lm-text-muted); }
.ai-msg-time { font-size: 10px; }
.ai-msg-actions { display: flex; align-items: center; gap: 4px; margin-top: 4px; }
.ai-msg-actions button { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; border: 0; border-radius: 6px; background: transparent; color: var(--lm-text-muted); cursor: pointer; transition: background .15s, color .15s; }
.ai-msg-actions button:hover { background: var(--lm-bg-secondary); color: var(--lm-primary); }
.ai-msg-avatar.support + .ai-msg-col .ai-bubble.typing { display: inline-flex; gap: 4px; padding: 12px 14px; }
.ai-typing-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--lm-text-muted); animation: ai-blink 1.2s infinite ease-in-out; }
.ai-typing-dot:nth-child(2) { animation-delay: .2s; }
.ai-typing-dot:nth-child(3) { animation-delay: .4s; }
@keyframes ai-blink { 0%, 80%, 100% { opacity: .3; } 40% { opacity: 1; } }
.ai-empty { margin: auto; padding: 16px; text-align: center; color: var(--lm-text-muted); font-size: 13px; line-height: 1.7; }
.ai-empty-icon { display: inline-flex; width: 48px; height: 48px; align-items: center; justify-content: center; margin-bottom: 8px; border-radius: 14px; background: var(--lm-primary-bg); color: var(--lm-primary); }
.ai-empty p { margin: 0; }

/* Suggestions + quick + input */
.ai-suggest { padding: 4px 12px 0; }
.ai-suggest-item { display: flex; width: 100%; align-items: center; gap: 7px; padding: 8px 10px; margin-bottom: 3px; text-align: left; border: 1px solid var(--lm-border-light); border-radius: 10px; background: var(--lm-surface); color: var(--lm-text-secondary); font: inherit; font-size: 13px; cursor: pointer; transition: border-color .15s, color .15s; }
.ai-suggest-item:hover { color: var(--lm-primary); border-color: var(--lm-primary); }
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
@media (max-width: 520px) { .ai-widget { right: 12px; bottom: 12px; gap: 10px; } .ai-panel { width: calc(100vw - 24px); height: 74vh; border-radius: 14px; } }
</style>
