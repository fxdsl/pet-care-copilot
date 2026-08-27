<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'

import {
  createCommunityComment,
  createCommunityReport,
  deleteCommunityComment,
  getCommunityPost,
  listCommunityComments,
  listCommunityPosts,
  listRecommendations,
  listCommunityTopics,
  setPostFavorite,
  setPostLike,
  setPostRepost,
  setRecommendationNotInterested,
  setUserFollow,
  setUserRelation,
  type CommunityComment,
  type CommunityFeed,
  type CommunityPost,
  type CommunityTopic,
} from '../api/community'
import { ApiRequestError } from '../api/http'
import CommunityMediaGallery from '../components/community/CommunityMediaGallery.vue'
import CommunityPostCard from '../components/community/CommunityPostCard.vue'
import CommunityPostEditorDialog from '../components/community/CommunityPostEditorDialog.vue'
import PageState from '../components/PageState.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const posts = ref<CommunityPost[]>([])
const topics = ref<CommunityTopic[]>([])
const feed = ref<CommunityFeed>('LATEST')
const topicId = ref('')
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const page = ref(0)
const total = ref(0)
const composerOpen = ref(false)
const detailOpen = ref(false)
const selectedPost = ref<CommunityPost>()
const comments = ref<CommunityComment[]>([])
const commentText = ref('')
const replyTo = ref<CommunityComment>()
const commentLoading = ref(false)
const latitude = ref<number>()
const longitude = ref<number>()
const feedScroller = ref<HTMLElement>()
const showBackToTop = ref(false)
let openPostRequestId = 0

const feedOptions: { value: CommunityFeed; label: string; hint: string }[] = [
  { value: 'FOR_YOU', label: '推荐', hint: '为你解释每一次推荐' },
  { value: 'LATEST', label: '最新', hint: '刚刚发生的新鲜事' },
  { value: 'HOT', label: '热门', hint: '正在被大家讨论' },
  { value: 'FOLLOWING', label: '关注', hint: '你关注的养宠伙伴' },
  { value: 'NEARBY', label: '附近', hint: '发现同城宠友' },
]
const canLoadMore = computed(() => posts.value.length < total.value)
const activeFeedOption = computed(() => feedOptions.find((option) => option.value === feed.value) ?? feedOptions[0])
/** 右栏推荐直接复用当前信息流作者，不伪造尚未实现的推荐用户接口。 */
const suggestedAuthors = computed(() => {
  const seen = new Set<string>()
  return posts.value.filter((post) => {
    if (post.authorId === auth.user?.id || seen.has(post.authorId)) return false
    seen.add(post.authorId)
    return true
  }).slice(0, 4)
})

async function load(reset = true): Promise<void> {
  if (reset) { loading.value = true; page.value = 0; error.value = '' }
  else loadingMore.value = true
  try {
    if (feed.value === 'NEARBY' && (latitude.value == null || longitude.value == null)) {
      await locate()
    }
    const result = feed.value === 'FOR_YOU'
      ? await listRecommendations(page.value, 12).then((value) => ({
          ...value,
          items: value.items.map((item) => ({ ...item.post, recommendationReason: item.reason })),
        }))
      : await listCommunityPosts({
          feed: feed.value, topicId: topicId.value || undefined,
          latitude: feed.value === 'NEARBY' ? latitude.value : undefined,
          longitude: feed.value === 'NEARBY' ? longitude.value : undefined,
          radiusKm: 20, page: page.value, size: 12,
        })
    posts.value = reset ? result.items : [...posts.value, ...result.items]
    total.value = result.total
  } catch (cause) { error.value = readable(cause) }
  finally { loading.value = false; loadingMore.value = false }
}

async function loadMore(): Promise<void> { page.value += 1; await load(false) }

async function initialize(): Promise<void> {
  const results = await Promise.allSettled([listCommunityTopics()])
  if (results[0].status === 'fulfilled') topics.value = results[0].value
  await load()

  // 消息中心点击帖子通知时带上 post Query；即使帖子不在当前首屏，也能直接打开详情。
  const postId = typeof route.query.post === 'string' ? route.query.post : ''
  if (postId) await openPostById(postId, posts.value.find((item) => item.id === postId))
}

