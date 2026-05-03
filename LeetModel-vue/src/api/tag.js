function notify() {
  console.log('标签功能正在开发中')
}

export function getTagsByCategory() {
  notify()
  return Promise.resolve([])
}

export function createTag() {
  notify()
  return Promise.resolve({ code: 20000 })
}

export function updateTag() {
  notify()
  return Promise.resolve({ code: 20000 })
}

export function deleteTag() {
  notify()
  return Promise.resolve({ code: 20000 })
}
