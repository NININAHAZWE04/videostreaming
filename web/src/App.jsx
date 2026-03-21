import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import * as auth from './authStore.js'

const QUALITY_COLORS = {'4K':'#a78bfa','1080p':'#38bdf8','720p':'#4ade80','480p':'#fbbf24','SD':'#94a3b8'}

/* ── Utils ─────────────────────────────────────────────────── */
const getProgress=()=>{ try{return JSON.parse(localStorage.getItem('vs.progress')||'{}')}catch{return{}} }
const saveProgress=o=>localStorage.setItem('vs.progress',JSON.stringify(o))
const getHistory=()=>{ try{return JSON.parse(localStorage.getItem('vs.history')||'[]')}catch{return[]} }
const addToHistory=t=>{ const h=getHistory().filter(x=>x!==t); h.unshift(t); localStorage.setItem('vs.history',JSON.stringify(h.slice(0,20))) }
const fmtDur=s=>{ if(!s||s<=0) return null; const h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sc=s%60; return h>0?`${h}:${String(m).padStart(2,'0')}:${String(sc).padStart(2,'0')}`:`${m}:${String(sc).padStart(2,'0')}` }

/* ── SVG Icons ──────────────────────────────────────────────── */
const svg=d=>(props={})=><svg className={props.className||'w-5 h-5'} fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24">{d}</svg>
const PlayIcon    = svg(<polygon points="5 3 19 12 5 21 5 3" fill="currentColor" stroke="none"/>)
const InfoIcon    = svg(<><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></>)
const XIcon       = svg(<><path d="M6 18L18 6M6 6l12 12"/></>)
const SearchIcon  = svg(<><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></>)
const GearIcon    = svg(<><path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><path d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></>)
const ChevL       = svg(<path d="M15 19l-7-7 7-7"/>)
const ChevR       = svg(<path d="M9 5l7 7-7 7"/>)
const LockIcon    = svg(<><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></>)
const DownloadIcon= svg(<><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></>)
const UserIcon    = svg(<><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></>)
const LogoutIcon  = svg(<><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></>)
const CheckIcon   = svg(<><polyline points="20 6 9 17 4 12"/></>)
const StarIcon    = svg(<polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" fill="currentColor" stroke="none"/>)
const ClockIcon   = svg(<><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></>)

function QBadge({label}) {
  if(!label) return null
  const c=QUALITY_COLORS[label]||'#94a3b8'
  return <span style={{background:`${c}22`,color:c,border:`1px solid ${c}44`}} className="text-[10px] font-bold px-1.5 py-0.5 rounded uppercase tracking-wider">{label}</span>
}

function Spinner({size='w-5 h-5'}) { return <div className={`${size} border-2 rounded-full`} style={{borderColor:'rgba(255,255,255,0.2)',borderTopColor:'#fff',animation:'spin 0.7s linear infinite'}}/> }

/* ── Auth Modal (Login/Register) ────────────────────────────── */
function AuthModal({mode:initMode='login', onClose, onSuccess, context}) {
  const [mode,setMode]=useState(initMode)
  const [email,setEmail]=useState('')
  const [username,setUsername]=useState('')
  const [password,setPassword]=useState('')
  const [err,setErr]=useState('')
  const [loading,setLoading]=useState(false)

  useEffect(()=>{ const fn=e=>{if(e.key==='Escape')onClose()}; window.addEventListener('keydown',fn); document.body.style.overflow='hidden'; return()=>{window.removeEventListener('keydown',fn);document.body.style.overflow=''} },[onClose])

  const submit=async()=>{
    setErr(''); setLoading(true)
    try {
      if(mode==='login') {
        await auth.login(email,password)
      } else {
        const res=await auth.register(email,username,password)
        // Auto-start trial on register
        if(res.canStartTrial) {
          try { await auth.startTrial() } catch {}
        }
      }
      onSuccess()
    } catch(e) { setErr(e.message) }
    finally { setLoading(false) }
  }

  const onKey=e=>{ if(e.key==='Enter') submit() }

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4" style={{background:'rgba(0,0,0,0.88)',backdropFilter:'blur(8px)'}} onClick={onClose}>
      <div className="w-full max-w-sm rounded-2xl overflow-hidden animate-slide-up" style={{background:'#181818',border:'1px solid rgba(255,255,255,0.1)'}} onClick={e=>e.stopPropagation()}>
        {/* Context banner */}
        {context && <div className="px-6 pt-5 pb-0 flex items-center gap-2 text-sm" style={{color:'#fbbf24'}}><LockIcon className="w-4 h-4"/>{context}</div>}
        <div className="px-6 pt-6 pb-2">
          <h2 className="text-xl font-bold text-white mb-1">{mode==='login'?'Connexion':'Créer un compte'}</h2>
          <p className="text-sm" style={{color:'rgba(255,255,255,0.45)'}}>
            {mode==='login'?'Accédez à votre catalogue complet':'14 jours d\'essai gratuit — sans carte bancaire'}
          </p>
        </div>
        <div className="p-6 space-y-3">
          <input className="w-full rounded-xl px-4 py-3 text-sm text-white outline-none" style={{background:'rgba(255,255,255,0.06)',border:'1px solid rgba(255,255,255,0.1)'}} placeholder="Email" type="email" value={email} onChange={e=>setEmail(e.target.value)} onKeyDown={onKey} autoFocus/>
          {mode==='register'&&<input className="w-full rounded-xl px-4 py-3 text-sm text-white outline-none" style={{background:'rgba(255,255,255,0.06)',border:'1px solid rgba(255,255,255,0.1)'}} placeholder="Nom d'utilisateur" value={username} onChange={e=>setUsername(e.target.value)} onKeyDown={onKey}/>}
          <input className="w-full rounded-xl px-4 py-3 text-sm text-white outline-none" style={{background:'rgba(255,255,255,0.06)',border:'1px solid rgba(255,255,255,0.1)'}} placeholder="Mot de passe (8 caractères min.)" type="password" value={password} onChange={e=>setPassword(e.target.value)} onKeyDown={onKey}/>
          {err&&<p className="text-xs text-red-400 px-1">{err}</p>}
          <button className="w-full py-3 rounded-xl font-bold text-sm transition-all flex items-center justify-center gap-2" style={{background:'#e50914',color:'#fff'}} onClick={submit} disabled={loading}>
            {loading?<Spinner size="w-4 h-4"/>:mode==='login'?'Se connecter':'Créer mon compte gratuitement'}
          </button>
          {mode==='register'&&<p className="text-[11px] text-center" style={{color:'rgba(255,255,255,0.3)'}}>En créant un compte vous acceptez nos conditions d'utilisation</p>}
        </div>
        <div className="px-6 pb-6 text-center text-sm" style={{color:'rgba(255,255,255,0.4)'}}>
          {mode==='login'?<>Pas encore de compte ? <button className="text-red-500 font-medium hover:text-red-400" onClick={()=>setMode('register')}>S'inscrire</button></>
           :<>Déjà un compte ? <button className="text-red-500 font-medium hover:text-red-400" onClick={()=>setMode('login')}>Se connecter</button></>}
        </div>
      </div>
    </div>
  )
}

/* ── Subscription Modal ─────────────────────────────────────── */
function SubscriptionModal({user, onClose, onSuccess}) {
  const [plans, setPlans] = useState(auth.getPlans())
  const [selected,setSelected]=useState('monthly')
  const [proofNote,setProofNote]=useState('')
  const [step,setStep]=useState('plans') // plans | payment | confirm
  const [loading,setLoading]=useState(false)
  const [err,setErr]=useState('')

  useEffect(()=>{ const fn=e=>{if(e.key==='Escape')onClose()}; window.addEventListener('keydown',fn); document.body.style.overflow='hidden'; return()=>{window.removeEventListener('keydown',fn);document.body.style.overflow=''} },[onClose])
  useEffect(()=>{ auth.fetchPlans().then(p=>setPlans(p)).catch(()=>{}) },[])

  const handleTrial=async()=>{
    if(!user) { onClose(); return }
    setLoading(true)
    try { await auth.startTrial(); onSuccess() }
    catch(e) { setErr(e.message) }
    finally { setLoading(false) }
  }

  const handlePayment=async()=>{
    setLoading(true); setErr('')
    const plan=plans.find(p=>p.id===selected)
    try {
      await auth.requestPayment(plan.id, plan.price, 'USD', proofNote)
      setStep('confirm')
    } catch(e) { setErr(e.message) }
    finally { setLoading(false) }
  }

  const trialAvailable = user && user.canStartTrial
  const currentPlan = plans.find(p=>p.id===selected)

  if(step==='confirm') return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4" style={{background:'rgba(0,0,0,0.88)',backdropFilter:'blur(8px)'}} onClick={onClose}>
      <div className="w-full max-w-md rounded-2xl p-8 text-center animate-slide-up" style={{background:'#181818',border:'1px solid rgba(255,255,255,0.1)'}} onClick={e=>e.stopPropagation()}>
        <div className="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-4" style={{background:'rgba(52,211,153,0.15)'}}>
          <CheckIcon className="w-8 h-8" style={{color:'#34d399'}}/>
        </div>
        <h2 className="text-xl font-bold text-white mb-2">Demande envoyée!</h2>
        <p className="text-sm mb-6" style={{color:'rgba(255,255,255,0.5)'}}>Votre demande de paiement a été enregistrée. L'administrateur activera votre abonnement sous 24h après réception du paiement cash.</p>
        <button className="w-full py-3 rounded-xl font-bold text-sm bg-red-600 text-white" onClick={onClose}>Fermer</button>
      </div>
    </div>
  )

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4" style={{background:'rgba(0,0,0,0.88)',backdropFilter:'blur(8px)'}} onClick={onClose}>
      <div className="w-full max-w-2xl rounded-2xl overflow-hidden animate-slide-up" style={{background:'#181818',border:'1px solid rgba(255,255,255,0.1)',maxHeight:'90vh',overflowY:'auto'}} onClick={e=>e.stopPropagation()}>
        <div className="p-6 border-b" style={{borderColor:'rgba(255,255,255,0.08)'}}>
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-bold text-white">Choisissez votre plan</h2>
              <p className="text-sm mt-0.5" style={{color:'rgba(255,255,255,0.45)'}}>Accès illimité à tout le catalogue</p>
            </div>
            <button onClick={onClose} className="p-2 rounded-full" style={{background:'rgba(255,255,255,0.06)'}}><XIcon className="w-5 h-5 text-white"/></button>
          </div>
        </div>

        {step==='plans' && (
          <div className="p-6">
            {/* Trial CTA */}
            {trialAvailable && (
              <div className="mb-5 rounded-xl p-4 flex items-center gap-4" style={{background:'rgba(251,191,36,0.08)',border:'1px solid rgba(251,191,36,0.25)'}}>
                <div className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0" style={{background:'rgba(251,191,36,0.15)'}}>
                  <StarIcon className="w-5 h-5" style={{color:'#fbbf24'}}/>
                </div>
                <div className="flex-1">
                  <div className="text-sm font-semibold" style={{color:'#fbbf24'}}>Essai gratuit disponible</div>
                  <div className="text-xs mt-0.5" style={{color:'rgba(255,255,255,0.4)'}}>Profitez de 14 jours d'accès complet sans engagement</div>
                </div>
                <button className="px-4 py-2 rounded-lg text-sm font-bold flex-shrink-0" style={{background:'#fbbf24',color:'#000'}} onClick={handleTrial} disabled={loading}>
                  {loading?<Spinner size="w-4 h-4"/>:'Commencer'}
                </button>
              </div>
            )}

            {/* Plan cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-5">
              {plans.filter(p=>p.id!=='trial').map(plan=>(
                <div key={plan.id} onClick={()=>setSelected(plan.id)}
                  className="relative rounded-xl p-5 cursor-pointer transition-all"
                  style={{background:selected===plan.id?'rgba(229,9,20,0.12)':'rgba(255,255,255,0.04)',border:`1px solid ${selected===plan.id?'rgba(229,9,20,0.5)':'rgba(255,255,255,0.08)'}`,outline:selected===plan.id?'none':'none'}}>
                  {plan.badge&&<span className="absolute top-3 right-3 text-[10px] font-bold px-2 py-0.5 rounded-full" style={{background:'rgba(229,9,20,0.2)',color:'#f87171'}}>{plan.badge}</span>}
                  {plan.highlight&&<div className="absolute -top-px left-1/2 -translate-x-1/2 text-[10px] font-bold px-3 py-0.5 rounded-b-lg" style={{background:'#e50914',color:'#fff'}}>POPULAIRE</div>}
                  <div className="text-sm font-semibold text-white mb-1">{plan.name}</div>
                  <div className="flex items-baseline gap-1 mb-3">
                    <span className="text-3xl font-bold text-white">{plan.price}$</span>
                    <span className="text-xs" style={{color:'rgba(255,255,255,0.4)'}}>{plan.period}</span>
                  </div>
                  <ul className="space-y-1.5">
                    {plan.features.map(f=><li key={f} className="flex items-center gap-2 text-xs" style={{color:'rgba(255,255,255,0.6)'}}><CheckIcon className="w-3 h-3 flex-shrink-0" style={{color:'#34d399'}}/>{f}</li>)}
                  </ul>
                </div>
              ))}
            </div>

            <div className="rounded-xl p-4 mb-4" style={{background:'rgba(255,255,255,0.03)',border:'1px solid rgba(255,255,255,0.06)'}}>
              <div className="text-xs font-medium text-white mb-2">💵 Paiement en cash uniquement</div>
              <p className="text-xs" style={{color:'rgba(255,255,255,0.4)'}}>Contactez l'administrateur pour régler en cash. Votre abonnement sera activé manuellement après confirmation du paiement.</p>
            </div>

            {err&&<p className="text-xs text-red-400 mb-3">{err}</p>}
            <button className="w-full py-3 rounded-xl font-bold text-sm" style={{background:'#e50914',color:'#fff'}} onClick={()=>setStep('payment')} disabled={!selected}>
              Continuer avec le plan {plans.find(p=>p.id===selected)?.name}
            </button>
          </div>
        )}

        {step==='payment' && (
          <div className="p-6">
            <button className="flex items-center gap-2 text-sm mb-5" style={{color:'rgba(255,255,255,0.5)'}} onClick={()=>setStep('plans')}>← Retour</button>
            <h3 className="text-lg font-bold text-white mb-1">Paiement cash</h3>
            <p className="text-sm mb-5" style={{color:'rgba(255,255,255,0.4)'}}>Plan sélectionné: <b className="text-white">{currentPlan?.name} — {currentPlan?.price}$ USD</b></p>
            <div className="rounded-xl p-4 mb-5 text-sm space-y-2" style={{background:'rgba(255,255,255,0.04)',border:'1px solid rgba(255,255,255,0.08)'}}>
              <p className="text-white font-medium">Instructions de paiement</p>
              <p style={{color:'rgba(255,255,255,0.5)'}}>1. Versez <strong className="text-white">{currentPlan?.price} USD</strong> en cash à l'administrateur</p>
              <p style={{color:'rgba(255,255,255,0.5)'}}>2. Notez ci-dessous une référence ou confirmation</p>
              <p style={{color:'rgba(255,255,255,0.5)'}}>3. Votre abonnement sera activé sous 24h</p>
            </div>
            <div className="mb-4">
              <label className="block text-xs font-medium mb-1.5" style={{color:'rgba(255,255,255,0.5)'}}>Note de paiement (optionnel)</label>
              <textarea className="w-full rounded-xl px-4 py-3 text-sm text-white outline-none resize-none" rows={3} style={{background:'rgba(255,255,255,0.06)',border:'1px solid rgba(255,255,255,0.1)'}} placeholder="Ex: Payé le 16/03 à Jean, reçu #1234..." value={proofNote} onChange={e=>setProofNote(e.target.value)}/>
            </div>
            {err&&<p className="text-xs text-red-400 mb-3">{err}</p>}
            <button className="w-full py-3 rounded-xl font-bold text-sm flex items-center justify-center gap-2" style={{background:'#e50914',color:'#fff'}} onClick={handlePayment} disabled={loading}>
              {loading?<Spinner size="w-4 h-4"/>:'Soumettre ma demande'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

/* ── Premium Lock Overlay ────────────────────────────────────── */
function PremiumLock({onUnlock}) {
  return (
    <div className="absolute inset-0 flex flex-col items-center justify-center rounded-md z-10" style={{background:'rgba(0,0,0,0.75)',backdropFilter:'blur(4px)'}}>
      <div className="w-10 h-10 rounded-full flex items-center justify-center mb-2" style={{background:'rgba(229,9,20,0.2)'}}>
        <LockIcon className="w-5 h-5 text-red-500"/>
      </div>
      <p className="text-white text-xs font-semibold mb-0.5">Contenu premium</p>
      <button onClick={e=>{e.stopPropagation();onUnlock()}} className="mt-2 px-3 py-1 rounded-full text-[10px] font-bold" style={{background:'#e50914',color:'#fff'}}>
        S'abonner
      </button>
    </div>
  )
}

/* ── Thumbnail ──────────────────────────────────────────────── */
function Thumb({video,className='h-full w-full object-cover'}) {
  const [err,setErr]=useState(false)
  if(err||!video.thumbnailUrl) return (
    <div className="absolute inset-0 flex items-center justify-center bg-[#111]">
      <div className="w-10 h-10 rounded-full bg-red-600/20 flex items-center justify-center"><PlayIcon className="w-5 h-5 text-red-500"/></div>
    </div>
  )
  return <img src={video.thumbnailUrl} alt={video.title} className={className} loading="lazy" onError={()=>setErr(true)}/>
}

/* ── Skeleton card ──────────────────────────────────────────── */
function SkeletonCard() {
  return <div className="flex-none w-48 md:w-56"><div className="aspect-video skeleton rounded-md"/><div className="mt-2 space-y-1.5 px-1"><div className="h-3 skeleton rounded w-3/4"/><div className="h-2.5 skeleton rounded w-1/2"/></div></div>
}

/* ── Video Card ─────────────────────────────────────────────── */
function VideoCard({video,onPlay,onSelect,progress=0,user,onNeedAuth}) {
      const canPlay = !!video.url
  const [hover,setHover]=useState(false)
  const locked = !video.free && !auth.userHasAccess(user)
  const pct = progress>0&&video.durationSec>0?(progress/video.durationSec)*100:0

  const handlePlay = e => {
    e.stopPropagation()
    if (locked) { onNeedAuth('premium'); return }
    onPlay(video)
  }

  return (
    <div className="video-card group flex-none w-48 md:w-56 cursor-pointer" onMouseEnter={()=>setHover(true)} onMouseLeave={()=>setHover(false)} onClick={()=>locked?onNeedAuth('premium'):(canPlay?onPlay(video):onSelect(video))}>
      <div className="relative aspect-video rounded-md overflow-hidden bg-[#1a1a1a]">
        <Thumb video={video}/>
        {hover && !locked && canPlay && (
          <video className="absolute inset-0 w-full h-full object-cover" src={video.url} autoPlay muted loop playsInline />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300"/>
        <div className="absolute top-2 left-2 flex items-center gap-1 bg-red-600 text-white text-[10px] font-bold px-2 py-0.5 rounded-sm uppercase tracking-wider">
          <span className="live-pulse inline-block w-1.5 h-1.5 rounded-full bg-white"/>Live
        </div>
        <div className="absolute top-2 right-2 flex flex-col items-end gap-1">
          <QBadge label={video.qualityLabel}/>
          {video.free && <span className="text-[9px] font-bold px-1.5 py-0.5 rounded" style={{background:'rgba(52,211,153,0.2)',color:'#34d399'}}>GRATUIT</span>}
        </div>
        {video.formattedDuration&&<div className="absolute bottom-3 right-2 text-[10px] font-mono text-white/80 bg-black/60 px-1.5 py-0.5 rounded">{video.formattedDuration}</div>}
        {!locked && (
          <div className="absolute inset-0 flex items-end justify-between p-3 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
            <button className="w-9 h-9 rounded-full bg-white flex items-center justify-center shadow-lg hover:bg-white/90 transition-colors" onClick={canPlay ? handlePlay : (e=>{e.stopPropagation();onSelect(video)})} aria-label="Lire">
              <PlayIcon className="w-4 h-4 text-black ml-0.5"/>
            </button>
            <button className="w-9 h-9 rounded-full bg-black/70 border border-white/30 flex items-center justify-center hover:bg-black/90 transition-colors"
              onClick={e=>{e.stopPropagation();onSelect(video)}} aria-label="Plus d'infos">
              <InfoIcon className="w-4 h-4 text-white"/>
            </button>
          </div>
        )}
        {locked && <PremiumLock onUnlock={()=>onNeedAuth('premium')}/>}
        {pct>0&&<div className="absolute bottom-0 left-0 right-0 h-0.5 bg-white/20"><div className="h-full" style={{width:`${Math.min(pct,100)}%`,background:'#e50914'}}/></div>}
      </div>
      <div className="mt-2 px-0.5">
        <h4 className="text-white text-sm font-semibold truncate group-hover:text-red-500 transition-colors">{video.title}</h4>
        <div className="flex items-center gap-2 mt-0.5">
          {video.categoryName&&<span className="text-[10px] px-1.5 py-0.5 rounded" style={{background:`${video.categoryColor||'#6366f1'}22`,color:video.categoryColor||'#6366f1'}}>{video.categoryName}</span>}
          {video.viewCount>0&&<span className="text-[10px] text-white/40">{video.viewCount} vue{video.viewCount!==1?'s':''}</span>}
        </div>
      </div>
    </div>
  )
}

/* ── Content Row ────────────────────────────────────────────── */
function ContentRow({title,badge,videos,loading,onPlay,onSelect,progressMap,user,onNeedAuth}) {
  const rowRef=useRef(null)
  const scroll=dir=>rowRef.current?.scrollBy({left:dir*420,behavior:'smooth'})
  return (
    <section className="mb-10">
      <div className="flex items-baseline gap-3 mb-4 px-4 md:px-12">
        <h2 className="text-white text-xl font-bold tracking-tight">{title}</h2>
        {badge&&<span className="text-red-500 text-sm font-semibold animate-pulse">{badge}</span>}
      </div>
      <div className="relative group/row">
        <button onClick={()=>scroll(-1)} className="absolute left-0 top-0 bottom-6 z-20 w-10 md:w-12 flex items-center justify-center bg-gradient-to-r from-[#141414] to-transparent opacity-0 group-hover/row:opacity-100 transition-opacity" aria-label="Défiler à gauche"><ChevL className="w-6 h-6 text-white drop-shadow"/></button>
        <div ref={rowRef} className="flex gap-3 overflow-x-auto scrollbar-hide px-4 md:px-12 pb-6">
          {loading?Array.from({length:6}).map((_,i)=><SkeletonCard key={i}/>):videos.map(v=>(
            <VideoCard key={String(v.id ?? v.databaseId ?? v.filePath ?? v.title ?? `${v.host}-${v.port}`)} video={v} onPlay={onPlay} onSelect={onSelect}
              progress={progressMap?.[v.title]??0} user={user} onNeedAuth={onNeedAuth}/>
          ))}
        </div>
        <button onClick={()=>scroll(1)} className="absolute right-0 top-0 bottom-6 z-20 w-10 md:w-12 flex items-center justify-center bg-gradient-to-l from-[#141414] to-transparent opacity-0 group-hover/row:opacity-100 transition-opacity" aria-label="Défiler à droite"><ChevR className="w-6 h-6 text-white drop-shadow"/></button>
      </div>
    </section>
  )
}

/* ── Player Modal ───────────────────────────────────────────── */
function PlayerModal({video,startAt=0,onClose,user,onNeedAuth}) {
  const vRef=useRef(null)
  const [downloading,setDownloading]=useState(false)

  useEffect(()=>{ const fn=e=>{if(e.key==='Escape')onClose()}; window.addEventListener('keydown',fn); document.body.style.overflow='hidden'; return()=>{window.removeEventListener('keydown',fn);document.body.style.overflow=''} },[onClose])
  const onLoaded=()=>{ if(startAt>0&&vRef.current) vRef.current.currentTime=startAt }
  const onPauseEnd=()=>{
    const v=vRef.current; if(!v||v.duration<=0) return
    const pct=v.currentTime/v.duration,prog=getProgress()
    if(pct>0.02&&pct<0.95){prog[video.title]=Math.floor(v.currentTime)}else{delete prog[video.title]}
    saveProgress(prog); addToHistory(video.title)
  }

  const handleDownload=async()=>{
    if(!auth.userCanDownload(user)){onNeedAuth('download');return}
    setDownloading(true)
    try {
      const res=await auth.getDownloadToken(video.databaseId||video.id)
      window.location.href=res.downloadUrl
    } catch(e) {
      alert(e.message)
    } finally { setDownloading(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{background:'rgba(0,0,0,0.92)',backdropFilter:'blur(8px)'}} onClick={onClose}>
      <div className="relative w-full max-w-5xl rounded-xl overflow-hidden shadow-2xl animate-slide-up" style={{background:'#111'}} onClick={e=>e.stopPropagation()}>
        <div className="aspect-video bg-black">
          <video ref={vRef} key={video.url} controls autoPlay poster={video.thumbnailUrl} className="w-full h-full" onLoadedMetadata={onLoaded} onPause={onPauseEnd} onEnded={onPauseEnd}>
            <source src={video.url} type="video/mp4"/>Votre navigateur ne supporte pas la lecture vidéo.
          </video>
        </div>
        <div className="p-5">
          <div className="flex justify-between items-start gap-4">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-3 mb-2 flex-wrap">
                <span className="text-red-500 text-xs font-bold uppercase tracking-wider flex items-center gap-1"><span className="live-pulse inline-block w-1.5 h-1.5 rounded-full bg-red-500"/>En direct</span>
                <QBadge label={video.qualityLabel}/>
                {video.formattedDuration&&<span className="text-white/50 text-xs flex items-center gap-1"><ClockIcon className="w-3 h-3"/>{video.formattedDuration}</span>}
                {video.formattedSize&&<span className="text-white/40 text-xs">{video.formattedSize}</span>}
              </div>
              <h2 className="text-white text-2xl font-bold truncate">{video.title}</h2>
              {video.synopsis&&<p className="text-white/60 text-sm mt-2 line-clamp-2">{video.synopsis}</p>}
              <div className="flex items-center gap-3 mt-3 flex-wrap">
                {video.categoryName&&<span className="text-xs px-2 py-0.5 rounded-full" style={{background:`${video.categoryColor||'#6366f1'}22`,color:video.categoryColor||'#6366f1'}}>{video.categoryName}</span>}
                {video.tags&&video.tags.split(',').filter(Boolean).map(t=><span key={t} className="text-[10px] px-2 py-0.5 rounded-full bg-white/5 text-white/40">{t.trim()}</span>)}
              </div>
            </div>
            <div className="flex items-center gap-2 flex-shrink-0">
              {/* Download button */}
              <button onClick={handleDownload} disabled={downloading}
                className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium transition-all"
                style={{background: auth.userCanDownload(user)?'rgba(255,255,255,0.1)':'rgba(229,9,20,0.15)', color: auth.userCanDownload(user)?'#fff':'#f87171', border:'1px solid rgba(255,255,255,0.15)'}}
                title={auth.userCanDownload(user)?'Télécharger':'Abonnement requis'}>
                {downloading?<Spinner size="w-4 h-4"/>:<DownloadIcon className="w-4 h-4"/>}
                <span className="hidden sm:inline">Télécharger</span>
                {!auth.userCanDownload(user)&&<LockIcon className="w-3 h-3"/>}
              </button>
              <button onClick={onClose} className="p-2 rounded-full bg-white/10 hover:bg-white/20 transition-colors" aria-label="Fermer"><XIcon className="w-5 h-5 text-white"/></button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ── Hero Banner ────────────────────────────────────────────── */
function HeroBanner({video,onPlay,onMoreInfo,user,onNeedAuth}) {
  const locked = !video.free && !auth.userHasAccess(user)
  return (
    <div className="relative w-full" style={{height:'min(56.25vw, 680px)'}}>
      <div className="absolute inset-0">{video.thumbnailUrl?<img src={video.thumbnailUrl} alt={video.title} className="w-full h-full object-cover"/>:<div className="w-full h-full bg-[#1a1a1a]"/>}</div>
      <div className="absolute inset-0 hero-vignette"/>
      <div className="absolute inset-0 hero-vignette-side"/>
      <div className="absolute bottom-[20%] left-0 px-4 md:px-12 max-w-xl animate-slide-up">
        <div className="flex items-center gap-2 mb-3 flex-wrap">
          <span className="flex items-center gap-1.5 bg-red-600 text-white text-xs font-bold px-2.5 py-1 rounded-sm uppercase tracking-wider"><span className="live-pulse inline-block w-1.5 h-1.5 rounded-full bg-white"/>En direct</span>
          <QBadge label={video.qualityLabel}/>
          {video.free&&<span className="text-xs font-bold px-2 py-0.5 rounded" style={{background:'rgba(52,211,153,0.2)',color:'#34d399'}}>GRATUIT</span>}
        </div>
        <h1 className="text-white text-4xl md:text-5xl font-bold leading-tight drop-shadow-2xl">{video.title}</h1>
        {video.synopsis&&<p className="text-white/70 text-sm mt-2 line-clamp-2 max-w-sm">{video.synopsis}</p>}
        <div className="flex items-center gap-3 mt-6">
          <button onClick={()=>locked?onNeedAuth('premium'):onPlay(video)}
            className="flex items-center gap-2 bg-white text-black font-bold px-6 py-2.5 rounded-md hover:bg-white/85 active:scale-95 transition-all text-sm md:text-base">
            {locked?<><LockIcon className="w-5 h-5"/>S'abonner</>:<><PlayIcon className="w-5 h-5"/>Lire</>}
          </button>
          <button onClick={()=>onMoreInfo(video)} className="flex items-center gap-2 bg-white/20 backdrop-blur-sm text-white font-semibold px-5 py-2.5 rounded-md hover:bg-white/30 active:scale-95 transition-all text-sm md:text-base border border-white/20">
            <InfoIcon className="w-5 h-5"/>Plus d'infos
          </button>
        </div>
      </div>
    </div>
  )
}

/* ── Navbar ─────────────────────────────────────────────────── */
function Navbar({query,onQueryChange,onSettingsClick,connected,autoRefresh,onToggleRefresh,user,onAuthClick,onSubClick}) {
  const [scrolled,setScrolled]=useState(false)
  const [searchOpen,setSearchOpen]=useState(false)
  const [profileOpen,setProfileOpen]=useState(false)
  const ref=useRef(null)
  useEffect(()=>{ const fn=()=>setScrolled(window.scrollY>20); window.addEventListener('scroll',fn,{passive:true}); return()=>window.removeEventListener('scroll',fn) },[])
  useEffect(()=>{ if(searchOpen&&ref.current) ref.current.focus() },[searchOpen])

  const handleLogout=()=>{ auth.logout(); setProfileOpen(false); window.location.reload() }

  return (
    <nav className={`fixed top-0 left-0 right-0 z-40 h-16 flex items-center px-4 md:px-12 transition-all duration-500 ${scrolled?'bg-[#141414]':'nav-gradient'}`}>
      <div className="flex-shrink-0 mr-8"><span className="font-display font-bold text-2xl tracking-tight text-red-600 select-none">STREAM<span className="text-white">HOME</span></span></div>
      <div className="hidden md:flex items-center gap-6 text-sm font-medium mr-auto">
        <a href="#" className="text-white hover:text-white/70 transition-colors">Accueil</a>
        <a href="#" className="text-white/70 hover:text-white transition-colors">Flux Live</a>
      </div>
      <div className="ml-auto flex items-center gap-3">
        {searchOpen?(
          <div className="flex items-center bg-black/80 border border-white/40 px-3 py-1.5 rounded-sm">
            <SearchIcon className="w-4 h-4 text-white/60 flex-shrink-0 mr-2"/>
            <input ref={ref} type="search" placeholder="Titres, catégories…" className="bg-transparent text-white text-sm outline-none w-40 md:w-56 placeholder-white/40" value={query} onChange={e=>onQueryChange(e.target.value)} onBlur={()=>{ if(!query) setSearchOpen(false) }}/>
            {query&&<button onClick={()=>{onQueryChange('');setSearchOpen(false)}} className="ml-2 text-white/60 hover:text-white"><XIcon className="w-4 h-4"/></button>}
          </div>
        ):(
          <button onClick={()=>setSearchOpen(true)} className="text-white/80 hover:text-white transition-colors p-1"><SearchIcon className="w-5 h-5"/></button>
        )}
        <div className={`h-2 w-2 rounded-full flex-shrink-0 ${connected?'bg-emerald-500 shadow-[0_0_6px_rgba(16,185,129,0.7)]':'bg-red-500'}`}/>
        <button onClick={onToggleRefresh} className={`hidden sm:flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-sm transition-colors ${autoRefresh?'bg-white/10 text-white hover:bg-white/20':'bg-red-600/20 text-red-500 hover:bg-red-600/30'}`}>
          <span className={`w-1.5 h-1.5 rounded-full ${autoRefresh?'bg-emerald-400 live-pulse':'bg-red-500'}`}/>{autoRefresh?'Live':'Pausé'}
        </button>

        {/* Auth area */}
        {user ? (
          <div className="relative">
            <button onClick={()=>setProfileOpen(p=>!p)} className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-white/10 transition-colors">
              <div className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold" style={{background:`${user.avatarColor||'#38bdf8'}22`,color:user.avatarColor||'#38bdf8'}}>
                {user.initials||user.username?.charAt(0)?.toUpperCase()}
              </div>
              <span className="hidden sm:block text-sm text-white/80 max-w-20 truncate">{user.username}</span>
            </button>
            {profileOpen&&(
              <div className="absolute top-full right-0 mt-2 w-56 rounded-xl overflow-hidden shadow-2xl z-50" style={{background:'#1a1a1a',border:'1px solid rgba(255,255,255,0.1)'}}>
                <div className="px-4 py-3 border-b" style={{borderColor:'rgba(255,255,255,0.08)'}}>
                  <div className="text-sm font-medium text-white">{user.username}</div>
                  <div className="text-xs mt-0.5" style={{color:'rgba(255,255,255,0.4)'}}>{user.email}</div>
                  {user.hasSubscription?(
                    <div className="mt-1.5 text-[11px] font-medium" style={{color:'#34d399'}}>✓ Abonnement actif {user.daysRemaining>=0?`(${user.daysRemaining}j)`:''}</div>
                  ):(
                    <button onClick={()=>{setProfileOpen(false);onSubClick()}} className="mt-1.5 text-[11px] font-medium text-red-500 hover:text-red-400">→ S'abonner</button>
                  )}
                </div>
                <button onClick={handleLogout} className="w-full flex items-center gap-3 px-4 py-3 text-sm hover:bg-white/5 transition-colors" style={{color:'rgba(255,255,255,0.6)'}}>
                  <LogoutIcon className="w-4 h-4"/>Déconnexion
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <button onClick={()=>onAuthClick('login')} className="text-sm text-white/70 hover:text-white px-3 py-1.5 transition-colors">Connexion</button>
            <button onClick={()=>onAuthClick('register')} className="text-sm font-medium px-3 py-1.5 rounded-lg transition-all" style={{background:'#e50914',color:'#fff'}}>S'inscrire</button>
          </div>
        )}

        <button onClick={onSettingsClick} className="text-white/80 hover:text-white transition-colors p-1"><GearIcon className="w-5 h-5"/></button>
      </div>
    </nav>
  )
}

/* ── Category Tabs ──────────────────────────────────────────── */
function CategoryTabs({categories,active,onChange}) {
  if(!categories.length) return null
  return (
    <div className="flex gap-2 overflow-x-auto scrollbar-hide px-4 md:px-12 pb-2">
      <button onClick={()=>onChange('')} className={`flex-none px-4 py-1.5 rounded-full text-sm font-medium transition-all ${!active?'bg-white text-black':'bg-white/10 text-white/70 hover:bg-white/20'}`}>Tous</button>
      {categories.map(c=>(
        <button key={c.id} onClick={()=>onChange(String(c.id))} className="flex-none px-4 py-1.5 rounded-full text-sm font-medium transition-all"
          style={active===String(c.id)?{background:c.color,color:'#000'}:{background:`${c.color}22`,color:c.color}}>{c.name}</button>
      ))}
    </div>
  )
}

/* ── Info Modal ─────────────────────────────────────────────── */
function InfoModal({video,onPlay,onClose,user,onNeedAuth}) {
  useEffect(()=>{ const fn=e=>{if(e.key==='Escape')onClose()}; window.addEventListener('keydown',fn); document.body.style.overflow='hidden'; return()=>{window.removeEventListener('keydown',fn);document.body.style.overflow=''} },[onClose])
  const locked = !video.free && !auth.userHasAccess(user)
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{background:'rgba(0,0,0,0.85)',backdropFilter:'blur(6px)'}} onClick={onClose}>
      <div className="relative w-full max-w-lg rounded-xl overflow-hidden shadow-2xl animate-slide-up" style={{background:'#181818'}} onClick={e=>e.stopPropagation()}>
        <div className="aspect-video relative bg-black">
          <Thumb video={video} className="w-full h-full object-cover opacity-60"/>
          <div className="absolute inset-0 bg-gradient-to-t from-[#181818] via-transparent to-transparent"/>
          <button onClick={onClose} className="absolute top-3 right-3 p-2 rounded-full bg-black/50 hover:bg-black/70 transition-colors"><XIcon className="w-5 h-5 text-white"/></button>
          {video.free&&<div className="absolute top-3 left-3 text-xs font-bold px-2 py-0.5 rounded" style={{background:'rgba(52,211,153,0.2)',color:'#34d399'}}>GRATUIT</div>}
        </div>
        <div className="p-6 space-y-4">
          <div>
            <div className="flex items-center gap-2 mb-1 flex-wrap">
              <QBadge label={video.qualityLabel}/>
              {video.formattedDuration&&<span className="text-white/50 text-xs">{video.formattedDuration}</span>}
              {video.categoryName&&<span className="text-xs px-2 py-0.5 rounded-full" style={{background:`${video.categoryColor||'#6366f1'}22`,color:video.categoryColor||'#6366f1'}}>{video.categoryName}</span>}
            </div>
            <h2 className="text-white text-xl font-bold">{video.title}</h2>
          </div>
          {video.synopsis&&<p className="text-white/70 text-sm leading-relaxed">{video.synopsis}</p>}
          <div className="grid grid-cols-2 gap-3 text-sm">
            {[['Taille',video.formattedSize],['Résolution',video.resolution],['Codec',video.codec],['Vues',video.viewCount>0?video.viewCount:null]].filter(([,v])=>v).map(([k,v])=>(
              <div key={k} className="rounded-lg p-2.5" style={{background:'rgba(255,255,255,0.04)'}}>
                <div className="text-[10px] uppercase tracking-wider text-white/30 mb-1">{k}</div>
                <div className="text-white/80 text-xs font-medium font-mono">{v}</div>
              </div>
            ))}
          </div>
          <button onClick={()=>{locked?onNeedAuth('premium'):(onPlay(video),onClose())}}
            className="w-full flex items-center justify-center gap-2 font-bold py-3 rounded-lg transition-colors"
            style={{background:locked?'rgba(229,9,20,0.15)':'#fff',color:locked?'#f87171':'#000'}}>
            {locked?<><LockIcon className="w-5 h-5"/>S'abonner pour regarder</>:<><PlayIcon className="w-5 h-5"/>Lire</>}
          </button>
        </div>
      </div>
    </div>
  )
}

/* ── Settings Panel ─────────────────────────────────────────── */
function SettingsPanel({draftApiUrl,onDraftChange,onSave,onClose,error}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{background:'rgba(0,0,0,0.7)',backdropFilter:'blur(4px)'}} onClick={onClose}>
      <div className="bg-[#1a1a1a] border border-white/10 rounded-xl p-6 w-full max-w-sm shadow-2xl animate-slide-up" onClick={e=>e.stopPropagation()}>
        <div className="flex justify-between items-center mb-5"><h3 className="text-white font-bold text-lg flex items-center gap-2"><GearIcon className="w-5 h-5 text-red-500"/>Configuration API</h3><button onClick={onClose} className="text-white/50 hover:text-white"><XIcon/></button></div>
        <form onSubmit={e=>{e.preventDefault(); if(onSave()) onClose()}} className="space-y-4">
          <div>
            <label className="block text-white/50 text-xs font-medium mb-1.5 uppercase tracking-wider">URL de l'API</label>
            <input className="w-full bg-white/5 border border-white/15 rounded-lg px-3 py-2.5 text-sm text-white focus:outline-none focus:border-red-500/60 transition-colors" value={draftApiUrl} onChange={e=>onDraftChange(e.target.value)} placeholder="http://localhost:8081"/>
            {error && <p className="text-xs text-red-400 mt-2">{error}</p>}
          </div>
          <button type="submit" className="w-full bg-red-600 hover:bg-red-700 text-white font-bold py-2.5 rounded-lg transition-colors text-sm">Enregistrer</button>
        </form>
      </div>
    </div>
  )
}

/* ── Empty State ────────────────────────────────────────────── */
function EmptyState({hasQuery}) {
  return (
    <div className="flex flex-col items-center justify-center py-32 px-4 text-center animate-fade-in">
      <div className="w-20 h-20 rounded-full bg-white/5 flex items-center justify-center mb-6"><PlayIcon className="w-10 h-10 text-white/20"/></div>
      <h3 className="text-white text-xl font-bold mb-2">{hasQuery?'Aucun résultat':'Aucun flux disponible'}</h3>
      <p className="text-white/40 text-sm max-w-xs">{hasQuery?'Essayez un autre terme.':'Lancez le Provider pour démarrer un flux vidéo.'}</p>
    </div>
  )
}

/* ── Main App ───────────────────────────────────────────────── */
export default function App() {
  const [apiUrl,setApiUrl]=useState(()=>auth.getApiBaseUrl())
  const [draftApiUrl,setDraftApiUrl]=useState(apiUrl)
  const [apiError,setApiError]=useState('')
  const [videos,setVideos]=useState([])
  const [highlights, setHighlights] = useState({ newest: [], trendingWeek: [], comingSoon: [] })
  const [categories,setCategories]=useState([])
  const [loading,setLoading]=useState(true)
  const [connected,setConnected]=useState(false)
  const [query,setQuery]=useState('')
  const [activeCat,setActiveCat]=useState('')
  const [autoRefresh,setAutoRefresh]=useState(true)
  const [playing,setPlaying]=useState(null)
  const [selected,setSelected]=useState(null)
  const [showSettings,setShowSettings]=useState(false)
  const [progressMap,setProgressMap]=useState(getProgress)
  const [user,setUser]=useState(()=>auth.getUser())
  const [authModal,setAuthModal]=useState(null) // null | { mode, context }
  const [subModal,setSubModal]=useState(false)
  const [profileOpen,setProfileOpen]=useState(false)

  // Close profile dropdown on outside click
  useEffect(()=>{
    const fn=()=>setProfileOpen(false)
    document.addEventListener('click',fn)
    return()=>document.removeEventListener('click',fn)
  },[])

  // Refresh user from server on mount
  useEffect(()=>{
    auth.fetchMe().then(u=>{ if(u) setUser(u) }).catch(()=>{})
  },[])

  const handleNeedAuth=(context)=>{
    if(!auth.isLoggedIn()) setAuthModal({mode:'login',context:context==='premium'?'Connexion requise pour accéder au contenu premium':'Connexion requise pour télécharger'})
    else setSubModal(true)
  }

  const handlePlayClose=()=>{ setPlaying(null); setProgressMap(getProgress()) }
  const playVideo=v=>{ const p=getProgress(); setPlaying({...v,_startAt:p[v.title]||0}) }

  const fetchAll=useCallback(async()=>{
    try {
      const[vR,cR,hR]=await Promise.all([fetch(`${apiUrl}/api/videos`),fetch(`${apiUrl}/api/categories`),fetch(`${apiUrl}/api/videos/highlights`)])
      if(!vR.ok) throw new Error()
      const vD=await vR.json()
      const abs = (u) => {
        if (!u) return null
        if (u.startsWith('http://') || u.startsWith('https://')) return u
        if (u.startsWith('/')) return `${apiUrl}${u}`
        return `${apiUrl}/${u}`
      }
      const normalizedVideos = (Array.isArray(vD.videos)?vD.videos:[]).map(v=>({
        ...v,
        url: abs(v.url || v.streamUrl) || (v.id && v.active ? `${apiUrl}/api/media/${v.id}/stream` : null),
        thumbnailUrl: abs(v.thumbnailUrl) || (v.id && v.filePath ? `${apiUrl}/api/media/${v.id}/thumbnail` : null),
      }))
      setVideos(normalizedVideos)
      setConnected(true)
      if(cR.ok){const cD=await cR.json();setCategories(Array.isArray(cD)?cD:[])}
      if (hR.ok) {
        const hD = await hR.json()
        const mapList = (list) => (Array.isArray(list) ? list : []).map(v => ({
          ...v,
          url: abs(v.url || v.streamUrl) || (v.id && v.active ? `${apiUrl}/api/media/${v.id}/stream` : null),
          thumbnailUrl: abs(v.thumbnailUrl) || (v.id && v.filePath ? `${apiUrl}/api/media/${v.id}/thumbnail` : null),
        }))
        setHighlights({
          newest: mapList(hD?.newest),
          trendingWeek: mapList(hD?.trendingWeek),
          comingSoon: mapList(hD?.comingSoon),
        })
      }
    } catch{setConnected(false)}
    finally{setLoading(false)}
  },[apiUrl])

  const handleSaveApiUrl=()=>{
    const normalized = auth.normalizeApiBaseUrl(draftApiUrl)
    const raw = draftApiUrl.trim()
    if(raw && normalized !== raw.replace(/\/$/, '')){
      setApiError('URL invalide. Utilisez un format http(s)://host[:port].')
      return false
    }
    setApiError('')
    const nextUrl = auth.setApiBaseUrl(normalized)
    setApiUrl(nextUrl)
    setDraftApiUrl(nextUrl)
    return true
  }

  useEffect(()=>{ setLoading(true); fetchAll() },[fetchAll])

  useEffect(()=>{
    if(!autoRefresh) return
    let es=null,fb=null
    try{
      es=new EventSource(`${apiUrl}/api/events`)
      ;['video_added','video_removed','stream_started','stream_stopped'].forEach(ev=>es.addEventListener(ev,()=>fetchAll()))
      es.onerror=()=>{ if(!fb) fb=setInterval(fetchAll,8000) }
    }catch{ fb=setInterval(fetchAll,8000) }
    return()=>{ es?.close(); clearInterval(fb) }
  },[autoRefresh,fetchAll,apiUrl])

  const filtered=useMemo(()=>{
    let l=videos
    if(activeCat) l=l.filter(v=>String(v.categoryId)===activeCat)
    const q=query.trim().toLowerCase()
    if(q) l=l.filter(v=>v.title.toLowerCase().includes(q)||(v.synopsis||'').toLowerCase().includes(q)||(v.tags||'').toLowerCase().includes(q)||(v.categoryName||'').toLowerCase().includes(q))
    return l
  },[videos,activeCat,query])

  const history=useMemo(()=>{
    const h=getHistory(),p=getProgress()
    return h.filter(t=>p[t]!=null).map(t=>videos.find(v=>v.title===t)).filter(Boolean)
  },[videos,progressMap])

  const catRows=useMemo(()=>{
    if(activeCat||query) return []
    const map=new Map()
    filtered.forEach(v=>{const k=v.categoryName||'Autres',c=v.categoryColor||'#9ca3af';if(!map.has(k))map.set(k,{name:k,color:c,videos:[]});map.get(k).videos.push(v)})
    return[...map.entries()].map(([,v])=>v).sort((a,b)=>b.videos.length-a.videos.length)
  },[filtered,activeCat,query])

  const newReleases=useMemo(()=>(highlights.newest?.length ? highlights.newest : [...filtered].sort((a,b)=>(b.id||0)-(a.id||0)).slice(0,12)),[filtered, highlights.newest])
  const trendingNow=useMemo(()=>(highlights.trendingWeek?.length ? highlights.trendingWeek : [...filtered].sort((a,b)=>(b.viewCount||0)-(a.viewCount||0)).slice(0,12)),[filtered, highlights.trendingWeek])
  const premiumPicks=useMemo(()=>(filtered.filter(v=>!v.free).sort((a,b)=>(b.downloadCount||0)-(a.downloadCount||0)).slice(0,12)),[filtered])
  const comingSoon=useMemo(()=>(Array.isArray(highlights.comingSoon) ? highlights.comingSoon : []),[highlights.comingSoon])

  // Free content row (always visible)
  const freeVideos=useMemo(()=>filtered.filter(v=>v.free),[filtered])

  const homeRows=useMemo(()=>{
    if(query||activeCat||loading) return []

    const rows=[]
    const usedIds=new Set()
    const keyOf=v=>String(v.id ?? v.databaseId ?? v.filePath ?? v.title)
    const uniqueList=list=>{
      const seen=new Set()
      return list.filter(v=>{ const k=keyOf(v); if(seen.has(k)) return false; seen.add(k); return true })
    }
    const pushRow=(title,badge,list,{allowSingle=false}={})=>{
      const unique=uniqueList(list)
      const fresh=unique.filter(v=>!usedIds.has(keyOf(v)))
      if(!fresh.length) return
      if(!allowSingle && fresh.length<2) return
      fresh.forEach(v=>usedIds.add(keyOf(v)))
      rows.push({title,badge,videos:fresh})
    }

    pushRow('Reprendre','● En cours',history)
    pushRow('Contenu gratuit','✓ Sans abonnement',freeVideos,{allowSingle:true})
    pushRow('Nouveautes','Nouveau',newReleases)
    pushRow('Tendances','Top vues',trendingNow)
    pushRow('Coming soon','Bientot',comingSoon)
    pushRow('Premium du moment','Abonnés',premiumPicks)
    catRows.forEach(r=>pushRow(r.name,null,r.videos))

    if(!rows.length && filtered.length>0) {
      pushRow('Catalogue',null,filtered,{allowSingle:true})
    }
    return rows
  },[query,activeCat,loading,history,freeVideos,newReleases,trendingNow,comingSoon,premiumPicks,catRows,filtered])

  return (
    <div className="min-h-screen bg-[#141414] text-white overflow-x-hidden flex flex-col">
      <Navbar query={query} onQueryChange={setQuery} onSettingsClick={()=>setShowSettings(true)} connected={connected}
        autoRefresh={autoRefresh} onToggleRefresh={()=>setAutoRefresh(v=>!v)} user={user}
        onAuthClick={mode=>setAuthModal({mode})} onSubClick={()=>setSubModal(true)}/>

      {/* Trial banner for logged-in users without subscription */}
      {user && !user.hasSubscription && user.canStartTrial && (
        <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-40 flex items-center gap-4 px-5 py-3 rounded-2xl shadow-2xl" style={{background:'linear-gradient(135deg,#1a0a0a,#2d1010)',border:'1px solid rgba(229,9,20,0.4)'}}>
          <span className="text-sm text-white/80">🎁 Essai gratuit 14 jours disponible</span>
          <button onClick={()=>setSubModal(true)} className="text-xs font-bold px-3 py-1.5 rounded-lg" style={{background:'#e50914',color:'#fff'}}>Activer maintenant</button>
          <button onClick={()=>{}} className="text-white/40 hover:text-white/60"><XIcon className="w-4 h-4"/></button>
        </div>
      )}

      {/* Hero */}
      {filtered[0]&&!loading&&!query&&(
        <HeroBanner video={filtered[0]} onPlay={playVideo} onMoreInfo={setSelected} user={user} onNeedAuth={handleNeedAuth}/>
      )}
      {loading&&<div className="relative w-full skeleton" style={{height:'min(56.25vw, 680px)'}}><div className="absolute inset-0 hero-vignette"/></div>}

      <div className={`relative z-10 flex-1 ${filtered[0]&&!loading&&!query?'-mt-24':'mt-16'}`}>
        {!connected&&!loading&&<div className="mx-4 md:mx-12 mb-6 flex items-center gap-3 bg-red-500/10 border border-red-500/30 text-red-400 text-sm px-4 py-3 rounded-lg"><span>●</span><span>API hors ligne — Vérifiez l'URL dans les paramètres.</span></div>}
        {!loading&&categories.length>0&&!query&&<div className="mb-6"><CategoryTabs categories={categories} active={activeCat} onChange={setActiveCat}/></div>}

        {!query&&!activeCat&&homeRows.map(r=><ContentRow key={r.title} title={r.title} badge={r.badge} videos={r.videos} loading={false} onPlay={playVideo} onSelect={setSelected} progressMap={progressMap} user={user} onNeedAuth={handleNeedAuth}/>)}

        {(query||activeCat)&&<ContentRow title={query?`Résultats: "${query}"`:`Catégorie · ${categories.find(c=>String(c.id)===activeCat)?.name||''}`} videos={filtered} loading={loading} onPlay={playVideo} onSelect={setSelected} progressMap={progressMap} user={user} onNeedAuth={handleNeedAuth}/>}
        {!query&&!activeCat&&catRows.length===0&&homeRows.length===0&&<ContentRow title="Flux en direct" badge="● Live" videos={filtered} loading={loading} onPlay={playVideo} onSelect={setSelected} progressMap={progressMap} user={user} onNeedAuth={handleNeedAuth}/>} 
        {!loading&&filtered.length===0&&<EmptyState hasQuery={!!(query||activeCat)}/>}

        <footer className="px-4 md:px-12 py-10 mt-auto border-t border-white/5">
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
            <span className="font-display font-bold text-xl text-red-600">STREAM<span className="text-white/50">HOME</span></span>
            <p className="text-white/40 text-xs">© {new Date().getFullYear()} StreamHOME — Tous droits réservés</p>
          </div>
        </footer>
      </div>

      {playing&&<PlayerModal video={playing} startAt={playing._startAt||0} onClose={handlePlayClose} user={user} onNeedAuth={handleNeedAuth}/>}
      {selected&&<InfoModal video={selected} onPlay={playVideo} onClose={()=>setSelected(null)} user={user} onNeedAuth={handleNeedAuth}/>}
      {showSettings&&<SettingsPanel draftApiUrl={draftApiUrl} onDraftChange={setDraftApiUrl} onSave={handleSaveApiUrl} error={apiError} onClose={()=>{ setApiError(''); setShowSettings(false) }}/>}
      {authModal&&<AuthModal mode={authModal.mode} context={authModal.context} onClose={()=>setAuthModal(null)} onSuccess={()=>{ setAuthModal(null); const u=auth.getUser(); setUser(u); fetchAll() }}/>}
      {subModal&&<SubscriptionModal user={user} onClose={()=>setSubModal(false)} onSuccess={()=>{ setSubModal(false); auth.fetchMe().then(u=>setUser(u)) }}/>}
    </div>
  )
}
