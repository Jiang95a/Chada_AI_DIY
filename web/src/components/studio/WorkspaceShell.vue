<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import { useProjectStore } from '@/stores/projects'
import { useWorkspace } from '@/composables/useWorkspace'
import {
  listNotifications,
  markAllNotificationsRead as markAllNotificationsReadApi,
  markNotificationRead,
  type NotificationRecord,
} from '@/services/notifications'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const projectStore = useProjectStore()
const toast = useToast()
const { selected: currentWorkspace } = useWorkspace()
const rootRef = ref<HTMLElement | null>(null)
const searchRef = ref<HTMLInputElement | null>(null)
const searchText = ref('')
const showOrgMenu = ref(false)
const showNotifications = ref(false)
const showUserMenu = ref(false)
const showHelp = ref(false)
const workspaceName = computed(() => currentWorkspace.value.name)
const workspaceType = computed(() => currentWorkspace.value.type)
const workspaceAvatar = computed(() => currentWorkspace.value.avatar)

const notifications = ref<NotificationRecord[]>([])
const unreadNotificationCount = ref(0)

const operationNavItems = [
  { href: '/projects', label: '运营概览', icon: 'home' },
  { href: '/published', label: '参与数据', icon: 'chart' },
  { href: '/referrals', label: '转介待办', icon: 'phone' },
]

const toolNavItems = [
  { href: '/projects/new', label: '页面工坊', icon: 'plus' },
  { href: '/templates', label: '模板库', icon: 'grid' },
  { href: '/ai', label: 'AI 助手', icon: 'wand' },
]

const isActive = computed(() => (href: string) => {
  const path = href.split('#')[0] ?? href
  if (href === '/projects') {
    return route.path === '/projects' || /^\/projects\/[^/]+\/(analytics|submissions|exports)$/.test(route.path)
  }
  if (href.includes('#')) return route.hash === `#${href.split('#')[1] ?? ''}`
  return route.path.startsWith(path)
})

const pageTitle = computed(() => {
  if (route.path === '/projects') return '运营概览'
  if (route.path === '/published') return '参与数据'
  if (route.path === '/referrals') return '转介待办'
  if (route.path === '/campus') return '心理活动'
  if (route.path === '/projects/new') return '页面工坊'
  if (route.path === '/templates') return '心晴模板'
  if (route.path === '/ai') return 'AI 心情助手'
  if (route.path === '/profile') return '个人资料'
  if (route.path === '/settings') return '偏好设置'
  if (route.path === '/help') return '帮助与反馈'
  if (/^\/projects\/[^/]+\/(analytics|submissions|exports)$/.test(route.path)) return '活动数据'
  if (route.path.startsWith('/editor')) return '页面工坊'
  if (route.path.startsWith('/p/')) return '心理活动预览'
  return '心屿工作台'
})

const displayName = computed(() => auth.user?.displayName || 'Huiyi')
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())

async function signOut(): Promise<void> {
  await auth.logout()
  showUserMenu.value = false
  await router.replace('/login')
}

async function loadNotifications(): Promise<void> {
  try {
    const data = await listNotifications()
    notifications.value = data.items
    unreadNotificationCount.value = data.unread
  } catch {
    // 未登录或网络异常时通知静默为空。
  }
}

function notificationTime(value: string): string {
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) return ''
  const diff = Date.now() - time
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return new Date(value).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function queryText(): string {
  const value = route.query.q
  return Array.isArray(value) ? (value[0] ?? '') : (value ?? '')
}

watch(
  queryText,
  (value) => {
    searchText.value = value
  },
  { immediate: true }
)

function syncSearch(): void {
  if (route.path !== '/projects') return
  void router.replace({
    path: '/projects',
    query: { ...route.query, q: searchText.value.trim() || undefined },
  })
}

function submitSearch(): void {
  if (route.path !== '/projects') {
    void router.push({
      path: '/projects',
      query: { q: searchText.value.trim() || undefined },
    })
    return
  }
  syncSearch()
}

function clearSearch(): void {
  searchText.value = ''
  syncSearch()
}

async function openNotification(id: string): Promise<void> {
  const item = notifications.value.find((notification) => notification.id === id)
  if (!item) return
  showNotifications.value = false
  if (!item.read) {
    try {
      await markNotificationRead(item.id)
    } catch {
      // 跳转不依赖标记已读成功。
    }
  }
  await loadNotifications()
  await router.push(item.targetPath || '/published')
}

async function markAllNotificationsRead(): Promise<void> {
  try {
    await markAllNotificationsReadApi()
    await loadNotifications()
    toast.pushToast('通知已全部标为已读', 'success')
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '通知操作失败', 'error')
  }
}

