<script setup>
defineProps({
  tags: { type: Array, default: () => [] },
  max: { type: Number, default: 5 },
  color: { type: String, default: '#6366f1' },
})
</script>

<template>
  <div class="tag-list">
    <span v-for="tag in tags.slice(0, max)" :key="tag" class="tag">{{ tag }}</span>
    <span v-if="tags.length > max" class="tag tag-more">+{{ tags.length - max }}</span>
  </div>
</template>

<style scoped>
.tag-list { display: flex; flex-wrap: wrap; gap: 4px; }
.tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
  color: v-bind(color);
  background: color-mix(in srgb, v-bind(color) 12%, transparent);
}
.tag-more { background: #f3f4f6; color: #9ca3af; font-size: 11px; padding: 2px 8px; border-radius: 10px; }
</style>
