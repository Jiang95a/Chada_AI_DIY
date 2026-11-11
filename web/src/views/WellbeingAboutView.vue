<script setup lang="ts">
import { RouterLink } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'

const capabilities = [
  {
    title: '活动组织',
    text: '发起团辅、讲座、游园会和朋辈交流，统一管理报名、签到与反馈。',
    icon: 'calendar',
  },
  {
    title: '内容制作',
    text: '用模板、页面工坊和海报生成准备 H5 活动页与传播物料。',
    icon: 'layout',
  },
  {
    title: '参与数据',
    text: '按活动查看参与汇总、匿名比例和反馈结果，不展示个体敏感信息。',
    icon: 'chart',
  },
]

const boundaries = [
  '不提供心理诊断、心理治疗或风险判定。',
  '匿名编号只用于单场活动内部关联，不用于识别参与者。',
  '活动反馈和报名信息仅用于本次活动组织与复盘。',
  '危机情况通过校内心理资源与紧急电话转介。',
]
</script>

<template>
  <main class="about-page">
    <section class="about-hero">
      <div class="about-title">
        <span>CAMPUS PSYCHOLOGY CLUB</span>
        <h1>关于心屿</h1>
        <p>面向校园心理社团的活动组织与内容制作平台，覆盖从发起活动到参与复盘的完整流程。</p>
      </div>
      <dl class="about-facts">
        <div>
          <dt>主要使用者</dt>
          <dd>心理社团负责人、干事、班级心理委员</dd>
        </div>
        <div>
          <dt>核心场景</dt>
          <dd>心理活动发布、报名参与、匿名反馈</dd>
        </div>
        <div>
          <dt>平台边界</dt>
          <dd>活动组织工具，不替代心理咨询与医疗支持</dd>
        </div>
      </dl>
    </section>

    <section class="about-purpose">
      <span>PLATFORM SCOPE</span>
      <h2>把活动发布、页面制作和参与数据放在同一套流程里。</h2>
      <p>
        心屿不是心理测评网站，也不是咨询预约平台。它解决的是心理社团日常运营中最重复的部分：活动信息整理、页面和海报制作、报名收集以及活动后的数据汇总。
      </p>
    </section>

    <section class="about-capabilities">
      <article v-for="(item, index) in capabilities" :key="item.title">
        <span>0{{ index + 1 }}</span>
        <Icon :name="item.icon" :size="20" />
        <h3>{{ item.title }}</h3>
        <p>{{ item.text }}</p>
      </article>
    </section>

    <section class="about-process">
      <div>
        <span>WORKFLOW</span>
        <h2>从想法到复盘</h2>
        <p>每个步骤都可以由社团成员协作完成，不需要掌握前后端技术。</p>
      </div>
      <ol>
        <li><b>01</b><span><strong>发起活动</strong><small>填写时间、地点、参与方式和隐私说明</small></span></li>
        <li><b>02</b><span><strong>制作页面与海报</strong><small>选择模板、上传图片或生成视觉底图</small></span></li>
        <li><b>03</b><span><strong>发布并查看数据</strong><small>分享 H5，查看报名、签到和匿名反馈汇总</small></span></li>
      </ol>
    </section>

    <section class="about-boundary">
      <div>
        <span>BOUNDARIES</span>
        <h2>使用边界</h2>
        <RouterLink to="/resources">查看心理资源 <Icon name="arrow-right" :size="14" /></RouterLink>
      </div>
      <ul>
        <li v-for="item in boundaries" :key="item"><Icon name="check" :size="14" />{{ item }}</li>
      </ul>
    </section>
  </main>
</template>

<style scoped>
.about-page {
  width: min(1200px, calc(100% - 56px));
  margin: 0 auto;
  padding: 44px 0 60px;
}

.about-hero {
  display: grid;
  align-items: end;
  gap: 40px;
  padding-bottom: 34px;
  border-bottom: 1px solid var(--line);
  grid-template-columns: minmax(0, 0.95fr) minmax(420px, 1.05fr);
}

