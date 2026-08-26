import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'

import { useAuthStore } from '../stores/auth'

const USER_ROLES = ['USER', 'VERIFIED_SELLER']
const ADMIN_ROLES = ['ADMIN']
const MODERATION_ROLES = ['ADMIN', 'MODERATOR']

/** 页面使用动态导入，避免社区、问答和管理端一次性进入同一个首屏包。 */
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/AuthView.vue'), meta: { public: true } },
    {
      path: '/app',
      component: () => import('../layouts/UserLayout.vue'),
      meta: { roles: USER_ROLES },
      children: [
        { path: '', redirect: '/app/community' },
        { path: 'community', name: 'community', component: () => import('../views/CommunityView.vue') },
        { path: 'search', name: 'search', component: () => import('../views/SearchView.vue') },
        { path: 'chat', name: 'chat', component: () => import('../views/ChatView.vue') },
        { path: 'messages', name: 'messages', component: () => import('../views/MessageCenterView.vue') },
        { path: 'knowledge', name: 'knowledge-submissions', component: () => import('../views/KnowledgeSubmissionView.vue') },
        { path: 'users/:userId', name: 'public-user', component: () => import('../views/PublicUserView.vue') },
        { path: 'profiles', name: 'profiles', component: () => import('../views/ProfilesView.vue') },
        { path: 'account', name: 'user-account', component: () => import('../views/AccountView.vue') },
      ],
    },
    {
      path: '/manage',
      component: () => import('../layouts/AdminLayout.vue'),
      meta: { roles: MODERATION_ROLES },
      children: [
        { path: '', redirect: '/manage/moderation' },
        { path: 'moderation', name: 'moderation', component: () => import('../views/ModerationView.vue'), meta: { roles: MODERATION_ROLES } },
        { path: 'knowledge', name: 'knowledge', component: () => import('../views/KnowledgeView.vue'), meta: { roles: ADMIN_ROLES } },
        { path: 'users', name: 'admin-users', component: () => import('../views/AdminUsersView.vue'), meta: { roles: ADMIN_ROLES } },
        { path: 'audit', name: 'audit', component: () => import('../views/AuditView.vue'), meta: { roles: ADMIN_ROLES } },
        { path: 'system', name: 'system', component: () => import('../views/SystemView.vue'), meta: { roles: ADMIN_ROLES } },
        { path: 'account', name: 'admin-account', component: () => import('../views/AccountView.vue') },
      ],
    },
    { path: '/', redirect: '/login' },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') },
  ],
})

/** 角色守卫只改善体验；真实授权仍由 Spring Security 和 Service 层执行。 */
router.beforeEach(async (to: RouteLocationNormalized) => {
  const auth = useAuthStore()
  await auth.bootstrap()
  if (to.meta.public) return auth.user ? defaultPath(auth.user.role) : true
  if (!auth.user) return { name: 'login', query: { redirect: to.fullPath } }
  const roles = (to.meta.roles as string[] | undefined)
    ?? (to.matched.find((record) => record.meta.roles)?.meta.roles as string[] | undefined)
  if (roles && !roles.includes(auth.user.role)) return defaultPath(auth.user.role)
  return true
})

export function defaultPath(role: string): string {
  if (role === 'ADMIN') return '/manage/knowledge'
  if (role === 'MODERATOR') return '/manage/moderation'
  return '/app/community'
}

export default router
