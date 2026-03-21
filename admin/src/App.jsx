import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, BarChart, Bar, Cell } from 'recharts'
import * as api from './api.js'
import { Dashboard, Film, Tag, BarChart2, Terminal, Settings, Play, Square, Plus, Trash2, Edit, Eye, Search, X, Check, Wifi, WifiOff, AlertCircle, Clock, HardDrive, Activity, Users, Zap, RefreshCw, Upload, Lock, Layers, CreditCard, UserCheck, Calendar, Cpu } from './icons.jsx'
import UsersPage from './UsersPage.jsx'
import PaymentsPage from './PaymentsPage.jsx'
import SubscriptionsPage from './SubscriptionsPage.jsx'

/* ──────────────────────────────────────────────────────────────
   UTILITIES
────────────────────────────────────────────────────────────── */
function useLocalStorage(key, defaultVal) {
  const [val, setVal] = useState(() => {
    try { const s = localStorage.getItem(key); return s ? JSON.parse(s) : defaultVal }
    catch { return defaultVal }
  })
  const set = v => { setVal(v); localStorage.setItem(key, JSON.stringify(v)) }
  return [val, set]
}

function toast(msg, type = 'info') {
  const el = document.createElement('div')
  el.className = `fixed bottom-6 right-6 z-[9999] flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium shadow-2xl animate-slide-in`
  el.style.cssText = `background:${type==='error'?'#2d1515':type==='success'?'#0d2518':'#111827'};color:${type==='error'?'#fca5a5':type==='success'?'#6ee7b7':'#e4e4f0'};border:1px solid ${type==='error'?'rgba(248,113,113,0.3)':type==='success'?'rgba(52,211,153,0.3)':'rgba(255,255,255,0.08)'}`
  el.textContent = msg
  document.body.appendChild(el)
  setTimeout(() => el.remove(), 3500)
}

const QUALITY_COLORS = { '4K':'#a78bfa','1080p':'#38bdf8','720p':'#34d399','480p':'#fbbf24','SD':'#94a3b8' }
const fmtBytes = b => b >= 1e9 ? (b/1e9).toFixed(1)+'Go' : b >= 1e6 ? (b/1e6).toFixed(0)+'Mo' : b >= 1e3 ? (b/1e3).toFixed(0)+'Ko' : b+'o'
const fmtDuration = s => { if (!s) return '--'; const h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sc=s%60; return h>0?`${h}:${String(m).padStart(2,'0')}:${String(sc).padStart(2,'0')}`:`${m}:${String(sc).padStart(2,'0')}` }
const timeAgo = ts => { if (!ts) return '—'; const d=(Date.now()-new Date(ts))/1000; if(d<60)return'à l\'instant'; if(d<3600)return`il y a ${Math.floor(d/60)}min`; if(d<86400)return`il y a ${Math.floor(d/3600)}h`; return`il y a ${Math.floor(d/86400)}j` }
const normalizeMediaUrl = (url) => {
  if (!url) return null
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  if (url.startsWith('/')) return `${api.getApiBaseUrl()}${url}`
  return `${api.getApiBaseUrl()}/${url}`
}
const normalizeVideo = (v) => ({
  ...v,
  streamUrl: normalizeMediaUrl(v.streamUrl) || (v.id && v.active ? `${api.getApiBaseUrl()}/api/media/${v.id}/stream` : null),
  thumbnailUrl: normalizeMediaUrl(v.thumbnailUrl) || (v.id ? `${api.getApiBaseUrl()}/api/media/${v.id}/thumbnail` : null),
})

/* ──────────────────────────────────────────────────────────────
   SHARED COMPONENTS
────────────────────────────────────────────────────────────── */
function Spinner({ size = 'w-5 h-5' }) {
  return <div className={`${size} border-2 rounded-full spinner`} style={{ borderColor: 'var(--brand-dim)', borderTopColor: 'var(--brand)' }} />
}

function StatusDot({ online }) {
  return <span className={`w-2 h-2 rounded-full flex-shrink-0 ${online ? 'bg-green-400 live-dot' : 'bg-red-400'}`} />
}

function Modal({ title, onClose, children, wide }) {
  useEffect(() => {
    const fn = e => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', fn)
    document.body.style.overflow = 'hidden'
    return () => { window.removeEventListener('keydown', fn); document.body.style.overflow = '' }
  }, [onClose])
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)' }} onClick={onClose}>
      <div className={`card animate-fade-in w-full ${wide ? 'max-w-2xl' : 'max-w-md'} max-h-[90vh] flex flex-col`} onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-5 py-4 border-b" style={{ borderColor: 'var(--border)' }}>
          <h2 className="text-base font-semibold" style={{ color: 'var(--text)' }}>{title}</h2>
          <button onClick={onClose} className="btn-ghost p-1.5 rounded-lg"><X /></button>
        </div>
        <div className="overflow-y-auto flex-1 px-5 py-4">{children}</div>
      </div>
    </div>
  )
}

function ConfirmModal({ msg, onConfirm, onCancel }) {
  return (
    <Modal title="Confirmer la suppression" onClose={onCancel}>
      <p className="text-sm mb-6" style={{ color: 'var(--text-muted)' }}>{msg}</p>
      <div className="flex gap-3 justify-end">
        <button className="btn-ghost btn" onClick={onCancel}>Annuler</button>
        <button className="btn-danger btn" onClick={onConfirm}>Supprimer</button>
      </div>
    </Modal>
  )
}

function StatCard({ label, value, sub, icon: Icon, color = 'var(--brand)', trend }) {
  return (
    <div className="stat-card animate-fade-in">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium uppercase tracking-widest" style={{ color: 'var(--text-muted)' }}>{label}</span>
        <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: `${color}18`, color }}>
          <Icon className="w-4 h-4" />
        </div>
      </div>
      <div>
        <div className="text-3xl font-semibold tracking-tight" style={{ color: 'var(--text)' }}>{value ?? '—'}</div>
        {sub && <div className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>{sub}</div>}
      </div>
    </div>
  )
}

function QualityBadge({ label }) {
  if (!label) return null
  return <span className="badge text-[10px]" style={{ background: `${QUALITY_COLORS[label]}18`, color: QUALITY_COLORS[label] }}>{label}</span>
}

function Thumbnail({ video }) {
  const [err, setErr] = useState(false)
  if (!video.thumbnailUrl || err) return (
    <div className="w-full h-full flex items-center justify-center rounded-md" style={{ background: 'var(--surface2)' }}>
      <Film className="w-6 h-6" style={{ color: 'var(--text-dim)' }} />
    </div>
  )
  return <img src={video.thumbnailUrl} alt={video.title} className="w-full h-full object-cover rounded-md" onError={() => setErr(true)} />
}

