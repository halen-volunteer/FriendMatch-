export const FRIEND_SECTIONS = ['friends', 'requests', 'teams', 'square', 'blacklist']
export const DEFAULT_CONFIRM_TEXT = '确认'

export function createConfirmState() {
  return {
    visible: false,
    title: '',
    message: '',
    confirmText: DEFAULT_CONFIRM_TEXT,
    blackUserId: null,
    loading: false,
  }
}

export function normalizeFriend(item) {
  return {
    ...item,
    friendId: item.friendId ?? item.userId,
    userId: item.userId ?? item.friendId,
    userNickname: item.userNickname ?? item.friendRemark ?? item.nickname ?? '好友',
    userAvatar: item.userAvatar ?? item.avatar ?? '',
    userAccount: item.userAccount ?? item.account ?? '',
    userIntro: item.userIntro ?? '',
  }
}

export function normalizeRequest(item) {
  const target = item.target ?? item.userAccount ?? item.userEmail ?? ''
  const source = item.sourceText
    || item.source
    || (String(target).includes('@') ? '通过邮箱添加' : '通过账号添加')

  return {
    ...item,
    applicantId: item.applicantId ?? item.userId ?? item.friendId,
    userId: item.userId ?? item.applicantId ?? item.friendId,
    userNickname: item.userNickname ?? item.nickname ?? '新的朋友',
    userAvatar: item.userAvatar ?? item.avatar ?? '',
    userAccount: item.userAccount ?? item.account ?? target,
    applyMsg: item.applyMsg ?? item.message ?? '请求添加你为好友',
    source,
  }
}

export function normalizeBlacklist(item) {
  return {
    ...item,
    blackUserId: item.blackUserId ?? item.userId,
    userId: item.userId ?? item.blackUserId,
    userNickname: item.userNickname ?? item.nickname ?? '黑名单用户',
    userAvatar: item.userAvatar ?? item.avatar ?? '',
    userAccount: item.userAccount ?? item.account ?? '',
    userIntro: item.userIntro ?? '',
  }
}

export function normalizeTeam(item) {
  return {
    ...item,
    id: item.id ?? item.teamId,
    teamIntro: item.teamIntro ?? '',
    maxMember: item.maxMember ?? 0,
    memberCount: item.memberCount ?? 0,
  }
}

export function filterJoinedTeams(list = []) {
  return list.filter((item) => Number(item?.isMember) === 1)
}

export function getUserInitial(name, fallback = '友') {
  return name?.charAt(0) || fallback
}

export function getUserAccount(account) {
  return account || '未知'
}

export function getUserBio(userIntro) {
  return userIntro || '这个人很安静，什么都没有留下。'
}

export function getTeamInitial(name) {
  return name?.charAt(0) || '团'
}

export function getTeamMemberText(memberCount, maxMember) {
  return `${memberCount || 0}/${maxMember || 0} 人`
}

export function getTeamIntro(teamIntro) {
  return teamIntro || '暂无简介'
}

export function getTeamTypeText(teamType) {
  return teamType === 1 ? '公开' : '私有'
}

export function getJoinRuleText(joinRule) {
  if (joinRule === 1) return '申请审批'
  if (joinRule === 2) return '仅邀请'
  return '密码加入'
}
