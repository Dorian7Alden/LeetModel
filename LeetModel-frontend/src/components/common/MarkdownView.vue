<template>
  <div class="markdown-view-container" :class="{ 'is-compact': compact }">
    <article
      v-if="renderedHtml"
      class="markdown-body markdown-rendered-content"
      v-html="renderedHtml"
    />
    <slot v-else name="empty">
      <div class="markdown-empty">{{ emptyText || '暂无内容' }}</div>
    </slot>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { renderSafeMarkdown } from '@/utils/markdown';

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
  compact: {
    type: Boolean,
    default: false,
  },
  streaming: {
    type: Boolean,
    default: false,
  },
  emptyText: {
    type: String,
    default: '暂无内容',
  },
});

const renderedHtml = computed(() => {
  const html = renderSafeMarkdown(props.content);
  return props.streaming ? appendStreamingCursor(html) : html;
});

function appendStreamingCursor(html) {
  if (!html || typeof document === 'undefined') return html;
  const container = document.createElement('div');
  container.innerHTML = html;

  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  let currentNode = walker.nextNode();
  let lastVisibleTextNode = null;
  while (currentNode) {
    const hiddenContainer = currentNode.parentElement?.closest(
      'annotation, .code-block-header, button'
    );
    if (currentNode.textContent?.trim() && !hiddenContainer) {
      lastVisibleTextNode = currentNode;
    }
    currentNode = walker.nextNode();
  }

  const cursor = document.createElement('span');
  cursor.className = 'markdown-stream-cursor';
  cursor.setAttribute('aria-hidden', 'true');
  if (lastVisibleTextNode?.parentNode) {
    lastVisibleTextNode.parentNode.insertBefore(cursor, lastVisibleTextNode.nextSibling);
  } else {
    container.appendChild(cursor);
  }
  return container.innerHTML;
}
</script>

<style>
/* Markdown 统一渲染组件基础与增强样式 */
.markdown-view-container {
  width: 100%;
  min-width: 0;
}

.markdown-view-container .markdown-rendered-content {
  background: transparent;
  font-family: var(--lm-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif);
  font-size: 14px;
  line-height: 1.75;
  color: #1f2937;
  overflow-wrap: break-word;
  word-break: break-word;
}

.markdown-view-container.is-compact .markdown-rendered-content {
  font-size: 13px;
  line-height: 1.6;
}

/* 强调文字与标题 */
.markdown-view-container .markdown-rendered-content strong,
.markdown-view-container .markdown-rendered-content b {
  font-weight: 750;
  color: #111827;
}

.markdown-view-container .markdown-rendered-content h1,
.markdown-view-container .markdown-rendered-content h2,
.markdown-view-container .markdown-rendered-content h3 {
  color: #111827;
  font-weight: 750;
  margin-top: 1.4em;
  margin-bottom: 0.6em;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 0.3em;
}

/* 段落与文本排版 */
.markdown-view-container .markdown-rendered-content p {
  margin-top: 0.8em;
  margin-bottom: 0.8em;
  line-height: 1.75;
}

/* 有序列表与无序列表统一样式与规范缩进 */
.markdown-view-container .markdown-rendered-content ol,
.markdown-view-container .markdown-rendered-content ul {
  padding-left: 1.85em !important;
  margin: 0.8em 0 1.2em !important;
  box-sizing: border-box;
}

.markdown-view-container .markdown-rendered-content ol {
  list-style-type: decimal !important;
}

.markdown-view-container .markdown-rendered-content ul {
  list-style-type: disc !important;
}

/* 嵌套列表缩进 */
.markdown-view-container .markdown-rendered-content ol ol,
.markdown-view-container .markdown-rendered-content ul ol {
  list-style-type: lower-alpha !important;
  margin: 0.4em 0 0.6em !important;
  padding-left: 1.5em !important;
}

.markdown-view-container .markdown-rendered-content ul ul,
.markdown-view-container .markdown-rendered-content ol ul {
  list-style-type: circle !important;
  margin: 0.4em 0 0.6em !important;
  padding-left: 1.5em !important;
}

