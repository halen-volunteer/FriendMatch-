import { computed } from 'vue'
import { FRIEND_SECTIONS } from '@/composables/useFriendsHelpers'

export function useFriendsQueryState(route, router) {
  const section = computed(() => {
    const value = String(route.query.section || 'friends')
    return FRIEND_SECTIONS.includes(value) ? value : 'friends'
  })

  const itemId = computed(() => String(route.query.itemId || ''))

  const isFriendsSection = computed(() => section.value === 'friends')
  const isRequestsSection = computed(() => section.value === 'requests')
  const isTeamsSection = computed(() => section.value === 'teams')
  const isBlacklistSection = computed(() => section.value === 'blacklist')
  const isSquareSection = computed(() => section.value === 'square')

  function routeToSection(nextSection, nextItemId) {
    const query = { section: nextSection }
    if (nextItemId !== undefined && nextItemId !== null && String(nextItemId) !== '') {
      query.itemId = String(nextItemId)
    }
    router.replace({ path: '/friends', query })
  }

  return {
    isBlacklistSection,
    isFriendsSection,
    isRequestsSection,
    isSquareSection,
    isTeamsSection,
    itemId,
    routeToSection,
    section,
  }
}
