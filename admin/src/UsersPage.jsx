import { useState, useEffect, useCallback, useMemo } from 'react'
import * as api from './api.js'
import { Search, X, Trash2, UserCheck, Shield, Ban, Gift } from './icons.jsx'

function toast(msg, type = 'info') {
  const el = document.createElement('div')
  el.className = 'fixed bottom-6 right-6 z-[9999] flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium shadow-2xl'
  el.style.cssText = `background:${type==='error'?'#2d1515':type==='success'?'#0d2518':'#111827'};color:${type==='error'?'#fca5a5':type==='success'?'#6ee7b7':'#e4e4f0'};border:1px solid ${type==='error'?'rgba(248,113,113,0.3)':type==='success'?'rgba(52,211,153,0.3)':'rgba(255,255,255,0.08)'};animation:slideIn 0.2s ease`
  el.textContent = msg; document.body.appendChild(el); setTimeout(() => el.remove(), 3500)
}

function Spinner() { return <div className="w-5 h-5 border-2 rounded-full spinner" style={{ borderColor:'var(--brand-dim)',borderTopColor:'var(--brand)' }} /> }

function Avatar({ user }) {
  return (
    <div className="w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold flex-shrink-0"
      style={{ background: `${user.avatarColor||'#38bdf8'}22`, color: user.avatarColor||'#38bdf8' }}>
      {user.initials || user.username?.charAt(0)?.toUpperCase() || '?'}
    </div>
  )
}

const PLAN_LABELS = { trial:'Essai 14j', monthly:'Mensuel', annual:'Annuel', free:'Gratuit' }
const PLAN_COLORS = { trial:'#fbbf24', monthly:'#38bdf8', annual:'#a78bfa', free:'#6b7280' }

function SubBadge({ user }) {
  if (!user.hasSubscription) return (
    <span className="badge text-[10px]" style={{ background:'rgba(107,114,128,0.15)',color:'#6b7280' }}>Aucun</span>
  )
  const plan = user.subPlan || 'free'
  const c = PLAN_COLORS[plan] || '#38bdf8'
  const days = user.daysRemaining
  return (
    <div className="flex items-center gap-1.5">
      <span className="badge text-[10px]" style={{ background:`${c}20`,color:c,border:`1px solid ${c}30` }}>
        {PLAN_LABELS[plan] || plan}
      </span>
      {days >= 0 && days < 999 && (
        <span className="text-[10px]" style={{ color:'var(--text-dim)' }}>{days}j restants</span>
      )}
    </div>
  )
}

