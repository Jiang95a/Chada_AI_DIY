<script setup lang="ts">
import { RouterView } from 'vue-router'
import WellbeingPublicShell from '@/components/public/WellbeingPublicShell.vue'
</script>

<template>
  <WellbeingPublicShell>
    <RouterView v-slot="{ Component, route }">
      <Transition name="public-page" mode="out-in">
        <component :is="Component" :key="route.path" />
      </Transition>
    </RouterView>
  </WellbeingPublicShell>
</template>

<style scoped>
.public-page-enter-active,
.public-page-leave-active {
  transition:
    opacity 0.14s ease,
    transform 0.14s ease;
}

.public-page-enter-from {
  opacity: 0;
  transform: translateY(5px);
}

.public-page-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}
</style>
