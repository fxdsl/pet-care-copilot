<script setup lang="ts">
defineProps<{ type: 'loading' | 'empty' | 'error'; message: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="page-state" :class="`state-${type}`" role="status">
    <span aria-hidden="true">{{ type === 'loading' ? '•••' : type === 'empty' ? '🐾' : '!' }}</span>
    <p>{{ message }}</p>
    <button v-if="type === 'error'" class="button button-secondary" @click="$emit('retry')">重新加载</button>
  </div>
</template>
