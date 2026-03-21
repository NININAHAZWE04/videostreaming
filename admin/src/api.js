const RAW_ENV_BASE = import.meta.env.VITE_ADMIN_API_URL || import.meta.env.VITE_API_URL || 'http://localhost:18081'
const DEFAULT_BASE = normalizeBaseUrl(RAW_ENV_BASE, 'http://localhost:18081')
const SECRET_KEY = 'vs.admin.secret'
const API_BASE_KEY = 'vs.admin.api'
const ENV_SECRET = String(import.meta.env.VITE_ADMIN_SECRET || '').trim()
const LEGACY_BASES = new Set([
  'http://localhost:18081', ,
])

export function normalizeBaseUrl(value, fallback = DEFAULT_BASE) {
  const raw = String(value || '').trim()
  if (!raw) return fallback
  try {
    const url = new URL(raw)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return fallback
    return url.origin
  } catch {
    return fallback
  }
}

export function getApiBaseUrl() {
  const persisted = String(localStorage.getItem(API_BASE_KEY) || '').trim()
  const normalized = normalizeBaseUrl(persisted, DEFAULT_BASE)
  if (!persisted || LEGACY_BASES.has(normalized)) {
    localStorage.setItem(API_BASE_KEY, DEFAULT_BASE)
    return DEFAULT_BASE
  }
  return normalized
}

export function setApiBaseUrl(value) {
  const normalized = normalizeBaseUrl(value, DEFAULT_BASE)
  localStorage.setItem(API_BASE_KEY, normalized)
  return normalized
}

export function getSecret() {
  const persisted = String(localStorage.getItem(SECRET_KEY) || '').trim()
  return persisted || ENV_SECRET
}

export function setSecret(s) {
  const value = String(s || '').trim()
  if (value) localStorage.setItem(SECRET_KEY, value)
  else localStorage.removeItem(SECRET_KEY)
}

const headers = (extra = {}) => ({
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${getSecret()}`,
  ...extra,
})

function encodeMeta(meta) {
  return btoa(unescape(encodeURIComponent(JSON.stringify(meta || {}))))
}

async function req(path, opts = {}) {
  const secret = getSecret()
  if (!secret) throw new Error('Secret admin manquant. Configurez-le dans Parametres.')

  const res = await fetch(`${getApiBaseUrl()}${path}`, {
    ...opts,
    headers: headers(opts.headers || {})
  })

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error(err.error || `HTTP ${res.status}`)
  }
  return res.json()
}

async function publicReq(path) {
  const res = await fetch(`${getApiBaseUrl()}${path}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

// ── Public ─────────────────────────────────────────────────
export const fetchVideosPublic = () => publicReq('/api/videos')
export const fetchCategories   = () => publicReq('/api/categories')
export const fetchHealth       = () => publicReq('/api/health')

// ── Admin — Videos ─────────────────────────────────────────
export const fetchAdminVideos  = ()        => req('/api/admin/videos')
export const createVideo       = (body)    => req('/api/admin/videos', { method: 'POST', body: JSON.stringify(body) })
export const updateVideo       = (id, body)=> req(`/api/admin/videos/${id}`, { method: 'PUT',  body: JSON.stringify(body) })
export const deleteVideo       = (id)      => req(`/api/admin/videos/${id}`, { method: 'DELETE' })
export const setVideoFree      = (id, free)=> req(`/api/admin/videos/${id}`, { method: 'PUT', body: JSON.stringify({ isFree: String(free) }) })
export const startVideoStream  = (id)      => req(`/api/admin/videos/${id}/stream`, { method: 'POST', body: '{}' })
export const stopVideoStream   = (id)      => req(`/api/admin/videos/${id}/stop`, { method: 'POST', body: '{}' })
export async function uploadVideo(file, meta = {}) {
  const secret = getSecret()
  if (!secret) throw new Error('Secret admin manquant. Configurez-le dans Parametres.')
  if (!(file instanceof File)) throw new Error('Fichier video invalide')

  const res = await fetch(`${getApiBaseUrl()}/api/admin/videos/upload`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${secret}`,
      'Content-Type': file.type || 'application/octet-stream',
      'X-Upload-Filename': file.name,
      'X-Video-Meta': encodeMeta(meta),
    },
    body: file,
  })

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error(err.error || `HTTP ${res.status}`)
  }
  return res.json()
}

// ── Admin — Categories ─────────────────────────────────────
export const fetchAdminCategories  = ()        => req('/api/admin/categories')
export const createCategory        = (body)    => req('/api/admin/categories', { method: 'POST', body: JSON.stringify(body) })
export const updateCategory        = (id, body)=> req(`/api/admin/categories/${id}`, { method: 'PUT',  body: JSON.stringify(body) })
export const deleteCategory        = (id)      => req(`/api/admin/categories/${id}`, { method: 'DELETE' })

// ── Admin — Stats ──────────────────────────────────────────
export const fetchStats        = ()  => req('/api/admin/stats')
export const fetchHourlyStats  = ()  => req('/api/admin/stats/hourly')

// ── Admin — Users ──────────────────────────────────────────
export const fetchAdminUsers       = ()            => req('/api/admin/users')
export const updateUser            = (id, body)    => req(`/api/admin/users/${id}`, { method: 'PUT', body: JSON.stringify(body) })
export const deleteUser            = (id)          => req(`/api/admin/users/${id}`, { method: 'DELETE' })
export const grantSubscription     = (id, body)    => req(`/api/admin/users/${id}/subscription`, { method: 'POST', body: JSON.stringify(body) })
export const revokeSubscription    = (id)          => req(`/api/admin/users/${id}/revoke`, { method: 'POST', body: '{}' })

// ── Admin — Subscriptions ──────────────────────────────────
export async function fetchAdminSubs(status) {
  const data = await req(`/api/admin/subscriptions${status ? `?status=${status}` : ''}`)
  return Array.isArray(data?.subscriptions) ? data.subscriptions : []
}

// ── Admin — Payments ───────────────────────────────────────
export const fetchAdminPayments    = (status) => req(`/api/admin/payments${status ? `?status=${status}` : ''}`)
export const approvePayment        = (id, body)    => req(`/api/admin/payments/${id}/approve`, { method: 'POST', body: JSON.stringify(body) })
export const rejectPayment         = (id, body)    => req(`/api/admin/payments/${id}/reject`,  { method: 'POST', body: JSON.stringify(body) })

// ── SSE ───────────────────────────────────────────────────
export function createEventSource(path, handlers) {
  const es = new EventSource(`${getApiBaseUrl()}${path}`)
  Object.entries(handlers).forEach(([evt, fn]) => {
    es.addEventListener(evt, e => {
      try { fn(JSON.parse(e.data)) } catch { fn(e.data) }
    })
  })
  es.onerror = () => {}
  return es
}
