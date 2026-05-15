<script setup>
import { computed, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: [String, Array], default: '' },
  placeholder: { type: String, default: '输入标签后回车，可用逗号一次输入多个标签' },
  max: { type: Number, default: 10 },
})

const emit = defineEmits(['update:modelValue'])

const draft = ref('')
const tags = ref([])

function normalize(input) {
  const source = Array.isArray(input) ? input : String(input || '').split(/[，,]/)
  const unique = []
  source.forEach((item) => {
    const value = String(item || '').trim()
    if (value && !unique.includes(value)) {
      unique.push(value)
    }
  })
  return unique.slice(0, props.max)
}

function syncToParent() {
  emit('update:modelValue', tags.value.join(','))
}

watch(
  () => props.modelValue,
  (value) => {
    const next = normalize(value)
    if (next.join('|') !== tags.value.join('|')) {
      tags.value = next
    }
  },
  { immediate: true },
)

const canAddMore = computed(() => tags.value.length < props.max)

function appendDraftTags() {
  if (!draft.value.trim() || !canAddMore.value) return
  const merged = normalize([...tags.value, ...draft.value.split(/[，,]/)])
  tags.value = merged
  draft.value = ''
  syncToParent()
}

function removeTag(tag) {
  tags.value = tags.value.filter(item => item !== tag)
  syncToParent()
}

function handleKeydown(event) {
  if (event.key === 'Enter' || event.key === ',' || event.key === '，') {
    event.preventDefault()
    appendDraftTags()
  }
}

function handleBlur() {
  appendDraftTags()
}
</script>

<template>
  <div class="tag-input">
    <div v-if="tags.length" class="tag-list">
      <span v-for="tag in tags" :key="tag" class="tag-chip">
        <span>{{ tag }}</span>
        <button type="button" class="chip-close" @click="removeTag(tag)">
          <el-icon><Close /></el-icon>
        </button>
      </span>
    </div>

    <el-input
      v-model="draft"
      :disabled="!canAddMore"
      :placeholder="canAddMore ? placeholder : `最多添加 ${max} 个标签`"
      @keydown="handleKeydown"
      @blur="handleBlur"
    />
  </div>
</template>

<style scoped>
.tag-input {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 10px 0 12px;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
}

.chip-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 50%;
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
  cursor: pointer;
  padding: 0;
}

.chip-close:hover {
  background: rgba(37, 99, 235, 0.2);
}
</style>
