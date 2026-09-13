import { clearLoginSession } from './session'

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

export function apiUrl(path = '') {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE}${normalized}`
}

export function wsUrl(path = '/ws/chat') {
  const envWs = import.meta.env.VITE_WS_BASE_URL
  if (envWs) {
    const normalized = path.startsWith('/') ? path : `/${path}`
    return `${String(envWs).replace(/\/$/, '')}${normalized}`
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${path.startsWith('/') ? path : `/${path}`}`
}

function authHeaders(extra = {}) {
  const headers = { ...extra }
  const token = sessionStorage.getItem('token')
  if (token && !headers.Authorization) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

export async function apiFetch(path, options = {}) {
  const headers = authHeaders(options.headers || {})
  if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(apiUrl(path), { ...options, headers })
  if (response.status === 401) {
    clearLoginSession()
    if (!window.location.pathname.startsWith('/login')) {
      window.location.href = '/login'
    }
  }
  return response
}

export async function apiJson(path, options = {}) {
  const response = await apiFetch(path, options)
  const data = await response.json()
  return data
}

if (typeof window !== 'undefined' && !window.__AUTH_FETCH_PATCHED__) {
  const originalFetch = window.fetch.bind(window)
  window.fetch = (input, init = {}) => {
    const url = typeof input === 'string' ? input : (input && input.url) || ''
    const isApi = url.startsWith('/api') || url.includes('/api/')
    if (!isApi) {
      return originalFetch(input, init)
    }
    const headers = new Headers(init.headers || (input && input.headers) || {})
    const token = sessionStorage.getItem('token')
    if (token && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${token}`)
    }
    return originalFetch(input, { ...init, headers }).then(response => {
      if (response.status === 401) {
        clearLoginSession()
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
      }
      return response
    })
  }
  window.__AUTH_FETCH_PATCHED__ = true
}

export default {
  apiUrl,
  wsUrl,
  apiFetch,
  apiJson
}