async function chooseFeed(value: CommunityFeed): Promise<void> {
  feed.value = value
  await load()
}

async function locate(): Promise<void> {
  if (!navigator.geolocation) throw new Error('当前浏览器不支持定位')
  const position = await new Promise<GeolocationPosition>((resolve, reject) =>
    navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: false, timeout: 8000 }),
  )
  latitude.value = Number(position.coords.latitude.toFixed(7))
  longitude.value = Number(position.coords.longitude.toFixed(7))
  ElMessage.success('已取得当前位置，仅保存到你发布的帖子')
}

async function toggleLike(post: CommunityPost): Promise<void> {
  const next = !post.viewerLiked
  post.viewerLiked = next
  post.likeCount += next ? 1 : -1
  try { const result = await setPostLike(post.id, next); post.viewerLiked = result.active; post.likeCount = result.count }
  catch (cause) { post.viewerLiked = !next; post.likeCount += next ? -1 : 1; ElMessage.error(readable(cause)) }
}

async function toggleFavorite(post: CommunityPost): Promise<void> {
  const next = !post.viewerFavorited
  post.viewerFavorited = next
  post.favoriteCount += next ? 1 : -1
  try { const result = await setPostFavorite(post.id, next); post.viewerFavorited = result.active; post.favoriteCount = result.count }
  catch (cause) { post.viewerFavorited = !next; post.favoriteCount += next ? -1 : 1; ElMessage.error(readable(cause)) }
}

async function toggleRepost(post: CommunityPost): Promise<void> {
  const next = !post.viewerReposted
  post.viewerReposted = next
  post.repostCount += next ? 1 : -1
  try {
    const result = await setPostRepost(post.id, next)
    post.viewerReposted = result.active
    post.repostCount = result.count
    ElMessage.success(result.active ? '已转发到你的动态关系中' : '已取消转发')
  } catch (cause) {
    post.viewerReposted = !next
    post.repostCount += next ? -1 : 1
    ElMessage.error(readable(cause))
  }
}

async function hideRecommendation(post: CommunityPost): Promise<void> {
  try {
    await setRecommendationNotInterested(post.id, true)
    posts.value = posts.value.filter((item) => item.id !== post.id)
    total.value = Math.max(0, total.value - 1)
    ElMessage.success('已减少此类推荐，你可以随时通过接口撤销反馈')
  } catch (cause) { ElMessage.error(readable(cause)) }
}

async function controlAuthor(post: CommunityPost, type: 'mute' | 'block'): Promise<void> {
  const label = type === 'block' ? '拉黑' : '静音'
  try {
    await ElMessageBox.confirm(
      type === 'block' ? '拉黑后双方不能互看、关注、评论或私信。确认继续吗？' : '静音后你将不再在信息流看到该作者。',
      `${label}${post.authorDisplayName}`,
      { type: 'warning' },
    )
    await setUserRelation(post.authorId, type, true)
    posts.value = posts.value.filter((item) => item.authorId !== post.authorId)
    ElMessage.success(`已${label}该用户`)
  } catch (cause) {
    if (cause === 'cancel' || cause === 'close') return
    ElMessage.error(readable(cause))
  }
}

async function toggleFollow(post: CommunityPost): Promise<void> {
  try {
    const result = await setUserFollow(post.authorId, !post.viewerFollowsAuthor)
    posts.value.filter((item) => item.authorId === post.authorId).forEach((item) => { item.viewerFollowsAuthor = result.following })
    ElMessage.success(result.following ? '已关注这位宠友' : '已取消关注')
  } catch (cause) { ElMessage.error(readable(cause)) }
}

async function openPost(post: CommunityPost): Promise<void> {
  await openPostById(post.id, post)
}

/**
 * 统一打开帖子详情。sourcePost 可选：从通知跳转时帖子未必在当前信息流中，
 * 因而不能要求调用方先构造一份假的 CommunityPost。
 */
