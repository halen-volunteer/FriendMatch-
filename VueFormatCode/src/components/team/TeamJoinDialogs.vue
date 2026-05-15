<script setup>
const props = defineProps({
  applyVisible: { type: Boolean, default: false },
  applyTeam: { type: Object, default: null },
  applyMsg: { type: String, default: '' },
  applySubmitting: { type: Boolean, default: false },
  joinVisible: { type: Boolean, default: false },
  joinTeam: { type: Object, default: null },
  joinPwd: { type: String, default: '' },
  joinSubmitting: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:applyVisible',
  'update:applyMsg',
  'submit-apply',
  'update:joinVisible',
  'update:joinPwd',
  'submit-join',
])

function closeApplyDialog() {
  emit('update:applyVisible', false)
}

function closeJoinDialog() {
  emit('update:joinVisible', false)
}
</script>

<template>
  <el-dialog
    :model-value="applyVisible"
    :title="`申请加入「${applyTeam?.teamName || ''}」`"
    width="400px"
    @update:model-value="emit('update:applyVisible', $event)"
  >
    <el-form :model="{ applyMsg }">
      <el-form-item label="申请留言（选填）">
        <el-input
          :model-value="applyMsg"
          type="textarea"
          rows="3"
          placeholder="请输入申请留言"
          @update:model-value="emit('update:applyMsg', $event)"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer-row">
        <el-button @click="closeApplyDialog">取消</el-button>
        <el-button type="primary" :loading="applySubmitting" :disabled="applySubmitting" @click="emit('submit-apply')">
          发送申请
        </el-button>
      </div>
    </template>
  </el-dialog>

  <el-dialog
    :model-value="joinVisible"
    :title="`加入「${joinTeam?.teamName || ''}」`"
    width="400px"
    @update:model-value="emit('update:joinVisible', $event)"
  >
    <el-form :model="{ joinPwd }">
      <el-form-item label="入团密码" required>
        <el-input
          :model-value="joinPwd"
          type="password"
          placeholder="请输入入团密码"
          show-password
          @update:model-value="emit('update:joinPwd', $event)"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer-row">
        <el-button @click="closeJoinDialog">取消</el-button>
        <el-button type="primary" :loading="joinSubmitting" :disabled="joinSubmitting" @click="emit('submit-join')">
          加入
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>
