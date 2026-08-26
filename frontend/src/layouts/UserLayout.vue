<script setup lang="ts">
import { Bell, ChatDotRound, Collection, HomeFilled, Postcard, Search, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { checkInToday, getCheckInStatus, type CommunityCheckIn } from '../api/community'
import AppBrand from '../components/AppBrand.vue'
import { useAuthStore } from '../stores/auth'
import { useMessageStore } from '../stores/message'

const auth = useAuthStore()
const messageStore = useMessageStore()
const route = useRoute()
const router = useRouter()
const checkIn = ref<CommunityCheckIn>()
/** 问答页使用视口内双滚动区，其余业务页继续使用普通文档滚动。 */
const isChatRoute = computed(() => route.name === 'chat')
/** 社区三栏各自滚动，浏览器文档本身保持固定。 */
const isCommunityRoute = computed(() => route.name === 'community')
/** 个人主页固定两侧信息栏，只让中间动态列表滚动。 */
const isAccountRoute = computed(() => route.name === 'user-account')
/** 宠物档案固定导航与标题，只让档案功能区内部滚动。 */
const isProfilesRoute = computed(() => route.name === 'profiles')
/** 知识共建固定应用外壳，只让向导、投稿列表和详情区内部滚动。 */
const isKnowledgeRoute = computed(() => route.name === 'knowledge-submissions')
/** 消息中心桌面端固定三栏，只有会话和内容区内部滚动。 */
const isMessageRoute = computed(() => route.name === 'messages')
/** 统一搜索采用固定三栏布局，只让各栏内部滚动。 */
const isSearchRoute = computed(() => route.name === 'search')

/** 签到属于当前用户的全局快捷操作，状态只保存在后端，不写浏览器缓存。 */
async function loadCheckIn(): Promise<void> {
  try { checkIn.value = await getCheckInStatus() }
  catch { checkIn.value = undefined }
}

async function doCheckIn(): Promise<void> {
  try {
    checkIn.value = await checkInToday()
    ElMessage.success('今日养宠签到完成')
  } catch {
    ElMessage.error('签到失败，请稍后重试')
  }
}

async function logout(): Promise<void> {
  messageStore.disconnect()
  await auth.logout()
  await router.replace('/login')
}

onMounted(() => {
  void loadCheckIn()
  void messageStore.refreshUnread()
  messageStore.connect()
})
onUnmounted(messageStore.disconnect)
</script>

<template>
  <div class="app-shell user-shell" :class="{ 'chat-route': isChatRoute, 'community-route': isCommunityRoute, 'account-route': isAccountRoute, 'profiles-route': isProfilesRoute, 'knowledge-route': isKnowledgeRoute, 'message-route': isMessageRoute, 'search-route': isSearchRoute }">
    <header class="user-header">
      <AppBrand />
      <nav class="desktop-nav" aria-label="普通用户导航">
        <RouterLink to="/app/community"><el-icon><HomeFilled /></el-icon>社区</RouterLink>
        <RouterLink to="/app/search"><el-icon><Search /></el-icon>搜索</RouterLink>
        <RouterLink to="/app/chat"><el-icon><ChatDotRound /></el-icon>智能问答</RouterLink>
        <RouterLink to="/app/messages" class="nav-with-badge"><el-icon><Bell /></el-icon>消息<span v-if="messageStore.unread.total" class="nav-unread-badge">{{ messageStore.unread.total > 99 ? '99+' : messageStore.unread.total }}</span></RouterLink>
        <RouterLink to="/app/knowledge"><el-icon><Collection /></el-icon>知识共建</RouterLink>
        <RouterLink to="/app/profiles"><el-icon><Postcard /></el-icon>宠物档案</RouterLink>
        <RouterLink to="/app/account"><el-icon><UserFilled /></el-icon>我的</RouterLink>
      </nav>
      <div class="user-profile-tools">
        <button
          class="header-checkin-button"
          :class="{ completed: checkIn?.checkedIn }"
          :disabled="checkIn?.checkedIn"
          :title="checkIn?.checkedIn ? `已连续签到 ${checkIn.currentStreak} 天` : `本月已签到 ${checkIn?.daysThisMonth ?? 0} 天`"
          @click="doCheckIn"
        >
          <span>🐾</span><span class="header-checkin-copy"><strong>{{ checkIn?.checkedIn ? '今日已签' : '每日签到' }}</strong><small>{{ checkIn?.checkedIn ? `连续 ${checkIn.currentStreak} 天` : `本月 ${checkIn?.daysThisMonth ?? 0} 天` }}</small></span>
        </button>
        <button class="profile-button" @click="router.push('/app/account')">
          <span class="avatar">{{ auth.user?.displayName?.slice(0, 1) }}</span>
          <span>{{ auth.user?.displayName }}</span>
        </button>
      </div>
      <button class="button button-ghost logout-button" @click="logout">退出</button>
    </header>
    <main class="page-container"><RouterView /></main>
    <nav class="mobile-nav" aria-label="移动端导航">
      <RouterLink to="/app/community"><el-icon><HomeFilled /></el-icon><span>社区</span></RouterLink>
      <RouterLink to="/app/search"><el-icon><Search /></el-icon><span>搜索</span></RouterLink>
      <RouterLink to="/app/chat"><el-icon><ChatDotRound /></el-icon><span>问答</span></RouterLink>
      <RouterLink to="/app/messages" class="nav-with-badge"><el-icon><Bell /></el-icon><span>消息</span><b v-if="messageStore.unread.total" class="mobile-unread-dot" /></RouterLink>
      <RouterLink to="/app/knowledge"><el-icon><Collection /></el-icon><span>共建</span></RouterLink>
      <RouterLink to="/app/profiles"><el-icon><Postcard /></el-icon><span>档案</span></RouterLink>
      <RouterLink to="/app/account"><el-icon><UserFilled /></el-icon><span>我的</span></RouterLink>
    </nav>
  </div>
</template>