async function openPostById(postId: string, sourcePost?: CommunityPost): Promise<void> {
  const requestId = ++openPostRequestId
  selectedPost.value = sourcePost
  detailOpen.value = true
  commentLoading.value = true
  comments.value = []

  // 详情与评论互不依赖：详情成功即可累计浏览量，评论失败不能撤销计数结果。
  const [detailResult, commentResult] = await Promise.allSettled([
    getCommunityPost(postId),
    listCommunityComments(postId),
  ])

  // 快速切换帖子时丢弃上一次界面结果，避免旧请求覆盖当前抽屉。
  if (requestId !== openPostRequestId) return

  if (detailResult.status === 'fulfilled') {
    selectedPost.value = detailResult.value
    // 立即同步信息流卡片，用户关闭抽屉后无需刷新即可看到最新浏览数。
    if (sourcePost) sourcePost.viewCount = detailResult.value.viewCount
  } else {
    ElMessage.error(`帖子详情加载失败：${readable(detailResult.reason)}`)
  }

  if (commentResult.status === 'fulfilled') comments.value = commentResult.value.items
  else ElMessage.error(`评论加载失败：${readable(commentResult.reason)}`)
  commentLoading.value = false
}

async function submitComment(): Promise<void> {
  if (!selectedPost.value || !commentText.value.trim()) return
  try {
    const created = await createCommunityComment(selectedPost.value.id, commentText.value, replyTo.value?.id)
    comments.value.push(created)
    selectedPost.value.commentCount += 1
    commentText.value = ''
    replyTo.value = undefined
  } catch (cause) { ElMessage.error(readable(cause)) }
}

async function removeComment(comment: CommunityComment): Promise<void> {
  await ElMessageBox.confirm('确认删除这条评论吗？', '删除评论', { type: 'warning' })
  await deleteCommunityComment(comment.id)
  comments.value = comments.value.filter((item) => item.id !== comment.id)
  if (selectedPost.value) selectedPost.value.commentCount = Math.max(0, selectedPost.value.commentCount - 1)
}

async function reportPost(post: CommunityPost): Promise<void> {
  try {
    const result = await ElMessageBox.prompt('请简要说明举报原因', '举报内容', {
      inputPlaceholder: '例如：疑似危险用药建议', inputValidator: (value) => Boolean(value.trim()) || '请填写说明',
    })
    await createCommunityReport({ targetType: 'POST', targetId: post.id, reasonType: 'OTHER', description: result.value })
    ElMessage.success('举报已提交，管理员会在审核队列中处理')
  } catch (cause) {
    if (cause === 'cancel' || cause === 'close') return
    ElMessage.error(readable(cause))
  }
}

function openCreate(): void {
  composerOpen.value = true
}

async function handlePostSaved(): Promise<void> { await load() }

/** 只监听中间信息流，左右栏与浏览器页面不会触发这个回顶按钮。 */
function handleFeedScroll(event: Event): void {
  showBackToTop.value = (event.currentTarget as HTMLElement).scrollTop > 480
}

