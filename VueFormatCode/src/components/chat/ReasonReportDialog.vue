<script setup>
const model = defineModel({
  default: () => ({ visible: false, reason: 1, content: '' }),
})

defineProps({
  title: { type: String, default: '' },
  reasonLabel: { type: String, default: '举报原因' },
  contentLabel: { type: String, default: '补充说明' },
  contentPlaceholder: { type: String, default: '' },
  options: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  submitText: { type: String, default: '提交' },
  width: { type: String, default: '460px' },
})

const emit = defineEmits(['submit'])

function handleClose() {
  model.value.visible = false
}

function handleSubmit() {
  emit('submit')
}
</script>

<template>
  <el-dialog v-model="model.visible" :title="title" :width="width" @close="handleClose">
    <div class="form-group">
      <label>{{ reasonLabel }}</label>
      <el-select v-model="model.reason" class="modal-input">
        <el-option
          v-for="option in options"
          :key="option.value"
          :value="option.value"
          :label="option.label"
        />
      </el-select>
    </div>
    <div class="form-group">
      <label>{{ contentLabel }}</label>
      <el-input
        v-model="model.content"
        type="textarea"
        :rows="4"
        :placeholder="contentPlaceholder"
        class="modal-input"
      />
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button :disabled="loading" @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">{{ submitText }}</el-button>
      </span>
    </template>
  </el-dialog>
</template>
