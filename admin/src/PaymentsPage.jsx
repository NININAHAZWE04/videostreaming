import { useState, useEffect, useCallback, useMemo } from 'react'
import * as api from './api.js'
import { Check, X, DollarSign, Clock2, CheckCircle, XCircle, AlertCircle, RefreshCw, Plus } from './icons.jsx'

function toast(msg, type='info') {
  const el = document.createElement('div')
  el.style.cssText = `position:fixed;bottom:24px;right:24px;z-index:9999;padding:12px 16px;border-radius:12px;font-size:13px;font-weight:500;animation:slideIn 0.2s ease;background:${type==='error'?'#2d1515':type==='success'?'#0d2518':'#111827'};color:${type==='error'?'#fca5a5':type==='success'?'#6ee7b7':'#e4e4f0'};border:1px solid ${type==='error'?'rgba(248,113,113,0.3)':type==='success'?'rgba(52,211,153,0.3)':'rgba(255,255,255,0.08)'}`
  el.textContent = msg; document.body.appendChild(el); setTimeout(()=>el.remove(),3500)
}

function Spinner() { return <div className="w-4 h-4 border-2 rounded-full spinner" style={{ borderColor:'var(--brand-dim)',borderTopColor:'var(--brand)' }} /> }

const STATUS_CONF = {
  pending:  { label:'En attente', color:'#fbbf24', Icon: Clock2 },
  approved: { label:'Approuvé',   color:'#34d399', Icon: CheckCircle },
  rejected: { label:'Rejeté',     color:'#f87171', Icon: XCircle },
}

const PLAN_NAMES = { trial:'Essai', monthly:'Mensuel', annual:'Annuel' }

