import { useState, useEffect, useCallback, useMemo } from 'react'
import * as api from './api.js'
import { Shield, Clock2, CheckCircle, XCircle, RefreshCw, Layers } from './icons.jsx'

function Spinner() { return <div className="w-5 h-5 border-2 rounded-full spinner" style={{ borderColor:'var(--brand-dim)',borderTopColor:'var(--brand)' }} /> }

const PLAN_CONF = {
  trial:   { label:'Essai 14j',  color:'#fbbf24', icon:'⏱' },
  monthly: { label:'Mensuel',    color:'#38bdf8', icon:'📅' },
  annual:  { label:'Annuel',     color:'#a78bfa', icon:'🏆' },
  free:    { label:'Gratuit',    color:'#6b7280', icon:'🔓' },
}
const STATUS_CONF = {
  active:    { color:'#34d399', label:'Actif' },
  expired:   { color:'#f87171', label:'Expiré' },
  cancelled: { color:'#6b7280', label:'Annulé' },
  pending:   { color:'#fbbf24', label:'En attente' },
}

function daysUntil(dateStr) {
  if (!dateStr || dateStr === 'illimité') return null
  const d = new Date(dateStr) - Date.now()
  return Math.ceil(d / 86400000)
}

export default function SubscriptionsPage() {
  const [subs, setSubs]     = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('active')
  const [search, setSearch] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try { setSubs(await api.fetchAdminSubs()) }
    catch { }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { load() }, [load])

  const filtered = useMemo(() => subs.filter(s => {
    const matchF = filter === 'all'
      || (filter === 'trial' && s.plan === 'trial' && s.status === 'active')
      || (filter !== 'trial' && s.status === filter)
    const q = search.toLowerCase()
    const matchQ = !q || (s.userEmail || '').toLowerCase().includes(q) || (s.username || '').toLowerCase().includes(q)
    return matchF && matchQ
  }), [subs, filter, search])

  const stats = useMemo(() => ({
    active:   subs.filter(s => s.status === 'active').length,
    trial:    subs.filter(s => s.plan === 'trial' && s.status === 'active').length,
    monthly:  subs.filter(s => s.plan === 'monthly' && s.status === 'active').length,
    annual:   subs.filter(s => s.plan === 'annual' && s.status === 'active').length,
    expired:  subs.filter(s => s.status === 'expired').length,
  }), [subs])

  return (
    <div className="space-y-5 animate-fade-in">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-semibold" style={{ color:'var(--text)' }}>Abonnements</h1>
          <p className="text-sm mt-0.5" style={{ color:'var(--text-muted)' }}>{stats.active} actifs · {stats.expired} expirés</p>
        </div>
        <button className="btn-ghost btn" onClick={load}><RefreshCw />Actualiser</button>
      </div>

      {/* Plan breakdown */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          ['Essais actifs', stats.trial,   '#fbbf24'],
          ['Mensuels',      stats.monthly, '#38bdf8'],
          ['Annuels',       stats.annual,  '#a78bfa'],
          ['Expirés',       stats.expired, '#f87171'],
        ].map(([label, val, c]) => (
          <div key={label} className="card p-4">
            <div className="text-2xl font-semibold mb-1" style={{ color: val > 0 ? c : 'var(--text)' }}>{val}</div>
            <div className="text-xs" style={{ color:'var(--text-muted)' }}>{label}</div>
          </div>
        ))}
      </div>

      {/* Filter bar */}
      <div className="card p-3 flex items-center gap-3 flex-wrap">
        <input className="input flex-1 min-w-32 text-sm !bg-transparent !border-0 !p-0"
          placeholder="Rechercher email, nom…" value={search} onChange={e=>setSearch(e.target.value)} />
        <div className="flex gap-1.5 flex-wrap">
          {[['all','Tous'],['active','Actifs'],['trial','Essais'],['expired','Expirés'],['cancelled','Annulés']].map(([v,l])=>(
            <button key={v} onClick={()=>setFilter(v)}
              className="text-xs px-3 py-1 rounded-full transition-all"
              style={{ background:filter===v?'var(--brand-dim)':'var(--surface)', color:filter===v?'var(--brand)':'var(--text-muted)', border:`1px solid ${filter===v?'var(--brand)':'var(--border)'}` }}>
              {l}
            </button>
          ))}
        </div>
      </div>

      {/* List */}
      {loading ? <div className="flex justify-center py-16"><Spinner /></div>
      : filtered.length === 0 ? (
        <div className="card py-16 text-center">
          <Layers className="w-12 h-12 mx-auto mb-3" style={{ color:'var(--text-dim)' }} />
          <p className="text-sm" style={{ color:'var(--text-muted)' }}>Aucun abonnement trouvé</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b text-left" style={{ borderColor:'var(--border)' }}>
                  {['Utilisateur','Plan','Statut','Expire','Créé le'].map(h=>(
                    <th key={h} className="px-4 py-3 text-xs font-medium uppercase tracking-wider" style={{ color:'var(--text-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map(s => {
                  const planConf = PLAN_CONF[s.plan] || { label: s.plan, color:'#38bdf8', icon:'?' }
                  const statConf = STATUS_CONF[s.status] || STATUS_CONF.active
                  const days = daysUntil(s.endsAt)
                  const expiringSoon = days !== null && days >= 0 && days <= 5

                  return (
                    <tr key={s.id} className="table-row">
                      <td className="px-4 py-3">
                        <div className="text-sm font-medium" style={{ color:'var(--text)' }}>{s.username}</div>
                        <div className="text-xs" style={{ color:'var(--text-muted)' }}>{s.userEmail}</div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="badge text-[10px]" style={{ background:`${planConf.color}18`,color:planConf.color }}>
                          {planConf.label}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className="badge text-[10px]" style={{ background:`${statConf.color}15`,color:statConf.color }}>
                          {statConf.label}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-xs" style={{ color: expiringSoon ? '#fbbf24' : 'var(--text-muted)' }}>
                          {s.endsAt === 'illimité' ? '—' : s.endsAt}
                          {days !== null && days >= 0 && days <= 30 && (
                            <span className="ml-1.5 badge text-[9px]"
                              style={{ background:expiringSoon?'rgba(251,191,36,0.15)':'rgba(56,189,248,0.1)', color:expiringSoon?'#fbbf24':'var(--text-dim)' }}>
                              {days === 0 ? 'Expire auj.' : `${days}j`}
                            </span>
                          )}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs" style={{ color:'var(--text-muted)' }}>{s.createdAt}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
