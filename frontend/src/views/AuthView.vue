<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppBrand from '../components/AppBrand.vue'
import { ApiRequestError } from '../api/http'
import { defaultPath } from '../router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const busy = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '', displayName: '' })

/** 登录/注册共用一个安全入口，成功后按后端角色进入不同门户。 */
async function submit(): Promise<void> {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    if (mode.value === 'login') await auth.login(form.username, form.password)
    else await auth.register(form.username, form.password, form.displayName || undefined)
    const requested = typeof route.query.redirect === 'string' ? route.query.redirect : undefined
    await router.replace(requested ?? defaultPath(auth.user?.role ?? 'USER'))
  } catch (cause) {
    error.value = cause instanceof ApiRequestError ? cause.message : '登录失败，请稍后重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-story">
      <AppBrand />
      <div class="story-copy">
        <p class="eyebrow">A BETTER LIFE WITH PETS</p>
        <h1>记录相伴，<br>分享每一种养宠生活。</h1>
        <p>可信知识、真实社区与智能问答，在同一个属于宠物爱好者的空间里相遇。</p>
      </div>
      <div class="story-orbit" aria-hidden="true"><span>🐈</span><span>🐕</span><span>🐾</span></div>
    </section>

    <section class="auth-panel">
      <div class="auth-card">
        <p class="eyebrow">WELCOME HOME</p>
        <h2>{{ mode === 'login' ? '欢迎回来' : '加入宠里个宠' }}</h2>
        <p class="muted">{{ mode === 'login' ? '继续你的养宠记录与交流。' : '创建普通用户账号，管理员角色由后台授予。' }}</p>
        <form @submit.prevent="submit">
          <label v-if="mode === 'register'">昵称<input v-model="form.displayName" maxlength="100" autocomplete="name" placeholder="大家如何称呼你"></label>
          <label>用户名<input v-model="form.username" minlength="3" maxlength="50" autocomplete="username" required placeholder="3～50 个字符"></label>
          <label>密码<input v-model="form.password" type="password" minlength="8" maxlength="100" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required placeholder="至少 8 个字符"></label>
          <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
          <button class="button button-primary button-wide" type="submit" :disabled="busy">{{ busy ? '正在处理…' : mode === 'login' ? '登录' : '注册并登录' }}</button>
        </form>
        <button class="auth-switch" @click="mode = mode === 'login' ? 'register' : 'login'; error = ''">
          {{ mode === 'login' ? '还没有账号？立即注册' : '已经有账号？返回登录' }}
        </button>
      </div>
    </section>
  </main>
</template>
