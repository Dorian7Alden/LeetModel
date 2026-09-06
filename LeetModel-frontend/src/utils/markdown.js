import DOMPurify from 'dompurify'
import { marked } from 'marked'
import katex from 'katex'
import markedKatex from 'marked-katex-extension'
import hljs from 'highlight.js'
import 'katex/dist/katex.min.css'
import 'highlight.js/styles/github.css'

// 配置 marked-katex 支持数学公式渲染（throwOnError: false 确保公式容错）
marked.use(markedKatex({
  katex,
  throwOnError: false,
  nonStandard: true
}))

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
      return `<div class="code-block-wrapper"><div class="code-block-header"><span class="code-lang">${langLabel}</span><button type="button" class="copy-code-btn" data-code="${encoded}" title="复制代码">复制</button></div><pre><code class="hljs ${validLang ? 'language-' + validLang : ''}">${highlighted}</code></pre></div>`
    }
  }
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

export function renderSafeMarkdown(value) {
  if (!value) return ''
  const rawHtml = marked.parse(value, { async: false, breaks: true, gfm: true })
  return DOMPurify.sanitize(rawHtml, {
    USE_PROFILES: { html: true, mathMl: true, svg: true },
    ADD_TAGS: ['semantics', 'annotation', 'annotation-xml', 'button'],
    ADD_ATTR: ['mathvariant', 'encoding', 'data-code']
  })
}

export function markdownToPlainText(value) {
  if (!value) return ''
  const container = document.createElement('div')
  container.innerHTML = renderSafeMarkdown(value)
  return (container.textContent || '').replace(/\s+/g, ' ').trim()
}
