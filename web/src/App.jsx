import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

const API_DEFAULT = 'http://localhost:8080'

/* ─── Icons (inline SVG helpers) ──────────────────────── */
function PlayIcon({ className = 'w-5 h-5' }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path d="M8 5v14l11-7z" />
    </svg>
  )
}

function InfoIcon({ className = 'w-5 h-5' }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
      <circle cx="12" cy="12" r="10" />
      <path strokeLinecap="round" d="M12 16v-4M12 8h.01" />
    </svg>
  )
}

function XIcon({ className = 'w-5 h-5' }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
      <path strokeLinecap="round" d="M6 18L18 6M6 6l12 12" />
    </svg>
  )
}

function SearchIcon({ className = 'w-5 h-5' }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
      <circle cx="11" cy="11" r="8" />
      <path strokeLinecap="round" d="M21 21l-4.35-4.35" />
    </svg>
  )
}

function GearIcon({ className = 'w-5 h-5' }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}

/* ─── Thumbnail with fallback ──────────────────────────── */
function Thumbnail({ video, className = 'h-full w-full object-cover' }) {
  const [failed, setFailed] = useState(false)

  if (failed || !video.thumbnailUrl) {
    return (
      <div className="absolute inset-0 flex flex-col items-center justify-center bg-[#1a1a1a]">
        <div className="w-12 h-12 rounded-full bg-netflix-red/20 flex items-center justify-center mb-2">
          <PlayIcon className="w-6 h-6 text-netflix-red" />
        </div>
        <span className="text-[11px] font-semibold uppercase tracking-widest text-white/30">
          Aperçu indisponible
        </span>
      </div>
    )
  }

  return (
    <img
      src={video.thumbnailUrl}
      alt={`Aperçu de ${video.title}`}
      className={className}
      loading="lazy"
      onError={() => setFailed(true)}
    />
  )
}

/* ─── Skeleton card ─────────────────────────────────────── */
function SkeletonCard() {
  return (
    <div className="flex-none w-48 md:w-56 rounded-md overflow-hidden">
      <div className="aspect-video skeleton rounded-md" />
      <div className="mt-2 space-y-1.5 px-1">
        <div className="h-3 skeleton rounded w-3/4" />
        <div className="h-2.5 skeleton rounded w-1/2" />
      </div>
    </div>
  )
}

/* ─── Video card ─────────────────────────────────────────── */
function VideoCard({ video, onPlay, onSelect }) {
  return (
    <div
      className="video-card group flex-none w-48 md:w-56 cursor-pointer"
      onClick={() => onPlay(video)}
    >
      <div className="relative aspect-video rounded-md overflow-hidden bg-[#1a1a1a]">
        <Thumbnail video={video} />
        {/* Gradient overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
        {/* Live badge */}
        <div className="absolute top-2 left-2 flex items-center gap-1 bg-netflix-red text-white text-[10px] font-bold px-2 py-0.5 rounded-sm uppercase tracking-wider">
          <span className="live-pulse inline-block w-1.5 h-1.5 rounded-full bg-white" />
          Live
        </div>
        {/* Play overlay */}
        <div className="absolute inset-0 flex items-end justify-between p-3 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
          <button
            className="w-9 h-9 rounded-full bg-white flex items-center justify-center shadow-lg hover:bg-white/90 transition-colors"
            onClick={(e) => { e.stopPropagation(); onPlay(video) }}
            aria-label="Lire"
          >
            <PlayIcon className="w-4 h-4 text-black ml-0.5" />
          </button>
          <button
            className="w-9 h-9 rounded-full bg-netflix-card/80 border border-white/30 flex items-center justify-center hover:bg-netflix-card transition-colors"
            onClick={(e) => { e.stopPropagation(); onSelect(video) }}
            aria-label="Plus d'infos"
          >
            <InfoIcon className="w-4 h-4 text-white" />
          </button>
        </div>
      </div>

      {/* Card info */}
      <div className="mt-2 px-0.5">
        <h4 className="text-white text-sm font-semibold truncate group-hover:text-netflix-red transition-colors">
          {video.title}
        </h4>
        <p className="text-netflix-light text-xs mt-0.5 font-mono truncate">
          {video.host}:{video.port}
        </p>
      </div>
    </div>
  )
}

