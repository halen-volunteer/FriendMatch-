import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/auth/RegisterView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/forget-password',
      name: 'ForgetPassword',
      component: () => import('@/views/auth/ForgetPasswordView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/components/layout/MainLayout.vue'),
      meta: { requiresAuth: true, adminOnly: false },
      children: [
        { path: '', redirect: '/chat' },
        { path: 'chat', name: 'Chat', component: () => import('@/views/chat/ChatView.vue') },
        { path: 'chat/private/:friendId', name: 'PrivateChat', component: () => import('@/views/chat/PrivateChatView.vue') },
        { path: 'chat/team/:teamId', name: 'TeamChat', component: () => import('@/views/chat/TeamChatView.vue') },
        { path: 'profile', name: 'MyProfile', component: () => import('@/views/user/MyProfileView.vue') },
        { path: 'profile/:userId', name: 'UserProfile', component: () => import('@/views/user/UserProfileView.vue') },
        { path: 'friends', name: 'Friends', component: () => import('@/views/user/FriendsView.vue') },
        { path: 'blacklist', name: 'Blacklist', component: () => import('@/views/user/BlacklistView.vue') },
        { path: 'teams', name: 'Teams', component: () => import('@/views/team/TeamListView.vue') },
        { path: 'teams/:teamId', name: 'TeamDetail', component: () => import('@/views/team/TeamDetailView.vue') },
        { path: 'teams/:teamId/members', name: 'TeamMembers', component: () => import('@/views/team/TeamMembersView.vue') },
        { path: 'notices', name: 'Notices', component: () => import('@/views/user/NoticesView.vue') },
        { path: 'feedback', name: 'Feedback', component: () => import('@/views/user/FeedbackView.vue') },
        { path: 'settings', name: 'Settings', component: () => import('@/views/user/SettingsView.vue') },
        { path: 'search', name: 'Search', component: () => import('@/views/search/SearchView.vue') },
        { path: 'recommend', name: 'Recommend', component: () => import('@/views/search/RecommendView.vue') },
        { path: 'devices', name: 'Devices', component: () => import('@/views/user/DevicesView.vue') },
        { path: 'account-status', name: 'AccountStatus', component: () => import('@/views/user/AccountStatusView.vue') },
        { path: 'reports', name: 'ReportCenter', component: () => import('@/views/report/ReportCenterView.vue') },
        { path: 'appeals', name: 'Appeal', component: () => import('@/views/report/AppealView.vue') },
      ],
    },
    {
      path: '/admin',
      component: () => import('@/components/layout/AdminLayout.vue'),
      meta: { requiresAuth: true, adminOnly: true },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/AdminDashboardView.vue') },
        { path: 'reports', name: 'AdminUserReports', component: () => import('@/views/admin/AdminUserReportsView.vue') },
        { path: 'message-reports', name: 'AdminMessageReports', component: () => import('@/views/admin/AdminMessageReportsView.vue') },
        { path: 'team-reports', name: 'AdminTeamReports', component: () => import('@/views/admin/AdminTeamReportsView.vue') },
        { path: 'appeals', name: 'AdminAppeals', component: () => import('@/views/admin/AdminAppealsView.vue') },
        { path: 'feedback', name: 'AdminFeedback', component: () => import('@/views/admin/AdminFeedbackView.vue') },
        { path: 'punish', name: 'AdminPunish', component: () => import('@/views/admin/AdminPunishView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.isLoggedIn) return { name: 'Login' }
  if (to.meta.adminOnly && !authStore.isAdmin) return { name: 'Chat' }
  if (to.meta.requiresAuth === false && authStore.isLoggedIn) {
    return authStore.isAdmin ? { name: 'AdminDashboard' } : { name: 'Chat' }
  }
})

export default router