function GrantModal({ user, onClose, onGrant }) {
  const [plan, setPlan] = useState('monthly')
  const [days, setDays] = useState('')
  const [saving, setSaving] = useState(false)
  const planDays = { trial: 14, monthly: 30, annual: 365 }

  const submit = async () => {
    setSaving(true)
    try {
      const d = days ? parseInt(days) : planDays[plan] || 30
      await api.grantSubscription(user.id, { plan, days: String(d) })
      toast(`Abonnement "${plan}" accordé à ${user.username}`, 'success')
      onGrant()
    } catch (e) { toast(e.message, 'error') }
    finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background:'rgba(0,0,0,0.7)', backdropFilter:'blur(4px)' }} onClick={onClose}>
      <div className="card w-full max-w-sm p-5 animate-fade-in" onClick={e=>e.stopPropagation()}>
        <div className="flex items-center justify-between mb-5">
          <h3 className="text-base font-semibold" style={{ color:'var(--text)' }}>Accorder un abonnement</h3>
          <button className="btn-ghost btn p-1" onClick={onClose}><X /></button>
        </div>
        <div className="flex items-center gap-3 mb-5 p-3 rounded-lg" style={{ background:'var(--surface2)' }}>
          <Avatar user={user} />
          <div>
            <div className="text-sm font-medium" style={{ color:'var(--text)' }}>{user.username}</div>
            <div className="text-xs" style={{ color:'var(--text-muted)' }}>{user.email}</div>
          </div>
        </div>
        <div className="space-y-3">
          <div>
            <label className="label">Plan</label>
            <div className="grid grid-cols-3 gap-2">
              {[['trial','Essai 14j','#fbbf24'],['monthly','Mensuel','#38bdf8'],['annual','Annuel','#a78bfa']].map(([p,l,c])=>(
                <button key={p} onClick={()=>{ setPlan(p); setDays('') }}
                  className="p-2.5 rounded-lg text-xs font-medium text-center transition-all border"
                  style={{ background:plan===p?`${c}20`:'var(--surface2)', color:plan===p?c:'var(--text-muted)', borderColor:plan===p?`${c}50`:'var(--border)' }}>
                  {l}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="label">Durée (jours) <span style={{ color:'var(--text-dim)', textTransform:'none' }}>— défaut: {planDays[plan] || 30}j</span></label>
            <input className="input" type="number" min="1" value={days} onChange={e=>setDays(e.target.value)} placeholder={`${planDays[plan] || 30} jours`} />
          </div>
        </div>
        <div className="flex gap-2 mt-5">
          <button className="btn-ghost btn flex-1" onClick={onClose}>Annuler</button>
          <button className="btn btn-primary flex-1" onClick={submit} disabled={saving}>
            {saving ? <Spinner /> : <Gift />} Accorder
          </button>
        </div>
      </div>
    </div>
  )
}

export default function UsersPage() {
  const [users, setUsers]     = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch]   = useState('')
  const [filter, setFilter]   = useState('all')
  const [granting, setGranting] = useState(null)
  const [confirm, setConfirm]   = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try { setUsers(await api.fetchAdminUsers()) }
    catch (e) { toast(e.message, 'error') }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { load() }, [load])

  const filtered = useMemo(() => users.filter(u => {
    const q = search.toLowerCase()
    const matchQ = !q || u.email.toLowerCase().includes(q) || u.username.toLowerCase().includes(q)
    const matchF = filter === 'all'
      || (filter === 'active' && u.hasSubscription)
      || (filter === 'trial' && u.subPlan === 'trial' && u.hasSubscription)
      || (filter === 'inactive' && !u.hasSubscription)
      || (filter === 'admin' && u.role === 'admin')
    return matchQ && matchF
  }), [users, search, filter])

  const doRevoke = async (user) => {
    try { await api.revokeSubscription(user.id); toast(`Abonnement révoqué pour ${user.username}`, 'success'); load() }
    catch (e) { toast(e.message, 'error') }
  }

  const doToggleActive = async (user) => {
    try {
      await api.updateUser(user.id, { active: String(!user.active) })
      toast(`Compte ${!user.active ? 'réactivé' : 'suspendu'}`, 'success'); load()
    } catch (e) { toast(e.message, 'error') }
  }

  const doDelete = async (user) => {
    try { await api.deleteUser(user.id); toast('Utilisateur supprimé', 'success'); load() }
    catch (e) { toast(e.message, 'error') }
    finally { setConfirm(null) }
  }

  const counts = useMemo(() => ({
    total: users.length,
    active: users.filter(u => u.hasSubscription).length,
    trial: users.filter(u => u.subPlan === 'trial' && u.hasSubscription).length,
  }), [users])

  return (
    <div className="space-y-5 animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-semibold" style={{ color:'var(--text)' }}>Utilisateurs</h1>
          <p className="text-sm mt-0.5" style={{ color:'var(--text-muted)' }}>
            {counts.total} comptes · {counts.active} abonnés · {counts.trial} en essai
          </p>
        </div>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-3 gap-3">
        {[
          ['Total', counts.total, '#38bdf8'],
          ['Abonnés actifs', counts.active, '#34d399'],
          ['Essais en cours', counts.trial, '#fbbf24'],
        ].map(([label, val, c]) => (
          <div key={label} className="card p-4">
            <div className="text-2xl font-semibold mb-1" style={{ color:'var(--text)' }}>{val}</div>
            <div className="text-xs" style={{ color:'var(--text-muted)' }}>{label}</div>
            <div className="h-0.5 mt-3 rounded-full" style={{ background:`${c}40` }}><div className="h-full rounded-full" style={{ width:`${Math.min((val/Math.max(counts.total,1))*100,100)}%`, background:c }} /></div>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="card p-3 flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-2 flex-1 min-w-36">
          <Search style={{ color:'var(--text-dim)',width:16,height:16,flexShrink:0 }} />
          <input className="input !bg-transparent !border-0 !p-0 text-sm flex-1" placeholder="Rechercher email, nom…" value={search} onChange={e=>setSearch(e.target.value)} />
          {search && <button onClick={()=>setSearch('')} style={{ color:'var(--text-dim)' }}><X /></button>}
        </div>
        <div className="flex gap-1.5 flex-wrap">
          {[['all','Tous'],['active','Abonnés'],['trial','Essai'],['inactive','Sans abonnement'],['admin','Admins']].map(([v,l])=>(
            <button key={v} onClick={()=>setFilter(v)}
              className="text-xs px-3 py-1 rounded-full transition-all"
              style={{ background:filter===v?'var(--brand-dim)':'var(--surface)', color:filter===v?'var(--brand)':'var(--text-muted)', border:`1px solid ${filter===v?'var(--brand)':'var(--border)'}` }}>
              {l}
            </button>
          ))}
        </div>
      </div>

      {/* Table */}
      <div className="card overflow-hidden">
        {loading ? <div className="flex justify-center py-16"><Spinner /></div>
        : filtered.length === 0 ? (
          <div className="py-16 text-center">
            <UserCheck className="w-12 h-12 mx-auto mb-3" style={{ color:'var(--text-dim)' }} />
            <p className="text-sm" style={{ color:'var(--text-muted)' }}>Aucun utilisateur trouvé</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b text-left" style={{ borderColor:'var(--border)' }}>
                  {['Utilisateur','Abonnement','Rôle','Statut','Actions'].map(h=>(
                    <th key={h} className="px-4 py-3 text-xs font-medium uppercase tracking-wider" style={{ color:'var(--text-muted)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map(u => (
                  <tr key={u.id} className="table-row">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <Avatar user={u} />
                        <div>
                          <div className="text-sm font-medium" style={{ color:'var(--text)' }}>{u.username}</div>
                          <div className="text-xs" style={{ color:'var(--text-muted)' }}>{u.email}</div>
                          <div className="text-[10px] font-mono mt-0.5" style={{ color:'var(--text-dim)' }}>id:{u.id} · {u.createdAt?.split(' ')[0]||'—'}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3"><SubBadge user={u} /></td>
                    <td className="px-4 py-3">
                      {u.role === 'admin'
                        ? <span className="badge text-[10px]" style={{ background:'rgba(167,139,250,0.15)',color:'#a78bfa' }}><Shield className="w-3 h-3 inline mr-1"/>Admin</span>
                        : <span className="badge text-[10px]" style={{ background:'var(--surface2)',color:'var(--text-muted)' }}>Utilisateur</span>}
                    </td>
                    <td className="px-4 py-3">
                      {u.active
                        ? <span className="badge text-[10px]" style={{ background:'rgba(52,211,153,0.12)',color:'var(--success)' }}>Actif</span>
                        : <span className="badge text-[10px]" style={{ background:'rgba(248,113,113,0.12)',color:'var(--danger)' }}>Suspendu</span>}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1">
                        <button className="btn-ghost btn p-1.5" onClick={()=>setGranting(u)} title="Accorder abonnement"><Gift /></button>
                        {u.hasSubscription && (
                          <button className="btn-ghost btn p-1.5" title="Révoquer" onClick={()=>doRevoke(u)}><Ban /></button>
                        )}
                        <button className="btn-ghost btn p-1.5" title={u.active?'Suspendre':'Réactiver'} onClick={()=>doToggleActive(u)}>
                          {u.active ? <Ban /> : <CheckCircle />}
                        </button>
                        <button className="btn-danger btn p-1.5" title="Supprimer" onClick={()=>setConfirm(u)}><Trash2 /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {granting && <GrantModal user={granting} onClose={()=>setGranting(null)} onGrant={()=>{ setGranting(null); load() }} />}
      {confirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background:'rgba(0,0,0,0.7)',backdropFilter:'blur(4px)' }} onClick={()=>setConfirm(null)}>
          <div className="card p-5 max-w-sm w-full animate-fade-in" onClick={e=>e.stopPropagation()}>
            <h3 className="text-base font-semibold mb-2" style={{ color:'var(--text)' }}>Supprimer le compte?</h3>
            <p className="text-sm mb-5" style={{ color:'var(--text-muted)' }}>Cette action est irréversible. L'abonnement et l'historique seront supprimés.</p>
            <div className="flex gap-2">
              <button className="btn-ghost btn flex-1" onClick={()=>setConfirm(null)}>Annuler</button>
              <button className="btn-danger btn flex-1" onClick={()=>doDelete(confirm)}>Supprimer</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function CheckCircle({ className }) {
  return <svg className={className||'w-4 h-4'} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
}