/* ──────────────────────────────────────────────────────────────
   PAGE — DASHBOARD
────────────────────────────────────────────────────────────── */
function DashboardPage({ health, stats, hourly, loading }) {
  const chartData = useMemo(() => {
    const now = new Date().getHours()
    return Array.from({ length: 24 }, (_, i) => {
      const h = (now - 23 + i + 24) % 24
      const found = hourly?.find(x => x.hour === h)
      return { hour: `${String(h).padStart(2,'0')}h`, views: found?.views ?? 0 }
    })
  }, [hourly])

  const topVideos = stats?.topVideos ?? []

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold" style={{ color: 'var(--text)' }}>Dashboard</h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>Vue d'ensemble en temps réel</p>
        </div>
        <div className="flex items-center gap-2 text-xs px-3 py-1.5 rounded-lg" style={{ background: 'var(--surface)', border: '1px solid var(--border)', color: health ? 'var(--success)' : 'var(--danger)' }}>
          <StatusDot online={!!health} />
          {health ? 'API connectée' : 'API hors ligne'}
        </div>
      </div>

      {/* KPI Grid */}
      <div className="grid grid-cols-2 lg:grid-cols-6 gap-4">
        <StatCard label="Vidéos totales" value={stats?.totalVideos ?? '—'} icon={Film} color="var(--brand)" sub="en base de données" />
        <StatCard label="Streams actifs" value={stats?.activeStreams ?? 0} icon={Wifi} color="var(--success)" sub="en diffusion maintenant" />
        <StatCard label="Clients live" value={stats?.activeClients ?? 0} icon={Users} color="#22d3ee" sub={stats?.maxClients ? `limite ${stats.maxClients}` : 'connectés'} />
        <StatCard label="Vues aujourd'hui" value={stats?.viewsToday ?? 0} icon={Eye} color="#a78bfa" sub="depuis minuit" />
        <StatCard label="Bande passante" value={stats ? fmtBytes(Math.round((stats.bandwidthMb??0)*1048576)) : '—'} icon={Activity} color="var(--warning)" sub="total transmis" />
        <StatCard label="Utilisateurs" value={stats?.totalUsers ?? 0} icon={UserCheck} color="#60a5fa" sub={`${stats?.subscribedUsers ?? 0} abonnés`} />
        <StatCard label="Paiements" value={stats?.pendingPayments ?? 0} icon={CreditCard} color="#fbbf24" sub="en attente" />
      </div>

      {/* Health details */}
      {health && (
        <div className="card p-4 grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
          {[
            { label: 'Mémoire JVM', val: `${health.jvmUsedMb}Mo / ${health.jvmTotalMb}Mo`, icon: Cpu ?? Activity },
            { label: 'Clients SSE', val: health.sseClients ?? 0, icon: Users },
            { label: 'Vidéos totales', val: health.totalVideos ?? 0, icon: HardDrive },
            { label: 'Streams actifs', val: health.activeStreams ?? 0, icon: Zap },
          ].map(({ label, val, icon: Icon }) => (
            <div key={label} className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: 'var(--surface2)', color: 'var(--text-muted)' }}>
                <Icon />
              </div>
              <div>
                <div className="text-xs" style={{ color: 'var(--text-muted)' }}>{label}</div>
                <div className="font-medium" style={{ color: 'var(--text)' }}>{val}</div>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Views chart */}
        <div className="card p-5 lg:col-span-2">
          <div className="flex items-center justify-between mb-5">
            <div className="text-sm font-medium" style={{ color: 'var(--text)' }}>Vues — dernières 24h</div>
            <span className="text-xs" style={{ color: 'var(--text-muted)' }}>par heure</span>
          </div>
          {loading ? <div className="flex justify-center py-10"><Spinner /></div> : (
            <ResponsiveContainer width="100%" height={180}>
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="grad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="var(--brand)" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="var(--brand)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="hour" tick={{ fontSize: 10, fill: 'var(--text-dim)' }} axisLine={false} tickLine={false} interval={5} />
                <YAxis tick={{ fontSize: 10, fill: 'var(--text-dim)' }} axisLine={false} tickLine={false} width={25} />
                <Tooltip contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 12, color: 'var(--text)' }} />
                <Area type="monotone" dataKey="views" stroke="var(--brand)" strokeWidth={2} fill="url(#grad)" dot={false} />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Top videos */}
        <div className="card p-5">
          <div className="text-sm font-medium mb-4" style={{ color: 'var(--text)' }}>Top vidéos</div>
          {topVideos.length === 0
            ? <p className="text-xs text-center py-8" style={{ color: 'var(--text-dim)' }}>Aucune donnée</p>
            : <div className="space-y-3">
                {topVideos.map((v, i) => (
                  <div key={v.id} className="flex items-center gap-3">
                    <span className="text-xs w-4 font-mono" style={{ color: 'var(--text-dim)' }}>#{i+1}</span>
                    <div className="flex-1 min-w-0">
                      <div className="text-xs font-medium truncate" style={{ color: 'var(--text)' }}>{v.title}</div>
                      <div className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>{v.viewCount} vue{v.viewCount !== 1 ? 's' : ''}</div>
                    </div>
                    <QualityBadge label={v.qualityLabel} />
                  </div>
                ))}
              </div>
          }
        </div>
      </div>
    </div>
  )
}