function onDocumentClick(event: MouseEvent): void {
  if (!rootRef.value?.contains(event.target as Node)) {
    showOrgMenu.value = false
    showNotifications.value = false
    showUserMenu.value = false
    showHelp.value = false
  }
}

function onKeydown(event: KeyboardEvent): void {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    void nextTick(() => searchRef.value?.focus())
  }
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentClick)
  window.addEventListener('keydown', onKeydown)
  void loadNotifications()
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentClick)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="rootRef" class="workspace-layout studio-workspace">
    <aside class="workspace-sidebar">
      <RouterLink class="workspace-brand" to="/" aria-label="返回心屿同行首页">
        <span class="workspace-brand-mark"><span /><span /></span>
        <span class="workspace-brand-copy">
          <strong>心屿同行</strong>
          <small>校园心理服务</small>
        </span>
      </RouterLink>

      <div class="workspace-popover-anchor workspace-org-anchor">
        <button
          class="workspace-org-card"
          type="button"
          :aria-expanded="showOrgMenu"
          @click="showOrgMenu = !showOrgMenu"
        >
          <span class="workspace-org-avatar">{{ workspaceAvatar }}</span>
          <span>
            <strong>{{ workspaceName }}</strong>
            <small>{{ workspaceType }}</small>
          </span>
          <Icon name="chevron" :size="15" :class="{ 'is-rotated': showOrgMenu }" />
        </button>
        <div v-if="showOrgMenu" class="workspace-popover workspace-org-menu">
          <p>当前组织身份</p>
          <div class="workspace-identity">
            <span class="workspace-menu-avatar">{{ workspaceAvatar }}</span>
            <span>
              <strong>{{ workspaceName }}</strong>
              <small>{{ workspaceType }}</small>
            </span>
          </div>
          <div class="workspace-role-note">
            <p>当前权限</p>
            <span>发起心理活动</span>
            <span>管理本人活动与参与数据</span>
            <span>处理本人活动的转介待办</span>
          </div>
          <RouterLink class="workspace-menu-footer" to="/settings#workspace" @click="showOrgMenu = false">
            <Icon name="sliders" :size="14" />查看身份说明
          </RouterLink>
        </div>
      </div>

      <nav class="workspace-nav" aria-label="心屿工作台导航">
        <span class="workspace-nav-label">运营空间</span>
        <RouterLink
          v-for="item in operationNavItems"
          :key="item.href"
          class="workspace-nav-item"
          :class="{ 'is-active': isActive(item.href) }"
          :to="item.href"
        >
          <Icon :name="item.icon" :size="17" />
          <span>{{ item.label }}</span>
        </RouterLink>
        <span class="workspace-nav-label workspace-nav-label--tools">创作工具</span>
        <RouterLink
          v-for="item in toolNavItems"
          :key="item.href"
          class="workspace-nav-item"
          :class="{ 'is-active': isActive(item.href), 'is-ai': item.href === '/ai' }"
          :to="item.href"
        >
          <Icon :name="item.icon" :size="17" />
          <span>{{ item.label }}</span>
        </RouterLink>
        <RouterLink class="workspace-nav-item workspace-nav-item--visitor" to="/">
          <Icon name="external" :size="17" />
          <span>访问用户前台</span>
          <Icon name="arrow-right" :size="14" />
        </RouterLink>
      </nav>

      <div class="workspace-sidebar-bottom">
        <button
          class="workspace-nav-item workspace-help-trigger"
          type="button"
          :aria-expanded="showHelp"
          @click="showHelp = !showHelp"
        >
          <Icon name="help" :size="17" />
          <span>使用帮助</span>
        </button>
        <div v-if="showHelp" class="workspace-popover workspace-help-popover">
          <div class="workspace-help-heading">
            <strong>使用帮助</strong>
            <button type="button" aria-label="关闭使用帮助" @click="showHelp = false">
              <Icon name="close" :size="13" />
            </button>
          </div>
          <strong>快速开始</strong>
          <p>从心晴模板开始，在页面工坊中调整内容，再发布给同学参与。</p>
          <div class="workspace-help-actions">
            <RouterLink to="/projects/new" @click="showHelp = false">制作第一个活动页</RouterLink>
            <RouterLink to="/help" @click="showHelp = false">进入帮助中心</RouterLink>
          </div>
        </div>
        <button class="workspace-profile" type="button" @click="showUserMenu = !showUserMenu">
          <span class="workspace-profile-avatar">{{ avatarText }}</span>
          <span>
            <strong>{{ displayName }}</strong>
            <small>心晴运营员</small>
          </span>
          <Icon name="sliders" :size="15" />
        </button>
      </div>
    </aside>

    <div class="workspace-main">
      <header class="workspace-topbar">
        <div class="workspace-breadcrumb">
          <RouterLink to="/projects"><Icon name="home" :size="13" />心屿工作台</RouterLink>
          <span>/</span>
          <strong>{{ pageTitle }}</strong>
        </div>
        <div class="workspace-actions">
          <label class="workspace-search">
            <Icon name="search" :size="16" />
            <input
              ref="searchRef"
              v-model="searchText"
              aria-label="搜索项目"
              placeholder="搜索心晴活动..."
              @input="syncSearch"
              @keydown.enter.prevent="submitSearch"
            />
            <button
              v-if="searchText"
              class="workspace-search-clear"
              type="button"
              aria-label="清空搜索"
              @click="clearSearch"
            >
              <Icon name="close" :size="12" />
            </button>
            <kbd>Ctrl K</kbd>
          </label>
          <div class="workspace-popover-anchor">
            <button
              class="app-icon-button workspace-notification-button"
              type="button"
              aria-label="通知"
              :aria-expanded="showNotifications"
              @click="showNotifications = !showNotifications"
            >
              <Icon name="bell" :size="17" />
              <span v-if="unreadNotificationCount" class="workspace-notification-dot" />
            </button>
            <div v-if="showNotifications" class="workspace-popover workspace-notification-menu">
              <div class="workspace-popover-heading">
                <strong>通知</strong>
                <span>{{ unreadNotificationCount }} 条未读</span>
              </div>
              <button
                v-for="item in notifications"
                :key="item.id"
                type="button"
                :class="{ 'is-unread': !item.read }"
                @click="openNotification(item.id)"
              >
                <span class="workspace-notification-icon"><Icon name="bell" :size="14" /></span>
                <span
                  ><strong>{{ item.title }}</strong
                  ><small>{{ item.body }}</small
                  ><em>{{ notificationTime(item.createdAt) }}</em></span
                >
              </button>
              <button
                class="workspace-menu-footer"
                type="button"
                :disabled="unreadNotificationCount === 0"
                @click="markAllNotificationsRead"
              >
                <Icon name="check" :size="14" />
                {{ unreadNotificationCount ? '全部标为已读' : '全部已读' }}
              </button>
            </div>
          </div>
          <div class="workspace-popover-anchor">
            <button
              class="workspace-top-avatar"
              type="button"
              aria-label="账号菜单"
              :aria-expanded="showUserMenu"
              @click="showUserMenu = !showUserMenu"
            >
              {{ avatarText }}
            </button>
            <div v-if="showUserMenu" class="workspace-popover workspace-user-menu">
              <div class="workspace-user-card">
                <span class="workspace-profile-avatar">{{ avatarText }}</span>
                <span
                  ><strong>{{ displayName }}</strong
                  ><small>{{ auth.user?.email || 'huiyi11111 · 心晴运营员' }}</small></span
                >
              </div>
              <RouterLink to="/profile" @click="showUserMenu = false"
                ><Icon name="users" :size="14" />个人资料</RouterLink
              >
              <RouterLink to="/settings" @click="showUserMenu = false"
                ><Icon name="sliders" :size="14" />偏好设置</RouterLink
              >
              <RouterLink to="/help" @click="showUserMenu = false"
                ><Icon name="help" :size="14" />帮助与反馈</RouterLink
              >
              <button type="button" @click="signOut"><Icon name="external" :size="14" />退出登录</button>
            </div>
          </div>
        </div>
      </header>
      <div class="workspace-content"><slot /></div>
    </div>
  </div>
</template>

<style scoped>
.workspace-notification-menu > button.is-unread {
  background: var(--brand-50);
}

.workspace-notification-menu > button.is-unread strong::after {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-left: 6px;
  border-radius: 50%;
  background: var(--brand-500);
  content: '';
  vertical-align: middle;
}

.workspace-notification-menu .workspace-menu-footer:disabled {
  opacity: 0.48;
  cursor: default;
}

.workspace-identity {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 4px 12px 10px;
}

.workspace-identity > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.workspace-identity strong {
  font-size: 10px;
}

.workspace-identity small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 8px;
}

.workspace-role-note {
  display: grid;
  gap: 5px;
  margin: 0 12px 10px;
  padding: 10px;
  border-radius: 9px;
  background: var(--surface-soft);
}

.workspace-role-note p {
  padding: 0;
  color: #8e98aa;
  font-size: 7px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.workspace-role-note span {
  color: var(--ink-500);
  font-size: 8px;
}
</style>
