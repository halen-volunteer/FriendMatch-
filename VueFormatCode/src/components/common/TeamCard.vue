<script setup>
import TagList from './TagList.vue'

defineProps({
  team: { type: Object, required: true },
})
const emit = defineEmits(['apply', 'join-password', 'view'])
const joinRuleMap = { 1: '申请加入', 2: '仅邀请', 3: '密码加入' }

function handleJoin(team) {
  if (team.joinRule === 1) emit('apply', team)
  else if (team.joinRule === 3) emit('join-password', team)
}
</script>

<template>
  <div class="team-card" @click="emit('view', team)">
    <div class="team-header">
      <div class="team-avatar">{{ team.teamName?.charAt(0) }}</div>
      <div class="team-info">
        <h3 class="team-name">{{ team.teamName }}</h3>
        <span class="team-count">{{ team.memberCount || 0 }}/{{ team.maxMember }} 人</span>
      </div>
    </div>
    <div class="team-content">
      <p class="team-intro"><span class="content-label">简介：</span>{{ team.teamIntro || '暂无简介' }}</p>
      <div class="team-tag-row">
        <span class="content-label">标签：</span>
        <TagList :tags="team.teamTags" empty-text="暂无标签" />
      </div>
    </div>
    <div class="team-footer">
      <span class="join-rule">{{ team.teamType === 1 ? '公开团队' : '私有团队' }}</span>
      <button class="join-btn" :disabled="team.joinRule === 2" @click.stop="handleJoin(team)">{{ joinRuleMap[team.joinRule] }}</button>
    </div>
  </div>
</template>

<style scoped>
.team-card { border-radius: 20px; padding: 18px; min-height: 220px; }
.team-header { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.team-avatar { width: 46px; height: 46px; background: linear-gradient(135deg, var(--color-complement-2), var(--color-accent2-2)); border-radius: 14px; display: grid; place-items: center; color: #fffef4; font-weight: 700; font-size: 20px; flex-shrink: 0; }
.team-name { font-weight: 700; color: var(--wx-text); font-size: 16px; }
.team-count { font-size: 12px; color: var(--wx-muted); }
.team-content { display: flex; flex-direction: column; gap: 12px; min-height: 96px; }
.content-label { font-size: 12px; color: var(--wx-text); font-weight: 700; }
.team-intro { font-size: 12px; color: var(--wx-text-2); margin: 0; line-height: 1.7; }
.team-tag-row { display: flex; flex-direction: column; gap: 8px; }
.team-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; }
.join-rule { font-size: 12px; font-weight: 600; color: var(--color-accent2-3); }
.join-btn { padding: 7px 14px; border: none; border-radius: 999px; font-size: 12px; cursor: pointer; font-weight: 600; background: linear-gradient(135deg, var(--color-accent1-3), var(--color-primary-3)); color: #fffef4; }
.join-btn:disabled { background: rgba(76, 72, 16, 0.12); color: var(--wx-muted); cursor: not-allowed; }
</style>
