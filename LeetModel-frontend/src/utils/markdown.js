import DOMPurify from 'dompurify'
import { marked } from 'marked'

export function renderSafeMarkdown(value) {
  if (!value) return ''
  return DOMPurify.sanitize(marked.parse(value, { async: false, breaks: true, gfm: true }))
}

export function markdownToPlainText(value) {
  if (!value) return ''
  const container = document.createElement('div')
  container.innerHTML = renderSafeMarkdown(value)
  return (container.textContent || '').replace(/\s+/g, ' ').trim()
}
