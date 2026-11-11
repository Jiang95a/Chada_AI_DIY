<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'
import authWorkspaceStory from '@/assets/auth-workspace-story.svg'

defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <main class="auth-page">
    <div class="auth-shell">
      <aside class="auth-story">
        <div class="auth-brand">
          <span><Icon name="spark" :size="20" /></span>
          <div>
            <strong>心屿同行</strong>
            <small>校园心理社团服务平台</small>
          </div>
        </div>
        <figure class="auth-story-visual">
          <img :src="authWorkspaceStory" alt="心理社团活动运营工作台示意" />
        </figure>
        <div class="auth-story-copy">
          <span>CAMPUS ACTIVITY WORKFLOW</span>
          <h2>活动发布、页面制作、<br />参与数据，一处完成。</h2>
          <p>登录后完成活动发布、页面与海报制作，并查看报名、签到和反馈数据。</p>
        </div>
        <ul class="auth-capabilities">
          <li><Icon name="calendar" :size="17" /><span><strong>活动发布</strong><small>创建、审核与发布</small></span></li>
          <li><Icon name="layout" :size="17" /><span><strong>页面与海报</strong><small>H5、表单与传播素材</small></span></li>
          <li><Icon name="chart" :size="17" /><span><strong>参与数据</strong><small>报名、签到与匿名反馈</small></span></li>
        </ul>
      </aside>

      <section class="auth-card">
        <div class="auth-brand auth-brand--mobile">
          <span><Icon name="spark" :size="20" /></span>
          <div>
            <strong>心屿同行</strong>
            <small>校园心理社团服务平台</small>
          </div>
        </div>
        <h1>{{ title }}</h1>
        <p class="auth-copy">{{ description }}</p>
        <slot />
      </section>
    </div>
  </main>
</template>

<style>
.auth-page {
  position: relative;
  display: grid;
  min-height: 100vh;
  overflow: hidden;
  padding: 28px;
  background-color: var(--bg);
  background-image:
    linear-gradient(rgb(45 80 70 / 4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(45 80 70 / 4%) 1px, transparent 1px);
  background-size: 48px 48px;
  place-items: center;
}

.auth-page::before,
.auth-page::after {
  position: absolute;
  content: '';
  pointer-events: none;
}

.auth-page::before {
  top: 0;
  right: 0;
  width: 31%;
  height: 100%;
  background: var(--surface-warm);
}

.auth-page::after {
  right: 9%;
  bottom: 0;
  width: 21%;
  height: 17%;
  border-top: 1px solid var(--line-strong);
  border-left: 1px solid var(--line-strong);
}

.auth-shell {
  position: relative;
  z-index: 1;
  display: grid;
  width: min(1080px, 100%);
  min-height: 680px;
  border: 1px solid var(--line);
  background: var(--surface);
  grid-template-columns: minmax(360px, 0.9fr) minmax(420px, 1.1fr);
}

.auth-story {
  display: flex;
  min-width: 0;
  justify-content: space-between;
  padding: 38px;
  color: #fff;
  background: var(--brand-800);
  flex-direction: column;
}

.auth-card {
  display: flex;
  width: 100%;
  justify-content: center;
  padding: 46px clamp(34px, 6vw, 76px);
  border: 0;
  border-radius: 0;
  background: var(--surface);
  box-shadow: none;
  flex-direction: column;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 11px;
}

.auth-brand > span {
  display: grid;
  width: 42px;
  height: 42px;
  border-radius: 2px;
  color: #fff;
  background: var(--brand-600);
  place-items: center;
}

.auth-brand > div {
  display: flex;
  flex-direction: column;
}

.auth-brand strong {
  font-size: 15px;
}

.auth-brand small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 9px;
}

.auth-story .auth-brand strong {
  color: #fff;
}

.auth-story .auth-brand small {
  color: rgb(255 255 255 / 44%);
}

.auth-brand--mobile {
  display: none;
}

.auth-story-copy > span {
  color: var(--accent-200);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.auth-story-copy h2 {
  margin-top: 12px;
  font-family: var(--font-serif);
  font-size: 34px;
  letter-spacing: -0.045em;
  line-height: 1.35;
}

.auth-story-visual {
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 7;
  margin: 22px 0 18px;
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 13%);
}

.auth-story-visual img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.auth-story-copy p {
  max-width: 360px;
  margin-top: 14px;
  color: rgb(255 255 255 / 62%);
  font-size: 12px;
  line-height: 1.75;
}

