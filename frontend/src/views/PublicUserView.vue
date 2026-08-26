<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import {
  getPublicUserProfile,
  listCommunityPosts,
  listUserFollowers,
  listUserFollowing,
  setUserFollow,
  type CommunityPost,
  type PublicUserProfile,
  type PublicUserSummary,
} from '../api/community'
import { ApiRequestError } from '../api/http'
import CommunityMediaGallery from '../components/community/CommunityMediaGallery.vue'
import PageState from '../components/PageState.vue'

type ProfileTab = 'POSTS' | 'FOLLOWERS' | 'FOLLOWING'

const route = useRoute()
const router = useRouter()
const profile = ref<PublicUserProfile>()
const posts = ref<CommunityPost[]>([])
const people = ref<PublicUserSummary[]>([])
const tab = ref<ProfileTab>('POSTS')
const loading = ref(true)
const contentLoading = ref(false)
const error = ref('')
const userId = computed(() => String(route.params.userId || ''))

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  tab.value = 'POSTS'
  try {
    profile.value = await getPublicUserProfile(userId.value)
    posts.value = (await listCommunityPosts({ authorId: userId.value, size: 30 })).items
  } catch (cause) { error.value = readable(cause) }
  finally { loading.value = false }
}

async function chooseTab(next: ProfileTab): Promise<void> {
  tab.value = next
  if (next === 'POSTS') return
  contentLoading.value = true
  try {
    people.value = (next === 'FOLLOWERS'
      ? await listUserFollowers(userId.value)
      : await listUserFollowing(userId.value)).items
  } catch (cause) { ElMessage.error(readable(cause)) }
  finally { contentLoading.value = false }
}

async function toggleProfileFollow(): Promise<void> {
  if (!profile.value || profile.value.ownProfile) return
  try {
    const result = await setUserFollow(profile.value.id, !profile.value.viewerFollowing)
    profile.value.viewerFollowing = result.following
    profile.value.followerCount = result.followerCount
  } catch (cause) { ElMessage.error(readable(cause)) }
}

async function togglePersonFollow(person: PublicUserSummary): Promise<void> {
  try {
    person.viewerFollowing = (await setUserFollow(person.id, !person.viewerFollowing)).following
  } catch (cause) { ElMessage.error(readable(cause)) }
}

function messageUser(): void {
  void router.push({ path: '/app/messages', query: { user: userId.value } })
}

function readable(cause: unknown): string {
  return cause instanceof ApiRequestError ? cause.message : '公开主页加载失败'
}

watch(userId, load)
onMounted(load)
</script>

