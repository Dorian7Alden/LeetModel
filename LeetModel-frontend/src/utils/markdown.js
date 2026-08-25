import DOMPurify from 'dompurify'
import { marked } from 'marked'

export function renderSafeMarkdown(value) {
  if (!value) return ''
  return DOMPurify.sanitize(marked.parse(value, { async: false, breaks: true, gfm: true }))
}