.auth-capabilities {
  display: grid;
  margin: 0;
  padding: 0;
  border-top: 1px solid rgb(255 255 255 / 12%);
  list-style: none;
}

.auth-capabilities li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 0;
  border-bottom: 1px solid rgb(255 255 255 / 9%);
  color: rgb(255 255 255 / 58%);
}

.auth-capabilities li > span {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.auth-capabilities strong {
  color: #fff;
  font-size: 11px;
}

.auth-capabilities small {
  color: rgb(255 255 255 / 42%);
  font-size: 9px;
}

.auth-card h1 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 30px;
  letter-spacing: -0.035em;
}

.auth-copy {
  margin: 9px 0 22px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.auth-form {
  display: grid;
  gap: 14px;
}

.auth-field {
  display: grid;
  gap: 7px;
}

.auth-field > span {
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 700;
}

.auth-field input {
  width: 100%;
  height: 46px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 2px;
  outline: none;
  color: var(--ink-800);
  background: var(--surface-soft);
  font-size: 13px;
}

.auth-field input:focus {
  border-color: var(--brand-400);
  box-shadow: 0 0 0 3px rgb(45 80 70 / 9%);
}

.auth-password-field {
  position: relative;
}

.auth-password-field input {
  padding-right: 46px;
}

.auth-password-toggle {
  position: absolute;
  top: 50%;
  right: 6px;
  display: grid;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 0;
  color: var(--ink-500);
  background: transparent;
  cursor: pointer;
  transform: translateY(-50%);
  place-items: center;
}

.auth-password-toggle:hover {
  color: var(--brand-700);
}

.auth-rules {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 0;
  color: var(--ink-500);
  font-size: 10px;
  list-style: none;
}

.auth-rules li {
  display: flex;
  align-items: center;
  gap: 6px;
}

.auth-rules li::before {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink-300);
  content: '';
}

.auth-rules li.is-pass {
  color: var(--brand-700);
}

.auth-rules li.is-pass::before {
  background: var(--brand-500);
}

.auth-strength {
  display: grid;
  gap: 5px;
  color: var(--ink-500);
  font-size: 10px;
}

.auth-strength-bars {
  display: grid;
  gap: 4px;
  grid-template-columns: repeat(3, 1fr);
}

.auth-strength-bars i {
  height: 4px;
  background: var(--ink-100);
}

.auth-strength-bars i.is-pass {
  background: var(--brand-500);
}

.auth-hint {
  color: var(--muted);
  font-size: 10px;
  line-height: 1.6;
}

.auth-error,
.auth-success {
  margin: 0;
  padding: 9px 11px;
  border: 1px solid transparent;
  font-size: 11px;
  line-height: 1.6;
}

.auth-error {
  border-color: #e6c7c4;
  color: #9a433c;
  background: #fbefed;
}

.auth-success {
  border-color: var(--brand-200);
  color: var(--brand-800);
  background: var(--brand-50);
}

.auth-submit {
  height: 46px;
  margin-top: 3px;
  border: 0;
  border-radius: 2px;
  color: #fff;
  background: var(--brand-600);
  font-size: 13px;
  font-weight: 750;
  cursor: pointer;
}

.auth-submit:hover:not(:disabled) {
  background: var(--brand-700);
}

.auth-submit:disabled {
  opacity: 0.58;
  cursor: wait;
}

.auth-links {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 15px;
}

.auth-link {
  padding: 0;
  border: 0;
  color: var(--brand-600);
  background: transparent;
  font-size: 11px;
  cursor: pointer;
}

.auth-link:hover {
  color: var(--brand-800);
}

.auth-footer-note {
  margin: 23px 0 0;
  color: var(--muted);
  font-size: 9px;
  text-align: center;
}

@media (max-width: 900px) {
  .auth-shell {
    display: block;
    width: min(520px, 100%);
    min-height: 0;
    border: 0;
    background: transparent;
  }

  .auth-story {
    display: none;
  }

  .auth-card {
    min-height: auto;
    border: 1px solid var(--line);
    padding: 34px;
    justify-content: flex-start;
  }

  .auth-brand--mobile {
    display: flex;
  }

  .auth-card h1 {
    margin-top: 26px;
  }
}

@media (max-width: 520px) {
  .auth-page {
    padding: 14px;
  }

  .auth-card {
    min-height: calc(100vh - 28px);
    padding: 24px 20px;
  }
}
</style>