<template>
  <section class="public-profile-page">
    <PageState v-if="loading" type="loading" message="正在打开宠友主页…" />
    <PageState v-else-if="error" type="error" :message="error" @retry="load" />
    <template v-else-if="profile">
      <aside class="public-profile-left surface-card">
        <button class="profile-back-link" @click="router.back()">‹ 返回社区</button>
        <div class="public-profile-avatar"><el-image v-if="profile.avatarUrl" :src="profile.avatarUrl" fit="cover" /><span v-else>{{ profile.displayName.slice(0, 1) }}</span></div>
        <h1>{{ profile.displayName }}</h1><p>@{{ profile.username }}</p>
        <div class="public-profile-actions"><RouterLink v-if="profile.ownProfile" to="/app/account" class="button button-primary">编辑我的主页</RouterLink><template v-else><button class="button button-primary" :class="{ followed: profile.viewerFollowing }" @click="toggleProfileFollow">{{ profile.viewerFollowing ? '已关注' : '＋ 关注' }}</button><button class="button button-secondary" @click="messageUser">发私信</button></template></div>
        <p class="public-profile-bio">{{ profile.bio || '这位宠友还没有填写简介。' }}</p>
        <dl><div><dt>地区</dt><dd>{{ profile.region || '暂未填写' }}</dd></div><div><dt>加入时间</dt><dd>{{ new Date(profile.joinedAt).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long' }) }}</dd></div></dl>
      </aside>

      <main class="public-profile-main">
        <section class="public-profile-hero surface-card"><div><p class="eyebrow">PET FRIEND PROFILE</p><h2>{{ profile.displayName }} 的养宠空间</h2><p>公开动态与宠物伙伴都来自用户主动分享。</p></div><div class="public-profile-stats"><button @click="chooseTab('POSTS')"><strong>{{ profile.postCount }}</strong><small>动态</small></button><button @click="chooseTab('FOLLOWERS')"><strong>{{ profile.followerCount }}</strong><small>粉丝</small></button><button @click="chooseTab('FOLLOWING')"><strong>{{ profile.followingCount }}</strong><small>关注</small></button></div></section>
        <nav class="public-profile-tabs surface-card"><button :class="{ active: tab === 'POSTS' }" @click="chooseTab('POSTS')">公开动态</button><button :class="{ active: tab === 'FOLLOWERS' }" @click="chooseTab('FOLLOWERS')">粉丝</button><button :class="{ active: tab === 'FOLLOWING' }" @click="chooseTab('FOLLOWING')">关注</button></nav>

        <PageState v-if="contentLoading" type="loading" message="正在加载关系列表…" />
        <template v-else-if="tab === 'POSTS'"><PageState v-if="!posts.length" type="empty" message="这位宠友还没有公开动态。" /><div v-else class="public-profile-posts"><article v-for="post in posts" :key="post.id" class="profile-post-card surface-card"><header><span class="avatar">{{ profile.displayName.slice(0, 1) }}</span><div><strong>{{ profile.displayName }}</strong><p>{{ new Date(post.publishedAt || post.createdAt).toLocaleString('zh-CN') }}</p></div><span v-if="post.topicName" class="topic-pill"># {{ post.topicName }}</span></header><div class="profile-post-copy"><h2>{{ post.title }}</h2><p>{{ post.content }}</p></div><CommunityMediaGallery v-if="post.media.length" :media="post.media" :alt="post.title" /><footer><div><span>浏览 {{ post.viewCount }}</span><span>评论 {{ post.commentCount }}</span><span>点赞 {{ post.likeCount }}</span><span>收藏 {{ post.favoriteCount }}</span></div></footer></article></div></template>
        <template v-else><PageState v-if="!people.length" type="empty" :message="tab === 'FOLLOWERS' ? '还没有粉丝。' : '还没有关注其他宠友。'" /><div v-else class="public-people-grid"><article v-for="person in people" :key="person.id" class="public-person-card surface-card"><RouterLink :to="`/app/users/${person.id}`" class="public-person-identity"><span class="avatar">{{ person.displayName.slice(0, 1) }}</span><div><strong>{{ person.displayName }}</strong><small>@{{ person.username }}<template v-if="person.region"> · {{ person.region }}</template></small></div></RouterLink><p>{{ person.bio || '喜欢宠物，也愿意分享。' }}</p><button class="button button-secondary" :class="{ followed: person.viewerFollowing }" @click="togglePersonFollow(person)">{{ person.viewerFollowing ? '已关注' : '关注' }}</button></article></div></template>
      </main>

      <aside class="public-profile-right">
        <section class="surface-card public-pet-card"><p class="eyebrow">PET FAMILY</p><h2>宠物伙伴</h2><p v-if="!profile.pets.length" class="muted">暂未公开宠物摘要。</p><article v-for="pet in profile.pets" :key="pet.id"><span>{{ pet.petType === 'CAT' ? '🐈' : pet.petType === 'DOG' ? '🐕' : '🐾' }}</span><div><strong>{{ pet.name }}</strong><small>{{ pet.breed || pet.petType }}<template v-if="pet.ageMonths != null"> · {{ pet.ageMonths }} 月龄</template></small></div></article></section>
        <section class="surface-card public-profile-safety"><strong>友善交流</strong><p>公开宠物摘要只用于社区展示，不能被他人带入智能问答。</p></section>
      </aside>
    </template>
  </section>
</template>
