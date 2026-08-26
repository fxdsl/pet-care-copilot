<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'

import { updateCurrentUser } from '../api/auth'
import {
  deleteCommunityPost,
  getPublicUserProfile,
  listMyFavoritePosts,
  listMyLikedPosts,
  listMyCommunityPosts,
  listUserFollowers,
  listUserFollowing,
  publishCommunityPost,
  setUserFollow,
  type CommunityPost,
  type PublicUserProfile,
  type PublicUserSummary,
} from '../api/community'
import { ApiRequestError } from '../api/http'
import { loadAuthSession, saveAuthSession } from '../api/session'
import CommunityMediaGallery from '../components/community/CommunityMediaGallery.vue'
import CommunityPostEditorDialog from '../components/community/CommunityPostEditorDialog.vue'
import PageState from '../components/PageState.vue'
import { useAuthStore } from '../stores/auth'

type ProfileSection = 'ALL' | 'PUBLISHED' | 'DRAFT' | 'LIKED' | 'FAVORITED' | 'FOLLOWING' | 'FOLLOWERS'

const auth = useAuthStore()
const route = useRoute()
const saving = ref(false)
const error = ref('')
const profileEditorOpen = ref(false)
const postEditorOpen = ref(false)
const editingPost = ref<CommunityPost>()
const posts = ref<CommunityPost[]>([])
const postsLoading = ref(false)
const postsError = ref('')
const postFilter = ref<ProfileSection>('ALL')
const likedPosts = ref<CommunityPost[]>([])
const favoritePosts = ref<CommunityPost[]>([])
const relationUsers = ref<PublicUserSummary[]>([])
const publicProfile = ref<PublicUserProfile>()
const personalScroller = ref<HTMLElement>()
const showBackToTop = ref(false)
const isUserAccount = computed(() => route.name === 'user-account')
const publishedCount = computed(() => posts.value.filter((post) => post.status === 'PUBLISHED').length)
const draftCount = computed(() => posts.value.filter((post) => post.status === 'DRAFT').length)
const visiblePosts = computed(() => {
  if (postFilter.value === 'LIKED') return likedPosts.value
  if (postFilter.value === 'FAVORITED') return favoritePosts.value
  if (postFilter.value === 'ALL') return posts.value
  if (postFilter.value === 'PUBLISHED' || postFilter.value === 'DRAFT') {
    return posts.value.filter((post) => post.status === postFilter.value)
  }
  return []
})
const relationSection = computed(() => postFilter.value === 'FOLLOWING' || postFilter.value === 'FOLLOWERS')
const sectionTitle = computed(() => ({
  ALL: '我的全部动态', PUBLISHED: '公开发布', DRAFT: '未完成草稿', LIKED: '我赞过的动态',
  FAVORITED: '我的收藏', FOLLOWING: '我的关注', FOLLOWERS: '我的粉丝',
})[postFilter.value])
const joinedAt = computed(() => auth.user?.createdAt
  ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(new Date(auth.user.createdAt))
  : '未知')

const form = reactive({
  displayName: auth.user?.displayName ?? '',
  avatarUrl: auth.user?.avatarUrl ?? '',
  bio: auth.user?.bio ?? '',
  region: auth.user?.region ?? '',
})

