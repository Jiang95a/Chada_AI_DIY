<script setup lang="ts">
import { reactive } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import {
  getActivityPrivacy,
  saveActivityPrivacy,
  type ActivityPrivacySettings,
  type PrivacyMode,
} from '@/composables/useActivityPrivacy'

const props = defineProps<{
  projectId: string
  slug: string
  organizer?: string
}>()

const toast = useToast()
const current = getActivityPrivacy(props.projectId, props.slug)
const form = reactive<ActivityPrivacySettings>({
  ...current,
  organizerName: current.organizerName || props.organizer || '',
})

function save(): void {
  try {
    saveActivityPrivacy(props.projectId, props.slug, form)
    toast.pushToast('活动隐私与同意设置已保存', 'success')
  } catch {
    toast.pushToast('隐私设置保存失败，请检查浏览器存储权限', 'error')
  }
}
</script>

<template>
  <div class="privacy-panel">
    <header>
      <span><Icon name="lock" :size="14" />隐私与参与边界</span>
      <em>{{
        form.privacyMode === 'anonymous' ? '匿名参与' : form.privacyMode === 'optional' ? '可选匿名' : '实名参与'
      }}</em>
    </header>

    <label class="field">
      <span class="field__label">参与方式</span>
      <select v-model="form.privacyMode" class="select">
        <option value="optional">可选匿名</option>
        <option value="anonymous">匿名参与</option>
        <option value="real_name">实名参与</option>
      </select>
    </label>

    <label class="field">
      <span class="field__label">组织者名称</span>
      <input v-model="form.organizerName" class="input" placeholder="如：心屿心理协会" />
    </label>

    <label class="field">
      <span class="field__label">组织者说明</span>
      <textarea
        v-model="form.organizerIntro"
        class="textarea"
        rows="3"
        maxlength="180"
        placeholder="简单介绍活动由谁组织、希望营造怎样的参与氛围。"
      />
    </label>

    <div class="privacy-switches">
      <label><span>提交前需要知情同意</span><input v-model="form.consentRequired" type="checkbox" /></label>
      <label><span>允许参与者随时离开</span><input v-model="form.allowLeave" type="checkbox" /></label>
      <label><span>现场设有安静休息区</span><input v-model="form.quietSpace" type="checkbox" /></label>
      <label><span>允许导出参与信息</span><input v-model="form.exportAllowed" type="checkbox" /></label>
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
        <option :value="0">长期保留（需有明确用途）</option>
      </select>
    </label>

    <button class="privacy-save" type="button" @click="save"><Icon name="check" :size="13" />保存隐私设置</button>
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
</style>
