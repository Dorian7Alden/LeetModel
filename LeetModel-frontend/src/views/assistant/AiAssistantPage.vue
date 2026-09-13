<template>
  <div class="assistant-workspace">
    <!-- 移动端侧栏遮罩层 -->
    <div
      v-if="mobileSidebarOpen"
      class="mobile-backdrop"
      @click="mobileSidebarOpen = false"
    ></div>

    <!-- ====================================================================
         1. 左侧会话侧边栏 (Sidebar - 280px)
         ==================================================================== -->
    <aside class="assistant-sidebar" :class="{ 'mobile-open': mobileSidebarOpen }">
      <!-- 侧边栏顶部操作栏 -->
      <div class="sidebar-header">
        <div class="brand-badge">
          <div class="badge-avatar">
            <img :src="aiAvatarImg" alt="LeetModel AI" class="avatar-img" />
          </div>
          <div class="badge-info">
            <span class="badge-title">AI 客服</span>
            <span class="badge-status" :class="serviceStatus">
              <i class="status-dot"></i>
              {{ serviceStatusLabel }}
            </span>
          </div>
        </div>

        <button
          type="button"
          class="btn-new-conv"
          title="创建新对话"
          :disabled="creating"
          @click="handleNewConversation"
        >
          <el-icon :size="16"><Plus /></el-icon>
        </button>
      </div>

      <!-- 搜索过滤条 -->
      <div class="sidebar-search">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索会话记录..."
          clearable
          size="small"
          class="search-input"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- 会话列表（按时间自然分组：今天、本周、更早） -->
      <div class="sidebar-scroll" v-loading="loadingConvs">
        <template v-if="filteredGroups.length">
          <div v-for="group in filteredGroups" :key="group.label" class="conv-group">
            <div class="group-label">{{ group.label }}</div>
            <div
              v-for="conv in group.items"
              :key="conv.id"
              class="conv-item"
              :class="{ active: String(conv.id) === String(currentId) }"
              @click="handleSelectConversation(conv.id)"
            >
              <div class="conv-icon">
                <el-icon :size="15"><ChatDotRound /></el-icon>
              </div>

              <!-- 标题呈现或行内重命名编辑 -->
              <div class="conv-content">
                <input
                  v-if="editingConvId === conv.id"
                  ref="editInputRef"
                  v-model="editingTitle"
                  type="text"
                  maxlength="50"
                  class="edit-title-input"
                  @blur="saveRename(conv)"
                  @keydown.enter.prevent="saveRename(conv)"
                  @keydown.esc.prevent="cancelRename"
                  @click.stop
                />
                <span v-else class="conv-title" :title="conv.title || '未命名会话'">
                  {{ conv.title || '未命名会话' }}
                </span>
                <span class="conv-time">{{ shortTime(conv.updateTime) }}</span>
              </div>

              <!-- 悬停操作动作栏 (重命名与删除) -->
              <div v-if="editingConvId !== conv.id" class="conv-actions" @click.stop>
                <button
                  type="button"
                  class="conv-action-btn"
                  title="重命名"
                  @click="startRename(conv)"
                >
                  <el-icon :size="13"><EditPen /></el-icon>
                </button>
                <el-popconfirm
                  title="确认删除该会话？"
                  confirm-button-text="删除"
                  cancel-button-text="取消"
                  confirm-button-type="danger"
                  width="180px"
                  @confirm="handleDeleteConversation(conv.id)"
                >
                  <template #reference>
                    <button type="button" class="conv-action-btn delete" title="删除会话">
                      <el-icon :size="13"><Delete /></el-icon>
                    </button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </div>
        </template>

        <!-- 侧栏空状态 -->
        <div v-else-if="!loadingConvs" class="sidebar-empty">
          <el-icon :size="24"><ChatLineSquare /></el-icon>
          <p>{{ searchKeyword ? '无匹配会话' : '暂无历史会话' }}</p>
        </div>
      </div>

      <!-- 侧边栏底部简要统计 -->
      <div class="sidebar-footer">
        <span>共 {{ conversations.length }} 条历史会话</span>
      </div>
    </aside>

    <!-- ====================================================================
         2. 右侧沉浸式聊天主区 (Main Chat Area - Flex 1)
         ==================================================================== -->
    <main class="assistant-main">
      <!-- 聊天主区顶栏 Header -->
      <header class="chat-topbar">
        <div class="topbar-left">
          <!-- 移动端抽屉唤起按钮 -->
          <button
            type="button"
            class="mobile-toggle-btn"
            title="查看会话历史"
            @click="mobileSidebarOpen = true"
          >
            <el-icon :size="17"><Fold /></el-icon>
          </button>

          <div class="current-meta">
            <h2 class="current-title">
              {{ currentConversation?.title || '新会话' }}
            </h2>
            <span class="meta-tag">
              <i class="meta-dot"></i>
              实时应答
            </span>
          </div>
        </div>

        <div class="topbar-actions">
          <button
            type="button"
            class="topbar-btn"
            title="新建对话"
            :disabled="creating"
            @click="handleNewConversation"
          >
            <el-icon :size="14"><Plus /></el-icon>
            <span>新对话</span>
          </button>
          <button
            type="button"
            class="topbar-btn"
            title="刷新会话"
            :disabled="loadingDetail"
            @click="handleRefreshCurrent"
          >
            <el-icon :size="14" :class="{ 'spin-anim': loadingDetail }"><Refresh /></el-icon>
          </button>
        </div>
      </header>

      <!-- 消息流动滚动区 (Messages Container) -->
      <div ref="messagesRef" class="chat-scroll-area">
        <!-- 欢迎空态（当前会话尚无任何消息） -->
        <div v-if="messages.length === 0 && !sending" class="welcome-container">
          <div class="welcome-hero">
            <div class="welcome-avatar-wrap">
              <img :src="aiSmileImg" alt="LeetModel Assistant" class="welcome-avatar" />
            </div>
            <h3 class="welcome-heading">你好，我是 LeetModel 智能客服</h3>
            <p class="welcome-desc">
              你可以向我咨询平台组队规则、论文 PDF 提交规范、AI 评审流程，或是让我推荐适合的实训练习赛题。
            </p>
          </div>

          <!-- 推荐问题引导网格 -->
          <div class="rec-section">
            <div class="rec-header">
              <span class="rec-label">常见指引与示例</span>
              <button type="button" class="btn-rec-refresh" @click="rotateRec">
                <el-icon :size="13"><Refresh /></el-icon>
                <span>换一换</span>
              </button>
            </div>
            <div class="rec-grid">
              <button
                v-for="r in recQuestions"
                :key="r.q"
                type="button"
                class="rec-card"
                :disabled="sending"
                @click="send(r.q)"
              >
                <div class="rec-icon-box">
                  <el-icon :size="15"><component :is="r.icon" /></el-icon>
                </div>
                <div class="rec-body">
                  <span class="rec-cat">{{ r.category }}</span>
                  <span class="rec-query">{{ r.q }}</span>
                </div>
                <el-icon class="rec-arrow" :size="14"><ArrowRight /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <!-- 消息气泡流 -->
        <div v-else class="messages-flow">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="msg-row"
            :class="msg.role"
          >
            <!-- 1. 客服消息 (左侧呈现) -->
            <template v-if="msg.role === 'assistant'">
              <div class="avatar-cell assistant">
                <img :src="aiAvatarImg" alt="AI 客服" class="avatar-img" />
              </div>
              <div class="bubble-cell assistant">
                <div class="cell-author">
                  <span class="author-name">AI 客服</span>
                  <span class="msg-time">{{ shortTime(msg.createTime) }}</span>
                </div>

                <div class="bubble-box">
                  <!-- 工具调用状态指示胶囊 -->
                  <div
                    v-if="msg.toolStatus"
                    class="tool-status-pill"
                    :class="msg.toolStatus.status"
                  >
                    <el-icon v-if="msg.toolStatus.status === 'RUNNING'" class="tool-spin">
                      <Loading />
                    </el-icon>
                    <el-icon v-else><Check /></el-icon>
                    <span>{{ msg.toolStatus.displayName }}</span>
                  </div>

                  <!-- Markdown 文本解析或流式打字中光标 -->
                  <div
                    v-if="msg.content"
                    class="markdown-body msg-md-content"
                    v-html="md(msg.content)"
                  ></div>
                  <div v-else-if="msg.status === 'RUNNING'" class="typing-wave">
                    <span class="wave-dot"></span>
                    <span class="wave-dot"></span>
                    <span class="wave-dot"></span>
                  </div>

                  <!-- 结构化赛题推荐卡片 -->
                  <div
                    v-if="getProblemCards(msg.toolContextJson).length"
                    class="problem-cards-group"
                  >
                    <div class="problem-cards-title">
                      <el-icon :size="13"><Document /></el-icon>
                      <span>推荐题目 ({{ getProblemCards(msg.toolContextJson).length }})</span>
                    </div>
                    <div class="problem-cards-list">
                      <div
                        v-for="card in getProblemCards(msg.toolContextJson)"
                        :key="card.code"
                        class="problem-card-item"
                        @click="goToProblem(card.code)"
                      >
                        <div class="problem-card-meta">
                          <div class="card-title-row">
                            <span class="card-code-pill">#{{ card.code }}</span>
                            <span class="card-title" :title="card.title">{{ card.title }}</span>
                          </div>
                          <div class="card-tags-row">
                            <el-tag
                              v-if="card.difficulty"
                              size="small"
                              :type="difficultyType(card.difficulty)"
                              effect="light"
                            >
                              {{ difficultyLabel(card.difficulty) }}
                            </el-tag>
                            <el-tag v-if="card.year" size="small" type="info" effect="plain">
                              {{ card.year }}年
                            </el-tag>
                            <el-tag v-if="card.contestName" size="small" type="info" effect="plain">
                              {{ card.contestName }}
                            </el-tag>
                            <el-tag
                              v-for="tag in card.tagNames.slice(0, 2)"
                              :key="tag"
                              size="small"
                              effect="plain"
                            >
                              {{ tag }}
                            </el-tag>
                          </div>
                        </div>
                        <el-icon class="card-arrow" :size="14"><ArrowRight /></el-icon>
                      </div>
                    </div>
                  </div>

                  <!-- 异常失败提示 -->
                  <div v-if="msg.status === 'FAILED'" class="msg-error-alert">
                    <el-icon :size="14"><WarningFilled /></el-icon>
                    <span>{{ msg.errorMessage || '回复生成遇到异常' }}</span>
                  </div>

                  <!-- 消息底部操作条 -->
                  <div v-if="msg.status !== 'RUNNING'" class="msg-bottom-bar">
                    <button
                      type="button"
                      class="msg-action-btn"
                      title="复制回答"
                      @click="copy(msg.content)"
                    >
                      <el-icon :size="13"><CopyDocument /></el-icon>
                      <span>复制</span>
                    </button>
                    <button
                      v-if="msg.status === 'FAILED'"
                      type="button"
                      class="msg-action-btn retry"
                      title="重试生成"
                      @click="retry(msg.id)"
                    >
                      <el-icon :size="13"><Refresh /></el-icon>
                      <span>重试</span>
                    </button>
                  </div>
                </div>
              </div>
            </template>

            <!-- 2. 用户消息 (右侧呈现) -->
            <template v-else>
              <div class="bubble-cell user">
                <div class="cell-author">
                  <span class="msg-time">{{ shortTime(msg.createTime) }}</span>
                  <span class="author-name">{{ userDisplayName }}</span>
                </div>
                <div class="bubble-box user">
                  <p class="user-text">{{ msg.content || '（无内容）' }}</p>
                </div>
              </div>
              <div class="avatar-cell user">
                <img v-if="userAvatar" :src="userAvatar" alt="我" class="avatar-img" />
                <span v-else class="avatar-initial">{{ userInitial }}</span>
              </div>
            </template>
          </div>

          <!-- 正在回复等待占位 (未接收到首个 chunk 前) -->
          <div v-if="sending && !messages.some((m) => m.status === 'RUNNING')" class="msg-row assistant">
            <div class="avatar-cell assistant">
              <img :src="aiAvatarImg" alt="AI 客服" class="avatar-img" />
            </div>
            <div class="bubble-cell assistant">
              <div class="cell-author">
                <span class="author-name">AI 客服</span>
              </div>
              <div class="bubble-box">
                <div class="typing-wave">
                  <span class="wave-dot"></span>
                  <span class="wave-dot"></span>
                  <span class="wave-dot"></span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部输入交互停靠坞 (Input Dock) -->
      <footer class="chat-input-dock">
        <!-- 快捷提问推荐胶囊（有消息时在输入框上方浮动） -->
        <div v-if="messages.length" class="quick-chip-scroll">
          <button
            v-for="q in quickQuestions"
            :key="q"
            type="button"
            class="quick-chip"
            :disabled="sending"
            @click="send(q)"
          >
            {{ q }}
          </button>
        </div>

        <!-- 联想建议浮层 -->
        <div v-if="suggestOpen && suggestions.length" class="suggestions-popover">
          <button
            v-for="s in suggestions"
            :key="s.q"
            type="button"
            class="suggestion-item"
            @click="send(s.q)"
          >
            <el-icon :size="13"><Search /></el-icon>
            <span>{{ s.q }}</span>
          </button>
        </div>

        <!-- 文本输入核心区 -->
        <div class="input-form-box">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            resize="none"
            placeholder="向 AI 客服提问... (Enter 发送，Shift+Enter 换行)"
            @focus="suggestOpen = true"
            @blur="delayCloseSuggest"
            @input="suggestOpen = true"
            @keydown.enter.exact.prevent="send()"
          />

          <div class="input-actions-bar">
            <div class="input-hints">
              <span>支持 Markdown 与 LaTeX 规范解答</span>
            </div>
            <button
              type="button"
              class="btn-send-msg"
              :disabled="!draft.trim() || sending"
              title="发送消息"
              @click="send()"
            >
              <el-icon :size="16"><Promotion /></el-icon>
              <span>发送</span>
            </button>
          </div>
        </div>
      </footer>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserStore } from "@/store/user";
