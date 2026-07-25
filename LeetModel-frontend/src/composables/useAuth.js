import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { logout as logoutApi } from '@/api/user'
import { ElMessage } from 'element-plus'

export function useAuth() {
  const router = useRouter()
  const userStore = useUserStore()

  async function handleLogout() {
    try {
      await logoutApi()
      ElMessage.success('退出成功')
    } catch (err) {
      console.log('退出接口异常', err)
    } finally {
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      userStore.$reset()
      router.push('/login')
    }
  }

  return { handleLogout }
}