/* 列表项垂直间距、序号与内容间距 */
.markdown-view-container .markdown-rendered-content li {
  margin-bottom: 0.55em !important;
  line-height: 1.75 !important;
  padding-left: 0.25em !important;
  color: #24292f;
}

.markdown-view-container .markdown-rendered-content li:last-child {
  margin-bottom: 0 !important;
}

/* 序号与符号微调：采用 GitHub Markdown 风格沉稳中性色调 */
.markdown-view-container .markdown-rendered-content ol > li::marker {
  color: #57606a;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.markdown-view-container .markdown-rendered-content ul > li::marker {
  color: #57606a;
}

/* 数学公式渲染 */
.markdown-view-container .katex-display {
  margin: 1.2em 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 8px 0;
  text-align: center;
}

.markdown-view-container .katex {
  font-size: 1.08em;
  text-rendering: auto;
}

/* 表格渲染与响应式容器 */
.markdown-view-container .markdown-table-wrapper {
  width: 100%;
  overflow-x: auto;
  margin: 1.2em 0;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.markdown-view-container .markdown-rendered-content table {
  width: auto;
  min-width: 60%;
  margin: 0 auto;
  border-collapse: collapse;
  border-spacing: 0;
  font-size: 13.5px;
}

.markdown-view-container .markdown-rendered-content th,
.markdown-view-container .markdown-rendered-content td {
  padding: 8px 14px;
  border: 1px solid #e2e8f0;
  vertical-align: middle;
}

.markdown-view-container .markdown-rendered-content th {
  background: #f8fafc;
  font-weight: 700;
  color: #1e293b;
}

.markdown-view-container .markdown-rendered-content tr:nth-child(even) td {
  background: #fcfdfe;
}

.markdown-view-container .markdown-rendered-content tr:hover td {
  background: #f1f5f9;
}

/* 图片居中与最大宽度保护 */
.markdown-view-container .markdown-rendered-content img {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  margin: 0.8em auto;
  display: inline-block;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.markdown-view-container .markdown-rendered-content div[align="center"],
.markdown-view-container .markdown-rendered-content p[align="center"] {
  text-align: center;
  margin: 1em 0;
}

/* 引用块 */
.markdown-view-container .markdown-rendered-content blockquote {
  margin: 1.2em 0;
  padding: 10px 16px;
  color: #475569;
  background: #f8fafc;
  border-left: 4px solid #3b82f6;
  border-radius: 0 6px 6px 0;
}

/* 代码块增强 */
.markdown-view-container .code-block-wrapper {
  margin: 1.2em 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #f8fafc;
}

.markdown-view-container .code-block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
  font-size: 11.5px;
}

.markdown-view-container .code-lang {
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.markdown-view-container .copy-code-btn {
  padding: 2px 8px;
  font-size: 11px;
  color: #475569;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.markdown-view-container .copy-code-btn:hover {
  color: #1d4ed8;
  border-color: #93c5fd;
  background: #eff6ff;
}

.markdown-view-container .copy-code-btn.copied {
  color: #15803d;
  border-color: #86efac;
  background: #f0fdf4;
}

.markdown-view-container .markdown-rendered-content pre {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
  background: #f8fafc;
}

.markdown-view-container .markdown-rendered-content code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12.5px;
}

.markdown-view-container .markdown-empty {
  padding: 36px 0;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
}

.markdown-view-container .markdown-stream-cursor {
  display: inline-block;
  inline-size: 1px;
  block-size: 1em;
  margin-inline-start: 0.12em;
  vertical-align: -0.12em;
  background: currentColor;
  animation: markdown-cursor-blink 0.8s steps(1, end) infinite;
}

@keyframes markdown-cursor-blink {
  0%, 45% { opacity: 1; }
  46%, 100% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .markdown-view-container .markdown-stream-cursor {
    animation: none;
  }
}
</style>
