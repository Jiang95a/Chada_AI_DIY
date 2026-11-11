<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useEditorStore } from '@/stores/editor'
import BaseModal from '@/components/ui/BaseModal.vue'
import Icon from '@/components/ui/Icon.vue'
import QrCode from '@/components/ui/QrCode.vue'
import { copyText } from '@/services/exporters'
import { downloadSharePoster } from '@/services/poster'
import { useToast } from '@/composables/useToast'
import { formatTime } from '@/utils/misc'
import { publishProject as publishProjectApi, unpublishProject as unpublishProjectApi } from '@/services/publish'

const props = withDefaults(
  defineProps<{
    open: boolean
    projectId?: string
    baseRevision?: number
  }>(),
  { projectId: '', baseRevision: 0 }
)
const emit = defineEmits<{ close: [] }>()

const store = useEditorStore()
const { doc } = storeToRefs(store)
const toast = useToast()

const posterBusy = ref(false)

const published = computed(() => Boolean(doc.value.published))
const url = computed(() => store.shareUrl)

// 后端 POST /projects/{id}/publish 的 slug 是选填：不传就按标题自动生成，
// 并且会把生成结果写回项目，下次发布沿用同一个链接。
// 前端以前把 doc.slug 原样塞进去，空串会被当成「显式传了空 slug」，
// 生成出 http://host/ 这种打不开的地址。所以这里空值就不传，交给后端决定。
const normalizedSlug = computed(() => (doc.value.slug ?? '').trim().toLowerCase())
const slugValid = computed(
  () => normalizedSlug.value === '' || /^[a-z0-9]+(-[a-z0-9]+)*$/.test(normalizedSlug.value)
)

async function publish(): Promise<void> {
  if (props.projectId) {
    if (!slugValid.value) {
      toast.pushToast('访问路径只能用小写字母、数字和连字符，例如 club-recruit', 'warn')
      return
    }
    try {
      const result = await publishProjectApi(
        props.projectId,
        normalizedSlug.value === '' ? undefined : normalizedSlug.value
      )
      doc.value.slug = result.publicSlug
      doc.value.published = {
        url: result.publicUrl,
        slug: result.publicSlug,
        at: result.publishedAt ? new Date(result.publishedAt).getTime() : Date.now(),
      }
      toast.pushToast('已发布到业务后端，链接和二维码已生成', 'success')
      return
    } catch (error) {
      toast.pushToast(error instanceof Error ? `后端发布失败：${error.message}` : '后端发布失败', 'warn')
    }
  }
  store.publish()
  toast.pushToast('本地发布成功，链接和二维码已生成', 'success')
}

async function unpublish(): Promise<void> {
  if (props.projectId) {
    try {
      await unpublishProjectApi(props.projectId)
    } catch {
      // 后端不可用时撤下本地发布状态
    }
  }
  store.unpublish()
  toast.pushToast('已撤下发布，链接会失效', 'info')
}

async function copy(): Promise<void> {
  const ok = await copyText(url.value)
  toast.pushToast(ok ? '链接已复制' : '复制失败，手动选中复制吧', ok ? 'success' : 'warn')
}

function openLink(): void {
  window.open(url.value, '_blank', 'noopener')
}

async function downloadPoster(): Promise<void> {
  if (posterBusy.value) return
  posterBusy.value = true
  try {
    await downloadSharePoster(doc.value, url.value)
    toast.pushToast('分享海报已下载', 'success')
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '海报生成失败', 'error')
  } finally {
    posterBusy.value = false
  }
}
</script>

<template>
  <BaseModal
    :open="props.open"
    title="发布心晴活动页面"
    subtitle="生成参与链接与二维码，内容更新后链接不变"
    :width="680"
    @close="emit('close')"
  >
    <div class="pub">
      <div v-if="!published" class="pub__empty">
        <span class="pub__empty-icon"><Icon name="rocket" :size="22" /></span>
        <p class="pub__empty-title">还没发布</p>
        <p class="pub__empty-text">发布后会得到一个固定参与链接和二维码。之后再调整文案或样式，链接都不用换。</p>
        <button type="button" class="btn btn--primary btn--lg" @click="publish">
          <Icon name="rocket" :size="15" /> 发布并生成二维码
        </button>
      </div>

      <template v-else>
        <div class="pub__main">
          <div class="pub__qr">
            <QrCode :value="url" :size="168" />
            <p class="pub__qr-tip">手机扫码打开</p>
          </div>

          <div class="pub__info">
            <span class="tag tag--mint"><Icon name="check" :size="11" /> 已发布</span>
            <p class="pub__name">{{ doc.title }}</p>
            <p class="pub__meta">
              发布于 {{ doc.published ? formatTime(doc.published.at) : '' }} · {{ doc.pages.length }} 个页面 ·
              {{ doc.meta.capacity > 0 ? `限 ${doc.meta.capacity} 人` : '不限人数' }}
            </p>

            <div class="pub__link">
              <input
                class="input pub__input"
                :value="url"
                readonly
                @focus="($event.target as HTMLInputElement).select()"
              />
              <button type="button" class="btn btn--sm" @click="copy"><Icon name="copy" :size="12" /> 复制</button>
            </div>

            <div class="pub__actions">
              <button type="button" class="btn btn--sm" @click="openLink">
                <Icon name="external" :size="12" /> 打开链接
              </button>
              <button type="button" class="btn btn--sm" :disabled="posterBusy" @click="downloadPoster">
                <Icon name="download" :size="12" />
                {{ posterBusy ? '生成中…' : '下载分享海报' }}
              </button>
              <button type="button" class="btn btn--sm btn--danger" @click="unpublish">撤下</button>
            </div>
          </div>
        </div>

        <p class="pub__note">
          <Icon name="info" :size="12" />
          已优先调用 8080 业务后端发布；后端不可用时会降级为本地预览链接。
        </p>
      </template>
    </div>
  </BaseModal>
</template>

<style scoped>
.pub {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pub__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  padding: 26px 30px;
  border: 1px dashed var(--line-strong);
  border-radius: 14px;
  background: linear-gradient(180deg, #fbfaff, #fff);
  text-align: center;
}

.pub__empty-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  background: var(--accent-50);
  color: var(--brand-600);
}

.pub__empty-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-900);
}

.pub__empty-text {
  max-width: 380px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-500);
  margin-bottom: 4px;
}

.pub__main {
  display: grid;
  grid-template-columns: 190px 1fr;
  gap: 18px;
  align-items: center;
}

.pub__qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 16px 40px -30px rgba(15, 23, 42, 0.5);
}

.pub__qr-tip {
  font-size: 10.5px;
  color: var(--ink-400);
}

.pub__info {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.pub__name {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-900);
}

.pub__meta {
  font-size: 11px;
  color: var(--ink-400);
}

.pub__link {
  display: flex;
  gap: 6px;
  margin-top: 3px;
}

.pub__input {
  font-size: 11.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.pub__actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.pub__note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 9px 11px;
  border-radius: 10px;
  background: var(--brand-50);
  color: var(--ink-600);
  font-size: 11px;
  line-height: 1.7;
}

.pub__note code {
  padding: 1px 4px;
  border-radius: 4px;
  background: rgba(79, 70, 229, 0.1);
  color: var(--brand-700);
  font-size: 10.5px;
}

@media (max-width: 640px) {
  .pub__main {
    grid-template-columns: 1fr;
  }
}
</style>
