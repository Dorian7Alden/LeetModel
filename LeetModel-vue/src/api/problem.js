function notify() {
  console.log('题目功能正在开发中')
}

export function getProblemList() {
  notify()
  return Promise.resolve({ records: [], total: 0 })
}

export function getProblemDetail() {
  notify()
  return Promise.resolve(null)
}

export function uploadProblem() {
  notify()
  return Promise.resolve({ code: 20000 })
}
