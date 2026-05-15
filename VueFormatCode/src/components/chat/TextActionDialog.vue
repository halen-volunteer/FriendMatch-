<script setup>
const model = defineModel({
  default: () => ({ visible: false, note: '' }),
})

defineProps({
  title: { type: String, default: '' },
  label: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  submitText: { type: String, default: '提交' },
  width: { type: String, default: '460px' },
  rows: { type: Number, default: 4 },
  field: { type: String, default: 'note' },
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
    <div v-if="label" class="form-group">
      <label>{{ label }}</label>
      <el-input
        v-model="model[field]"
        type="textarea"
        :rows="rows"
        :placeholder="placeholder"
        class="modal-input"
      />
    </div>
    <el-input
      v-else
      v-model="model[field]"
      type="textarea"
      :rows="rows"
      :placeholder="placeholder"
      class="modal-input"
    />
    <template #footer>
      <span class="dialog-footer">
        <el-button :disabled="loading" @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">{{ submitText }}</el-button>
      </span>
    </template>
  </el-dialog>
</template>