import { renderSafeMarkdown } from "@/utils/markdown";
import {
  listConversations,
  createConversation,
  getConversation,
  renameConversation,
  deleteConversation,
  sendMessage,
  retryMessage,
} from "@/api/assistant";

import aiAvatarImg from "@/assets/images/AI客服-avatar.png";
import aiSmileImg from "@/assets/images/AI客服-smile.png";

const router = useRouter();
const userStore = useUserStore();

// 侧栏与状态
const mobileSidebarOpen = ref(false);
const loadingConvs = ref(false);
const loadingDetail = ref(false);
const creating = ref(false);
const sending = ref(false);
const conversations = ref([]);
const currentId = ref(null);
const searchKeyword = ref("");

// 行内重命名编辑
const editingConvId = ref(null);
const editingTitle = ref("");
const editInputRef = ref(null);

// 消息与输入
const messages = ref([]);
const draft = ref("");
const messagesRef = ref(null);
const recOffset = ref(0);
const suggestOpen = ref(false);
const serviceStatus = ref("unknown");
let suggestTimer = null;

// 用户信息计算属性
const userInitial = computed(() => (userStore.nickname || userStore.username || "我").charAt(0));
const userDisplayName = computed(() => userStore.nickname || userStore.username || "我");
const userAvatar = computed(() => userStore.avatarUrl || "");
const serviceStatusLabel = computed(() => ({
  unknown: "待连接",
  connected: "服务就绪",
  unavailable: "连接异常",
})[serviceStatus.value]);

