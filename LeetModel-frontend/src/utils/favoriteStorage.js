import { ElMessage } from 'element-plus'
import {
  addProblemFavorite,
  getFavoriteRecords as fetchFavoriteRecordsApi,
  removeProblemFavorite
} from '@/api/problem'

const STORAGE_KEY = 'lm_fav_problems'

const isUserLoggedIn = () => {
  if (typeof window === 'undefined') return false
  return Boolean(localStorage.getItem('token'))
}

function normalizeRecord(item, fallbackTimestamp = 0) {
  if (item == null) return null
  if (typeof item === 'object') {
    const id = String(item.problemId ?? item.id ?? '').trim()
    if (!id) return null
    const favoritedAt = Number(item.favoritedAt) || fallbackTimestamp || Date.now()
    return { id, favoritedAt }
  }
  const id = String(item).trim()
  if (!id) return null
  return { id, favoritedAt: fallbackTimestamp || Date.now() }
}

export function getFavoriteRecords() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .map((item, index) => normalizeRecord(item, index + 1))
      .filter(Boolean)
      .sort((a, b) => a.favoritedAt - b.favoritedAt)
  } catch {
    return []
  }
}

export function getFavoriteIds() {
  return getFavoriteRecords().map((item) => item.id)
}

export function isProblemFavorited(problemId) {
  if (!problemId) return false
  const sId = String(problemId).trim()
  return getFavoriteIds().includes(sId)
}

export function getProblemFavoritedAt(problemId) {
  if (!problemId) return null
  const sId = String(problemId).trim()
  const record = getFavoriteRecords().find((item) => item.id === sId)
  return record ? record.favoritedAt : null
}

export function getFavoriteCount() {
  return getFavoriteRecords().length
}

function notifyFavChange(problemId, isFav, records) {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(
      new CustomEvent('lm-fav-change', {
        detail: {
          problemId,
          isFavorited: isFav,
          count: records.length,
          records
        }
      })
    )
  }
}

/**
 * 从后端同步当前用户的收藏记录
 */
export async function syncFavoritesWithServer() {
  if (!isUserLoggedIn()) {
    return getFavoriteRecords()
  }

  try {
    const response = await fetchFavoriteRecordsApi()
    if (response?.data && Array.isArray(response.data)) {
      const records = response.data
        .map((item, index) => normalizeRecord(item, index + 1))
        .filter(Boolean)
        .sort((a, b) => a.favoritedAt - b.favoritedAt)

      localStorage.setItem(STORAGE_KEY, JSON.stringify(records))
      notifyFavChange('', false, records)
      return records
    }
    return getFavoriteRecords()
  } catch {
    return getFavoriteRecords()
  }
}

/**
 * 切换收藏状态（连接后端 API）
 */
export async function toggleProblemFavorite(problemId) {
  if (!problemId) return { isFavorited: false, count: 0, records: [] }

  if (!isUserLoggedIn()) {
    ElMessage.warning('请先登录以收藏题目')
    return { isFavorited: false, count: getFavoriteCount(), requireLogin: true }
  }

  const sId = String(problemId).trim()
  const records = getFavoriteRecords()
  const existingIndex = records.findIndex((item) => item.id === sId)
  const isCurrentlyFav = existingIndex >= 0
  const targetFavState = !isCurrentlyFav
  const originalRecords = [...records]

  if (isCurrentlyFav) {
    records.splice(existingIndex, 1)
  } else {
    records.push({
      id: sId,
      favoritedAt: Date.now()
    })
  }

  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(records))
  } catch {
    // 忽略异常
  }
  notifyFavChange(sId, targetFavState, records)

  try {
    if (targetFavState) {
      await addProblemFavorite(sId)
    } else {
      await removeProblemFavorite(sId)
    }
  } catch (err) {
    console.warn('后端收藏状态同步失败:', err)
  }

  return { isFavorited: targetFavState, count: records.length, records }
}
