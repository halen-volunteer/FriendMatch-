<script setup>
import AvatarWithStatus from './AvatarWithStatus.vue'
import TagList from './TagList.vue'

defineProps({
  user: { type: Object, required: true },
  actions: { type: Array, default: () => [] },
})
</script>

<template>
  <div class="user-card">
    <AvatarWithStatus :avatar="user.userAvatar" :status="user.onlineStatus || 0" :size="46" />
    <div class="user-info">
      <span class="nickname">{{ user.userNickname || user.userAccount }}</span>
      <p v-if="user.userIntro" class="intro">{{ user.userIntro }}</p>
      <TagList v-if="user.userTags" :tags="user.userTags.split(',').filter(Boolean)" />
    </div>
    <div v-if="actions.length" class="actions">
      <button v-for="action in actions" :key="action.label" :class="['action-btn', action.type || 'default']" @click="action.handler(user)">{{ action.label }}</button>
    </div>
  </div>
</template>

<style scoped>
.user-card { display: flex; align-items: center; gap: 14px; padding: 14px; border-radius: 18px; }
.user-info { flex: 1; min-width: 0; }
.nickname { font-weight: 700; color: var(--wx-text); font-size: 15px; }
.intro { color: var(--wx-muted); font-size: 12px; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.actions { display: flex; gap: 8px; flex-shrink: 0; }
.action-btn { padding: 7px 12px; border-radius: 999px; font-size: 12px; cursor: pointer; border: none; font-weight: 600; }
.action-btn.primary { background: linear-gradient(135deg, var(--color-accent1-3), var(--color-primary-3)); color: #fffef4; }
.action-btn.danger { background: rgba(188, 84, 172, 0.12); color: var(--color-accent2-4); }
.action-btn.default { background: rgba(255, 245, 114, 0.26); color: var(--wx-text-2); }
</style>