/* ──────────────────────────────────────────────────────────────
   PAGE — VIDEOS (CRUD)
────────────────────────────────────────────────────────────── */
function VideoForm({ video, categories, onSave, onClose }) {
  const [form, setForm] = useState({
    title: video?.title ?? '',
    synopsis: video?.synopsis ?? '',
    categoryId: video?.categoryId ? String(video.categoryId) : '',
    tags: video?.tags ?? '',
    isFree: video?.free ? 'true' : 'false',
  })
  const [saving, setSaving] = useState(false)
  const [file, setFile] = useState(null)
  const [dragging, setDragging] = useState(false)
  const fileInputRef = useRef(null)

  const f = k => e => setForm(p => ({ ...p, [k]: e.target.value }))

  const pickFile = nextFile => {
    if (!nextFile) return
    if (!nextFile.type.startsWith('video/') && !/\.(mp4|mkv|avi|mov|webm|m4v)$/i.test(nextFile.name)) {
      toast('Selectionnez un fichier video valide', 'error')
      return
    }
    setFile(nextFile)
    setForm(prev => prev.title.trim() ? prev : ({ ...prev, title: nextFile.name.replace(/\.[^.]+$/, '') }))
  }

  const submit = async () => {
    if (!form.title.trim()) { toast('Le titre est requis', 'error'); return }
    if (!video?.id && !file) { toast('Selectionnez une video a uploader', 'error'); return }
    setSaving(true)
    try {
      if (video?.id) await api.updateVideo(video.id, form)
      else await api.uploadVideo(file, form)
      toast(video?.id ? 'Vidéo mise à jour' : 'Vidéo uploadée', 'success')
      onSave()
    } catch (e) { toast(e.message, 'error') }
    finally { setSaving(false) }
  }

  return (
    <Modal title={video?.id ? 'Modifier la vidéo' : 'Nouvelle vidéo'} onClose={onClose} wide>
      <div className="max-w-xl mx-auto w-full space-y-4">
        {/* Thumbnail preview if editing */}
        {video?.thumbnailUrl && (
          <div className="aspect-video w-full rounded-lg overflow-hidden bg-zinc-900 relative">
            <img src={video.thumbnailUrl} alt="" className="w-full h-full object-cover" onError={e => e.target.style.display='none'} />
            <div className="absolute bottom-2 left-2 flex gap-2">
              {video.qualityLabel && <QualityBadge label={video.qualityLabel} />}
              {video.formattedDuration && <span className="badge text-[10px]" style={{ background:'rgba(0,0,0,0.7)',color:'#fff' }}>{video.formattedDuration}</span>}
            </div>
          </div>
        )}

        {/* Auto-detected metadata */}
        {video?.id && (
          <div className="rounded-lg p-3 text-xs grid grid-cols-2 gap-2" style={{ background: 'var(--surface2)', border: '1px solid var(--border)' }}>
            {[
              ['Résolution', video.resolution],
              ['Codec', video.codec],
              ['FPS', video.fps > 0 ? video.fps.toFixed(1) : null],
              ['Bitrate', video.bitrateKbps > 0 ? `${video.bitrateKbps} Kbps` : null],
              ['Taille', video.formattedSize],
              ['Durée', video.formattedDuration],
              ['Vues', video.viewCount],
              ['Fichier', video.filePath?.split('/').pop()],
            ].filter(([,v]) => v).map(([label, val]) => (
              <div key={label}>
                <span style={{ color: 'var(--text-dim)' }}>{label}: </span>
                <span style={{ color: 'var(--text-muted)' }}>{val}</span>
              </div>
            ))}
          </div>
        )}

        {!video?.id && (
          <div>
            <label className="label">Film à uploader *</label>
            <input
              ref={fileInputRef}
              type="file"
              accept="video/*,.mp4,.mkv,.avi,.mov,.webm,.m4v"
              className="hidden"
              onChange={e => pickFile(e.target.files?.[0])}
            />
            <div
              className="rounded-xl border-2 border-dashed p-6 text-center transition-all cursor-pointer"
              style={{
                borderColor: dragging ? 'var(--brand)' : 'var(--border)',
                background: dragging ? 'var(--brand-dim)' : 'var(--surface2)'
              }}
              onClick={() => fileInputRef.current?.click()}
              onDragOver={e => { e.preventDefault(); setDragging(true) }}
              onDragLeave={e => { e.preventDefault(); setDragging(false) }}
              onDrop={e => {
                e.preventDefault()
                setDragging(false)
                pickFile(e.dataTransfer.files?.[0])
              }}
            >
              <Upload className="w-8 h-8 mx-auto mb-3" style={{ color: 'var(--brand)' }} />
              <p className="text-sm font-medium" style={{ color: 'var(--text)' }}>
                {file ? file.name : 'Glissez-déposez votre film ici'}
              </p>
              <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
                ou cliquez pour sélectionner un fichier vidéo (MP4, MKV, AVI, MOV, WEBM)
              </p>
              {file && (
                <p className="text-xs mt-3" style={{ color: 'var(--brand)' }}>
                  {(file.size / 1024 / 1024).toFixed(1)} Mo sélectionné(s)
                </p>
              )}
            </div>
          </div>
        )}

        <div>
          <label className="label">Titre *</label>
          <input className="input" value={form.title} onChange={f('title')} placeholder="Titre de la vidéo" />
        </div>

        <div>
          <label className="label">Catégorie</label>
          <select className="input" value={form.categoryId} onChange={f('categoryId')}>
            <option value="">— Sans catégorie —</option>
            {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>

        <div>
          <label className="label">Synopsis <span style={{ color: 'var(--text-dim)' }}>(optionnel)</span></label>
          <textarea className="input resize-none" rows={3} value={form.synopsis} onChange={f('synopsis')} placeholder="Courte description de la vidéo…" />
        </div>

        <div>
          <label className="label">Tags <span style={{ color: 'var(--text-dim)' }}>(séparés par des virgules)</span></label>
          <input className="input" value={form.tags} onChange={f('tags')} placeholder="action, film, 2024" />
        </div>

        <div className="flex items-center justify-between p-3 rounded-lg" style={{ background: 'var(--surface2)', border: '1px solid var(--border)' }}>
          <div>
            <div className="text-sm font-medium" style={{ color: 'var(--text)' }}>Accès gratuit</div>
            <div className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>Visible sans abonnement</div>
          </div>
          <button onClick={() => setForm(p => ({ ...p, isFree: p.isFree === 'true' ? 'false' : 'true' }))}
            className="w-11 h-6 rounded-full transition-all relative"
            style={{ background: form.isFree === 'true' ? 'var(--success)' : 'var(--border-hover)' }}>
            <span className="absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-all"
              style={{ left: form.isFree === 'true' ? '22px' : '2px' }} />
          </button>
        </div>

        <div className="flex gap-3 pt-2">
          <button className="btn-ghost btn flex-1" onClick={onClose}>Annuler</button>
          <button className="btn btn-primary flex-1" onClick={submit} disabled={saving}>
            {saving ? <Spinner size="w-4 h-4" /> : <Check />}
            {saving ? 'Enregistrement…' : 'Enregistrer'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function VideosPage({ onRefreshStats }) {
  const [videos, setVideos]     = useState([])
  const [cats, setCats]         = useState([])
  const [loading, setLoading]   = useState(true)
  const [search, setSearch]     = useState('')
  const [filterCat, setFilterCat] = useState('')
  const [filterActive, setFilterActive] = useState('all')
  const [editing, setEditing]   = useState(null)
  const [creating, setCreating] = useState(false)
  const [confirm, setConfirm]   = useState(null)
  const [selected, setSelected] = useState(new Set())

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [vData, cData] = await Promise.all([api.fetchAdminVideos(), api.fetchAdminCategories()])
      const normalized = (Array.isArray(vData) ? vData : []).map(normalizeVideo)
      setVideos(normalized)
      setCats(Array.isArray(cData) ? cData : [])
    } catch (e) { toast(e.message, 'error') }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { load() }, [load])

  // SSE: auto-refresh when video_added/removed
  useEffect(() => {
    const es = api.createEventSource('/api/events', {
      video_added:   () => load(),
      video_removed: () => load(),
      stream_started:() => { load(); onRefreshStats?.() },
      stream_stopped:() => { load(); onRefreshStats?.() },
    })
    return () => es.close()
  }, [load, onRefreshStats])

  const filtered = useMemo(() => videos.filter(v => {
    const q = search.toLowerCase()
    const matchQ = !q || v.title.toLowerCase().includes(q) || (v.tags||'').toLowerCase().includes(q)
    const matchCat = !filterCat || String(v.categoryId) === filterCat
    const matchActive = filterActive === 'all' || (filterActive === 'active' ? v.active : !v.active)
    return matchQ && matchCat && matchActive
  }), [videos, search, filterCat, filterActive])

  const del = async id => {
    try { await api.deleteVideo(id); toast('Vidéo supprimée', 'success'); load(); onRefreshStats?.() }
    catch (e) { toast(e.message, 'error') }
    finally { setConfirm(null) }
  }

  const toggleSelect = id => setSelected(s => {
    const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n
  })
  const selectAll = () => setSelected(new Set(filtered.map(v => v.id)))
  const clearSel  = () => setSelected(new Set())

  const bulkDelete = async () => {
    if (!selected.size) return
    for (const id of selected) { try { await api.deleteVideo(id) } catch {} }
    toast(`${selected.size} vidéo(s) supprimée(s)`, 'success')
    clearSel(); load(); onRefreshStats?.()
  }

  const toggleStream = async video => {
    try {
      if (video.active) {
        await api.stopVideoStream(video.id)
        toast('Stream retiré du catalogue client', 'success')
      } else {
        await api.startVideoStream(video.id)
        toast('Vidéo publiée côté client', 'success')
      }
      load()
      onRefreshStats?.()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  return (
    <div className="space-y-4 animate-fade-in">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-semibold" style={{ color: 'var(--text)' }}>Vidéos</h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>{videos.length} vidéo{videos.length !== 1 ? 's' : ''} en base</p>
        </div>
        <div className="flex items-center gap-2">
          {selected.size > 0 && (
            <button className="btn-danger btn" onClick={bulkDelete}><Trash2 />{selected.size} sélectionné(s)</button>
          )}
          <button className="btn btn-primary" onClick={() => setCreating(true)}><Plus />Nouvelle vidéo</button>
        </div>
      </div>

      {/* Filters */}
      <div className="card p-3 flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-2 flex-1 min-w-40">
          <Search style={{ color: 'var(--text-dim)', flexShrink: 0, width: 16, height: 16 }} />
          <input className="input !bg-transparent !border-0 !p-0 !text-sm flex-1 !focus:shadow-none" placeholder="Rechercher…" value={search} onChange={e => setSearch(e.target.value)} />
          {search && <button onClick={() => setSearch('')} style={{ color: 'var(--text-dim)' }}><X /></button>}
        </div>
        <select className="input w-44" value={filterCat} onChange={e => setFilterCat(e.target.value)}>
          <option value="">Toutes catégories</option>
          {cats.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select className="input w-36" value={filterActive} onChange={e => setFilterActive(e.target.value)}>
          <option value="all">Tous statuts</option>
          <option value="active">En stream</option>
          <option value="inactive">Inactif</option>
        </select>
        {filtered.length < videos.length && (
          <span className="text-xs px-2 py-1 rounded-md" style={{ background: 'var(--brand-dim)', color: 'var(--brand)' }}>{filtered.length} résultat{filtered.length!==1?'s':''}</span>
        )}
      </div>

      {/* Table */}
      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex justify-center py-16"><Spinner /></div>
        ) : filtered.length === 0 ? (
          <div className="py-16 text-center">
            <Film className="w-12 h-12 mx-auto mb-3" style={{ color: 'var(--text-dim)' }} />
            <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Aucune vidéo trouvée</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b text-left" style={{ borderColor: 'var(--border)' }}>
                  <th className="px-4 py-3 w-8">
                    <input type="checkbox" className="rounded" checked={selected.size===filtered.length && filtered.length>0} onChange={() => selected.size===filtered.length?clearSel():selectAll()} />
                  </th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Vidéo</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider hidden md:table-cell" style={{ color: 'var(--text-muted)' }}>Métadonnées</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider hidden lg:table-cell" style={{ color: 'var(--text-muted)' }}>Catégorie</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Statut</th>
                  <th className="px-4 py-3 text-xs font-medium uppercase tracking-wider" style={{ color: 'var(--text-muted)' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(v => (
                  <tr key={v.id} className="table-row">
                    <td className="px-4 py-3">
                      <input type="checkbox" className="rounded" checked={selected.has(v.id)} onChange={() => toggleSelect(v.id)} />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <div className="w-16 h-10 rounded-md overflow-hidden flex-shrink-0">
                          <Thumbnail video={v} />
                        </div>
                        <div className="min-w-0">
                          <div className="text-sm font-medium truncate max-w-48" style={{ color: 'var(--text)' }}>{v.title}</div>
                          {v.synopsis && <div className="text-xs truncate max-w-48 mt-0.5" style={{ color: 'var(--text-muted)' }}>{v.synopsis}</div>}
                          <div className="text-[11px] mt-0.5 font-mono" style={{ color: 'var(--text-dim)' }}>id:{v.id}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 hidden md:table-cell">
                      <div className="flex flex-wrap gap-1.5 items-center">
                        <QualityBadge label={v.qualityLabel} />
                        {v.formattedDuration && <span className="text-xs" style={{ color: 'var(--text-muted)' }}>{v.formattedDuration}</span>}
                        {v.formattedSize && <span className="text-xs" style={{ color: 'var(--text-muted)' }}>{v.formattedSize}</span>}
                        {v.resolution && <span className="text-xs font-mono" style={{ color: 'var(--text-dim)' }}>{v.resolution}</span>}
                      </div>
                      {v.viewCount > 0 && <div className="text-xs mt-1" style={{ color: 'var(--text-dim)' }}>{v.viewCount} vue{v.viewCount!==1?'s':''}</div>}
                    </td>
                    <td className="px-4 py-3 hidden lg:table-cell">
                      {v.categoryName ? (
                        <span className="badge text-xs" style={{ background: `${v.categoryColor||'#6366f1'}18`, color: v.categoryColor||'#6366f1' }}>{v.categoryName}</span>
                      ) : <span className="text-xs" style={{ color: 'var(--text-dim)' }}>—</span>}
                    </td>
                    <td className="px-4 py-3">
                      {v.active
                        ? <span className="badge text-xs" style={{ background: 'rgba(52,211,153,0.12)', color: 'var(--success)' }}><span className="live-dot w-1.5 h-1.5 rounded-full bg-green-400 inline-block" /> Live</span>
                        : <span className="badge text-xs" style={{ background: 'var(--surface2)', color: 'var(--text-muted)' }}>Inactif</span>
                      }
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1">
                        <button className="btn-ghost btn p-1.5" onClick={() => setEditing(v)} title="Modifier"><Edit /></button>
                        <button
                          className="btn-ghost btn p-1.5"
                          onClick={() => toggleStream(v)}
                          title={v.active ? 'Retirer du client' : 'Publier côté client'}
                        >
                          {v.active ? <Square /> : <Play />}
                        </button>
                        {v.streamUrl && <a href={v.streamUrl} target="_blank" rel="noreferrer" className="btn-ghost btn p-1.5" title="Voir le stream"><Eye /></a>}
                        <button className="btn-danger btn p-1.5" onClick={() => setConfirm(v)} title="Supprimer"><Trash2 /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {creating && <VideoForm categories={cats} onSave={() => { setCreating(false); load(); onRefreshStats?.() }} onClose={() => setCreating(false)} />}
      {editing  && <VideoForm video={editing} categories={cats} onSave={() => { setEditing(null); load() }} onClose={() => setEditing(null)} />}
      {confirm  && <ConfirmModal msg={`Supprimer "${confirm.title}" définitivement ?`} onConfirm={() => del(confirm.id)} onCancel={() => setConfirm(null)} />}
    </div>
  )
}

/* ──────────────────────────────────────────────────────────────
   PAGE — CATÉGORIES
────────────────────────────────────────────────────────────── */
const PRESET_COLORS = ['#ef4444','#f97316','#eab308','#22c55e','#06b6d4','#3b82f6','#8b5cf6','#ec4899','#6366f1','#10b981']
const PRESET_ICONS  = ['film','zap','smile','book-open','heart','skull','rocket','more-horizontal','star','music','monitor','camera']

function CatForm({ cat, onSave, onClose }) {
  const [form, setForm] = useState({ name: cat?.name||'', color: cat?.color||'#6366f1', icon: cat?.icon||'film' })
  const [saving, setSaving] = useState(false)
  const f = k => v => setForm(p => ({ ...p, [k]: typeof v === 'string' ? v : v.target.value }))

  const submit = async () => {
    if (!form.name.trim()) { toast('Le nom est requis','error'); return }
    setSaving(true)
    try {
      if (cat?.id) await api.updateCategory(cat.id, form)
      else await api.createCategory(form)
      toast(cat?.id ? 'Catégorie mise à jour' : 'Catégorie créée', 'success')
      onSave()
    } catch (e) { toast(e.message,'error') }
    finally { setSaving(false) }
  }

  return (
    <Modal title={cat?.id ? 'Modifier la catégorie' : 'Nouvelle catégorie'} onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className="label">Nom *</label>
          <input className="input" value={form.name} onChange={f('name')} placeholder="Nom de la catégorie" />
        </div>
        <div>
          <label className="label">Couleur</label>
          <div className="flex flex-wrap gap-2 mb-2">
            {PRESET_COLORS.map(c => (
              <button key={c} onClick={() => f('color')(c)}
                className="w-7 h-7 rounded-full transition-transform hover:scale-110"
                style={{ background: c, outline: form.color===c ? `3px solid ${c}` : 'none', outlineOffset: 2 }} />
            ))}
          </div>
          <input type="color" className="w-full h-9 rounded-lg cursor-pointer" value={form.color} onChange={f('color')} />
        </div>
        <div>
          <label className="label">Aperçu</label>
          <div className="flex items-center gap-2 p-3 rounded-lg" style={{ background: 'var(--surface2)', border: '1px solid var(--border)' }}>
            <div className="w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold" style={{ background: `${form.color}20`, color: form.color }}>{form.name.charAt(0).toUpperCase() || '?'}</div>
            <span className="badge text-xs" style={{ background: `${form.color}18`, color: form.color }}>{form.name || 'Catégorie'}</span>
          </div>
        </div>
        <div className="flex gap-3 pt-2">
          <button className="btn-ghost btn flex-1" onClick={onClose}>Annuler</button>
          <button className="btn btn-primary flex-1" onClick={submit} disabled={saving}>
            {saving ? <Spinner size="w-4 h-4" /> : <Check />}
            {saving ? 'Enregistrement…' : 'Enregistrer'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function CategoriesPage() {
  const [cats, setCats] = useState([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(null)
  const [creating, setCreating] = useState(false)
  const [confirm, setConfirm] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try { setCats(await api.fetchAdminCategories()) }
    catch (e) { toast(e.message,'error') }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { load() }, [load])

  const del = async id => {
    try { await api.deleteCategory(id); toast('Catégorie supprimée','success'); load() }
    catch (e) { toast(e.message,'error') }
    finally { setConfirm(null) }
  }

  return (
    <div className="space-y-4 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold" style={{ color: 'var(--text)' }}>Catégories</h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>{cats.length} catégorie{cats.length!==1?'s':''}</p>
        </div>
        <button className="btn btn-primary" onClick={() => setCreating(true)}><Plus />Nouvelle catégorie</button>
      </div>

      {loading ? <div className="flex justify-center py-16"><Spinner /></div> : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {cats.map(c => (
            <div key={c.id} className="card p-4 flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl flex items-center justify-center text-lg font-bold flex-shrink-0" style={{ background: `${c.color}20`, color: c.color }}>
                {c.name.charAt(0)}
              </div>
              <div className="flex-1 min-w-0">
                <div className="font-medium truncate" style={{ color: 'var(--text)' }}>{c.name}</div>
                <div className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>{c.videoCount} vidéo{c.videoCount!==1?'s':''}</div>
              </div>
              <div className="flex items-center gap-1 flex-shrink-0">
                <button className="btn-ghost btn p-1.5" onClick={() => setEditing(c)}><Edit /></button>
                <button className="btn-danger btn p-1.5" onClick={() => setConfirm(c)}><Trash2 /></button>
              </div>
            </div>
          ))}
          <button className="card p-4 flex items-center gap-4 border-dashed hover:border-sky-500/40 transition-colors cursor-pointer" style={{ borderColor: 'var(--border)' }} onClick={() => setCreating(true)}>
            <div className="w-12 h-12 rounded-xl flex items-center justify-center" style={{ background: 'var(--surface2)' }}>
              <Plus style={{ color: 'var(--text-dim)', width: 20, height: 20 }} />
            </div>
            <span className="text-sm" style={{ color: 'var(--text-muted)' }}>Ajouter une catégorie</span>
          </button>
        </div>
      )}

      {creating && <CatForm onSave={() => { setCreating(false); load() }} onClose={() => setCreating(false)} />}
      {editing  && <CatForm cat={editing} onSave={() => { setEditing(null); load() }} onClose={() => setEditing(null)} />}
      {confirm  && <ConfirmModal msg={`Supprimer "${confirm.name}" ? Les vidéos associées perdront leur catégorie.`} onConfirm={() => del(confirm.id)} onCancel={() => setConfirm(null)} />}
    </div>
  )
}

/* ──────────────────────────────────────────────────────────────
   PAGE — MONITORING (streams actifs)
────────────────────────────────────────────────────────────── */
function MonitoringPage() {
  const [videos, setVideos] = useState([])
  const [health, setHealth] = useState(null)
  const [loading, setLoading] = useState(true)
  const [lastRefresh, setLastRefresh] = useState(null)
  const [selectedStreamId, setSelectedStreamId] = useLocalStorage('vs.monitoring.selectedStreamId', '')

  const load = useCallback(async () => {
    try {
      const [allV, h] = await Promise.all([api.fetchAdminVideos(), api.fetchHealth()])
      setVideos((Array.isArray(allV) ? allV : []).map(normalizeVideo))
      setHealth(h)
      setLastRefresh(new Date())
    } catch (e) { toast(e.message,'error') }
    finally { setLoading(false) }
  }, [])

  const activeVideos = useMemo(() => videos.filter(v => v.active), [videos])
  const selectedStream = useMemo(
    () => activeVideos.find(v => String(v.id) === String(selectedStreamId)) || activeVideos[0] || null,
    [activeVideos, selectedStreamId]
  )

  useEffect(() => {
    if (!activeVideos.length) {
      if (selectedStreamId) setSelectedStreamId('')
      return
    }
    const exists = activeVideos.some(v => String(v.id) === String(selectedStreamId))
    if (!exists) setSelectedStreamId(String(activeVideos[0].id))
  }, [activeVideos, selectedStreamId, setSelectedStreamId])

  useEffect(() => { load() }, [load])

  // SSE real-time updates
  useEffect(() => {
    const es = api.createEventSource('/api/events', {
      stream_started: () => load(),
      stream_stopped: () => load(),
    })
    return () => es.close()
  }, [load])

  // Auto-refresh every 10s
  useEffect(() => {
    const t = setInterval(load, 10000)
    return () => clearInterval(t)
  }, [load])

  return (
    <div className="space-y-4 animate-fade-in">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-semibold" style={{ color: 'var(--text)' }}>Monitoring</h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>
            {activeVideos.length} stream{activeVideos.length!==1?'s':''} actif{activeVideos.length!==1?'s':''}
            {lastRefresh && <span> · mis à jour {timeAgo(lastRefresh)}</span>}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex items-center gap-2">
            <label className="text-xs" style={{ color: 'var(--text-muted)' }}>Film à streamer</label>
            <select
              className="input w-56"
              value={selectedStream ? String(selectedStream.id) : ''}
              onChange={e => setSelectedStreamId(e.target.value)}
              disabled={!activeVideos.length}
            >
              {!activeVideos.length && <option value="">Aucun stream actif</option>}
              {activeVideos.map(v => <option key={v.id} value={v.id}>{v.title}</option>)}
            </select>
          </div>
          <button
            className="btn btn-primary"
            onClick={() => selectedStream?.streamUrl && window.open(selectedStream.streamUrl, '_blank', 'noopener,noreferrer')}
            disabled={!selectedStream?.streamUrl}
          >
            <Play />Ouvrir sélection
          </button>
          <button className="btn-ghost btn" onClick={load}><RefreshCw />Actualiser</button>
        </div>
      </div>

      {/* System health */}
      {health && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <StatCard label="Mémoire JVM" value={`${health.jvmUsedMb}Mo`} sub={`/${health.jvmTotalMb}Mo total`} icon={Activity} color="#a78bfa" />
          <StatCard label="Clients SSE" value={health.sseClients ?? 0} sub="connexions ouvertes" icon={Users} color="var(--brand)" />
          <StatCard label="Streams actifs" value={health.activeStreams ?? 0} sub="en diffusion" icon={Wifi} color="var(--success)" />
          <StatCard label="Vidéos en base" value={health.totalVideos ?? 0} sub="enregistrées" icon={HardDrive} color="var(--warning)" />
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-16"><Spinner /></div>
      ) : activeVideos.length === 0 ? (
        <div className="card py-16 text-center">
          <WifiOff className="w-12 h-12 mx-auto mb-3" style={{ color: 'var(--text-dim)' }} />
          <p className="text-sm font-medium" style={{ color: 'var(--text)' }}>Aucun stream actif</p>
          <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>Lancez le Streaming Provider pour démarrer un flux</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {activeVideos.map(v => (
            <div key={v.id} className="card p-4 space-y-3 animate-fade-in">
              <div className="aspect-video rounded-lg overflow-hidden relative bg-zinc-900">
                <Thumbnail video={v} />
                <div className="absolute top-2 left-2 flex items-center gap-1.5 px-2 py-0.5 rounded-md text-xs font-medium" style={{ background: 'rgba(0,0,0,0.7)', color: 'var(--success)' }}>
                  <span className="live-dot w-1.5 h-1.5 rounded-full bg-green-400 inline-block" />
                  LIVE
                </div>
                <div className="absolute bottom-2 right-2 flex gap-1">
                  <QualityBadge label={v.qualityLabel} />
                </div>
              </div>
              <div>
                <h3 className="font-medium text-sm truncate" style={{ color: 'var(--text)' }}>{v.title}</h3>
                <div className="flex items-center gap-2 mt-1.5 flex-wrap">
                  {v.formattedDuration && <span className="text-xs" style={{ color: 'var(--text-muted)' }}><Clock className="inline w-3 h-3 mr-0.5" />{v.formattedDuration}</span>}
                  {v.formattedSize && <span className="text-xs" style={{ color: 'var(--text-muted)' }}><HardDrive className="inline w-3 h-3 mr-0.5" />{v.formattedSize}</span>}
                  {v.resolution && <span className="text-xs font-mono" style={{ color: 'var(--text-dim)' }}>{v.resolution}</span>}
                </div>
              </div>
              <div className="pt-2 border-t" style={{ borderColor: 'var(--border)' }}>
                <div className="text-xs font-mono truncate" style={{ color: 'var(--brand)' }}>{v.streamUrl}</div>
                <div className="flex gap-2 mt-2">
                  <span className="text-xs" style={{ color: 'var(--text-muted)' }}>{v.viewCount} vue{v.viewCount!==1?'s':''}</span>
                  {v.categoryName && <span className="badge text-xs" style={{ background: `${v.categoryColor||'#6366f1'}18`, color: v.categoryColor||'#6366f1' }}>{v.categoryName}</span>}
                </div>
              </div>
              <a href={v.streamUrl} target="_blank" rel="noreferrer" className="btn btn-ghost w-full justify-center gap-2 text-xs py-2">
                <Play className="w-3 h-3" />Ouvrir le stream
              </a>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/* ──────────────────────────────────────────────────────────────
   PAGE — LOGS (SSE terminal)
────────────────────────────────────────────────────────────── */
const LOG_COLORS = { DEBUG: '#6b7280', INFO: '#38bdf8', WARN: '#fbbf24', ERROR: '#f87171' }

function LogsPage() {
  const [logs, setLogs] = useState([])
  const [filter, setFilter] = useState('ALL')
  const [search, setSearch] = useState('')
  const [autoScroll, setAutoScroll] = useLocalStorage('vs.logs.autoscroll', true)
  const [connected, setConnected] = useState(false)
  const bottomRef = useRef(null)
  const MAX_LOGS = 500

  useEffect(() => {
    const es = api.createEventSource('/api/logs', {
      log_entry: entry => {
        setLogs(prev => {
          const n = [...prev, entry]
          return n.length > MAX_LOGS ? n.slice(-MAX_LOGS) : n
        })
      }
    })
    es.onopen = () => setConnected(true)
    es.onerror = () => setConnected(false)
    return () => es.close()
  }, [])

  useEffect(() => {
    if (autoScroll) bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [logs, autoScroll])

  const filtered = useMemo(() => logs.filter(l => {
    const matchLevel = filter === 'ALL' || l.level === filter
    const matchSearch = !search || l.message.toLowerCase().includes(search.toLowerCase()) || l.component.toLowerCase().includes(search.toLowerCase())
    return matchLevel && matchSearch
  }), [logs, filter, search])

  return (
    <div className="space-y-4 animate-fade-in h-full flex flex-col">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-semibold" style={{ color: 'var(--text)' }}>Logs en direct</h1>
          <div className="flex items-center gap-2 mt-0.5">
            <StatusDot online={connected} />
            <span className="text-sm" style={{ color: 'var(--text-muted)' }}>{connected ? 'Connecté — SSE actif' : 'Déconnecté'}</span>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <button className="btn-ghost btn text-xs" onClick={() => setAutoScroll(p => !p)}>
            <Activity />
            {autoScroll ? 'Auto-scroll ON' : 'Auto-scroll OFF'}
          </button>
          <button className="btn-ghost btn text-xs" onClick={() => setLogs([])}>
            <Trash2 />Vider
          </button>
        </div>
      </div>

      {/* Controls */}
      <div className="flex items-center gap-3 flex-wrap">
        {['ALL','DEBUG','INFO','WARN','ERROR'].map(lvl => (
          <button key={lvl} onClick={() => setFilter(lvl)}
            className="badge text-xs cursor-pointer transition-all"
            style={{
              background: filter===lvl ? (LOG_COLORS[lvl]||'var(--brand)')+'22' : 'var(--surface)',
              color: filter===lvl ? (LOG_COLORS[lvl]||'var(--brand)') : 'var(--text-muted)',
              border: `1px solid ${filter===lvl ? (LOG_COLORS[lvl]||'var(--brand)')+'44' : 'var(--border)'}`,
              padding: '4px 10px',
            }}>
            {lvl} {lvl !== 'ALL' && <span style={{ opacity: 0.6 }}>({logs.filter(l=>l.level===lvl).length})</span>}
          </button>
        ))}
        <div className="flex items-center gap-2 flex-1 min-w-32 max-w-64">
          <Search style={{ color: 'var(--text-dim)', width: 14, height: 14, flexShrink: 0 }} />
          <input className="input !bg-transparent !border-0 !p-0 text-xs flex-1" placeholder="Filtrer les logs…" value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <span className="text-xs" style={{ color: 'var(--text-dim)' }}>{filtered.length} / {logs.length}</span>
      </div>

      {/* Terminal */}
      <div className="card flex-1 overflow-hidden flex flex-col" style={{ minHeight: 400 }}>
        <div className="flex items-center gap-2 px-4 py-2.5 border-b" style={{ borderColor: 'var(--border)' }}>
          <div className="flex gap-1.5">
            <div className="w-3 h-3 rounded-full bg-red-500/60" />
            <div className="w-3 h-3 rounded-full bg-yellow-500/60" />
            <div className="w-3 h-3 rounded-full bg-green-500/60" />
          </div>
          <span className="text-xs font-mono ml-2" style={{ color: 'var(--text-dim)' }}>videostreaming.log</span>
          {connected && <span className="ml-auto live-dot w-1.5 h-1.5 rounded-full bg-green-400 inline-block" />}
        </div>
        <div className="flex-1 overflow-y-auto p-4 font-mono text-xs leading-relaxed" style={{ background: '#060609' }}>
          {filtered.length === 0 ? (
            <span style={{ color: 'var(--text-dim)' }}>En attente de logs{connected ? '…' : ' — connexion SSE perdue'}</span>
          ) : filtered.map((l, i) => (
            <div key={i} className="flex gap-3 mb-0.5 hover:bg-white/3 rounded px-1 -mx-1">
              <span className="flex-shrink-0" style={{ color: '#3a3a50' }}>{l.ts?.split(' ')[1] ?? ''}</span>
              <span className="flex-shrink-0 w-12" style={{ color: LOG_COLORS[l.level] ?? '#6b7280' }}>{l.level}</span>
              <span className="flex-shrink-0 w-28 truncate" style={{ color: '#4a4a65' }}>[{l.component}]</span>
              <span style={{ color: '#c8c8e0' }}>{l.message}</span>
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
      </div>
    </div>
  )
}

/* ──────────────────────────────────────────────────────────────
   PAGE — SETTINGS
────────────────────────────────────────────────────────────── */
function SettingsPage() {
  const [secret, setSecretLocal] = useState(api.getSecret())
  const [apiBaseUrl, setApiBaseUrl] = useState(api.getApiBaseUrl())
  const [saved, setSaved] = useState(false)
  const [runtime, setRuntime] = useState({ streamingMaxConnectionsPerIp: 5, streamingMaxConcurrentClients: 150, planCurrency: 'USD' })
  const [plans, setPlans] = useState([])

  const loadRuntime = useCallback(async () => {
    try {
      const [settings, planRows] = await Promise.all([api.fetchAdminSettings(), api.fetchAdminPlans()])
      if (settings) setRuntime({
        streamingMaxConnectionsPerIp: settings.streamingMaxConnectionsPerIp ?? 5,
        streamingMaxConcurrentClients: settings.streamingMaxConcurrentClients ?? 150,
        planCurrency: settings.planCurrency ?? 'USD',
      })
      setPlans(Array.isArray(planRows) ? planRows : [])
    } catch (e) {
      toast(e.message, 'error')
    }
  }, [])

  useEffect(() => { loadRuntime() }, [loadRuntime])

  const save = () => {
    api.setSecret(secret)
    const normalizedApiBase = api.setApiBaseUrl(apiBaseUrl)
    setApiBaseUrl(normalizedApiBase)
    setSaved(true)
    toast('Configuration sauvegardée', 'success')
    setTimeout(() => setSaved(false), 2000)
  }

  const saveRuntime = async () => {
    try {
      await api.updateAdminSettings(runtime)
      toast('Parametres runtime sauvegardes', 'success')
      loadRuntime()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const savePlan = async (plan) => {
    try {
      await api.updateAdminPlan(plan.plan, { price: String(plan.price), durationDays: String(plan.durationDays), currency: plan.currency, active: String(plan.active) })
      toast(`Plan ${plan.plan} mis a jour`, 'success')
      loadRuntime()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  return (
    <div className="space-y-6 animate-fade-in max-w-lg">
      <div>
        <h1 className="text-xl font-semibold" style={{ color: 'var(--text)' }}>Paramètres</h1>
        <p className="text-sm mt-0.5" style={{ color: 'var(--text-muted)' }}>Configuration du panel d'administration</p>
      </div>

      <div className="card p-5 space-y-4">
        <div className="flex items-center gap-3 mb-2">
          <Lock style={{ color: 'var(--brand)', width: 18, height: 18 }} />
          <h2 className="font-medium" style={{ color: 'var(--text)' }}>Authentification API</h2>
        </div>
        <div>
          <label className="label">Token Bearer (admin.secret)</label>
          <input type="password" className="input font-mono" value={secret} onChange={e => setSecretLocal(e.target.value)} placeholder="Votre secret admin…" />
          <p className="text-xs mt-1.5" style={{ color: 'var(--text-muted)' }}>Doit correspondre à la valeur <code style={{ color: 'var(--brand)', fontFamily: 'JetBrains Mono, monospace' }}>admin.secret</code> dans application.properties</p>
        </div>
        <div>
          <label className="label">URL API Admin (host:port)</label>
          <input type="text" className="input font-mono" value={apiBaseUrl} onChange={e => setApiBaseUrl(e.target.value)} placeholder="http://localhost:8081" />
          <p className="text-xs mt-1.5" style={{ color: 'var(--text-muted)' }}>Utilisee pour les requetes /api/* du panel admin.</p>
        </div>
        <button className="btn btn-primary" onClick={save}>
          {saved ? <><Check />Sauvegardé</> : 'Sauvegarder'}
        </button>
      </div>

      <div className="card p-5 space-y-4">
        <div className="flex items-center gap-3 mb-1">
          <Activity style={{ color: '#34d399', width: 18, height: 18 }} />
          <h2 className="font-medium" style={{ color: 'var(--text)' }}>Limites et systeme</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="label">Connexions max / IP</label>
            <input className="input" value={runtime.streamingMaxConnectionsPerIp} onChange={e => setRuntime(p => ({ ...p, streamingMaxConnectionsPerIp: Number(e.target.value) || 1 }))} />
          </div>
          <div>
            <label className="label">Clients simultanes max</label>
            <input className="input" value={runtime.streamingMaxConcurrentClients} onChange={e => setRuntime(p => ({ ...p, streamingMaxConcurrentClients: Number(e.target.value) || 1 }))} />
          </div>
          <div>
            <label className="label">Devise</label>
            <input className="input" value={runtime.planCurrency} onChange={e => setRuntime(p => ({ ...p, planCurrency: e.target.value.toUpperCase() }))} />
          </div>
        </div>
        <button className="btn btn-primary" onClick={saveRuntime}><Check />Sauvegarder limites</button>
      </div>

      <div className="card p-5 space-y-3">
        <div className="flex items-center gap-3 mb-1">
          <CreditCard style={{ color: '#a78bfa', width: 18, height: 18 }} />
          <h2 className="font-medium" style={{ color: 'var(--text)' }}>Gestion des prix</h2>
        </div>
        <div className="space-y-3">
          {plans.map((p, idx) => (
            <div key={p.plan} className="rounded-lg p-3 border" style={{ borderColor: 'var(--border)', background: 'var(--surface2)' }}>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-2 items-end">
                <div>
                  <label className="label">Plan</label>
                  <input className="input" value={p.plan} disabled />
                </div>
                <div>
                  <label className="label">Prix</label>
                  <input className="input" value={p.price} onChange={e => setPlans(rows => rows.map((r, i) => i === idx ? { ...r, price: e.target.value } : r))} />
                </div>
                <div>
                  <label className="label">Duree (jours)</label>
                  <input className="input" value={p.durationDays} onChange={e => setPlans(rows => rows.map((r, i) => i === idx ? { ...r, durationDays: e.target.value } : r))} />
                </div>
                <button className="btn btn-primary" onClick={() => savePlan(p)}><Check />Appliquer</button>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="card p-5 space-y-3">
        <div className="flex items-center gap-3 mb-2">
          <Layers style={{ color: '#a78bfa', width: 18, height: 18 }} />
          <h2 className="font-medium" style={{ color: 'var(--text)' }}>Stack technique</h2>
        </div>
        {[
          ['Backend',    'Java 21 — RMI + HTTP'],
          ['Base de données', 'H2 2.2.220 — embedded'],
          ['API public', 'HTTP :18080 — GET /api/videos'],
          ['API admin',  'HTTP :18081 — Bearer auth'],
          ['SSE',        'Temps réel — push events'],
          ['Frontend',   'React 18 + Tailwind CSS'],
        ].map(([k, v]) => (
          <div key={k} className="flex justify-between text-sm">
            <span style={{ color: 'var(--text-muted)' }}>{k}</span>
            <span className="font-mono text-xs" style={{ color: 'var(--text)' }}>{v}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/* ──────────────────────────────────────────────────────────────
   ROOT APP
────────────────────────────────────────────────────────────── */
const NAV = [
  { id: 'dashboard',     label: 'Dashboard',    Icon: Dashboard },
  { id: 'videos',        label: 'Vidéos',       Icon: Film },
  { id: 'categories',    label: 'Catégories',   Icon: Tag },
  { id: 'monitoring',    label: 'Monitoring',   Icon: BarChart2 },
  { id: 'users',         label: 'Utilisateurs', Icon: UserCheck },
  { id: 'subscriptions', label: 'Abonnements',  Icon: Calendar },
  { id: 'payments',      label: 'Paiements',    Icon: CreditCard },
  { id: 'logs',          label: 'Logs live',    Icon: Terminal },
  { id: 'settings',      label: 'Paramètres',   Icon: Settings },
]

export default function App() {
  const [page, setPage]     = useLocalStorage('vs.admin.page', 'dashboard')
  const [health, setHealth] = useState(null)
  const [stats, setStats]   = useState(null)
  const [hourly, setHourly] = useState([])
  const [sideOpen, setSideOpen] = useState(false)
  const [pendingPayments, setPendingPayments] = useState(0)

  const loadStats = useCallback(async () => {
    try {
      const [h, s, hly] = await Promise.all([api.fetchHealth(), api.fetchStats(), api.fetchHourlyStats()])
      setHealth(h); setStats(s); setHourly(hly)
    } catch { setHealth(null) }
  }, [])

  const loadPendingPayments = useCallback(async () => {
    try {
      const payments = await api.fetchAdminPayments('pending')
      setPendingPayments(Array.isArray(payments) ? payments.length : 0)
    } catch { setPendingPayments(0) }
  }, [])

  useEffect(() => { loadStats(); loadPendingPayments() }, [loadStats, loadPendingPayments])
  useEffect(() => {
    const t = setInterval(() => { loadStats(); loadPendingPayments() }, 30000)
    return () => clearInterval(t)
  }, [loadStats, loadPendingPayments])

  const navigate = id => { setPage(id); setSideOpen(false) }

  return (
    <div className="min-h-screen flex" style={{ background: 'var(--bg)' }}>
      {/* Sidebar */}
      <aside className={`fixed lg:static inset-y-0 left-0 z-40 w-60 flex flex-col border-r transition-transform duration-200 ${sideOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}`}
        style={{ background: 'var(--surface)', borderColor: 'var(--border)', height: '100dvh' }}>
        {/* Logo */}
        <div className="px-5 py-5 border-b" style={{ borderColor: 'var(--border)' }}>
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: 'var(--brand-dim)' }}>
              <Play style={{ color: 'var(--brand)', width: 16, height: 16 }} />
            </div>
            <div>
              <div className="text-sm font-semibold" style={{ color: 'var(--text)' }}>StreamAdmin</div>
              <div className="text-[11px]" style={{ color: 'var(--text-muted)' }}>Panneau d'administration</div>
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-0.5">
          {NAV.map(({ id, label, Icon }) => (
            <button key={id} className={`nav-item w-full ${page===id?'active':''}`} onClick={() => navigate(id)}>
              <Icon className="w-4 h-4 flex-shrink-0" />
              <span>{label}</span>
              {id === 'monitoring' && (stats?.activeStreams ?? 0) > 0 && (
                <span className="ml-auto text-[10px] px-1.5 py-0.5 rounded-full" style={{ background:'rgba(52,211,153,0.15)', color:'var(--success)' }}>
                  {stats.activeStreams}
                </span>
              )}
              {id === 'payments' && pendingPayments > 0 && (
                <span className="ml-auto text-[10px] px-1.5 py-0.5 rounded-full" style={{ background:'rgba(251,191,36,0.2)', color:'#fbbf24' }}>
                  {pendingPayments}
                </span>
              )}
            </button>
          ))}
        </nav>

        {/* Footer */}
        <div className="px-4 py-4 border-t" style={{ borderColor: 'var(--border)' }}>
          <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--text-muted)' }}>
            <StatusDot online={!!health} />
            <span>{health ? `API :${health.activeStreams !== undefined ? '8081' : '—'}` : 'API hors ligne'}</span>
          </div>
        </div>
      </aside>

      {/* Mobile overlay */}
      {sideOpen && <div className="fixed inset-0 z-30 bg-black/50 lg:hidden" onClick={() => setSideOpen(false)} />}

      {/* Main */}
      <main className="flex-1 flex flex-col min-h-0 overflow-hidden">
        {/* Topbar (mobile) */}
        <header className="flex lg:hidden items-center gap-3 px-4 py-3 border-b" style={{ background: 'var(--surface)', borderColor: 'var(--border)' }}>
          <button className="btn-ghost btn p-1.5" onClick={() => setSideOpen(true)}>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" className="w-5 h-5">
              <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <span className="text-sm font-semibold" style={{ color: 'var(--text)' }}>{NAV.find(n=>n.id===page)?.label}</span>
        </header>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4 lg:p-6">
          {page === 'dashboard'     && <DashboardPage health={health} stats={stats} hourly={hourly} loading={!stats} />}
          {page === 'videos'        && <VideosPage onRefreshStats={loadStats} />}
          {page === 'categories'    && <CategoriesPage />}
          {page === 'monitoring'    && <MonitoringPage />}
          {page === 'users'         && <UsersPage />}
          {page === 'subscriptions' && <SubscriptionsPage />}
          {page === 'payments'      && <PaymentsPage />}
          {page === 'logs'          && <LogsPage />}
          {page === 'settings'      && <SettingsPage />}
        </div>
      </main>
    </div>
  )
}