/** 保存后同步 Pinia 与 localStorage 中的非敏感用户摘要。 */
async function save(): Promise<void> {
  saving.value = true
  error.value = ''
  try {
    const updated = await updateCurrentUser(form)
    auth.updateUser(updated)
    const session = loadAuthSession()
    if (session) saveAuthSession({ ...session, user: updated })
    profileEditorOpen.value = false
    ElMessage.success('资料已保存')
  } catch (cause) {
    error.value = cause instanceof ApiRequestError ? cause.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function loadPosts(): Promise<void> {
  if (!isUserAccount.value) return
  postsLoading.value = true
  postsError.value = ''
  try {
    const [postPage, profile] = await Promise.all([
      listMyCommunityPosts(), getPublicUserProfile(auth.user!.id),
    ])
    posts.value = postPage.items
    publicProfile.value = profile
  } catch (cause) {
    postsError.value = readable(cause)
  } finally {
    postsLoading.value = false
  }
}

/** 第十周个人主页按需分页加载关系和赞藏内容，不在首屏读取全量关系。 */
async function chooseSection(section: ProfileSection): Promise<void> {
  postFilter.value = section
  postsError.value = ''
  if (!auth.user) return
  try {
    if (section === 'LIKED') likedPosts.value = (await listMyLikedPosts()).items
    else if (section === 'FAVORITED') favoritePosts.value = (await listMyFavoritePosts()).items
    else if (section === 'FOLLOWING') relationUsers.value = (await listUserFollowing(auth.user.id)).items
    else if (section === 'FOLLOWERS') relationUsers.value = (await listUserFollowers(auth.user.id)).items
  } catch (cause) { postsError.value = readable(cause) }
}

async function togglePersonFollow(person: PublicUserSummary): Promise<void> {
  try { person.viewerFollowing = (await setUserFollow(person.id, !person.viewerFollowing)).following }
  catch (cause) { ElMessage.error(readable(cause)) }
}

function createPost(): void {
  editingPost.value = undefined
  postEditorOpen.value = true
}

/** 回顶状态只读取个人主页中间栏，不让浏览器页面和两侧信息栏一起滚动。 */
function handlePersonalScroll(event: Event): void {
  showBackToTop.value = (event.currentTarget as HTMLElement).scrollTop > 480
}

function scrollPersonalToTop(): void {
  personalScroller.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

function editPost(post: CommunityPost): void {
  editingPost.value = post
  postEditorOpen.value = true
}

async function publishDraft(post: CommunityPost): Promise<void> {
  try {
    await publishCommunityPost(post.id)
    ElMessage.success('帖子已发布')
    await loadPosts()
  } catch (cause) {
    ElMessage.error(readable(cause))
  }
}

async function removePost(post: CommunityPost): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除“${post.title}”吗？`, '删除帖子', { type: 'warning' })
    await deleteCommunityPost(post.id)
    ElMessage.success('帖子已删除')
    await loadPosts()
  } catch (cause) {
    if (cause === 'cancel' || cause === 'close') return
    ElMessage.error(readable(cause))
  }
}

function statusLabel(status: CommunityPost['status']): string {
  return ({ DRAFT: '草稿', PENDING_REVIEW: '待审核', PUBLISHED: '已发布', HIDDEN: '已隐藏' })[status]
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function readable(cause: unknown): string {
  return cause instanceof ApiRequestError ? cause.message : cause instanceof Error ? cause.message : '请求失败，请稍后重试'
}

onMounted(loadPosts)
</script>

<template>
  <section v-if="isUserAccount" class="personal-page">
    <div class="personal-layout">
      <aside class="personal-side-nav surface-card" aria-label="个人主页导航">
        <div><p class="eyebrow">PERSONAL</p><h1>个人主页</h1><small>管理资料与公开动态</small></div>
        <nav>
          <button :class="{ active: postFilter === 'ALL' }" @click="chooseSection('ALL')">⌂<span><strong>我的主页</strong><small>{{ posts.length }} 条内容</small></span></button>
          <button :class="{ active: postFilter === 'PUBLISHED' }" @click="chooseSection('PUBLISHED')">▤<span><strong>我的发布</strong><small>{{ publishedCount }} 条已发布</small></span></button>
          <button :class="{ active: postFilter === 'DRAFT' }" @click="chooseSection('DRAFT')">□<span><strong>我的草稿</strong><small>{{ draftCount }} 条待完成</small></span></button>
          <button :class="{ active: postFilter === 'LIKED' }" @click="chooseSection('LIKED')">♡<span><strong>赞过</strong><small>我认可的经验</small></span></button>
          <button :class="{ active: postFilter === 'FAVORITED' }" @click="chooseSection('FAVORITED')">☆<span><strong>收藏</strong><small>稍后继续阅读</small></span></button>
          <button :class="{ active: postFilter === 'FOLLOWING' }" @click="chooseSection('FOLLOWING')">＋<span><strong>我的关注</strong><small>{{ publicProfile?.followingCount ?? 0 }} 位宠友</small></span></button>
          <button :class="{ active: postFilter === 'FOLLOWERS' }" @click="chooseSection('FOLLOWERS')">♧<span><strong>我的粉丝</strong><small>{{ publicProfile?.followerCount ?? 0 }} 位宠友</small></span></button>
        </nav>
        <button class="personal-settings-link" @click="profileEditorOpen = true">⚙ 编辑个人资料</button>
      </aside>

      <div class="personal-main-shell">
      <main ref="personalScroller" class="personal-main-column" @scroll.passive="handlePersonalScroll">
        <section class="personal-profile-card surface-card">
          <div class="personal-cover"><span>记录每一次陪伴</span></div>
          <div class="personal-identity">
            <el-image v-if="form.avatarUrl" class="personal-avatar-image" :src="form.avatarUrl" :preview-src-list="[form.avatarUrl]" fit="cover" preview-teleported hide-on-click-modal />
            <span v-else class="large-avatar personal-avatar">{{ form.displayName.slice(0, 1) }}</span>
            <div><h1>{{ form.displayName }}</h1><p>@{{ auth.user?.username }}<span v-if="form.region"> · {{ form.region }}</span></p></div>
            <div class="personal-hero-actions"><button class="button button-secondary" @click="profileEditorOpen = true">编辑资料</button><button class="button button-primary" @click="createPost">发布动态</button></div>
          </div>
          <p class="personal-bio">{{ form.bio || '还没有填写个人简介，用一句话介绍你和宠物吧。' }}</p>
          <div class="personal-stats"><button @click="chooseSection('ALL')"><strong>{{ posts.length }}</strong><small>全部动态</small></button><button @click="chooseSection('FOLLOWERS')"><strong>{{ publicProfile?.followerCount ?? 0 }}</strong><small>粉丝</small></button><button @click="chooseSection('FOLLOWING')"><strong>{{ publicProfile?.followingCount ?? 0 }}</strong><small>关注</small></button></div>
          <nav class="personal-tabs"><button :class="{ active: postFilter === 'ALL' }" @click="chooseSection('ALL')">全部</button><button :class="{ active: postFilter === 'PUBLISHED' }" @click="chooseSection('PUBLISHED')">已发布</button><button :class="{ active: postFilter === 'LIKED' }" @click="chooseSection('LIKED')">赞过</button><button :class="{ active: postFilter === 'FAVORITED' }" @click="chooseSection('FAVORITED')">收藏</button></nav>
        </section>

        <section class="personal-feed-heading"><div><p class="eyebrow">MY SPACE</p><h2>{{ sectionTitle }}</h2></div><button v-if="['ALL', 'PUBLISHED', 'DRAFT'].includes(postFilter)" class="button button-primary" @click="createPost">＋ 发布</button></section>
        <PageState v-if="postsLoading" type="loading" message="正在加载你的动态…" />
        <PageState v-else-if="postsError" type="error" :message="postsError" @retry="loadPosts" />
        <PageState v-else-if="relationSection && relationUsers.length === 0" type="empty" message="这个列表暂时还没有宠友。" />
        <div v-else-if="relationSection" class="public-people-grid"><article v-for="person in relationUsers" :key="person.id" class="public-person-card surface-card"><RouterLink :to="`/app/users/${person.id}`" class="public-person-identity"><span class="avatar">{{ person.displayName.slice(0, 1) }}</span><div><strong>{{ person.displayName }}</strong><small>@{{ person.username }}<template v-if="person.region"> · {{ person.region }}</template></small></div></RouterLink><p>{{ person.bio || '喜欢宠物，也愿意分享。' }}</p><button class="button button-secondary" :class="{ followed: person.viewerFollowing }" @click="togglePersonFollow(person)">{{ person.viewerFollowing ? '已关注' : '关注' }}</button></article></div>
        <PageState v-else-if="visiblePosts.length === 0" type="empty" message="这里暂时没有内容。" />
        <div v-else class="personal-post-list">
          <article v-for="post in visiblePosts" :key="post.id" class="profile-post-card surface-card">
            <header><span class="avatar">{{ form.displayName.slice(0, 1) }}</span><div><strong>{{ form.displayName }}</strong><p>{{ formatDate(post.publishedAt || post.updatedAt) }}<span v-if="post.region"> · {{ post.region }}</span></p></div><span class="status-pill">{{ statusLabel(post.status) }}</span></header>
            <div class="profile-post-copy"><span v-if="post.topicName"># {{ post.topicName }}</span><h2>{{ post.title }}</h2><p>{{ post.content }}</p></div>
            <CommunityMediaGallery v-if="post.media.length" :media="post.media" :alt="post.title" />
            <footer><div><span>浏览 {{ post.viewCount }}</span><span>评论 {{ post.commentCount }}</span><span>点赞 {{ post.likeCount }}</span><span>收藏 {{ post.favoriteCount }}</span></div><div v-if="['ALL', 'PUBLISHED', 'DRAFT'].includes(postFilter)"><button @click="editPost(post)">编辑</button><button v-if="post.status !== 'PUBLISHED'" @click="publishDraft(post)">发布</button><button class="text-danger" @click="removePost(post)">删除</button></div></footer>
          </article>
        </div>
      </main>
      <button v-show="showBackToTop" class="personal-back-top" aria-label="回到个人动态顶部" title="回到顶部" @click="scrollPersonalToTop">↑<small>顶部</small></button>
      </div>

      <aside class="personal-right-column">
        <section class="surface-card personal-info-card"><p class="eyebrow">ABOUT ME</p><h2>个人资料</h2><dl><div><dt>地区</dt><dd>{{ form.region || '暂未填写' }}</dd></div><div><dt>加入时间</dt><dd>{{ joinedAt }}</dd></div><div><dt>账号状态</dt><dd>{{ auth.user?.status }}</dd></div><div><dt>身份</dt><dd>{{ auth.user?.role }}</dd></div></dl><button class="button button-secondary button-wide" @click="profileEditorOpen = true">完善资料</button></section>
      </aside>
    </div>

    <CommunityPostEditorDialog v-model="postEditorOpen" :post="editingPost" @saved="loadPosts" />
    <el-dialog v-model="profileEditorOpen" title="编辑个人资料" width="min(680px, 94vw)">
      <el-form label-position="top" @submit.prevent="save"><div class="form-grid"><el-form-item label="用户名"><el-input :model-value="auth.user?.username" disabled /></el-form-item><el-form-item label="昵称"><el-input v-model="form.displayName" maxlength="100" show-word-limit /></el-form-item><el-form-item class="span-2" label="所在地区"><el-input v-model="form.region" maxlength="100" placeholder="例如：浙江省 杭州市" /></el-form-item><el-form-item class="span-2" label="头像链接"><el-input v-model="form.avatarUrl" maxlength="1000" placeholder="https://..." /></el-form-item><el-form-item class="span-2" label="个人简介"><el-input v-model="form.bio" type="textarea" :rows="5" maxlength="500" show-word-limit /></el-form-item></div><p v-if="error" class="inline-error">{{ error }}</p></el-form>
      <template #footer><button class="button button-secondary" @click="profileEditorOpen = false">取消</button><button class="button button-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存资料' }}</button></template>
    </el-dialog>
  </section>

  <section v-else class="content-page narrow-page">
    <header class="page-heading"><div><p class="eyebrow">MY ACCOUNT</p><h1>账号设置</h1><p>管理后台账号的公开昵称与安全资料。</p></div><span class="status-pill">{{ auth.user?.role }}</span></header>
    <article class="surface-card account-surface"><div class="account-hero"><span class="large-avatar">{{ form.displayName.slice(0, 1) }}</span><div><h2>{{ form.displayName }}</h2><p>@{{ auth.user?.username }} · {{ auth.user?.status }}</p></div></div><el-form label-position="top" @submit.prevent="save"><div class="form-grid"><el-form-item label="用户名"><el-input :model-value="auth.user?.username" disabled /></el-form-item><el-form-item label="昵称"><el-input v-model="form.displayName" maxlength="100" show-word-limit /></el-form-item><el-form-item label="所在地区"><el-input v-model="form.region" maxlength="100" /></el-form-item><el-form-item class="span-2" label="头像链接"><el-input v-model="form.avatarUrl" maxlength="1000" /></el-form-item><el-form-item class="span-2" label="个人简介"><el-input v-model="form.bio" type="textarea" :rows="5" maxlength="500" show-word-limit /></el-form-item></div><p v-if="error" class="inline-error">{{ error }}</p><button class="button button-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存资料' }}</button></el-form></article>
  </section>
</template>
