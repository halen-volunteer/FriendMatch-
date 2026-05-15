<script setup>
</script>

<template>
  <div class="chat-home">
    <div class="content-empty">
      <div class="empty-icon">💬</div>
      <h3>选择一个会话开始聊天</h3>
    </div>
  </div>
</template>

<style scoped>
.chat-home {
  height: 100%;
  min-height: calc(100vh - 94px);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content-empty {
  text-align: center;
  color: #666;
  padding: 24px;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.content-empty h3 {
  font-size: 32px;
  color: #111;
  margin-bottom: 12px;
}

.content-empty p {
  font-size: 14px;
  color: #888;
}
</style>