const currentConversation = computed(() =>
  conversations.value.find((c) => String(c.id) === String(currentId.value))
);

// 推荐问答题库池
const recPool = [
  { category: "组队实训", icon: "Trophy", q: "如何创建队伍并招募成员？" },
  { category: "组队实训", icon: "Trophy", q: "队长如何分配队员的实训职责？" },
  { category: "论文提交", icon: "Upload", q: "如何提交建模论文 PDF 稿件？" },
  { category: "论文提交", icon: "Upload", q: "如何确认队伍的最终提交稿？" },
  { category: "AI 评审", icon: "DataAnalysis", q: "AI 评审报告包含哪些核心维度？" },
  { category: "AI 评审", icon: "DataAnalysis", q: "提交完成后多久能够生成评审建议？" },
  { category: "赛题题库", icon: "Collection", q: "如何筛选适合团队练习的数模赛题？" },
  { category: "平台答疑", icon: "ChatDotRound", q: "如何查看我的队伍天梯排名？" },
];

const recPageSize = 4;
const recQuestions = computed(() => {
  const start = recOffset.value % recPool.length;
  return Array.from({ length: recPageSize }, (_, i) => recPool[(start + i) % recPool.length]);
});

// 输入联想
const suggestions = computed(() => {
  const k = draft.value.trim();
  if (!k) return [];
  return recPool.filter((r) => r.q.includes(k)).slice(0, 3);
});

