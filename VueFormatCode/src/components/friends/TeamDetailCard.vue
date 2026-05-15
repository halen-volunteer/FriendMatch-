<script setup>
import TagList from '@/components/common/TagList.vue'

defineProps({
  team: { type: Object, default: null },
  getTeamInitial: { type: Function, required: true },
  getTeamMemberText: { type: Function, required: true },
  getTeamIntro: { type: Function, required: true },
  getTeamTypeText: { type: Function, required: true },
  getJoinRuleText: { type: Function, required: true },
})

defineEmits(['enter-team'])
</script>

<template>
  <div class="detail-card team-card-detail">
    <div class="team-cover">
      <el-avatar :size="88" :src="team?.teamAvatar">
        {{ getTeamInitial(team?.teamName) }}
      </el-avatar>
      <div class="team-main">
        <h3>{{ team?.teamName }}</h3>
        <p>{{ getTeamMemberText(team?.memberCount, team?.maxMember) }}</p>
      </div>
    </div>

    <div class="info-grid">
      <div class="info-row">
        <span class="label">团队简介</span>
        <span class="value">{{ getTeamIntro(team?.teamIntro) }}</span>
      </div>
      <div class="info-row">
        <span class="label">团队标签</span>
        <span class="value">
          <TagList :tags="team?.teamTags" empty-text="暂无标签" />
        </span>
      </div>
      <div class="info-row">
        <span class="label">团队类型</span>
        <span class="value">{{ getTeamTypeText(team?.teamType) }}</span>
      </div>
      <div class="info-row">
        <span class="label">加入方式</span>
        <span class="value">{{ getJoinRuleText(team?.joinRule) }}</span>
      </div>
    </div>

    <div class="action-row">
      <el-button type="success" @click="$emit('enter-team')">进入团队</el-button>
    </div>
  </div>
</template>

<style scoped>
.detail-card {
  max-width: 760px;
}

.team-card-detail {
  padding-top: 18px;
}

.team-cover {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid #f0f0f0;
}

.team-main h3 {
  margin: 0 0 6px;
  color: #202020;
  font-size: 28px;
}

.team-main p {
  margin: 0;
  color: #8d8d8d;
  font-size: 16px;
}

.info-grid {
  margin-top: 12px;
}

.info-row {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 18px;
  padding: 18px 0;
  border-bottom: 1px solid #f3f3f3;
}

.label {
  color: #7d7d7d;
  font-size: 16px;
}

.value {
  color: #202020;
  font-size: 18px;
  line-height: 1.6;
}

.action-row {
  display: flex;
  gap: 12px;
  padding-top: 26px;
}
</style>
