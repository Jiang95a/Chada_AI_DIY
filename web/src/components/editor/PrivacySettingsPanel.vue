<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { defaultActivityPrivacy, type ActivityPrivacySettings } from '@/composables/useActivityPrivacy'
import { getProjectPrivacy, updateProjectPrivacy } from '@/services/privacy'

const props = defineProps<{ projectId: string }>()

const toast = useToast()
const REMOTE_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const loading = ref(false)
const saving = ref(false)
const form = reactive<ActivityPrivacySettings>({ ...defaultActivityPrivacy() })

async function load(): Promise<void> {
  if (!REMOTE_ID_PATTERN.test(props.projectId)) return
  loading.value = true
  try {
    Object.assign(form, await getProjectPrivacy(props.projectId))
  } catch {
    toast.pushToast('隐私设置读取失败，当前显示默认值', 'warn')
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (saving.value) return
  if (!REMOTE_ID_PATTERN.test(props.projectId)) {
    toast.pushToast('本地演示活动无法保存服务端隐私设置，请先连接后端', 'warn')
    return
  }
  saving.value = true
  try {
    Object.assign(form, await updateProjectPrivacy(props.projectId, form))
    toast.pushToast('活动隐私与同意设置已保存', 'success')
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '隐私设置保存失败', 'error')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="privacy-panel">
    <header>
      <span><Icon name="lock" :size="14" />隐私与参与边界</span>
      <em>{{
        loading
          ? '读取中'
          : form.privacyMode === 'anonymous'
            ? '匿名参与'
            : form.privacyMode === 'optional'
              ? '可选匿名'
              : '实名参与'
      }}</em>
    </header>

    <label class="field">
      <span class="field__label">参与方式</span>
      <select v-model="form.privacyMode" class="select" :disabled="loading">
        <option value="optional">可选匿名</option>
        <option value="anonymous">匿名参与</option>
        <option value="real_name">实名参与</option>
      </select>
    </label>

    <div class="privacy-switches">
      <label><span>提交前需要知情同意</span><input v-model="form.consentRequired" type="checkbox" /></label>
      <label><span>允许参与者随时离开</span><input v-model="form.allowLeave" type="checkbox" /></label>
      <label><span>现场设有安静休息区</span><input v-model="form.quietSpace" type="checkbox" /></label>
      <label><span>允许导出参与信息</span><input v-model="form.allowExport" type="checkbox" /></label>
    </div>

    <label class="field">
      <span class="field__label">同意说明</span>
      <textarea v-model="form.consentText" class="textarea" rows="4" maxlength="400" />
    </label>

    <label class="field">
      <span class="field__label">信息保留时间</span>
      <select v-model.number="form.retentionDays" class="select">
        <option :value="7">活动结束后 7 天</option>
        <option :value="30">活动结束后 30 天</option>
        <option :value="90">活动结束后 90 天</option>
        <option :value="365">活动结束后 1 年</option>
        <option :value="3650">长期保留（最多 10 年）</option>
      </select>
    </label>

    <button class="privacy-save" type="button" :disabled="loading || saving" @click="save">
      <Icon name="check" :size="13" />{{ saving ? '保存中…' : '保存隐私设置' }}
    </button>
  </div>
</template>

<style scoped>
.privacy-panel {
  display: grid;
  gap: 9px;
  padding: 11px;
  border: 1px solid #dce8df;
  border-radius: 12px;
  background: #f8fbf8;
}

.privacy-panel > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.privacy-panel > header span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--brand-700);
  font-size: 10px;
  font-weight: 750;
}

.privacy-panel > header em {
  padding: 3px 6px;
  border-radius: 99px;
  color: var(--brand-600);
  background: #e6f1e9;
  font-size: 7px;
  font-style: normal;
}

.privacy-switches {
  display: grid;
  gap: 6px;
  padding: 8px 9px;
  border-radius: 9px;
  background: #fff;
}

.privacy-switches label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--ink-600);
  font-size: 9px;
}

.privacy-switches input {
  accent-color: var(--brand-600);
}

.privacy-save {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 0;
  border-radius: 8px;
  color: #fff;
  background: var(--brand-600);
  font-size: 9px;
  font-weight: 700;
  cursor: pointer;
}

.privacy-save:disabled {
  opacity: 0.55;
  cursor: wait;
}
</style>
