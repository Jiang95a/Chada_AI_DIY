<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'

const principles = [
  {
    title: '先倾听，再行动',
    text: '心理活动不是完成任务，也不是要求参与者立刻变好。我们更在意每个人能否按自己的节奏参与。',
    icon: 'users',
  },
  {
    title: '不评判，不诊断',
    text: '心屿组织的是校园心理活动，不提供心理测量、诊断或治疗建议。',
    icon: 'check-circle',
  },
  {
    title: '保留边界和选择',
    text: '可以匿名，可以不发言，可以中途离开。拒绝参与不需要解释理由。',
    icon: 'lock',
  },
]

const activities = [
  { title: '心晴游园会', text: '用低压力互动让同学在考试周获得短暂放松。', icon: 'spark' },
  { title: '朋辈分享与团辅', text: '围绕适应、关系、睡眠与自我照顾开展主题活动。', icon: 'users' },
  { title: '心理知识科普', text: '用知识问答和内容分享减少误解，不做个体判断。', icon: 'file' },
  { title: '自我关怀行动', text: '用轻量练习和打卡提醒，把照顾自己放进日常。', icon: 'clock' },
]
</script>

<template>
  <main class="about-page">
    <section class="about-hero">
      <div class="about-mark"><i /><b /><span>心</span></div>
      <div>
        <span>CAMPUS WELLBEING COLLECTIVE</span>
        <h1>关于心屿</h1>
        <p>心屿同行是面向校园心理社团的活动服务平台，希望让心理关怀发生在日常，而不是只在出现问题之后。</p>
      </div>
    </section>

    <section class="about-manifesto">
      <span>我们在做的事</span>
      <h2>把“你要积极一点”，<br />换成“你可以按自己的节奏来”。</h2>
      <p>
        我们组织心理社团活动、朋辈分享、自我关怀练习和心理知识科普。平台帮助组织者更清楚地说明活动边界，也让参与者更容易判断一场活动是否适合自己。
      </p>
    </section>

    <section class="about-principles">
      <article v-for="(item, index) in principles" :key="item.title">
        <span>0{{ index + 1 }}</span>
        <i><Icon :name="item.icon" :size="21" /></i>
        <h3>{{ item.title }}</h3>
        <p>{{ item.text }}</p>
      </article>
    </section>

    <section class="about-work">
      <div>
        <span>心屿的日常</span>
        <h2>不是一场大型活动，<br />而是持续发生的连接。</h2>
      </div>
      <div class="about-work-list">
        <article v-for="item in activities" :key="item.title">
          <Icon :name="item.icon" :size="18" />
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.text }}</p>
          </div>
        </article>
      </div>
    </section>

    <section class="about-boundary">
      <div>
        <span>需要说清楚的事</span>
        <h2>心屿不是心理咨询机构</h2>
      </div>
      <div>
        <p>平台用于校园心理活动的组织与参与，不提供心理诊断、心理治疗或危机干预。</p>
        <p>
          如果困扰持续影响生活，请联系学校心理健康中心或专业机构。如果存在立即危险，请联系身边可信任的人，并拨打 120 /
          110。
        </p>
        <RouterLink to="/resources">查看心理资源与求助方向 <Icon name="arrow-right" :size="14" /></RouterLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
.about-page {
  width: min(1200px, calc(100% - 56px));
  margin: 0 auto;
  padding: 60px 0 84px;
}

.about-hero {
  display: grid;
  align-items: center;
  gap: 52px;
  grid-template-columns: 230px minmax(0, 1fr);
}

