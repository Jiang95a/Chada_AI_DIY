<script setup lang="ts">
import { RouterLink } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import {
  usePreferences,
  type DensityPreference,
  type LandingPreference,
  type MotionPreference,
} from '@/composables/usePreferences'
import { useWorkspace } from '@/composables/useWorkspace'
import { API_BASE } from '@/services/http'

const toast = useToast()
const { density, motion, landing, setDensity, setMotion, setLanding, reset } = usePreferences()
const { workspaces, selected, selectWorkspace } = useWorkspace()

function chooseDensity(value: DensityPreference): void {
  setDensity(value)
  toast.pushToast('界面密度已更新', 'success')
}

function chooseMotion(value: MotionPreference): void {
  setMotion(value)
  toast.pushToast(value === 'full' ? '已启用界面动效' : '已减少界面动效', 'success')
}

function chooseLanding(value: LandingPreference): void {
  setLanding(value)
  toast.pushToast('默认打开页面已更新', 'success')
}

function chooseWorkspace(id: string): void {
  selectWorkspace(id)
  toast.pushToast('当前空间已切换', 'success')
}

function resetAll(): void {
  reset()
  toast.pushToast('偏好设置已恢复默认', 'info')
}
</script>

<template>
  <WorkspaceShell>
    <main class="settings-page">
      <header class="settings-heading">
        <div>
          <p class="eyebrow">Preferences</p>
          <h1>偏好设置</h1>
          <p>设置只保存在当前设备，不会影响团队其他成员。</p>
        </div>
        <RouterLink class="app-button app-button--secondary" to="/profile">
          <Icon name="users" :size="15" />返回个人资料
        </RouterLink>
      </header>

      <div class="settings-layout">
        <nav class="settings-nav">
          <a href="#workspace"><Icon name="home" :size="15" />当前空间</a>
          <a href="#appearance"><Icon name="palette" :size="15" />界面偏好</a>
          <a href="#startup"><Icon name="clock" :size="15" />启动页面</a>
          <a href="#diagnostics"><Icon name="info" :size="15" />服务信息</a>
        </nav>

        <div class="settings-sections">
          <section id="workspace" class="settings-card">
            <div class="settings-card-head">
              <span><Icon name="home" :size="17" /></span>
              <div>
                <h2>当前空间</h2>
                <p>切换后，工作台会显示对应空间身份。</p>
              </div>
            </div>
            <div class="setting-options">
              <button
                v-for="workspace in workspaces"
                :key="workspace.id"
                type="button"
                :class="{ 'is-active': selected.id === workspace.id }"
                @click="chooseWorkspace(workspace.id)"
              >
                <span class="setting-workspace-avatar">{{ workspace.avatar }}</span>
                <span
                  ><strong>{{ workspace.name }}</strong
                  ><small>{{ workspace.type }}</small></span
                >
                <Icon v-if="selected.id === workspace.id" name="check-circle" :size="17" />
              </button>
            </div>
          </section>

          <section id="appearance" class="settings-card">
            <div class="settings-card-head">
              <span><Icon name="palette" :size="17" /></span>
              <div>
                <h2>界面偏好</h2>
                <p>控制信息密度和动画效果。</p>
              </div>
            </div>
            <div class="setting-row">
              <div>
                <strong>界面密度</strong>
                <p>紧凑模式会缩小卡片间距和封面高度。</p>
              </div>
              <div class="segmented">
                <button
                  type="button"
                  :class="{ 'is-active': density === 'comfortable' }"
                  @click="chooseDensity('comfortable')"
                >
                  舒适
                </button>
                <button type="button" :class="{ 'is-active': density === 'compact' }" @click="chooseDensity('compact')">
                  紧凑
                </button>
              </div>
            </div>
            <div class="setting-row">
              <div>
                <strong>界面动效</strong>
                <p>关闭动画适合低性能设备或减少干扰。</p>
              </div>
              <div class="segmented">
                <button type="button" :class="{ 'is-active': motion === 'full' }" @click="chooseMotion('full')">
                  开启
                </button>
                <button type="button" :class="{ 'is-active': motion === 'reduced' }" @click="chooseMotion('reduced')">
                  减少
                </button>
              </div>
            </div>
          </section>

          <section id="startup" class="settings-card">
            <div class="settings-card-head">
              <span><Icon name="clock" :size="17" /></span>
              <div>
                <h2>登录后默认打开</h2>
                <p>下次登录且没有指定跳转地址时生效。</p>
              </div>
            </div>
            <div class="setting-options setting-options--inline">
              <button
                type="button"
                :class="{ 'is-active': landing === '/projects' }"
                @click="chooseLanding('/projects')"
              >
                <Icon name="layout" :size="16" /><span><strong>心晴运营台</strong><small>继续管理心理活动</small></span>
              </button>
              <button
                type="button"
                :class="{ 'is-active': landing === '/templates' }"
                @click="chooseLanding('/templates')"
              >
                <Icon name="grid" :size="16" /><span><strong>心晴模板</strong><small>从心理活动模板开始</small></span>
              </button>
            </div>
          </section>

          <section id="diagnostics" class="settings-card">
            <div class="settings-card-head">
              <span><Icon name="info" :size="17" /></span>
              <div>
                <h2>服务信息</h2>
                <p>联调时可用来确认当前前端连接的后端地址。</p>
              </div>
            </div>
            <dl class="diagnostic-list">
              <div>
                <dt>业务后端</dt>
                <dd>{{ API_BASE }}</dd>
              </div>
              <div>
                <dt>前端版本</dt>
                <dd>Vue 3 · Vite 8</dd>
              </div>
              <div>
                <dt>智能体</dt>
                <dd>经 8080 代理，不可用时自动降级</dd>
              </div>
            </dl>
            <button class="settings-reset" type="button" @click="resetAll">
              <Icon name="refresh" :size="14" />恢复默认设置
            </button>
          </section>
        </div>
      </div>
    </main>
  </WorkspaceShell>
