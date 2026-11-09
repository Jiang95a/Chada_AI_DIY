<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'

const lifeTopics = [
  { title: '学业与考试压力', text: '从可执行的小步骤开始，比如拆分任务、短暂休息和寻求同学帮助。', icon: 'file' },
  { title: '睡眠与作息', text: '先观察最近的作息变化，不急着给自己贴上“失眠”的标签。', icon: 'clock' },
  { title: '人际关系', text: '关系里的困扰通常不是一句建议就能解决，可以先找到安全的倾听对象。', icon: 'users' },
  { title: '孤独与适应', text: '进入新环境时感到不适应很常见，可以从一场低压力活动开始连接。', icon: 'pin' },
]

const companionTips = [
  '先听对方把话说完，不急着判断对错。',
  '问一句“你希望我听你说，还是一起想办法？”',
  '不替对方贴心理标签，也不强迫分享细节。',
  '鼓励对方联系学校心理健康中心或可信任的老师。',
]
</script>

<template>
  <main class="resources-page">
    <section class="resources-hero">
      <div>
        <span>CAMPUS WELLBEING RESOURCES</span>
        <h1>不知道从哪里开始时，<br />可以先看看这里。</h1>
        <p>这里提供参与校园心理活动的信息和求助方向，不做心理诊断，也不替代专业帮助。</p>
      </div>
      <aside>
        <Icon name="alert" :size="22" />
        <div>
          <strong>如果你正处在立即危险中</strong>
          <p>请联系身边可信任的人，拨打 <b>120 / 110</b>，或直接前往校医院、医院急诊。</p>
        </div>
      </aside>
    </section>

    <section class="resource-section">
      <header>
        <span>从近期困扰开始</span>
        <h2>先找到最接近自己的那一块</h2>
      </header>
      <div class="topic-grid">
        <article v-for="topic in lifeTopics" :key="topic.title">
          <span><Icon :name="topic.icon" :size="19" /></span>
          <h3>{{ topic.title }}</h3>
          <p>{{ topic.text }}</p>
        </article>
      </div>
    </section>

    <section class="support-layout">
      <article class="support-card">
        <span>如何陪伴一个正在难受的朋友</span>
        <h2>不是马上把他“修好”</h2>
        <ul>
          <li v-for="tip in companionTips" :key="tip"><Icon name="check" :size="13" />{{ tip }}</li>
        </ul>
      </article>
      <article class="where-card">
        <span>校内可以联系谁</span>
        <h2>优先选择真实、可信任的支持</h2>
        <div>
          <p><Icon name="pin" :size="15" /><b>学校心理健康中心</b><small>可预约专业咨询或了解活动信息</small></p>
          <p><Icon name="users" :size="15" /><b>辅导员或信任的老师</b><small>适合帮助联系资源和协调现实问题</small></p>
          <p><Icon name="help" :size="15" /><b>心屿朋辈活动</b><small>参与活动与表达，不等于专业咨询</small></p>
        </div>
      </article>
    </section>

    <section class="resources-boundary">
      <div>
        <span>PLATFORM BOUNDARY</span>
        <h2>我们希望被怎样使用</h2>
      </div>
      <div class="boundary-list">
        <article>
          <b>01</b><strong>用于组织活动</strong>
          <p>报名、打卡、知识问答和匿名表达都需要清晰的用途说明。</p>
        </article>
        <article>
          <b>02</b><strong>不用于心理测量</strong>
          <p>任何页面都不会输出抑郁、焦虑程度或风险等级。</p>
        </article>
        <article>
          <b>03</b><strong>尊重不参与</strong>
          <p>同学可以不填写、不分享，也可以随时离开活动页面。</p>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped>
.resources-page {
  width: min(1180px, calc(100% - 56px));
  margin: 0 auto;
  padding: 54px 0 80px;
}

.resources-hero {
  display: grid;
  align-items: end;
  gap: 50px;
  grid-template-columns: minmax(0, 1fr) 360px;
}

