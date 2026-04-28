<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  width: { type: String, default: '440px' },
})
const emit = defineEmits(['close'])
const modalWidth = computed(() => props.width)
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-card">
      <div class="modal-head">
        <strong>{{ title }}</strong>
        <button class="modal-close" @click="emit('close')">×</button>
      </div>
      <div class="modal-body">
        <slot />
      </div>
      <div v-if="$slots.footer" class="modal-footer">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-mask { position: fixed; inset: 0; background: rgba(32, 14, 51, 0.38); backdrop-filter: blur(8px); display: flex; align-items: center; justify-content: center; z-index: 2000; padding: 20px; }
.modal-card { width: v-bind(modalWidth); max-width: calc(100vw - 40px); background: var(--wx-panel-strong); border: 1px solid var(--wx-line); border-radius: 22px; box-shadow: 0 24px 80px rgba(32, 14, 51, 0.24); overflow: hidden; }
.modal-head { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px 14px; border-bottom: 1px solid rgba(76, 72, 16, 0.08); }
.modal-head strong { font-size: 16px; color: var(--wx-text); }
.modal-close { border: none; background: rgba(255, 245, 114, 0.22); width: 34px; height: 34px; border-radius: 999px; font-size: 20px; color: var(--wx-muted); }
.modal-body { padding: 18px 20px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 10px; padding: 0 20px 20px; }
</style>