function scrollFeedToTop(): void {
  feedScroller.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

function readable(cause: unknown): string {
  if (cause instanceof ApiRequestError) return cause.message
  if (cause instanceof GeolocationPositionError) return '无法取得定位，请允许浏览器定位权限'
  return cause instanceof Error ? cause.message : '请求失败，请稍后重试'
}

onMounted(initialize)
</script>

<template>
  <section class="community-page">
    <div class="community-network-layout">
      <aside class="community-left-rail" aria-label="社区信息流导航">
        <section class="community-rail-card">
          <div class="community-rail-title"><span>宠</span><div><p class="eyebrow">PET COMMUNITY</p><h1>宠友圈</h1></div></div>
          <nav class="community-feed-nav">
            <button v-for="option in feedOptions" :key="option.value" :class="{ active: feed === option.value }" @click="chooseFeed(option.value)"><span>{{ option.value === 'FOR_YOU' ? '✦' : option.value === 'LATEST' ? '◷' : option.value === 'HOT' ? '♨' : option.value === 'FOLLOWING' ? '♡' : '⌖' }}</span><div><strong>{{ option.label }}</strong><small>{{ option.hint }}</small></div></button>
          </nav>
        </section>

        <section class="community-rail-card community-topic-nav">
          <div class="rail-section-heading"><strong>社区话题</strong><button v-if="topicId" @click="topicId = ''; load()">清除</button></div>
          <button :class="{ active: !topicId }" @click="topicId = ''; load()"><span>#</span> 全部话题</button>
          <button v-for="topic in topics" :key="topic.id" :class="{ active: topicId === topic.id }" @click="topicId = topic.id; load()"><span>#</span>{{ topic.name }}</button>
        </section>

      </aside>

      <div class="community-feed-shell">
      <main ref="feedScroller" class="community-feed-column" @scroll.passive="handleFeedScroll">
        <section class="community-compose-card">
          <span class="avatar">{{ auth.user?.displayName?.slice(0, 1) }}</span>
          <button class="community-compose-prompt" @click="openCreate"><strong>分享你的养宠日常…</strong><small>经验、照片和有趣瞬间都值得被记录</small></button>
          <button class="community-compose-submit" @click="openCreate">发布</button>
          <div class="community-compose-tools"><button @click="openCreate">▧ 图片 / 视频</button><button @click="openCreate"># 选择话题</button><button @click="openCreate">⌖ 记录位置</button></div>
        </section>

        <section class="community-feed-toolbar">
          <div><p class="eyebrow">{{ activeFeedOption.value }} FEED</p><h2>{{ activeFeedOption.label }}动态</h2><small>{{ activeFeedOption.hint }} · 共 {{ total }} 条</small></div>
          <el-select v-model="topicId" clearable placeholder="全部话题" @change="load()"><el-option v-for="topic in topics" :key="topic.id" :label="topic.name" :value="topic.id" /></el-select>
        </section>

        <div class="mobile-community-tabs">
          <button v-for="option in feedOptions" :key="option.value" :class="{ active: feed === option.value }" @click="chooseFeed(option.value)">{{ option.label }}</button>
        </div>

        <PageState v-if="loading" type="loading" message="正在整理宠友们的新鲜分享…" />
        <PageState v-else-if="error" type="error" :message="error" @retry="load" />
        <PageState v-else-if="posts.length === 0" type="empty" :message="feed === 'FOLLOWING' ? '关注喜欢的作者后，他们的分享会出现在这里。' : '这个信息流暂时还没有内容。'" />
        <div v-else class="community-feed-list"><CommunityPostCard v-for="post in posts" :key="post.id" :post="post" :current-user-id="auth.user?.id" @open="openPost" @like="toggleLike" @favorite="toggleFavorite" @repost="toggleRepost" @follow="toggleFollow" @not-interested="hideRecommendation" @mute="controlAuthor($event, 'mute')" @block="controlAuthor($event, 'block')" @report="reportPost" /></div>
        <button v-if="!loading && canLoadMore" class="button button-secondary load-more" :disabled="loadingMore" @click="loadMore">{{ loadingMore ? '加载中…' : '查看更多分享' }}</button>
      </main>
      <button v-show="showBackToTop" class="community-back-top" aria-label="回到信息流顶部" title="回到顶部" @click="scrollFeedToTop">↑<small>顶部</small></button>
      </div>

      <aside class="community-right-rail">
        <section class="community-discovery-card">
          <div class="rail-section-heading"><div><p class="eyebrow">DISCOVER</p><h2>社区话题</h2></div><span>实时</span></div>
          <button v-for="(topic, index) in topics.slice(0, 6)" :key="topic.id" class="community-topic-rank" @click="topicId = topic.id; load()"><b>{{ index + 1 }}</b><div><strong># {{ topic.name }}</strong><small>{{ topic.description }}</small></div><span>›</span></button>
          <p v-if="topics.length === 0" class="muted">暂无可用话题</p>
        </section>

        <section class="community-discovery-card">
          <div class="rail-section-heading"><div><p class="eyebrow">PEOPLE</p><h2>可能感兴趣的宠友</h2></div></div>
          <article v-for="post in suggestedAuthors" :key="post.authorId" class="suggested-author"><RouterLink :to="`/app/users/${post.authorId}`" class="avatar avatar-small">{{ post.authorDisplayName.slice(0, 1) }}</RouterLink><RouterLink :to="`/app/users/${post.authorId}`"><strong>{{ post.authorDisplayName }}</strong><small>@{{ post.authorUsername }}</small></RouterLink><button :class="{ active: post.viewerFollowsAuthor }" @click="toggleFollow(post)">{{ post.viewerFollowsAuthor ? '已关注' : '＋ 关注' }}</button></article>
          <p v-if="suggestedAuthors.length === 0" class="muted">更多宠友会随着社区动态出现。</p>
        </section>

      </aside>
    </div>

    <CommunityPostEditorDialog v-model="composerOpen" @saved="handlePostSaved" />

    <el-drawer v-model="detailOpen" size="min(720px, 100vw)" :with-header="false" class="post-detail-drawer">
      <article v-if="selectedPost" class="post-detail"><button class="drawer-close" @click="detailOpen = false">×</button><span class="topic-pill">{{ selectedPost.topicName || '萌宠日常' }}</span><h1>{{ selectedPost.title }}</h1><RouterLink :to="`/app/users/${selectedPost.authorId}`" class="detail-author-row" @click="detailOpen = false"><span class="avatar">{{ selectedPost.authorDisplayName.slice(0, 1) }}</span><div><strong>{{ selectedPost.authorDisplayName }}</strong><small>@{{ selectedPost.authorUsername }}</small></div></RouterLink><p class="post-detail-copy">{{ selectedPost.content }}</p><CommunityMediaGallery v-if="selectedPost.media.length" :media="selectedPost.media" :alt="selectedPost.title" /><div class="detail-actions"><button :class="{ active: selectedPost.viewerLiked }" @click="toggleLike(selectedPost)">♡ {{ selectedPost.likeCount }}</button><button :class="{ active: selectedPost.viewerFavorited }" @click="toggleFavorite(selectedPost)">收藏 {{ selectedPost.favoriteCount }}</button><button :class="{ active: selectedPost.viewerReposted }" @click="toggleRepost(selectedPost)">转发 {{ selectedPost.repostCount }}</button><button @click="reportPost(selectedPost)">举报</button></div><section class="comment-section"><h2>评论 {{ selectedPost.commentCount }}</h2><PageState v-if="commentLoading" type="loading" message="正在加载评论…" /><p v-else-if="comments.length === 0" class="muted">还没有评论，来留下第一条回应吧。</p><article v-for="comment in comments" :key="comment.id" class="comment-item" :class="{ reply: comment.depth === 1 }"><RouterLink :to="`/app/users/${comment.authorId}`" class="avatar avatar-small" @click="detailOpen = false">{{ comment.authorDisplayName.slice(0, 1) }}</RouterLink><div><RouterLink :to="`/app/users/${comment.authorId}`" @click="detailOpen = false"><strong>{{ comment.authorDisplayName }}</strong></RouterLink><p>{{ comment.content }}</p><small>{{ new Date(comment.createdAt).toLocaleString('zh-CN') }}</small><div><button v-if="comment.depth === 0" @click="replyTo = comment">回复</button><button v-if="comment.viewerCanDelete" class="text-danger" @click="removeComment(comment)">删除</button></div></div></article><div class="comment-composer"><span v-if="replyTo">回复 {{ replyTo.authorDisplayName }} <button @click="replyTo = undefined">取消</button></span><el-input v-model="commentText" type="textarea" :rows="3" maxlength="2000" placeholder="友善交流，分享真实养宠经验。" /><button class="button button-primary" @click="submitComment">发表评论</button></div></section></article>
    </el-drawer>
  </section>
</template>
