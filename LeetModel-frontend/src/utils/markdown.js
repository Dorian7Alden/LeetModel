import DOMPurify from 'dompurify'
import { marked } from 'marked'
import katex from 'katex'
import markedKatex from 'marked-katex-extension'
import hljs from 'highlight.js'
import 'katex/dist/katex.min.css'
import 'highlight.js/styles/github.css'

// 配置 marked-katex 支持数学公式渲染（throwOnError: false 确保公式容错）
marked.use(
  markedKatex({
    katex,
    throwOnError: false,
    nonStandard: true,
  })
)

// 定制代码块 renderer，增加语言标识与一键复制功能
marked.use({
  renderer: {
    code({ text, lang }) {
      const validLang = lang && hljs.getLanguage(lang) ? lang : null
      const highlighted = validLang
        ? hljs.highlight(text, { language: validLang }).value
        : hljs.highlightAuto(text).value
      const langLabel = validLang || lang || 'text'
      const encoded = encodeURIComponent(text)
      return `<div class="code-block-wrapper"><div class="code-block-header"><span class="code-lang">${langLabel}</span><button type="button" class="copy-code-btn" data-code="${encoded}" title="复制代码">复制</button></div><pre><code class="hljs ${
        validLang ? 'language-' + validLang : ''
      }">${highlighted}</code></pre></div>`
    },
  },
})

// 全局绑定代码复制事件委托（只绑定一次）
if (typeof window !== 'undefined' && !window.__LEETMODEL_COPY_CODE_BOUND) {
  window.__LEETMODEL_COPY_CODE_BOUND = true
  document.addEventListener('click', async (e) => {
    const target = e.target.closest('.copy-code-btn')
    if (!target) return
    const encoded = target.getAttribute('data-code')
    if (!encoded) return
    try {
      const text = decodeURIComponent(encoded)
      await navigator.clipboard.writeText(text)
      const originalText = target.innerText
      target.innerText = '已复制!'
      target.classList.add('copied')
      setTimeout(() => {
        target.innerText = originalText
        target.classList.remove('copied')
      }, 1500)
    } catch {
      // 静默处理异常
    }
  })
}

/**
 * 预处理 LaTeX 公式，兼容 \[ ... \] 与 \( ... \) 格式
 */
function preprocessLatex(text) {
  if (!text) return ''
  let res = text.replace(/\\\[([\s\S]*?)\\\]/g, (_, eq) => `$$\n${eq.trim()}\n$$`)
  res = res.replace(/\\\(([\s\S]*?)\\\)/g, (_, eq) => `$${eq.trim()}$`)
  return res
}

/**
 * 增强后处理：
 * 1. 自动为所有图片注入 referrerpolicy="no-referrer" 与懒加载，防止 Gitee/第三方图床 403
 * 2. 自动为表格包裹滚动容器，防止超宽排版破坏页面
 */
function prepareHtmlElements(html) {
  if (typeof document === 'undefined') return html
  const container = document.createElement('div')
  container.innerHTML = html

  container.querySelectorAll('img').forEach((image) => {
    image.setAttribute('referrerpolicy', 'no-referrer')
    image.setAttribute('loading', 'lazy')
    image.setAttribute('decoding', 'async')
  })

  container.querySelectorAll('table').forEach((table) => {
    if (table.parentElement && !table.parentElement.classList.contains('markdown-table-wrapper')) {
      const wrapper = document.createElement('div')
      wrapper.className = 'markdown-table-wrapper'
      table.parentNode.insertBefore(wrapper, table)
      wrapper.appendChild(table)
    }
  })

  return container.innerHTML
}

export function renderSafeMarkdown(value) {
  if (!value) return ''
  const preprocessed = preprocessLatex(value)
  const rawHtml = marked.parse(preprocessed, { async: false, breaks: true, gfm: true })
  const sanitized = DOMPurify.sanitize(rawHtml, {
    USE_PROFILES: { html: true, mathMl: true, svg: true },
    ADD_TAGS: [
      'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption', 'colgroup', 'col',
      'div', 'span', 'p', 'center', 'img', 'pre', 'code', 'button', 'a', 'b', 'strong',
      'i', 'em', 'del', 's', 'sub', 'sup', 'blockquote', 'ul', 'ol', 'li', 'h1', 'h2',
      'h3', 'h4', 'h5', 'h6', 'hr', 'br', 'figure', 'figcaption',
      'semantics', 'annotation', 'annotation-xml', 'math', 'mrow', 'mi', 'mo', 'mn',
      'msup', 'msub', 'msubsup', 'mfrac', 'munder', 'mover', 'munderover', 'mtable',
      'mtr', 'mtd', 'text', 'mtext', 'mspace', 'msqrt', 'mroot', 'svg', 'path',
    ],
    ADD_ATTR: [
      'style', 'class', 'id', 'align', 'valign', 'border', 'cellspacing', 'cellpadding',
      'colspan', 'rowspan', 'width', 'height', 'src', 'alt', 'title', 'href', 'target',
      'rel', 'referrerpolicy', 'loading', 'decoding', 'data-code', 'mathvariant',
      'display', 'xmlns', 'aria-hidden', 'role',
    ],
  })
  return prepareHtmlElements(sanitized)
}

export function markdownToPlainText(value) {
  if (!value) return ''
  const container = document.createElement('div')
  container.innerHTML = renderSafeMarkdown(value)
  return (container.textContent || '').replace(/\s+/g, ' ').trim()
}
