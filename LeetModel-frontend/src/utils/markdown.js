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
/**
 * 自动修补流式输出中可能未闭合的代码块 (```) 与行内代码 (`)
 */
/**
 * 自动修补流式输出中可能未闭合的语法（代码块 ```、行内代码 `、粗体 **、斜体 * 等）
 * 防止 marked 和 KaTeX 在打字机增量阶段解析崩溃或整块延迟展示
 */
function fixUnclosedMarkdown(markdown) {
  if (!markdown) return '';
  let res = markdown;

  // 1. 修复代码块 ```
  const codeBlockCount = (res.match(/```/g) || []).length;
  if (codeBlockCount % 2 !== 0) {
    res += "\n```";
    return res;
  }

  // 2. 修复未闭合的数学公式块 $$
  const mathBlockCount = (res.match(/\$\$/g) || []).length;
  if (mathBlockCount % 2 !== 0) {
    res += "$$";
  } else {
    // 3. 修复行内数学公式 $（排除转义 \$）
    const inlineMathCount = (res.match(/(?<!\\)\$/g) || []).length;
    if (inlineMathCount % 2 !== 0) {
      res += "$";
    }
  }

  // 4. 修复行内代码 `（排除已在 ``` 中的情况）
  const inlineCodeCount = (res.match(/(?<!`)`(?!`)/g) || []).length;
  if (inlineCodeCount % 2 !== 0) {
    res += "`";
  }

  // 5. 修复未闭合的粗体 **
  const boldCount = (res.match(/\*\*/g) || []).length;
  if (boldCount % 2 !== 0) {
    res += "**";
  }

  return res;
}


function preprocessLatex(text) {
  if (!text) return ''
  let res = text.replace(/\\\[([\s\S]*?)\\\]/g, (_, eq) => `$$\n${eq.trim()}\n$$`)
  res = res.replace(/\\\(([\s\S]*?)\\\)/g, (_, eq) => `$${eq.trim()}$`)
  return res
}

/**
 * 渲染原始 HTML 文本节点中的行内公式。
 *
 * marked-katex 只处理 Markdown 语法，不会进入解析器直接输出的 HTML 表格单元格。
 *
 * @param {string} html marked 生成的 HTML
 * @returns {string} 已补齐表格等原始 HTML 内联公式的 HTML
 */
function renderInlineMathInRawHtml(html) {
  if (typeof document === 'undefined' || !html?.includes('$')) return html
  const container = document.createElement('div')
  container.innerHTML = html
  const textNodes = []
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT)
  while (walker.nextNode()) textNodes.push(walker.currentNode)

  for (const node of textNodes) {
    const parent = node.parentElement
    if (!parent || parent.closest('code, pre, math, annotation, annotation-xml, script, style, .katex')) {
      continue
    }
    const value = node.textContent || ''
    if (!value.includes('$')) continue

    const fragment = document.createDocumentFragment()
    let lastIndex = 0
    let matched = false
    value.replace(/(?<!\\)\$([^$\n]+?)\$/g, (match, latex, offset) => {
      const formula = latex.trim()
      if (!formula) return match
      fragment.append(document.createTextNode(value.slice(lastIndex, offset)))
      const formulaNode = document.createElement('span')
      formulaNode.className = 'raw-html-math'
      formulaNode.innerHTML = katex.renderToString(formula, {
        throwOnError: false,
        displayMode: false,
        output: 'htmlAndMathml',
      })
      fragment.append(formulaNode)
      lastIndex = offset + match.length
      matched = true
      return match
    })
    if (!matched) continue
    fragment.append(document.createTextNode(value.slice(lastIndex)))
    node.parentNode?.replaceChild(fragment, node)
  }

  return container.innerHTML
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
  const fixed = fixUnclosedMarkdown(value)
  const preprocessed = preprocessLatex(fixed)
  const rawHtml = marked.parse(preprocessed, { async: false, breaks: true, gfm: true })
  const formulaEnhanced = renderInlineMathInRawHtml(rawHtml)
  const sanitized = DOMPurify.sanitize(formulaEnhanced, {
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