</template>

<style scoped>
.settings-page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 34px 34px 60px;
}
.settings-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}
.settings-heading h1 {
  margin: 0;
  font-size: clamp(30px, 4vw, 42px);
  letter-spacing: -0.05em;
}
.settings-heading > div > p:last-child {
  margin: 9px 0 0;
  color: var(--muted);
  font-size: 11px;
}
.settings-heading .app-button {
  min-height: 39px;
  font-size: 10px;
}
.settings-layout {
  display: grid;
  align-items: start;
  gap: 18px;
  margin-top: 28px;
  grid-template-columns: 210px minmax(0, 1fr);
}
.settings-nav {
  position: sticky;
  top: 92px;
  display: grid;
  gap: 4px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 15px;
  background: #fff;
}
.settings-nav a {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border-radius: 9px;
  color: var(--ink-soft);
  font-size: 10px;
}
.settings-nav a:hover {
  color: var(--primary);
  background: var(--primary-soft);
}
.settings-sections {
  display: grid;
  gap: 14px;
}
.settings-card {
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}
.settings-card-head {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}
.settings-card-head > span {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 11px;
  color: var(--primary);
  background: var(--primary-soft);
  place-items: center;
}
.settings-card-head h2 {
  margin: 0;
  font-size: 14px;
}
.settings-card-head p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 9px;
}
.setting-options {
  display: grid;
  gap: 8px;
  margin-top: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.setting-options--inline {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.setting-options button {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  text-align: left;
  cursor: pointer;
}
.setting-options button.is-active {
  border-color: #aabff3;
  color: var(--primary);
  background: var(--primary-soft);
}
.setting-options button > span:not(.setting-workspace-avatar) {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}
.setting-options strong {
  overflow: hidden;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.setting-options small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 8px;
}
.setting-workspace-avatar {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: 10px;
  color: #fff;
  background: #172033;
  font-size: 10px;
  font-weight: 800;
  place-items: center;
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 15px 0;
  border-bottom: 1px solid var(--line);
}
.setting-row:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}
.setting-row strong {
  font-size: 10px;
}
.setting-row p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 8px;
}
.segmented {
  display: flex;
  gap: 3px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-soft);
}
.segmented button {
  min-height: 28px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  color: var(--muted);
  background: transparent;
  font-size: 8px;
  font-weight: 700;
  cursor: pointer;
}
.segmented button.is-active {
  color: var(--primary);
  background: #fff;
  box-shadow: var(--shadow-sm);
}
.diagnostic-list {
  display: grid;
  gap: 0;
  margin: 16px 0;
}
.diagnostic-list div {
  display: grid;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  grid-template-columns: 110px minmax(0, 1fr);
}
.diagnostic-list dt {
  color: var(--muted);
  font-size: 9px;
}
.diagnostic-list dd {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--ink-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 8px;
}
.settings-reset {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 5px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 9px;
  color: var(--ink-soft);
  background: #fff;
  font-size: 9px;
  cursor: pointer;
}
@media (max-width: 820px) {
  .settings-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .settings-layout {
    grid-template-columns: 1fr;
  }
  .settings-nav {
    position: static;
    display: flex;
    overflow-x: auto;
  }
  .settings-nav a {
    flex: 0 0 auto;
  }
  .setting-options {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 600px) {
  .settings-page {
    padding: 22px 18px 44px;
  }
  .setting-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