/* ─── Horizontal scroll row ──────────────────────────────── */
function ContentRow({ title, badge, videos, loading, onPlay, onSelect }) {
  const rowRef = useRef(null)

  function scroll(dir) {
    if (rowRef.current) {
      rowRef.current.scrollBy({ left: dir * 400, behavior: 'smooth' })
    }
  }

  return (
    <section className="mb-10">
      <div className="flex items-baseline gap-3 mb-4 px-4 md:px-12">
        <h2 className="text-white text-xl font-bold tracking-tight">{title}</h2>
        {badge && (
          <span className="text-netflix-red text-sm font-semibold animate-pulse">{badge}</span>
        )}
      </div>

      <div className="relative group/row">
        {/* Left arrow */}
        <button
          onClick={() => scroll(-1)}
          className="absolute left-0 top-0 bottom-6 z-20 w-10 md:w-12 flex items-center justify-center
                     bg-gradient-to-r from-netflix-black to-transparent
                     opacity-0 group-hover/row:opacity-100 transition-opacity"
          aria-label="Défiler à gauche"
        >
          <svg className="w-6 h-6 text-white drop-shadow" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>

        {/* Cards */}
        <div
          ref={rowRef}
          className="flex gap-3 overflow-x-auto scrollbar-hide px-4 md:px-12 pb-6"
        >
          {loading
            ? Array.from({ length: 6 }).map((_, i) => <SkeletonCard key={i} />)
            : videos.map((video) => (
                <VideoCard
                  key={`${video.host}-${video.port}`}
                  video={video}
                  onPlay={onPlay}
                  onSelect={onSelect}
                />
              ))}
        </div>

        {/* Right arrow */}
        <button
          onClick={() => scroll(1)}
          className="absolute right-0 top-0 bottom-6 z-20 w-10 md:w-12 flex items-center justify-center
                     bg-gradient-to-l from-netflix-black to-transparent
                     opacity-0 group-hover/row:opacity-100 transition-opacity"
          aria-label="Défiler à droite"
        >
          <svg className="w-6 h-6 text-white drop-shadow" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
    </section>
  )
}

/* ─── Video player modal ─────────────────────────────────── */
function PlayerModal({ video, onClose }) {
  useEffect(() => {
    const handleKey = (e) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', handleKey)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', handleKey)
      document.body.style.overflow = ''
    }
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-50 modal-backdrop flex items-center justify-center p-4 animate-fade-in"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-5xl bg-netflix-dark rounded-lg overflow-hidden shadow-2xl animate-slide-up"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Video */}
        <div className="aspect-video bg-black">
          <video
            key={video.url}
            controls
            autoPlay
            poster={video.thumbnailUrl}
            className="w-full h-full"
          >
            <source src={video.url} type="video/mp4" />
            Votre navigateur ne supporte pas la lecture vidéo.
          </video>
        </div>

        {/* Info bar */}
        <div className="p-5 flex justify-between items-start">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-netflix-red text-xs font-bold uppercase tracking-wider live-pulse">● Live</span>
              <span className="text-netflix-light text-xs">HD</span>
            </div>
            <h2 className="text-white text-2xl font-bold">{video.title}</h2>
            <p className="text-netflix-light font-mono text-sm mt-1">{video.host}:{video.port}</p>
          </div>
          <button
            onClick={onClose}
            className="mt-1 p-2 rounded-full bg-white/10 hover:bg-white/20 transition-colors"
            aria-label="Fermer"
          >
            <XIcon className="w-5 h-5 text-white" />
          </button>
        </div>
      </div>
    </div>
  )
}