function ActionModal({ payment, action, onClose, onDone }) {
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const isApprove = action === 'approve'

  const submit = async () => {
    setSaving(true)
    try {
      const body = { adminNote: note, approvedBy: 'admin' }
      if (isApprove) await api.approvePayment(payment.id, body)
      else           await api.rejectPayment(payment.id, body)
      toast(isApprove ? 'Paiement approuvé — abonnement activé!' : 'Paiement rejeté', isApprove ? 'success' : 'info')
      onDone()
    } catch (e) { toast(e.message, 'error') }
    finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background:'rgba(0,0,0,0.7)',backdropFilter:'blur(4px)' }} onClick={onClose}>
      <div className="card w-full max-w-md p-5 animate-fade-in" onClick={e=>e.stopPropagation()}>
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-base font-semibold" style={{ color:'var(--text)' }}>
            {isApprove ? '✓ Approuver le paiement' : '✗ Rejeter le paiement'}
          </h3>
          <button className="btn-ghost btn p-1" onClick={onClose}><X /></button>
        </div>

        {/* Payment summary */}
        <div className="rounded-lg p-3 mb-4 space-y-1.5" style={{ background:'var(--surface2)', border:'1px solid var(--border)' }}>
          <div className="flex justify-between text-sm">
            <span style={{ color:'var(--text-muted)' }}>Utilisateur</span>
            <span style={{ color:'var(--text)' }}>{payment.username} <span style={{ color:'var(--text-dim)' }}>({payment.userEmail})</span></span>
          </div>
          <div className="flex justify-between text-sm">
            <span style={{ color:'var(--text-muted)' }}>Plan</span>
            <span style={{ color:'var(--text)' }}>{PLAN_NAMES[payment.plan]||payment.plan} · {payment.durationDays}j</span>
          </div>
          <div className="flex justify-between text-sm">
            <span style={{ color:'var(--text-muted)' }}>Montant</span>
            <span className="font-semibold" style={{ color:'#34d399' }}>{payment.amount} {payment.currency}</span>
          </div>
          {payment.proofNote && (
            <div className="pt-2 border-t" style={{ borderColor:'var(--border)' }}>
              <div className="text-xs mb-1" style={{ color:'var(--text-muted)' }}>Note du client :</div>
              <p className="text-xs" style={{ color:'var(--text)' }}>{payment.proofNote}</p>
            </div>
          )}
        </div>

        {isApprove && (
          <div className="mb-4 p-3 rounded-lg text-xs" style={{ background:'rgba(52,211,153,0.08)', border:'1px solid rgba(52,211,153,0.2)', color:'#6ee7b7' }}>
            L'abonnement sera activé immédiatement après approbation.
          </div>
        )}

        <div className="mb-4">
          <label className="label">Note admin (optionnel)</label>
          <textarea className="input resize-none" rows={2} value={note} onChange={e=>setNote(e.target.value)}
            placeholder={isApprove ? 'Paiement reçu en cash...' : 'Raison du rejet...'} />
        </div>

        <div className="flex gap-2">
          <button className="btn-ghost btn flex-1" onClick={onClose}>Annuler</button>
          <button className="btn flex-1 font-medium" onClick={submit} disabled={saving}
            style={{ background:isApprove?'rgba(52,211,153,0.2)':'rgba(248,113,113,0.2)', color:isApprove?'#34d399':'#f87171', border:`1px solid ${isApprove?'rgba(52,211,153,0.3)':'rgba(248,113,113,0.3)'}` }}>
            {saving ? <Spinner /> : isApprove ? <Check /> : <X />}
            {saving ? '...' : isApprove ? 'Approuver' : 'Rejeter'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function PaymentsPage() {
  const [payments, setPayments] = useState([])
  const [users, setUsers] = useState([])
  const [loading, setLoading]   = useState(true)
  const [filter, setFilter]     = useState('all')
  const [action, setAction]     = useState(null) // { payment, type: 'approve'|'reject' }
  const [manualOpen, setManualOpen] = useState(false)
  const [manual, setManual] = useState({ userId: '', plan: 'monthly', amount: '', durationDays: '', currency: 'USD', status: 'approved', proofNote: '', adminNote: '' })

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [paymentRows, userRows] = await Promise.all([api.fetchAdminPayments(), api.fetchAdminUsers()])
      setPayments(paymentRows)
      setUsers(Array.isArray(userRows) ? userRows : [])
    }
    catch (e) { toast(e.message, 'error') }
    finally { setLoading(false) }
  }, [])

  const submitManual = async () => {
    if (!manual.userId) { toast('Selectionnez un utilisateur', 'error'); return }
    try {
      await api.createAdminPayment({
        userId: manual.userId,
        plan: manual.plan,
        amount: manual.amount,
        durationDays: manual.durationDays,
        currency: manual.currency,
        status: manual.status,
        proofNote: manual.proofNote,
        adminNote: manual.adminNote,
      })
      toast('Paiement saisi avec succes', 'success')
      setManualOpen(false)
      setManual({ userId: '', plan: 'monthly', amount: '', durationDays: '', currency: 'USD', status: 'approved', proofNote: '', adminNote: '' })
      load()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  useEffect(() => { load() }, [load])

  // SSE: refresh when payment approved
  useEffect(() => {
    const es = api.createEventSource('/api/events', { payment_approved: () => load() })
    return () => es.close()
  }, [load])

  const filtered = useMemo(() => payments.filter(p =>
    filter === 'all' || p.status === filter
  ), [payments, filter])

  const totals = useMemo(() => ({
    pending:  payments.filter(p => p.status === 'pending').length,
    approved: payments.filter(p => p.status === 'approved').length,
    revenue:  payments.filter(p => p.status === 'approved').reduce((s, p) => s + (p.amount||0), 0),
  }), [payments])

  return (
    <div className="space-y-5 animate-fade-in">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-semibold" style={{ color:'var(--text)' }}>Paiements</h1>
          <p className="text-sm mt-0.5" style={{ color:'var(--text-muted)' }}>Gestion des paiements cash</p>
        </div>
        <div className="flex items-center gap-2">
          <button className="btn btn-primary" onClick={() => setManualOpen(true)}><Plus />Saisir un paiement</button>
          <button className="btn-ghost btn" onClick={load}><RefreshCw />Actualiser</button>
        </div>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-3 gap-3">
        <div className="card p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs uppercase tracking-wider" style={{ color:'var(--text-muted)' }}>En attente</span>
            <Clock2 style={{ color:'#fbbf24',width:16,height:16 }} />
          </div>
          <div className="text-2xl font-semibold" style={{ color:totals.pending>0?'#fbbf24':'var(--text)' }}>{totals.pending}</div>
        </div>
        <div className="card p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs uppercase tracking-wider" style={{ color:'var(--text-muted)' }}>Approuvés</span>
            <CheckCircle style={{ color:'#34d399',width:16,height:16 }} />
          </div>
          <div className="text-2xl font-semibold" style={{ color:'var(--text)' }}>{totals.approved}</div>
        </div>
        <div className="card p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs uppercase tracking-wider" style={{ color:'var(--text-muted)' }}>Revenu total</span>
            <DollarSign style={{ color:'#a78bfa',width:16,height:16 }} />
          </div>
          <div className="text-2xl font-semibold" style={{ color:'#a78bfa' }}>{totals.revenue.toFixed(2)} USD</div>
        </div>
      </div>

      {/* Filter tabs */}
      <div className="flex gap-2 flex-wrap">
        {[['all','Tous'],['pending','En attente'],['approved','Approuvés'],['rejected','Rejetés']].map(([v,l])=>(
          <button key={v} onClick={()=>setFilter(v)}
            className="text-xs px-3 py-1.5 rounded-full transition-all font-medium"
            style={{ background:filter===v?'var(--brand-dim)':'var(--surface)', color:filter===v?'var(--brand)':'var(--text-muted)', border:`1px solid ${filter===v?'var(--brand)':'var(--border)'}` }}>
            {l}
            {v==='pending' && totals.pending > 0 && (
              <span className="ml-1.5 text-[10px] bg-yellow-500/20 text-yellow-400 rounded-full px-1.5 py-0.5">{totals.pending}</span>
            )}
          </button>
        ))}
      </div>

      {/* Payments list */}
      {loading ? (
        <div className="flex justify-center py-16"><div className="w-6 h-6 border-2 rounded-full spinner" style={{ borderColor:'var(--brand-dim)',borderTopColor:'var(--brand)' }} /></div>
      ) : filtered.length === 0 ? (
        <div className="card py-16 text-center">
          <DollarSign className="w-12 h-12 mx-auto mb-3" style={{ color:'var(--text-dim)' }} />
          <p className="text-sm" style={{ color:'var(--text-muted)' }}>Aucun paiement {filter !== 'all' ? `"${filter}"` : ''}</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map(p => {
            const conf = STATUS_CONF[p.status] || STATUS_CONF.pending
            const StatusIcon = conf.Icon
            return (
              <div key={p.id} className="card p-4 animate-fade-in">
                <div className="flex items-start gap-4 flex-wrap">
                  {/* Status icon */}
                  <div className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0" style={{ background:`${conf.color}15` }}>
                    <StatusIcon style={{ color:conf.color, width:18, height:18 }} />
                  </div>

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold" style={{ color:'var(--text)' }}>{p.username}</span>
                      <span className="text-xs" style={{ color:'var(--text-muted)' }}>{p.userEmail}</span>
                      <span className="badge text-[10px]" style={{ background:`${conf.color}15`,color:conf.color }}>{conf.label}</span>
                    </div>
                    <div className="flex items-center gap-3 mt-1 flex-wrap text-xs" style={{ color:'var(--text-muted)' }}>
                      <span>Plan: <b style={{ color:'var(--text)' }}>{PLAN_NAMES[p.plan]||p.plan}</b></span>
                      <span>{p.durationDays} jours</span>
                      <span className="font-semibold" style={{ color:'#34d399' }}>{p.amount} {p.currency}</span>
                      <span>{p.paymentMethod}</span>
                      <span>#{p.id} · {p.createdAt}</span>
                    </div>
                    {p.proofNote && (
                      <p className="text-xs mt-2 px-2.5 py-1.5 rounded-md" style={{ background:'var(--surface2)',color:'var(--text-muted)' }}>
                        Client: "{p.proofNote}"
                      </p>
                    )}
                    {p.adminNote && (
                      <p className="text-xs mt-1" style={{ color:'var(--text-dim)' }}>Admin: {p.adminNote} {p.approvedBy && `(${p.approvedBy})`}</p>
                    )}
                  </div>

                  {/* Actions */}
                  {p.status === 'pending' && (
                    <div className="flex gap-2 flex-shrink-0">
                      <button className="btn text-xs py-1.5 px-3 font-medium" onClick={()=>setAction({payment:p,type:'approve'})}
                        style={{ background:'rgba(52,211,153,0.15)',color:'#34d399',border:'1px solid rgba(52,211,153,0.25)' }}>
                        <Check /> Approuver
                      </button>
                      <button className="btn text-xs py-1.5 px-3 font-medium" onClick={()=>setAction({payment:p,type:'reject'})}
                        style={{ background:'rgba(248,113,113,0.12)',color:'#f87171',border:'1px solid rgba(248,113,113,0.2)' }}>
                        <X /> Rejeter
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {action && (
        <ActionModal payment={action.payment} action={action.type}
          onClose={()=>setAction(null)}
          onDone={()=>{ setAction(null); load() }} />
      )}

      {manualOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background:'rgba(0,0,0,0.7)',backdropFilter:'blur(4px)' }} onClick={() => setManualOpen(false)}>
          <div className="card w-full max-w-xl p-5 animate-fade-in" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-semibold" style={{ color:'var(--text)' }}>Saisir un paiement</h3>
              <button className="btn-ghost btn p-1" onClick={() => setManualOpen(false)}><X /></button>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div className="md:col-span-2">
                <label className="label">Utilisateur</label>
                <select className="input" value={manual.userId} onChange={e=>setManual(p=>({...p,userId:e.target.value}))}>
                  <option value="">-- selectionner --</option>
                  {users.map(u => <option key={u.id} value={u.id}>{u.username} ({u.email})</option>)}
                </select>
              </div>
              <div>
                <label className="label">Plan</label>
                <select className="input" value={manual.plan} onChange={e=>setManual(p=>({...p,plan:e.target.value}))}>
                  <option value="monthly">Mensuel</option>
                  <option value="annual">Annuel</option>
                  <option value="trial">Essai</option>
                </select>
              </div>
              <div>
                <label className="label">Statut</label>
                <select className="input" value={manual.status} onChange={e=>setManual(p=>({...p,status:e.target.value}))}>
                  <option value="approved">Approuve</option>
                  <option value="pending">En attente</option>
                  <option value="rejected">Rejete</option>
                </select>
              </div>
              <div>
                <label className="label">Montant</label>
                <input className="input" value={manual.amount} onChange={e=>setManual(p=>({...p,amount:e.target.value}))} placeholder="ex: 9.99" />
              </div>
              <div>
                <label className="label">Duree (jours)</label>
                <input className="input" value={manual.durationDays} onChange={e=>setManual(p=>({...p,durationDays:e.target.value}))} placeholder="ex: 30" />
              </div>
              <div>
                <label className="label">Devise</label>
                <input className="input" value={manual.currency} onChange={e=>setManual(p=>({...p,currency:e.target.value.toUpperCase()}))} />
              </div>
              <div className="md:col-span-2">
                <label className="label">Note client</label>
                <input className="input" value={manual.proofNote} onChange={e=>setManual(p=>({...p,proofNote:e.target.value}))} />
              </div>
              <div className="md:col-span-2">
                <label className="label">Note admin</label>
                <input className="input" value={manual.adminNote} onChange={e=>setManual(p=>({...p,adminNote:e.target.value}))} />
              </div>
            </div>
            <div className="flex gap-2 mt-4 justify-end">
              <button className="btn-ghost btn" onClick={() => setManualOpen(false)}>Annuler</button>
              <button className="btn btn-primary" onClick={submitManual}><Check />Enregistrer</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