.resources-hero > div > span,
.resource-section header > span,
.support-card > span,
.where-card > span,
.resources-boundary > div > span {
  color: var(--brand-500);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.resources-hero h1 {
  margin-top: 14px;
  font-family: var(--font-serif);
  font-size: clamp(38px, 5vw, 62px);
  letter-spacing: -0.06em;
  line-height: 1.2;
}

.resources-hero > div > p {
  max-width: 610px;
  margin-top: 17px;
  color: var(--ink-500);
  font-size: 11px;
  line-height: 1.8;
}

.resources-hero aside {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 20px;
  border: 1px solid #e7d8c7;
  border-radius: 18px;
  color: #8a6541;
  background: #fcf7ee;
}

.resources-hero aside strong {
  font-size: 11px;
}

.resources-hero aside p {
  margin-top: 6px;
  font-size: 9px;
  line-height: 1.8;
}

.resources-hero aside b {
  color: #a14b3f;
}

.resource-section {
  margin-top: 70px;
  padding-top: 36px;
  border-top: 1px solid var(--line);
}

.resource-section h2,
.support-card h2,
.where-card h2,
.resources-boundary h2 {
  margin-top: 8px;
  font-family: var(--font-serif);
  font-size: 31px;
  letter-spacing: -0.04em;
}

.topic-grid {
  display: grid;
  gap: 10px;
  margin-top: 25px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.topic-grid article {
  min-height: 210px;
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
}

.topic-grid article > span {
  display: grid;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: var(--brand-600);
  background: var(--brand-50);
  place-items: center;
}

.topic-grid h3 {
  margin-top: 30px;
  font-size: 13px;
}

.topic-grid p {
  margin-top: 8px;
  color: var(--ink-400);
  font-size: 9px;
  line-height: 1.8;
}

.support-layout {
  display: grid;
  gap: 13px;
  margin-top: 58px;
  grid-template-columns: 1fr 1fr;
}

.support-card,
.where-card {
  padding: 28px;
  border-radius: 22px;
}

.support-card {
  color: #fff;
  background: linear-gradient(135deg, #294c3a, #5e846d);
}

.support-card > span {
  color: #b9d7c3;
}

.support-card ul {
  display: grid;
  gap: 12px;
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
}

.support-card li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: rgb(255 255 255 / 72%);
  font-size: 10px;
  line-height: 1.7;
}

.support-card li svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: #b9d7c3;
}

.where-card {
  border: 1px solid var(--line);
  background: #fff;
}

.where-card > div {
  display: grid;
  gap: 10px;
  margin-top: 20px;
}

.where-card p {
  display: grid;
  align-items: center;
  gap: 3px 9px;
  padding: 12px;
  border-radius: 12px;
  background: var(--ink-50);
  grid-template-columns: auto 1fr;
}

.where-card p svg {
  grid-row: 1 / 3;
  color: var(--brand-500);
}

.where-card p b {
  font-size: 10px;
}

.where-card p small {
  color: var(--ink-400);
  font-size: 8px;
}

.resources-boundary {
  display: grid;
  gap: 42px;
  margin-top: 58px;
  padding: 34px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: #fbfcf9;
  grid-template-columns: 0.8fr 1.2fr;
}

.boundary-list {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  border-radius: 15px;
  background: var(--line);
  overflow: hidden;
}

.boundary-list article {
  display: grid;
  align-items: start;
  gap: 5px 12px;
  padding: 17px;
  background: #fff;
  grid-template-columns: auto 1fr;
}

.boundary-list b {
  grid-row: 1 / 3;
  color: var(--brand-400);
  font-family: var(--font-serif);
}

.boundary-list strong {
  font-size: 11px;
}

.boundary-list p {
  color: var(--ink-400);
  font-size: 9px;
  line-height: 1.65;
}

@media (max-width: 900px) {
  .resources-hero,
  .support-layout,
  .resources-boundary {
    grid-template-columns: 1fr;
  }

  .topic-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .resources-page {
    width: calc(100% - 36px);
    padding-top: 34px;
  }

  .topic-grid {
    grid-template-columns: 1fr;
  }
}
</style>
