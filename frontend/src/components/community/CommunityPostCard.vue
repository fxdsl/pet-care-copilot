<script setup lang="ts">
import { ChatLineRound, CollectionTag, Location, Star, View } from '@element-plus/icons-vue'

import type { CommunityPost } from '../../api/community'
import CommunityMediaGallery from './CommunityMediaGallery.vue'

defineProps<{ post: CommunityPost; currentUserId?: string }>()
defineEmits<{
  open: [post: CommunityPost]
  like: [post: CommunityPost]
  favorite: [post: CommunityPost]
  follow: [post: CommunityPost]
  report: [post: CommunityPost]
}>()

const formatDate = (value: string | null) => value
  ? new Intl.DateTimeFormat('zh-CN', {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    }).format(new Date(value))
  : ''
</script>

<template>
  <article class="feed-post-card">
    <header class="feed-post-header">
      <RouterLink :to="`/app/users/${post.authorId}`" class="avatar feed-post-avatar" :aria-label="`打开 ${post.authorDisplayName} 的主页`">{{ post.authorDisplayName.slice(0, 1) }}</RouterLink>
      <div class="feed-post-author"><RouterLink :to="`/app/users/${post.authorId}`"><strong>{{ post.authorDisplayName }}</strong></RouterLink><p><span>@{{ post.authorUsername }}</span><span>{{ formatDate(post.publishedAt) }}</span><span v-if="post.region"><el-icon><Location /></el-icon>{{ post.region }}</span></p></div>
      <button v-if="post.authorId !== currentUserId" class="follow-button" :class="{ active: post.viewerFollowsAuthor }" @click="$emit('follow', post)">{{ post.viewerFollowsAuthor ? '已关注' : '＋ 关注' }}</button>
      <button class="feed-post-more" aria-label="举报这条动态" title="举报" @click="$emit('report', post)">•••</button>
    </header>

    <button class="feed-post-copy" type="button" @click="$emit('open', post)">
      <span class="feed-post-topic"># {{ post.topicName || '萌宠日常' }}</span>
      <h2>{{ post.title }}</h2>
      <p>{{ post.content }}</p>
    </button>

    <CommunityMediaGallery v-if="post.media.length" :media="post.media" :alt="post.title" compact :limit="1" />

    <div class="feed-post-context"><span v-if="post.petName">🐾 出镜宠物：{{ post.petName }}</span><span v-if="post.media.length">▧ {{ post.media.length }} 个媒体</span></div>

    <footer class="feed-post-actions">
      <span><el-icon><View /></el-icon><b>{{ post.viewCount }}</b><small>浏览</small></span>
      <button @click="$emit('open', post)"><el-icon><ChatLineRound /></el-icon><b>{{ post.commentCount }}</b><small>评论</small></button>
      <button :class="{ active: post.viewerLiked }" @click="$emit('like', post)"><el-icon><Star /></el-icon><b>{{ post.likeCount }}</b><small>{{ post.viewerLiked ? '已赞' : '点赞' }}</small></button>
      <button :class="{ active: post.viewerFavorited }" @click="$emit('favorite', post)"><el-icon><CollectionTag /></el-icon><b>{{ post.favoriteCount }}</b><small>{{ post.viewerFavorited ? '已收藏' : '收藏' }}</small></button>
    </footer>
  </article>
</template>
