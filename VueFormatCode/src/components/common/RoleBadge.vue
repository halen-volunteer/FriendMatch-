<script setup>
const props = defineProps({ roleType: { type: Number, default: 3 } })
const roleMap = {
  1: { label: '队长' },
  2: { label: '管理员' },
  3: { label: '成员' },
}
</script>
<template>
  <span class="role-badge" :class="`role-${props.roleType}`">{{ roleMap[props.roleType]?.label || '成员' }}</span>
</template>

<style scoped>
.role-badge { padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; }
.role-1 { background: #fef3c7; color: #d97706; }
.role-2 { background: #ede9fe; color: #7c3aed; }
.role-3 { background: #f3f4f6; color: #6b7280; }
</style>
