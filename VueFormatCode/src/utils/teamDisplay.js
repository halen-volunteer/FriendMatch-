export function getTeamTypeText(teamType) {
  return teamType === 1 ? '公开' : '私有'
}

export function getJoinRuleText(joinRule) {
  if (joinRule === 1) return '申请审批'
  if (joinRule === 2) return '仅邀请'
  return '密码加入'
}

export function getTeamMemberText(memberCount, maxMember) {
  return `${memberCount || 0}/${maxMember || 0} 人`
}
