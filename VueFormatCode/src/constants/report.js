export const MESSAGE_REPORT_REASON_OPTIONS = [
  { value: 1, label: '色情低俗' },
  { value: 2, label: '暴力血腥' },
  { value: 3, label: '辱骂骚扰' },
  { value: 4, label: '广告营销' },
  { value: 5, label: '诈骗引流' },
  { value: 6, label: '其他原因' },
]

export const USER_REPORT_REASON_OPTIONS = [
  { value: 1, label: '垃圾信息' },
  { value: 2, label: '欺诈行为' },
  { value: 3, label: '色情内容' },
  { value: 4, label: '暴力内容' },
  { value: 5, label: '侵犯隐私' },
  { value: 6, label: '其他原因' },
]

export const TEAM_REPORT_REASON_OPTIONS = [
  { value: 1, label: '垃圾信息' },
  { value: 2, label: '诈骗行为' },
  { value: 3, label: '色情内容' },
  { value: 4, label: '暴力内容' },
  { value: 5, label: '其他原因' },
]

export const REPORT_REASON_LABELS = {
  message: Object.fromEntries(MESSAGE_REPORT_REASON_OPTIONS.map((item) => [item.value, item.label])),
  user: Object.fromEntries(USER_REPORT_REASON_OPTIONS.map((item) => [item.value, item.label])),
  team: Object.fromEntries(TEAM_REPORT_REASON_OPTIONS.map((item) => [item.value, item.label])),
}

export const REPORT_TYPE_LABELS = {
  user: '用户举报',
  message: '消息举报',
  team: '团队举报',
}

export function normalizeReportType(reportType) {
  if (reportType === 1 || reportType === '1' || reportType === 'user') return 'user'
  if (reportType === 2 || reportType === '2' || reportType === 'message') return 'message'
  if (reportType === 3 || reportType === '3' || reportType === 'team') return 'team'
  return 'user'
}

export function getReportReasonLabel(reportType, reason) {
  const type = normalizeReportType(reportType)
  return REPORT_REASON_LABELS[type]?.[Number(reason)] || '其他原因'
}

export function getReportTypeLabel(reportType) {
  return REPORT_TYPE_LABELS[normalizeReportType(reportType)] || '举报记录'
}