.about-mark {
  position: relative;
  display: grid;
  width: 220px;
  height: 220px;
  overflow: hidden;
  border-radius: 48% 52% 45% 55%;
  color: #fff;
  background:
    radial-gradient(circle at 30% 25%, rgb(255 255 255 / 22%), transparent 22%),
    linear-gradient(145deg, #a2c9af, #37634c);
  box-shadow: 0 28px 60px -35px rgb(47 99 75 / 55%);
  place-items: center;
}

.about-mark i,
.about-mark b {
  position: absolute;
  width: 100px;
  height: 42px;
  border: 1px solid rgb(255 255 255 / 30%);
  border-radius: 50%;
}

.about-mark i {
  transform: translate(-30px, -18px) rotate(-18deg);
}

.about-mark b {
  transform: translate(32px, 22px) rotate(-18deg);
}

.about-mark span {
  position: relative;
  z-index: 1;
  font-family: var(--font-serif);
  font-size: 54px;
}

.about-hero > div:last-child > span,
.about-manifesto > span,
.about-work > div > span,
.about-boundary > div > span {
  color: var(--brand-500);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.about-hero h1 {
  margin-top: 10px;
  font-family: var(--font-serif);
  font-size: clamp(48px, 7vw, 80px);
  letter-spacing: -0.07em;
}

.about-hero p {
  max-width: 650px;
  margin-top: 15px;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.9;
}

.about-manifesto {
  margin-top: 72px;
  padding: 42px;
  border-radius: 28px;
  background: linear-gradient(145deg, #f9fbf7, #f4ede5);
}

.about-manifesto h2 {
  max-width: 850px;
  margin-top: 12px;
  font-family: var(--font-serif);
  font-size: clamp(30px, 4vw, 48px);
  letter-spacing: -0.05em;
  line-height: 1.35;
}

.about-manifesto p {
  max-width: 760px;
  margin-top: 18px;
  color: var(--ink-500);
  font-size: 10px;
  line-height: 1.9;
}

.about-principles {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.about-principles article {
  position: relative;
  min-height: 230px;
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
}

.about-principles article > span {
  color: var(--ink-300);
  font-family: var(--font-serif);
  font-size: 24px;
}

.about-principles article > i {
  display: grid;
  width: 40px;
  height: 40px;
  margin-top: 32px;
  border-radius: 12px;
  color: var(--brand-600);
  background: var(--brand-50);
  place-items: center;
}

.about-principles h3 {
  margin-top: 17px;
  font-size: 14px;
}

.about-principles p {
  margin-top: 8px;
  color: var(--ink-400);
  font-size: 9px;
  line-height: 1.8;
}

.about-work {
  display: grid;
  gap: 50px;
  margin-top: 70px;
  padding-top: 42px;
  border-top: 1px solid var(--line);
  grid-template-columns: 0.85fr 1.15fr;
}

.about-work h2,
.about-boundary h2 {
  margin-top: 10px;
  font-family: var(--font-serif);
  font-size: 36px;
  letter-spacing: -0.045em;
  line-height: 1.35;
}

.about-work-list {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  border-radius: 17px;
  background: var(--line);
  overflow: hidden;
}

.about-work-list article {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 17px;
  background: #fff;
}

.about-work-list svg {
  flex: 0 0 auto;
  color: var(--brand-500);
}

.about-work-list strong {
  font-size: 11px;
}

.about-work-list p {
  margin-top: 4px;
  color: var(--ink-400);
  font-size: 9px;
  line-height: 1.65;
}

.about-boundary {
  display: grid;
  gap: 50px;
  margin-top: 70px;
  padding: 34px;
  border: 1px solid #e6d9c9;
  border-radius: 22px;
  background: #fcf8f1;
  grid-template-columns: 0.85fr 1.15fr;
}

.about-boundary > div:last-child {
  display: grid;
  gap: 11px;
  color: #75624d;
  font-size: 9px;
  line-height: 1.8;
}

.about-boundary a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #8b623d;
  font-weight: 700;
}

@media (max-width: 820px) {
  .about-hero,
  .about-work,
  .about-boundary {
    grid-template-columns: 1fr;
  }

  .about-mark {
    width: 160px;
    height: 160px;
  }

  .about-principles {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .about-page {
    width: calc(100% - 36px);
    padding-top: 36px;
  }

  .about-manifesto {
    padding: 26px 20px;
  }
}
</style>
