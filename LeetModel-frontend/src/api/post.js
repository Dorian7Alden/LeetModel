function notify() {
  console.log('帖子功能正在开发中')
}

export function getPostList() {
  notify()
  return Promise.resolve({ records: [], total: 0 })
}
