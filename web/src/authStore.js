// Client auth store — JWT + user state persisted in localStorage

const TOKEN_KEY  = 'vs.token'
const USER_KEY   = 'vs.user'
const API_KEY    = 'vs.api'
const RAW_ENV_API_URL = import.meta.env.VITE_WEB_API_URL || import.meta.env.VITE_API_URL || 'http://localhost:18081'
export const DEFAULT_API_URL = normalizeApiBaseUrl(RAW_ENV_API_URL)
const LEGACY_API_BASES = new Set([
  'http://localhost:8081',
  'http://127.0.0.1:8081',
  'http://localhost:8080',
  'http://127.0.0.1:8080',
])

export function normalizeApiBaseUrl(value) {
  const raw = String(value || '').trim()
  const fallback = 'http://localhost:18081' //DEFAULT_API_URL
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
  const persisted = String(localStorage.getItem(API_KEY) || '').trim()
  const normalized = normalizeApiBaseUrl(persisted)
  if (!persisted || LEGACY_API_BASES.has(normalized)) {
    localStorage.setItem(API_KEY, DEFAULT_API_URL)
    return DEFAULT_API_URL
  }
  return normalized
}

export function setApiBaseUrl(value) {
  const normalized = normalizeApiBaseUrl(value)
  localStorage.setItem(API_KEY, normalized)
  return normalized
}

// ── Token management ───────────────────────────────────────
export function getToken()      { return localStorage.getItem(TOKEN_KEY) }
export function setToken(t)     { if (t) localStorage.setItem(TOKEN_KEY, t); else localStorage.removeItem(TOKEN_KEY) }
export function getUser()       { try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch { return null } }
export function setUser(u)      { if (u) localStorage.setItem(USER_KEY, JSON.stringify(u)); else localStorage.removeItem(USER_KEY) }
export function isLoggedIn()    { return !!getToken() && !!getUser() }
export function logout()        { setToken(null); setUser(null) }

function authHeaders() {
  const t = getToken()
  return { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) }
}

async function authReq(path, opts = {}) {
  const res = await fetch(`${getApiBaseUrl()}${path}`, {
    ...opts,
    headers: { ...authHeaders(), ...(opts.headers || {}) }
  })

  const contentType = res.headers.get('content-type') || ''
  let data
  if (contentType.includes('application/json')) {
    data = await res.json().catch(() => null)
  } else {
    const text = await res.text().catch(() => '')
    data = text ? { error: text } : null
  }

  if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`)
  return data || {}
}

// ── Auth actions ───────────────────────────────────────────
export async function register(email, username, password) {
  const data = await authReq('/api/auth/register', {
    method: 'POST', body: JSON.stringify({ email, username, password })
  })
  if (data.token) { setToken(data.token); setUser(data.user) }
  return data
}

export async function login(email, password) {
  const data = await authReq('/api/auth/login', {
    method: 'POST', body: JSON.stringify({ email, password })
  })
  if (data.token) { setToken(data.token); setUser(data.user) }
  return data
}

export async function fetchMe() {
  if (!isLoggedIn()) return null
  const data = await authReq('/api/auth/me')
  setUser(data)
  return data
}

export async function startTrial() {
  const data = await authReq('/api/auth/trial', { method: 'POST', body: '{}' })
  if (data.token) { setToken(data.token); setUser(data.user) }
  return data
}

export async function requestPayment(plan, amount, currency, proofNote) {
  return authReq('/api/auth/payment/request', {
    method: 'POST', body: JSON.stringify({ plan, amount: String(amount), currency, proofNote })
  })
}

export async function getPaymentStatus() {
  return authReq('/api/auth/payment/status')
}

export async function getDownloadToken(videoId) {
  return authReq('/api/auth/download-token', {
    method: 'POST', body: JSON.stringify({ videoId: String(videoId) })
  })
}

// ── User helpers ────────────────────────────────────────────
export function userHasAccess(user) {
  return user && (user.hasSubscription || user.role === 'admin')
}

export function userCanDownload(user) {
  return user && user.hasSubscription && user.subPlan !== 'free'
}

export const DEFAULT_PLANS = [
  {
    id: 'trial',
    name: 'Essai Gratuit',
    price: 0,
    period: '14 jours',
    highlight: false,
    badge: 'Aucune carte',
    features: ['Accès complet 14 jours', 'Tous les films', 'Qualité HD', 'Non renouvelable'],
  },
  {
    id: 'monthly',
    name: 'Mensuel',
    price: 9.99,
    period: 'par mois',
    highlight: false,
    badge: null,
    features: ['Accès illimité', 'Tous les films', 'Téléchargement', 'Qualité 4K'],
  },
  {
    id: 'annual',
    name: 'Annuel',
    price: 79.99,
    period: 'par an',
    highlight: true,
    badge: '−33%',
    features: ['Accès illimité', 'Tous les films', 'Téléchargement', 'Qualité 4K', 'Économisez 40€'],
  },
]

let plansCache = [...DEFAULT_PLANS]

export function getPlans() {
  return plansCache
}

export async function fetchPlans() {
  try {
    const rows = await authReq('/api/auth/plans')
    if (!Array.isArray(rows) || rows.length === 0) return plansCache
    const mapped = rows.map((p) => {
      const fallback = DEFAULT_PLANS.find(x => x.id === p.id)
      const durationDays = Number(p.durationDays) || fallback?.durationDays || 30
      const period = durationDays >= 365 ? 'par an' : durationDays <= 14 ? `${durationDays} jours` : 'par mois'
      return {
        id: p.id,
        name: fallback?.name || p.id,
        price: Number(p.price || 0),
        period,
        durationDays,
        highlight: fallback?.highlight || false,
        badge: fallback?.badge || null,
        features: fallback?.features || ['Accès illimité'],
      }
    })
    plansCache = mapped
    return mapped
  } catch {
    return plansCache
  }
}

