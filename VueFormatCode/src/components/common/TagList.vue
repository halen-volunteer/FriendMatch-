<script setup>
import { computed } from 'vue'

const props = defineProps({
  tags: { type: [Array, String], default: () => [] },
  max: { type: Number, default: 8 },
  color: { type: String, default: '#3b82f6' },
  emptyText: { type: String, default: '' },
})

const normalizedTags = computed(() => {
  const source = Array.isArray(props.tags) ? props.tags : String(props.tags || '').split(/[，,]/)
  return source
    .map(tag => String(tag || '').trim())
    .filter(Boolean)
})
</script>

<template>
  <div v-if="normalizedTags.length" class="tag-list">
    <span v-for="tag in normalizedTags.slice(0, max)" :key="tag" class="tag">{{ tag }}</span>
    <span v-if="normalizedTags.length > max" class="tag tag-more">+{{ normalizedTags.length - max }}</span>
  </div>
  <div v-else-if="emptyText" class="tag-empty">{{ emptyText }}</div>
</template>

<style scoped>
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 12px;
  border: 1px solid color-mix(in srgb, v-bind(color) 35%, white);
  border-radius: 999px;
  background: color-mix(in srgb, v-bind(color) 10%, white);
  color: v-bind(color);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.tag-more {
  border-color: #d8dee9;
  background: #f6f8fb;
  color: #8a94a6;
}

.tag-empty {
  color: #8a8a8a;
  font-size: 13px;
}
</style>
