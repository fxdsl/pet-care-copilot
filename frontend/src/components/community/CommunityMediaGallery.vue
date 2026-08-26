<script setup lang="ts">
import { computed, nextTick, reactive, watch } from 'vue'

import { getCommunityMediaUrl, type CommunityMedia } from '../../api/community'

const props = withDefaults(defineProps<{
  media: CommunityMedia[]
  alt: string
  compact?: boolean
  limit?: number
}>(), {
  compact: false,
  limit: 6,
})

const urls = reactive<Record<string, string>>({})
const loading = reactive<Record<string, boolean>>({})
const errors = reactive<Record<string, boolean>>({})
const visibleMedia = computed(() => props.media
  .filter((item) => item.status === 'CONFIRMED')
  .slice(0, props.limit))
const previewImages = computed(() => props.media
  .filter((item) => item.status === 'CONFIRMED' && item.mediaType === 'IMAGE')
  .map((item) => urls[item.id])
  .filter((url): url is string => Boolean(url)))

/**
 * 浏览器只取得短期下载地址；界面永远不展示对象 Key 或原始文件名。
 * 单个地址失败时保留媒体占位，不影响同一帖子里的其他图片。
 */
async function loadUrl(item: CommunityMedia): Promise<void> {
  if (item.status !== 'CONFIRMED' || loading[item.id]) return

  loading[item.id] = true
  errors[item.id] = false
  try {
    urls[item.id] = (await getCommunityMediaUrl(item.id)).url
  } catch {
    errors[item.id] = true
  } finally {
    loading[item.id] = false
  }
}

async function loadUrls(media: CommunityMedia[]): Promise<void> {
  await Promise.allSettled(media
    .filter((item) => item.status === 'CONFIRMED' && !urls[item.id])
    .map(loadUrl))
}

/**
 * 短期签名地址失效或 MinIO 暂时不可达时，允许用户重新获取地址。
 */
async function retry(item: CommunityMedia): Promise<void> {
  delete urls[item.id]
  errors[item.id] = false
  await nextTick()
  await loadUrl(item)
}

watch(
  () => props.media.map((item) => `${item.id}:${item.status}`).join(','),
  () => { void loadUrls(props.media) },
  { immediate: true },
)
</script>

<template>
  <div v-if="visibleMedia.length" class="community-media-gallery" :class="{ compact }" :style="{ '--media-columns': String(Math.min(3, visibleMedia.length)) }">
    <template v-for="(item, index) in visibleMedia" :key="item.id">
      <el-image
        v-if="item.mediaType === 'IMAGE' && urls[item.id]"
        class="community-media-item"
        :src="urls[item.id]"
        :alt="`${alt}，图片 ${index + 1}`"
        :preview-src-list="previewImages"
        :initial-index="Math.max(0, previewImages.indexOf(urls[item.id]!))"
        fit="cover"
        preview-teleported
        hide-on-click-modal
        @error="errors[item.id] = true"
      >
        <template #error>
          <button class="media-error" type="button" @click.stop="retry(item)">
            <span>图片加载失败</span>
            <small>点击重新加载</small>
          </button>
        </template>
      </el-image>
      <video
        v-else-if="item.mediaType === 'VIDEO' && urls[item.id]"
        class="community-media-item"
        :src="urls[item.id]"
        controls
        preload="metadata"
        playsinline
        :aria-label="`${alt}，视频 ${index + 1}`"
      />
      <button
        v-else-if="errors[item.id]"
        class="community-media-item media-error"
        type="button"
        @click="retry(item)"
      >
        <span>媒体加载失败</span><small>点击重新加载</small>
      </button>
      <div v-else class="community-media-item media-loading" aria-live="polite">
        <span>···</span><small>{{ item.mediaType === 'IMAGE' ? '图片加载中' : '视频加载中' }}</small>
      </div>
    </template>
    <span v-if="media.length > visibleMedia.length" class="media-count-badge">还有 {{ media.length - visibleMedia.length }} 项</span>
  </div>
</template>
