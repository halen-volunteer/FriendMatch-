<script setup>
import { Close } from '@element-plus/icons-vue'

defineProps({
  title: {
    type: String,
    required: true,
  },
})

defineEmits(['close'])
</script>

<template>
  <div class="tool-panel">
    <div class="panel-head">
      <strong>{{ title }}</strong>
      <el-button type="text" size="small" @click="$emit('close')">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>
    <div class="panel-body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.tool-panel {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e8e8e8;
  border-radius: 14px;
  padding: 12px;
  max-height: 280px;
  min-height: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.panel-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-head strong {
  color: var(--wx-text);
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