/* ─── Hero banner ────────────────────────────────────────── */
function HeroBanner({ video, onPlay, onMoreInfo }) {
  return (
    <div className="relative w-full" style={{ height: 'min(56.25vw, 680px)' }}>
      {/* Background image */}
      <div className="absolute inset-0">
        {video.thumbnailUrl ? (
          <img
            src={video.thumbnailUrl}
            alt={video.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full bg-[#1a1a1a]" />
        )}
      </div>

      {/* Vignettes */}
      <div className="absolute inset-0 hero-vignette" />
      <div className="absolute inset-0 hero-vignette-side" />

      {/* Content */}
      <div className="absolute bottom-[20%] left-0 px-4 md:px-12 max-w-xl animate-slide-up">
        <div className="flex items-center gap-2 mb-3">
          <span className="flex items-center gap-1.5 bg-netflix-red text-white text-xs font-bold px-2.5 py-1 rounded-sm uppercase tracking-wider">
            <span className="live-pulse inline-block w-1.5 h-1.5 rounded-full bg-white" />
            En direct
          </span>
        </div>
        <h1 className="text-white text-4xl md:text-5xl font-bold leading-tight drop-shadow-2xl">
          {video.title}
        </h1>
        <p className="text-white/70 font-mono text-sm mt-2">
          {video.host}:{video.port}
        </p>

        <div className="flex items-center gap-3 mt-6">
          <button
            onClick={() => onPlay(video)}
            className="flex items-center gap-2 bg-white text-black font-bold px-6 py-2.5 rounded-md
                       hover:bg-white/85 active:scale-95 transition-all text-sm md:text-base"
          >
            <PlayIcon className="w-5 h-5" />
            Lire
          </button>
          <button
            onClick={() => onMoreInfo(video)}
            className="flex items-center gap-2 bg-white/20 backdrop-blur-sm text-white font-semibold px-5 py-2.5 rounded-md
                       hover:bg-white/30 active:scale-95 transition-all text-sm md:text-base border border-white/20"
          >
            <InfoIcon className="w-5 h-5" />
            Plus d'infos
          </button>
        </div>
      </div>
    </div>
  )
}

/* ─── Navbar ─────────────────────────────────────────────── */
function Navbar({ query, onQueryChange, onSettingsClick, error, autoRefresh, onToggleRefresh }) {
  const [scrolled, setScrolled] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const searchRef = useRef(null)

  useEffect(() => {
    function onScroll() { setScrolled(window.scrollY > 20) }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    if (searchOpen && searchRef.current) searchRef.current.focus()
  }, [searchOpen])

  return (
    <nav
      className={`fixed top-0 left-0 right-0 z-40 h-16 flex items-center px-4 md:px-12 transition-all duration-500 ${
        scrolled ? 'bg-netflix-black' : 'nav-gradient'
      }`}
    >
      {/* Logo */}
      <div className="flex-shrink-0 mr-8">
        <span className="font-display font-bold text-2xl tracking-tight text-netflix-red select-none">
          STREAM<span className="text-white">DASH</span>
        </span>
      </div>

      {/* Nav links */}
      <div className="hidden md:flex items-center gap-6 text-sm font-medium mr-auto">
        <a href="#" className="text-white hover:text-white/70 transition-colors">Accueil</a>
        <a href="#" className="text-white/70 hover:text-white transition-colors">Flux Live</a>
        <a href="#" className="text-white/70 hover:text-white transition-colors">Récemment ajoutés</a>
      </div>

      {/* Right side */}
      <div className="ml-auto flex items-center gap-3">
        {/* Search */}
        <div className="flex items-center">
          {searchOpen ? (
            <div className="flex items-center bg-black/80 border border-white/40 px-3 py-1.5 rounded-sm">
              <SearchIcon className="w-4 h-4 text-white/60 flex-shrink-0 mr-2" />
              <input
                ref={searchRef}
                type="search"
                placeholder="Titres, flux..."
                className="bg-transparent text-white text-sm outline-none w-40 md:w-56 placeholder-white/40"
                value={query}
                onChange={(e) => onQueryChange(e.target.value)}
                onBlur={() => { if (!query) setSearchOpen(false) }}
              />
              {query && (
                <button onClick={() => { onQueryChange(''); setSearchOpen(false) }} className="ml-2 text-white/60 hover:text-white">
                  <XIcon className="w-4 h-4" />
                </button>
              )}
            </div>
          ) : (
            <button onClick={() => setSearchOpen(true)} className="text-white/80 hover:text-white transition-colors p-1" aria-label="Rechercher">
              <SearchIcon className="w-5 h-5" />
            </button>
          )}
        </div>

        {/* Status indicator */}
        <div
          className={`h-2 w-2 rounded-full flex-shrink-0 ${
            error
              ? 'bg-red-500 shadow-[0_0_6px_rgba(239,68,68,0.7)]'
              : 'bg-emerald-500 shadow-[0_0_6px_rgba(16,185,129,0.7)]'
          }`}
          title={error ? 'API hors ligne' : 'API connectée'}
        />

        {/* Auto-refresh toggle */}
        <button
          onClick={onToggleRefresh}
          className={`hidden sm:flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-sm transition-colors ${
            autoRefresh
              ? 'bg-white/10 text-white hover:bg-white/20'
              : 'bg-netflix-red/20 text-netflix-red hover:bg-netflix-red/30'
          }`}
        >
          <span className={`w-1.5 h-1.5 rounded-full ${autoRefresh ? 'bg-emerald-400 live-pulse' : 'bg-netflix-red'}`} />
          {autoRefresh ? 'Live' : 'Pausé'}
        </button>

        {/* Settings */}
        <button
          onClick={onSettingsClick}
          className="text-white/80 hover:text-white transition-colors p-1"
          aria-label="Paramètres"
        >
          <GearIcon className="w-5 h-5" />
        </button>
      </div>
    </nav>
  )
}

/* ─── Settings panel ─────────────────────────────────────── */
function SettingsPanel({ draftApiUrl, onDraftChange, onSave, onClose }) {
  return (
    <div className="fixed inset-0 z-50 modal-backdrop flex items-center justify-center p-4 animate-fade-in" onClick={onClose}>
      <div
        className="bg-netflix-dark border border-white/10 rounded-lg p-6 w-full max-w-sm shadow-2xl animate-slide-up"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex justify-between items-center mb-5">
          <h3 className="text-white font-bold text-lg flex items-center gap-2">
            <GearIcon className="w-5 h-5 text-netflix-red" />
            Configuration API
          </h3>
          <button onClick={onClose} className="text-white/50 hover:text-white transition-colors">
            <XIcon />
          </button>
        </div>

        <form
          onSubmit={(e) => { e.preventDefault(); onSave(); onClose() }}
          className="space-y-4"
        >
          <div>
            <label className="block text-netflix-light text-xs font-medium mb-1.5 uppercase tracking-wider">
              URL de l'API
            </label>
            <input
              className="w-full bg-white/5 border border-white/15 rounded-md px-3 py-2.5 text-sm text-white
                         focus:outline-none focus:border-netflix-red/60 focus:bg-white/8 transition-colors"
              value={draftApiUrl}
              onChange={(e) => onDraftChange(e.target.value)}
              placeholder="http://localhost:8080"
            />
          </div>
          <button
            type="submit"
            className="w-full bg-netflix-red hover:bg-netflix-red-hover text-white font-bold py-2.5 rounded-md transition-colors text-sm"
          >
            Enregistrer
          </button>
        </form>
      </div>
    </div>
  )
}

/* ─── Empty state ─────────────────────────────────────────── */
function EmptyState({ hasQuery }) {
  return (
    <div className="flex flex-col items-center justify-center py-32 px-4 text-center animate-fade-in">
      <div className="w-20 h-20 rounded-full bg-white/5 flex items-center justify-center mb-6">
        <PlayIcon className="w-10 h-10 text-white/20" />
      </div>
      <h3 className="text-white text-xl font-bold mb-2">
        {hasQuery ? 'Aucun résultat' : 'Aucun flux disponible'}
      </h3>
      <p className="text-netflix-light text-sm max-w-xs">
        {hasQuery
          ? 'Essayez un autre terme de recherche.'
          : 'Lancez le Provider pour démarrer un flux vidéo.'}
      </p>
    </div>
  )
}

/* ─── App ─────────────────────────────────────────────────── */
function App() {
  const [apiBaseUrl, setApiBaseUrl] = useState(() => localStorage.getItem('vs.api') || API_DEFAULT)
  const [draftApiUrl, setDraftApiUrl] = useState(apiBaseUrl)
  const [videos, setVideos] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [playingVideo, setPlayingVideo] = useState(null)
  const [selectedVideo, setSelectedVideo] = useState(null)
  const [showSettings, setShowSettings] = useState(false)

  const filteredVideos = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return videos
    return videos.filter(
      (v) =>
        v.title.toLowerCase().includes(q) ||
        v.host.toLowerCase().includes(q)
    )
  }, [videos, query])

  const fetchVideos = useCallback(async () => {
    try {
      const response = await fetch(`${apiBaseUrl}/api/videos`)
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const payload = await response.json()
      setVideos(Array.isArray(payload.videos) ? payload.videos : [])
      setError('')
    } catch {
      setError('Connexion API perdue')
    } finally {
      setLoading(false)
    }
  }, [apiBaseUrl])

  useEffect(() => {
    setLoading(true)
    fetchVideos()
  }, [fetchVideos])

  useEffect(() => {
    if (!autoRefresh) return
    const id = setInterval(fetchVideos, 5000)
    return () => clearInterval(id)
  }, [autoRefresh, fetchVideos])

  function handleSaveApiUrl() {
    setApiBaseUrl(draftApiUrl)
    localStorage.setItem('vs.api', draftApiUrl)
  }

  const heroVideo = selectedVideo || filteredVideos[0] || null

  // Split videos into rows
  const liveVideos = filteredVideos
  const recentVideos = [...filteredVideos].reverse().slice(0, 10)

  return (
    <div className="min-h-screen bg-netflix-black text-white overflow-x-hidden">
      <Navbar
        query={query}
        onQueryChange={setQuery}
        onSettingsClick={() => setShowSettings(true)}
        error={error}
        autoRefresh={autoRefresh}
        onToggleRefresh={() => setAutoRefresh((v) => !v)}
      />

      {/* Hero section */}
      {heroVideo && !loading && (
        <HeroBanner
          video={heroVideo}
          onPlay={setPlayingVideo}
          onMoreInfo={setSelectedVideo}
        />
      )}

      {/* Loading hero skeleton */}
      {loading && (
        <div className="relative w-full skeleton" style={{ height: 'min(56.25vw, 680px)' }}>
          <div className="absolute inset-0 hero-vignette" />
        </div>
      )}

      {/* Content rows */}
      <div className={`relative z-10 ${heroVideo || loading ? '-mt-24' : 'mt-16'}`}>
        {error && (
          <div className="mx-4 md:mx-12 mb-6 flex items-center gap-3 bg-netflix-red/10 border border-netflix-red/30 text-netflix-red text-sm px-4 py-3 rounded-md">
            <span className="flex-shrink-0">●</span>
            <span>{error} — Vérifiez l'URL de l'API dans les paramètres.</span>
          </div>
        )}

        <ContentRow
          title="Flux en direct"
          badge="● Live"
          videos={liveVideos}
          loading={loading}
          onPlay={setPlayingVideo}
          onSelect={setSelectedVideo}
        />

        {!loading && filteredVideos.length > 3 && (
          <ContentRow
            title="Récemment ajoutés"
            videos={recentVideos}
            loading={false}
            onPlay={setPlayingVideo}
            onSelect={setSelectedVideo}
          />
        )}

        {!loading && filteredVideos.length === 0 && (
          <EmptyState hasQuery={!!query.trim()} />
        )}

        {/* Footer */}
        <footer className="px-4 md:px-12 py-10 mt-6 border-t border-white/5">
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
            <span className="font-display font-bold text-xl text-netflix-red">
              STREAM<span className="text-white/50">DASH</span>
            </span>
            <p className="text-netflix-muted text-xs">
              © {new Date().getFullYear()} StreamDash — Plateforme de streaming en direct
            </p>
          </div>
        </footer>
      </div>

      {/* Player modal */}
      {playingVideo && (
        <PlayerModal video={playingVideo} onClose={() => setPlayingVideo(null)} />
      )}

      {/* Settings panel */}
      {showSettings && (
        <SettingsPanel
          draftApiUrl={draftApiUrl}
          onDraftChange={setDraftApiUrl}
          onSave={handleSaveApiUrl}
          onClose={() => setShowSettings(false)}
        />
      )}
    </div>
  )
}

export default App