.about-title > span,
.about-purpose > span,
.about-process > div > span,
.about-boundary > div > span {
  color: var(--brand-500);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.about-title h1 {
  margin-top: 8px;
  font-family: var(--font-serif);
  font-size: 58px;
  letter-spacing: -0.055em;
}

.about-title p {
  max-width: 580px;
  margin-top: 12px;
  color: var(--ink-500);
  font-size: 14px;
  line-height: 1.75;
}

.about-facts {
  display: grid;
  margin: 0;
  border-top: 1px solid var(--line);
}

.about-facts > div {
  display: grid;
  align-items: baseline;
  padding: 13px 0;
  border-bottom: 1px solid var(--line);
  gap: 16px;
  grid-template-columns: 100px minmax(0, 1fr);
}

.about-facts dt {
  color: var(--ink-500);
  font-size: 11px;
}

.about-facts dd {
  margin: 0;
  color: var(--brand-900);
  font-size: 13px;
  line-height: 1.55;
}

.about-purpose {
  display: grid;
  align-items: start;
  gap: 24px;
  padding: 38px 0 32px;
  grid-template-columns: 180px minmax(0, 1fr);
}

.about-purpose h2 {
  max-width: 880px;
  font-family: var(--font-serif);
  font-size: 34px;
  letter-spacing: -0.04em;
  line-height: 1.35;
}

.about-purpose p {
  max-width: 820px;
  grid-column: 2;
  color: var(--ink-500);
  font-size: 13px;
  line-height: 1.8;
}

.about-capabilities {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  background: var(--line);
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.about-capabilities article {
  min-height: 190px;
  padding: 22px;
  background: var(--surface);
}

.about-capabilities article > span {
  color: var(--ink-300);
  font-family: var(--font-serif);
  font-size: 20px;
}

.about-capabilities svg {
  display: block;
  margin-top: 25px;
  color: var(--brand-500);
}

.about-capabilities h3 {
  margin-top: 14px;
  font-size: 16px;
}

.about-capabilities p {
  margin-top: 7px;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.7;
}

.about-process {
  display: grid;
  gap: 44px;
  padding: 38px 0;
  border-bottom: 1px solid var(--line);
  grid-template-columns: 0.8fr 1.2fr;
}

.about-process h2,
.about-boundary h2 {
  margin-top: 7px;
  font-family: var(--font-serif);
  font-size: 32px;
  letter-spacing: -0.04em;
}

.about-process > div > p {
  max-width: 420px;
  margin-top: 10px;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.7;
}

.about-process ol {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.about-process li {
  display: grid;
  min-height: 74px;
  align-items: center;
  border-top: 1px solid var(--line);
  gap: 16px;
  grid-template-columns: 42px minmax(0, 1fr);
}

.about-process li:last-child {
  border-bottom: 1px solid var(--line);
}

.about-process li > b {
  color: var(--brand-400);
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
}

.about-process li > span {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.about-process strong {
  font-size: 14px;
}

.about-process small {
  color: var(--ink-500);
  font-size: 11px;
}

.about-boundary {
  display: grid;
  gap: 44px;
  padding-top: 34px;
  grid-template-columns: 0.8fr 1.2fr;
}

.about-boundary a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 12px;
  color: var(--brand-600);
  font-size: 11px;
  font-weight: 700;
}

.about-boundary ul {
  display: grid;
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--line);
  list-style: none;
}

.about-boundary li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink-600);
  font-size: 12px;
  line-height: 1.65;
}

.about-boundary li svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--brand-500);
}

@media (max-width: 820px) {
  .about-hero,
  .about-process,
  .about-boundary {
    grid-template-columns: 1fr;
  }

  .about-purpose {
    grid-template-columns: 1fr;
  }

  .about-purpose p {
    grid-column: auto;
  }

  .about-capabilities {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .about-page {
    width: calc(100% - 36px);
    padding-top: 30px;
  }

  .about-title h1 {
    font-size: 44px;
  }

  .about-facts > div {
    grid-template-columns: 1fr;
    gap: 4px;
  }
}
</style>
