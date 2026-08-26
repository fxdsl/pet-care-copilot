<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ text?: string | null; query: string }>()

/** 返回纯文本片段，由 Vue 自身转义渲染，避免搜索内容借高亮注入 HTML。 */
const parts = computed(() => {
  const text = props.text ?? ''
  const query = props.query.trim()
  if (!query) return [{ text, matched: false }]
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const matcher = new RegExp(`(${escaped})`, 'ig')
  return text.split(matcher).filter(Boolean).map((part) => ({
    text: part,
    matched: part.toLocaleLowerCase() === query.toLocaleLowerCase(),
  }))
})
</script>

<template>
  <template v-for="(part, index) in parts" :key="`${index}-${part.text}`">
    <mark v-if="part.matched">{{ part.text }}</mark><template v-else>{{ part.text }}</template>
  </template>
</template>

<style scoped>
mark { padding: 0 2px; border-radius: 4px; color: #9f4826; background: #ffeadb; }
</style>