const quickQuestions = recPool.slice(0, 5).map((r) => r.q);

// 按时间分组计算属性
const historyGroups = computed(() => {
  const now = new Date();
  const bands = [
    { label: "今天", fn: (t) => sameDay(t, now) },
    { label: "本周", fn: (t) => !sameDay(t, now) && withinDays(t, now, 7) },
    { label: "更早", fn: (t) => !withinDays(t, now, 7) },
  ];
  return bands
    .map((band) => ({
      label: band.label,
      items: conversations.value.filter((c) => band.fn(parseDate(c.updateTime || c.createTime))),
    }))
    .filter((band) => band.items.length);
});

// 过滤后的会话分组（支持搜索）
const filteredGroups = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return historyGroups.value;
  return historyGroups.value
    .map((g) => ({
      label: g.label,
      items: g.items.filter((item) => (item.title || "").toLowerCase().includes(kw)),
    }))
    .filter((g) => g.items.length);
});

function parseDate(value) {
  return value ? new Date(String(value).replace(" ", "T")) : null;
}
function sameDay(a, b) {
  return a && b && a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}
function withinDays(a, b, days) {
  return a && b && a.getTime() >= b.getTime() - days * 86400000;
}

const md = (value) => renderSafeMarkdown(value);

function uuid() {
  if (crypto?.randomUUID) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function shortTime(value) {
  return value ? String(value).replace("T", " ").slice(5, 16) : "";
}

function rotateRec() {
  recOffset.value += recPageSize;
}

function delayCloseSuggest() {
  suggestTimer = window.setTimeout(() => {
    suggestOpen.value = false;
  }, 150);
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text || "");
    ElMessage.success("已复制回答内容");
  } catch {
    ElMessage.error("复制失败");
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight;
    }
  });
}

// 加载会话列表
async function loadConversationsList(autoSelectFirst = false) {
  loadingConvs.value = true;
  try {
    const res = await listConversations();
    conversations.value = res.data || [];
    if (serviceStatus.value === "unknown") serviceStatus.value = "connected";

    if (autoSelectFirst) {
      if (conversations.value.length) {
        await handleSelectConversation(conversations.value[0].id);
      } else {
        await handleNewConversation();
      }
    }
  } catch (error) {
    serviceStatus.value = "unavailable";
    ElMessage.error(error.message || "对话列表加载失败");
  } finally {
    loadingConvs.value = false;
  }
}

// 选中并加载会话详情
async function handleSelectConversation(id) {
  if (String(currentId.value) === String(id) && messages.value.length) {
    mobileSidebarOpen.value = false;
    return;
  }
  currentId.value = id;
  loadingDetail.value = true;
  try {
    const res = await getConversation(id);
    messages.value = res.data?.messages || [];
    scrollToBottom();
    mobileSidebarOpen.value = false;
  } catch (error) {
    ElMessage.error(error.message || "会话详情加载失败");
  } finally {
    loadingDetail.value = false;
  }
}

// 刷新当前会话
async function handleRefreshCurrent() {
  if (!currentId.value) return;
  await handleSelectConversation(currentId.value);
}

// 新建会话
async function handleNewConversation() {
  creating.value = true;
  try {
    const res = await createConversation("新会话");
    const newConv = res.data;
    const existsIndex = conversations.value.findIndex((c) => String(c.id) === String(newConv.id));
    if (existsIndex >= 0) {
      conversations.value.splice(existsIndex, 1, newConv);
    } else {
      conversations.value.unshift(newConv);
    }
    currentId.value = newConv.id;
    messages.value = [];
    recOffset.value = 0;
    mobileSidebarOpen.value = false;
  } catch (error) {
    ElMessage.error(error.message || "新建会话失败");
  } finally {
    creating.value = false;
  }
}

// 开始重命名
function startRename(conv) {
  editingConvId.value = conv.id;
  editingTitle.value = conv.title || "";
  nextTick(() => {
    if (editInputRef.value) {
      editInputRef.value.focus();
    }
  });
}

function cancelRename() {
  editingConvId.value = null;
  editingTitle.value = "";
}

// 保存重命名
async function saveRename(conv) {
  if (!editingConvId.value) return;
  const newTitle = editingTitle.value.trim();
  if (!newTitle || newTitle === conv.title) {
    cancelRename();
    return;
  }
  try {
    await renameConversation(conv.id, newTitle);
    conv.title = newTitle;
    ElMessage.success("标题已更新");
  } catch (error) {
    ElMessage.error(error.message || "重命名失败");
  } finally {
    cancelRename();
  }
}

