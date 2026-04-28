<script setup>
import { computed } from 'vue'

const props = defineProps({
  avatar: { type: String, default: '' },
  status: { type: Number, default: 0 }, // 0离线 1在线 2离开 3忙碌 4隐身
  size: { type: Number, default: 40 },
})
const statusColor = { 0: '#9ca3af', 1: '#10b981', 2: '#f59e0b', 3: '#ef4444', 4: '#9ca3af' }
const fallback = 'https://api.dicebear.com/7.x/fun-emoji/svg?seed=default'
const wrapperSize = computed(() => `${props.size}px`)
const dotColor = computed(() => statusColor[props.status] || statusColor[0])
</script>

<template>
  <div class="avatar-wrap">
    <img
      :src="props.avatar || fallback"
      :width="props.size"
      :height="props.size"
      class="avatar-img"
      @error="(e) => e.target.src = fallback"
    />
    <span v-if="props.status > 0" class="status-dot" />
  </div>
</template>

<style scoped>
.avatar-wrap {
  position: relative;
  display: inline-block;
  width: v-bind(wrapperSize);
  height: v-bind(wrapperSize);
}

.avatar-img {
  border-radius: 50%;
  object-fit: cover;
  display: block;
}

.status-dot {
  position: absolute;
  right: 1px;
  bottom: 1px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid #fff;
  display: block;
  background: v-bind(dotColor);
}
</style>
