<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
import { defaultPath } from '../router'

const auth = useAuthStore()
</script>

<template>
  <main class="not-found-page">
    <span>404</span><h1>这只小爪子没有找到页面</h1>
    <RouterLink class="button button-primary" :to="defaultPath(auth.user?.role ?? 'USER')">返回首页</RouterLink>
  </main>
</template>