// 删除会话
async function handleDeleteConversation(convId) {
  try {
    await deleteConversation(convId);
    conversations.value = conversations.value.filter((c) => String(c.id) !== String(convId));
    ElMessage.success("会话已删除");
    if (String(currentId.value) === String(convId)) {
      if (conversations.value.length) {
        await handleSelectConversation(conversations.value[0].id);
      } else {
        await handleNewConversation();
      }
    }
  } catch (error) {
    ElMessage.error(error.message || "删除会话失败");
  }
}

// 发送消息
async function send(text) {
  const content = (text || draft.value).trim();
  if (!content || sending.value) return;
  if (!currentId.value) {
    ElMessage.warning("请先创建会话");
    return;
  }

  const tempId = `temp-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
  const optimisticUserMessage = {
    id: tempId,
    role: "user",
    content,
    createTime: new Date().toISOString(),
    status: "COMPLETED",
  };
  messages.value.push(optimisticUserMessage);

  const streamAssistantId = `stream-${Date.now()}`;
  const streamingAssistantMessage = {
    id: streamAssistantId,
    role: "assistant",
    content: "",
    status: "RUNNING",
    toolStatus: null,
    toolContextJson: null,
    createTime: new Date().toISOString(),
  };
  messages.value.push(streamingAssistantMessage);
  scrollToBottom();

  sending.value = true;
  suggestOpen.value = false;
  const originalDraft = draft.value;
  if (!text) draft.value = "";

  const clientRequestId = uuid();
  let sseHandled = false;

  try {
    const token = userStore.token;
    const response = await fetch(`/api/assistant/conversations/${currentId.value}/messages/stream`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        satoken: token || "",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ content, clientRequestId }),
    });

    if (response.ok && response.body) {
      sseHandled = true;
      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split("\n\n");
        buffer = blocks.pop() || "";

        for (const block of blocks) {
          if (!block.trim()) continue;
          let eventName = "message";
          let dataStr = "";
          for (const line of block.split("\n")) {
            if (line.startsWith("event:")) {
              eventName = line.slice(6).trim();
            } else if (line.startsWith("data:")) {
              dataStr += line.slice(5).trim();
            }
          }
          if (!dataStr) continue;
          try {
            const payload = JSON.parse(dataStr);
            if (eventName === "tool_start") {
              streamingAssistantMessage.toolStatus = {
                name: payload.tool,
                displayName: payload.displayName || "正在调用平台工具...",
                status: "RUNNING",
              };
              scrollToBottom();
            } else if (eventName === "tool_end") {
              streamingAssistantMessage.toolStatus = {
                name: payload.tool,
                displayName: payload.displayName || "工具执行完成",
                status: "COMPLETED",
              };
              if (payload.toolContextJson) {
                streamingAssistantMessage.toolContextJson = payload.toolContextJson;
              }
              scrollToBottom();
            } else if (eventName === "delta") {
              if (payload.content) {
                streamingAssistantMessage.content += payload.content;
                scrollToBottom();
              }
            } else if (eventName === "message_end") {
              streamingAssistantMessage.id = payload.messageId || streamingAssistantMessage.id;
              streamingAssistantMessage.status = payload.status || "COMPLETED";
              if (payload.fullContent) {
                streamingAssistantMessage.content = payload.fullContent;
              }
              if (payload.toolContextJson) {
                streamingAssistantMessage.toolContextJson = payload.toolContextJson;
              }
              streamingAssistantMessage.toolStatus = null;
              serviceStatus.value = "connected";
              scrollToBottom();
            } else if (eventName === "error") {
              streamingAssistantMessage.status = "FAILED";
              streamingAssistantMessage.errorMessage = payload.message || "回复失败";
              streamingAssistantMessage.toolStatus = null;
              serviceStatus.value = "unavailable";
            }
          } catch (e) {
            console.error("SSE parse error", e);
          }
        }
      }
    } else {
      throw new Error(`HTTP ${response.status}`);
    }
  } catch (err) {
    if (!sseHandled) {
      try {
        const res = await sendMessage(currentId.value, content, clientRequestId);
        const { assistantMessage } = res.data || {};
        const idx = messages.value.findIndex((m) => m.id === streamAssistantId);
        if (idx >= 0 && assistantMessage) {
          messages.value.splice(idx, 1, assistantMessage);
        }
        serviceStatus.value = assistantMessage?.status === "FAILED" ? "unavailable" : "connected";
      } catch (fallbackErr) {
        messages.value = messages.value.filter((m) => m.id !== tempId && m.id !== streamAssistantId);
        if (!text) draft.value = originalDraft;
        serviceStatus.value = "unavailable";
        ElMessage.error(fallbackErr.message || "发送失败");
        return;
      }
    }
  } finally {
    sending.value = false;
    const currentConv = conversations.value.find((c) => String(c.id) === String(currentId.value));
    if (currentConv && (!currentConv.title || currentConv.title === "新会话" || currentConv.title === "AI 客服咨询")) {
      const derived = content.length <= 30 ? content : content.substring(0, 30) + "…";
      currentConv.title = derived;
    }
    loadConversationsList(false);
    scrollToBottom();
  }
}

// 提取赛题结构化卡片
function getProblemCards(toolContextJson) {
  if (!toolContextJson) return [];
  try {
    const data = typeof toolContextJson === "string" ? JSON.parse(toolContextJson) : toolContextJson;
    if (Array.isArray(data)) {
      const toolWithProblems = data.find((ctx) => ctx && ctx.result && Array.isArray(ctx.result.items));
      if (toolWithProblems) {
        return (toolWithProblems.result.items || []).map(normalizeProblemCard);
      }
      if (data.length && (data[0].code !== undefined || data[0].title !== undefined)) {
        return data.map(normalizeProblemCard);
      }
    } else if (data && typeof data === "object" && Array.isArray(data.items)) {
      return data.items.map(normalizeProblemCard);
    }
    return [];
  } catch {
    return [];
  }
}

function normalizeProblemCard(item) {
  return {
    code: item.code,
    title: item.title || "未知题目",
    contestName: item.contestName || item.contestCode || "",
    year: item.year || null,
    difficulty: item.difficulty || null,
    durationMinutes: item.durationMinutes || null,
    tagNames: Array.isArray(item.tagNames) ? item.tagNames : [],
  };
}

function difficultyType(difficulty) {
  return { 1: "success", 2: "warning", 3: "danger" }[Number(difficulty)] || "info";
}

function difficultyLabel(difficulty) {
  return { 1: "简单", 2: "中等", 3: "困难" }[Number(difficulty)] || "未知";
}

function goToProblem(code) {
  if (!code) return;
  router.push(`/problem/${code}`);
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

onMounted(() => {
  if (userStore.isLogin) {
    loadConversationsList(true);
  }
});

onBeforeUnmount(() => {
  if (suggestTimer) clearTimeout(suggestTimer);
});
</script>

<style>
/* 全局 Markdown 样式微调，适配黑白灰与学术排版 */
.msg-md-content.markdown-body,
.msg-md-content.markdown-body * {
  box-sizing: border-box;
}
.msg-md-content.markdown-body {
  font-family: var(--lm-font-family);
  font-size: 14px;
  line-height: 1.7;
  color: var(--lm-text-primary);
  word-break: break-word;
  padding: 0;
  background: transparent;
  margin: 0;
}
.msg-md-content.markdown-body :is(p, ul, ol, pre, blockquote, table) {
  margin: 0 0 8px;
}
.msg-md-content.markdown-body :is(ul, ol) {
  padding-left: 20px;
}
.msg-md-content.markdown-body :is(h1, h2, h3, h4, h5, h6) {
  margin: 12px 0 6px;
  font-size: 1.05em;
  font-weight: 700;
  line-height: 1.4;
  color: var(--lm-text-primary);
}
.msg-md-content.markdown-body strong {
  font-weight: 700;
  color: var(--lm-text-primary);
}
.msg-md-content.markdown-body code {
  font-family: var(--lm-code-font-family);
  font-size: 12.5px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.05);
  color: #c026d3;
}
.msg-md-content.markdown-body pre {
  overflow: auto;
  padding: 10px 12px;
  border-radius: 8px;
  background: #18181b;
  color: #f4f4f5;
  margin: 8px 0;
}
.msg-md-content.markdown-body pre code {
  background: transparent;
  padding: 0;
  color: inherit;
}
.msg-md-content.markdown-body a {
  color: #2563eb;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.msg-md-content.markdown-body blockquote {
  padding-left: 12px;
  border-left: 3px solid var(--lm-border);
  color: var(--lm-text-muted);
}
.msg-md-content.markdown-body table {
  border-collapse: collapse;
  font-size: 13px;
  width: 100%;
  margin: 8px 0;
}
.msg-md-content.markdown-body th,
.msg-md-content.markdown-body td {
  padding: 6px 10px;
  border: 1px solid var(--lm-border);
}
</style>

<style scoped>
/* ==========================================================================
   AI Assistant Workspace 顶层容器 (高度撑满视口，双栏布局)
   ========================================================================== */
.assistant-workspace {
  display: flex;
  height: calc(100vh - 65px); /* 减去顶部导航条高度 */
  background: var(--lm-bg);
  position: relative;
  overflow: hidden;
}

/* ==========================================================================
   1. 左侧会话侧边栏 (Sidebar: 280px)
   ========================================================================== */
.assistant-sidebar {
  width: 280px;
  min-width: 280px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--lm-bg);
  border-right: 1px solid var(--lm-border);
  position: relative;
  z-index: 20;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--lm-border-light);
}

.brand-badge {
  display: flex;
  align-items: center;
  gap: 10px;
}

.badge-avatar {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  overflow: hidden;
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border-light);
  flex-shrink: 0;
}

.badge-avatar .avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.badge-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.badge-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--lm-text-primary);
  line-height: 1.2;
}

.badge-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--lm-text-muted);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lm-text-muted);
}

.badge-status.connected {
  color: #16a34a;
}
.badge-status.connected .status-dot {
  background: #16a34a;
}

.badge-status.unavailable {
  color: #dc2626;
}
.badge-status.unavailable .status-dot {
  background: #dc2626;
}

.btn-new-conv {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--lm-border);
  background: var(--lm-surface);
  color: var(--lm-text-primary);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.btn-new-conv:hover:not(:disabled) {
  border-color: var(--lm-primary);
  background: var(--lm-bg-secondary);
  color: var(--lm-primary);
}

.btn-new-conv:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.sidebar-search {
  padding: 10px 14px 6px;
}

.sidebar-search :deep(.el-input__wrapper) {
  background: var(--lm-bg-secondary);
  border-radius: 8px;
  box-shadow: none;
  border: 1px solid transparent;
  transition: all var(--lm-transition);
}

.sidebar-search :deep(.el-input__wrapper.is-focus) {
  background: var(--lm-surface);
  border-color: var(--lm-primary);
  box-shadow: none;
}

.sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 6px 10px;
}

.conv-group {
  margin-bottom: 12px;
}

.group-label {
  padding: 6px 8px 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--lm-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: all 0.16s ease;
  position: relative;
  margin-bottom: 2px;
}

.conv-item:hover {
  background: var(--lm-bg-secondary);
}

.conv-item.active {
  background: var(--lm-bg-secondary);
  border-color: var(--lm-border);
}

.conv-icon {
  color: var(--lm-text-muted);
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.conv-item.active .conv-icon {
  color: var(--lm-text-primary);
}

.conv-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.conv-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 10.5px;
  color: var(--lm-text-muted);
}

.edit-title-input {
  width: 100%;
  font-size: 12.5px;
  padding: 2px 4px;
  border: 1px solid var(--lm-primary);
  border-radius: 4px;
  background: var(--lm-surface);
  color: var(--lm-text-primary);
  outline: none;
}

.conv-actions {
  display: none;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.conv-item:hover .conv-actions,
.conv-item.active .conv-actions {
  display: flex;
}

.conv-action-btn {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--lm-text-muted);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.14s ease;
}

.conv-action-btn:hover {
  background: var(--lm-surface);
  color: var(--lm-text-primary);
}

.conv-action-btn.delete:hover {
  color: #dc2626;
  background: #fee2e2;
}

.sidebar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 12px;
  color: var(--lm-text-muted);
  gap: 8px;
  font-size: 12.5px;
}

.sidebar-footer {
  padding: 10px 14px;
  border-top: 1px solid var(--lm-border-light);
  font-size: 11px;
  color: var(--lm-text-muted);
  text-align: center;
}

/* ==========================================================================
   2. 右侧聊天主区 (Main Area: Flex 1)
   ========================================================================== */
.assistant-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--lm-surface);
  position: relative;
  min-width: 0;
}

/* 顶栏 Header */
.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--lm-border-light);
  background: var(--lm-surface);
  z-index: 10;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.mobile-toggle-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--lm-border);
  border-radius: 8px;
  background: var(--lm-surface);
  color: var(--lm-text-primary);
  cursor: pointer;
}

.current-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.current-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #16a34a;
  background: #f0fdf4;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid #dcfce7;
  flex-shrink: 0;
}

.meta-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #16a34a;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border: 1px solid var(--lm-border);
  border-radius: 8px;
  background: var(--lm-surface);
  color: var(--lm-text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition: all var(--lm-transition);
}

.topbar-btn:hover:not(:disabled) {
  border-color: var(--lm-primary);
  color: var(--lm-primary);
  background: var(--lm-bg-secondary);
}

.topbar-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 消息滚动主区 */
.chat-scroll-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
}

/* 欢迎态 */
.welcome-container {
  max-width: 720px;
  margin: auto;
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.welcome-hero {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.welcome-avatar-wrap {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  overflow: hidden;
  background: var(--lm-bg-secondary);
  border: 2px solid var(--lm-border);
  box-shadow: var(--lm-shadow-sm);
}

.welcome-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.welcome-heading {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: var(--lm-text-primary);
}

.welcome-desc {
  margin: 0;
  font-size: 13.5px;
  color: var(--lm-text-muted);
  line-height: 1.6;
  max-width: 520px;
}

.rec-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rec-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rec-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--lm-text-secondary);
}

.btn-rec-refresh {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--lm-text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: color var(--lm-transition);
}

.btn-rec-refresh:hover {
  color: var(--lm-primary);
}

.rec-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.rec-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  text-align: left;
  border-radius: 10px;
  border: 1px solid var(--lm-border);
  background: var(--lm-surface);
  cursor: pointer;
  transition: all 0.16s ease;
}

.rec-card:hover {
  border-color: var(--lm-primary);
  background: var(--lm-bg-secondary);
  transform: translateY(-1px);
  box-shadow: var(--lm-shadow-xs);
}

.rec-icon-box {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--lm-bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lm-primary);
  flex-shrink: 0;
}

.rec-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rec-cat {
  font-size: 10.5px;
  color: var(--lm-text-muted);
}

.rec-query {
  font-size: 13px;
  font-weight: 500;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rec-arrow {
  color: var(--lm-text-muted);
  flex-shrink: 0;
  transition: transform 0.15s;
}

.rec-card:hover .rec-arrow {
  color: var(--lm-primary);
  transform: translateX(2px);
}

/* 消息流 */
.messages-flow {
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.msg-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.msg-row.assistant {
  justify-content: flex-start;
}

.msg-row.user {
  justify-content: flex-end;
}

.avatar-cell {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar-cell.assistant {
  border: 1px solid var(--lm-border-light);
  background: var(--lm-bg-secondary);
}

.avatar-cell.user {
  background: #18181b;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}

.avatar-initial {
  text-transform: uppercase;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.bubble-cell {
  max-width: 82%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bubble-cell.user {
  align-items: flex-end;
}

.cell-author {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--lm-text-muted);
}

.bubble-box {
  padding: 12px 16px;
  border-radius: 12px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  box-shadow: var(--lm-shadow-xs);
  color: var(--lm-text-primary);
  word-break: break-word;
}

.bubble-box.user {
  background: #18181b;
  color: #ffffff;
  border-color: #18181b;
  border-top-right-radius: 4px;
}

.user-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 14px;
}

/* 工具状态胶囊 */
.tool-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11.5px;
  margin-bottom: 8px;
  background: var(--lm-bg-secondary);
  color: var(--lm-text-secondary);
  border: 1px solid var(--lm-border);
}

.tool-status-pill.RUNNING {
  background: #eff6ff;
  color: #1d4ed8;
  border-color: #bfdbfe;
}

.tool-status-pill.COMPLETED {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}

.tool-spin {
  animation: spin 1s linear infinite;
}

/* 打字微动效波浪 */
.typing-wave {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
}

.wave-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lm-text-muted);
  animation: wave-bounce 1.2s infinite ease-in-out;
}

.wave-dot:nth-child(2) {
  animation-delay: 0.2s;
}
.wave-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes wave-bounce {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

/* 赛题卡片渲染 */
.problem-cards-group {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--lm-border);
}

.problem-cards-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--lm-text-secondary);
  margin-bottom: 8px;
}

.problem-cards-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.problem-card-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border-radius: 8px;
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border-light);
  cursor: pointer;
  transition: all 0.16s ease;
}

.problem-card-item:hover {
  background: #ffffff;
  border-color: var(--lm-primary);
  transform: translateY(-1px);
  box-shadow: var(--lm-shadow-xs);
}

.problem-card-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.card-code-pill {
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-primary);
  background: var(--lm-surface);
  padding: 1px 5px;
  border-radius: 4px;
  border: 1px solid var(--lm-border);
  flex-shrink: 0;
}

.card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.card-tags-row :deep(.el-tag) {
  height: 20px;
  line-height: 20px;
  padding: 0 5px;
  font-size: 10.5px;
  border-radius: 4px;
}

.card-arrow {
  color: var(--lm-text-muted);
  flex-shrink: 0;
  transition: transform 0.16s;
}

.problem-card-item:hover .card-arrow {
  color: var(--lm-primary);
  transform: translateX(2px);
}

/* 异常警示 */
.msg-error-alert {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  background: #fef2f2;
  color: #dc2626;
  font-size: 12px;
}

/* 消息底部工具条 */
.msg-bottom-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--lm-border-light);
}

.msg-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: var(--lm-text-muted);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.14s;
}

.msg-action-btn:hover {
  background: var(--lm-bg-secondary);
  color: var(--lm-text-primary);
}

.msg-action-btn.retry:hover {
  color: #ea580c;
  background: #fff7ed;
}

/* ==========================================================================
   3. 底部输入停靠区 (Bottom Dock)
   ========================================================================== */
.chat-input-dock {
  border-top: 1px solid var(--lm-border-light);
  background: var(--lm-surface);
  padding: 12px 28px 16px;
  position: relative;
  max-width: 900px;
  width: 100%;
  margin: 0 auto;
}

/* 快捷推荐滚动栏 */
.quick-chip-scroll {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: none;
  margin-bottom: 8px;
}

.quick-chip-scroll::-webkit-scrollbar {
  display: none;
}

.quick-chip {
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--lm-border);
  background: var(--lm-surface);
  color: var(--lm-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.quick-chip:hover {
  border-color: var(--lm-primary);
  color: var(--lm-primary);
}

.quick-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 联想输入浮层 */
.suggestions-popover {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 28px;
  right: 28px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: 8px;
  box-shadow: var(--lm-shadow);
  padding: 4px;
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--lm-text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  text-align: left;
  transition: all 0.14s;
}

.suggestion-item:hover {
  background: var(--lm-bg-secondary);
  color: var(--lm-primary);
}

/* 输入表单框架 */
.input-form-box {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--lm-border);
  border-radius: 12px;
  background: var(--lm-surface);
  padding: 10px 12px 8px;
  transition: border-color var(--lm-transition), box-shadow var(--lm-transition);
  box-shadow: var(--lm-shadow-xs);
}

.input-form-box:focus-within {
  border-color: var(--lm-primary);
  box-shadow: var(--lm-shadow-sm);
}

.input-form-box :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  background: transparent;
  font-size: 13.5px;
  color: var(--lm-text-primary);
  line-height: 1.6;
}

.input-actions-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--lm-border-light);
}

.input-hints {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.btn-send-msg {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 8px;
  border: none;
  background: #18181b;
  color: #ffffff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.16s ease;
}

.btn-send-msg:hover:not(:disabled) {
  background: #27272a;
  transform: translateY(-1px);
}

.btn-send-msg:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
}

/* 动效与自适应响应 */
.spin-anim {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.mobile-backdrop {
  display: none;
}

@media (max-width: 860px) {
  .assistant-sidebar {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    transform: translateX(-100%);
    transition: transform 0.24s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: var(--lm-shadow-lg);
    background: var(--lm-surface);
  }

  .assistant-sidebar.mobile-open {
    transform: translateX(0);
  }

  .mobile-toggle-btn {
    display: inline-flex;
  }

  .mobile-backdrop {
    display: block;
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.35);
    z-index: 15;
  }

  .chat-scroll-area {
    padding: 16px 14px;
  }

  .chat-input-dock {
    padding: 10px 14px 12px;
  }

  .rec-grid {
    grid-template-columns: 1fr;
  }

  .bubble-cell {
    max-width: 90%;
  }
}
</style>
