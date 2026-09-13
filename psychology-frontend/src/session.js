const KEYS = ['token', 'userId', 'username', 'role', 'tabId']

function ensureTabId() {
  let tabId = sessionStorage.getItem('tabId')
  if (tabId) return tabId
  try {
    tabId = self.crypto ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2)
  } catch (e) {
    tabId = Date.now().toString(36) + Math.random().toString(36).slice(2)
  }
  sessionStorage.setItem('tabId', tabId)
  return tabId
}

export function saveLoginSession(data, fallbackUsername = '') {
  const tabId = ensureTabId()
  sessionStorage.setItem('username', data.username || fallbackUsername || '')
  sessionStorage.setItem('role', data.role || '')
  sessionStorage.setItem('userId', data.userId == null ? '' : String(data.userId))
  if (data.token) {
    sessionStorage.setItem('token', data.token)
  }
  return getLoginInfo()
}

export function clearLoginSession() {
  KEYS.forEach(key => sessionStorage.removeItem(key))
}

export function getLoginInfo() {
  const userIdRaw = sessionStorage.getItem('userId')
  const token = sessionStorage.getItem('token')
  if (!userIdRaw || userIdRaw === 'undefined' || userIdRaw === 'null') {
    return null
  }
  const userId = parseInt(userIdRaw, 10)
  if (!userId) {
    return null
  }
  return {
    userId,
    username: sessionStorage.getItem('username') || '',
    role: sessionStorage.getItem('role') || '',
    token: token || '',
    tabId: sessionStorage.getItem('tabId') || ''
  }
}

export function isLoggedIn() {
  return !!(getLoginInfo() && sessionStorage.getItem('token'))
}

export default {
  saveLoginSession,
  clearLoginSession,
  getLoginInfo,
  isLoggedIn
}
