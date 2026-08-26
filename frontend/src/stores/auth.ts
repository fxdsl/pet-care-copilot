import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
  type CurrentUser,
} from '../api/auth'
import { ApiRequestError } from '../api/http'
import { clearAuthSession, loadAuthSession } from '../api/session'

/** 全站唯一登录状态；业务列表和表单状态仍留在各自页面。 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | undefined>(loadAuthSession()?.user)
  const bootstrapped = ref(false)
  const isAuthenticated = computed(() => Boolean(user.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** 首次路由进入时向 Java 复核用户，不能长期只相信 localStorage。 */
  async function bootstrap(): Promise<void> {
    if (bootstrapped.value) return
    bootstrapped.value = true
    if (!loadAuthSession()) {
      user.value = undefined
      return
    }
    try {
      user.value = await getCurrentUser()
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) {
        clearAuthSession()
        user.value = undefined
        return
      }
      throw error
    }
  }

  async function login(username: string, password: string): Promise<void> {
    user.value = (await loginRequest({ username, password })).user
  }

  async function register(username: string, password: string, displayName?: string): Promise<void> {
    user.value = (await registerRequest({ username, password, displayName })).user
  }

  async function logout(): Promise<void> {
    try {
      await logoutRequest()
    } finally {
      user.value = undefined
    }
  }

  function updateUser(updated: CurrentUser): void {
    user.value = updated
  }

  return { user, bootstrapped, isAuthenticated, isAdmin, bootstrap, login, register, logout, updateUser }
})
