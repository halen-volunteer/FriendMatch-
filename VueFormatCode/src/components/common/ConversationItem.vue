<script setup>
import AvatarWithStatus from './AvatarWithStatus.vue'

defineProps({
  name: { type: String, default: '' },
  avatar: { type: String, default: '' },
  lastMsg: { type: String, default: '' },
  unreadCount: { type: Number, default: 0 },
  time: { type: String, default: '' },
  active: { type: Boolean, default: false },
})

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return d.toTimeString().slice(0, 5)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>

<template>
  <div :class="['conv-item', { active }]">
    <div class="avatar-wrap">
      <AvatarWithStatus :avatar="avatar" :size="44" />
      <span v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </div>
    <div class="conv-info">
      <div class="conv-top">
        <span class="conv-name">{{ name }}</span>
        <span class="conv-time">{{ formatTime(time) }}</span>
      </div>
      <p class="conv-last">{{ lastMsg || '暂无消息' }}</p>
    </div>
  </div>
</template>

<style scoped>
.conv-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; cursor: pointer; transition: background .15s; border-bottom: 1px solid #efefef; background: #f5f5f5; }
.conv-item:hover { background: #efefef; }
.conv-item.active { background: #07c160; }
.conv-item.active .conv-name, .conv-item.active .conv-time, .conv-item.active .conv-last { color: #fff; }
.avatar-wrap { position: relative; flex-shrink: 0; }
.badge { position: absolute; top: -4px; right: -4px; background: #fa5151; color: #fff; font-size: 10px; min-width: 18px; height: 18px; border-radius: 999px; display: grid; place-items: center; padding: 0 4px; border: 2px solid #fff; }
.conv-info { flex: 1; min-width: 0; }
.conv-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.conv-name { font-weight: 500; font-size: 15px; color: #111; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-time { font-size: 11px; color: #999; flex-shrink: 0; }
.conv-last { font-size: 13px; color: #8a8a8a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 4px; }
</style>
