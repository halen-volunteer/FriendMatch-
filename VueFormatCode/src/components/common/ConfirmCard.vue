<script setup>
import AppModal from '@/components/common/AppModal.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '' },
  confirmText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' },
  loading: { type: Boolean, default: false },
  danger: { type: Boolean, default: false },
})

const emit = defineEmits(['update:visible', 'confirm', 'cancel'])

function handleClose() {
  if (props.loading) return
  emit('update:visible', false)
  emit('cancel')
}

function handleConfirm() {
  if (props.loading) return
  emit('confirm')
}
</script>

<template>
  <AppModal v-if="visible" :title="title" width="420px" @close="handleClose">
    <div class="confirm-card">
      <div class="confirm-icon" :class="{ danger }">
        {{ danger ? '!' : '?' }}
      </div>
      <p class="confirm-message">{{ message }}</p>
    </div>

    <template #footer>
      <el-button round :disabled="loading" @click="handleClose">{{ cancelText }}</el-button>
      <el-button round :type="danger ? 'danger' : 'primary'" :loading="loading" @click="handleConfirm">
        {{ confirmText }}
      </el-button>
    </template>
  </AppModal>
</template>

<style scoped>
.confirm-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 6px 4px 2px;
  text-align: center;
}

.confirm-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(64, 158, 255, 0.12);
  color: #409eff;
  font-size: 24px;
  font-weight: 700;
}

.confirm-icon.danger {
  background: rgba(245, 108, 108, 0.12);
  color: #f56c6c;
}

.confirm-message {
  margin: 0;
  color: var(--wx-text, #303133);
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
}
</style>
