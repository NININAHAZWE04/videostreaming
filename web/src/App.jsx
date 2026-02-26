import { useEffect, useMemo, useState } from 'react'

const API_DEFAULT = 'http://localhost:8080'

function Thumbnail({ video }) {
  const [failed, setFailed] = useState(false)

  if (failed || !video.thumbnailUrl) {
    return (
      <div className="absolute inset-0 flex items-center justify-center bg-slate-900 text-slate-500">
        <span className="text-xs font-semibold uppercase tracking-widest">Aperçu indisponible</span>
      </div>
    )
  }

  return (
    <img
      src={video.thumbnailUrl}
      alt={`Aperçu de ${video.title}`}
      className="h-full w-full object-cover"
      loading="lazy"
      onError={() => setFailed(true)}
    />
  )
}

function App() {
  const [apiBaseUrl, setApiBaseUrl] = useState(() => localStorage.getItem('vs.api') || API_DEFAULT)
  const [draftApiUrl, setDraftApiUrl] = useState(apiBaseUrl)
  const [videos, setVideos] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [selectedVideo, setSelectedVideo] = useState(null)

  const filteredVideos = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return videos
    return videos.filter((v) =>
        v.title.toLowerCase().includes(q) || v.host.toLowerCase().includes(q)
    )
  }, [videos, query])

  async function fetchVideos() {
    setLoading(true)
    try {
      const response = await fetch(`${apiBaseUrl}/api/videos`)
      if (!response.ok) throw new Error(`Err ${response.status}`)
      const payload = await response.json()
      setVideos(Array.isArray(payload.videos) ? payload.videos : [])
      setError('')
    } catch (err) {
      setError("Connexion API perdue")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchVideos() }, [apiBaseUrl])
  useEffect(() => {
    if (!autoRefresh) return
    const id = setInterval(fetchVideos, 5000)
    return () => clearInterval(id)
  }, [autoRefresh, apiBaseUrl])

  return (
      <div className="min-h-screen bg-[#0f172a] text-slate-200 font-sans selection:bg-indigo-500/30">
        {/* Top Navigation */}
        <nav className="sticky top-0 z-50 border-b border-white/5 bg-[#0f172a]/80 backdrop-blur-md">
          <div className="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center font-bold text-white shadow-lg shadow-indigo-500/20">V</div>
              <span className="font-display font-bold text-xl tracking-tight text-white">Stream<span className="text-indigo-500">Dash</span></span>
            </div>

            <div className="hidden md:flex flex-1 max-w-md mx-8">
              <input
                  type="search"
                  placeholder="Rechercher un flux..."
                  className="w-full bg-slate-800/50 border border-slate-700 rounded-full px-4 py-1.5 text-sm focus:ring-2 ring-indigo-500 outline-none transition-all"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
              />
            </div>

            <div className="flex items-center gap-4">
              <div className={`h-2 w-2 rounded-full ${error ? 'bg-red-500 shadow-[0_0_8px_rgba(239,68,68,0.5)]' : 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]'}`} />
              <button onClick={() => setAutoRefresh(!autoRefresh)} className={`text-xs font-medium px-3 py-1 rounded-md transition ${autoRefresh ? 'bg-indigo-500/10 text-indigo-400' : 'bg-slate-800 text-slate-400'}`}>
                {autoRefresh ? 'Auto-sync ON' : 'Sync Manuel'}
              </button>
            </div>
          </div>
        </nav>

        <main className="max-w-7xl mx-auto px-4 py-8">
          {/* Player Section */}
          {selectedVideo && (
              <section className="mb-12 animate-in fade-in slide-in-from-bottom-4 duration-500">
                <div className="aspect-video w-full rounded-2xl overflow-hidden bg-black border border-white/10 shadow-2xl">
                  <video
                      key={selectedVideo.url}
                      controls
                      autoPlay
                      poster={selectedVideo.thumbnailUrl}
                      className="w-full h-full"
                  >
                    <source src={selectedVideo.url} type="video/mp4" />
                    Votre navigateur ne supporte pas la lecture.
                  </video>
                </div>
                <div className="mt-4 flex justify-between items-start">
                  <div>
                    <h2 className="text-2xl font-bold text-white">{selectedVideo.title}</h2>
                    <p className="text-slate-400 font-mono text-sm">{selectedVideo.host}:{selectedVideo.port}</p>
                  </div>
                  <button onClick={() => setSelectedVideo(null)} className="text-slate-500 hover:text-white transition">Fermer</button>
                </div>
              </section>
          )}

          {/* Video Grid */}
          <header className="mb-8">
            <h3 className="text-sm font-semibold uppercase tracking-widest text-indigo-500 mb-2">Live Now</h3>
            <h2 className="text-3xl font-bold text-white">Flux disponibles</h2>
          </header>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {filteredVideos.map((video) => (
                <div
                    key={`${video.host}-${video.port}`}
                    className="group bg-slate-800/40 border border-white/5 rounded-xl overflow-hidden hover:border-indigo-500/50 hover:bg-slate-800/60 transition-all duration-300 cursor-pointer"
                    onClick={() => setSelectedVideo(video)}
                >
                  <div className="aspect-video bg-slate-900 relative flex items-center justify-center">
                    <Thumbnail video={video} />
                    <div className="absolute top-2 right-2 bg-red-600 text-[10px] font-bold px-2 py-0.5 rounded uppercase tracking-wider text-white">Live</div>
                    <div className="group-hover:scale-110 transition-transform duration-300 w-12 h-12 rounded-full bg-indigo-600/35 backdrop-blur-sm flex items-center justify-center text-white">
                      <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
                    </div>
                  </div>
                  <div className="p-4">
                    <h4 className="font-bold text-white group-hover:text-indigo-400 transition-colors truncate">{video.title}</h4>
                    <div className="mt-1 flex items-center gap-2 text-xs text-slate-500">
                      <span className="font-mono">{video.host}</span>
                      <span>•</span>
                      <span>Port {video.port}</span>
                    </div>
                  </div>
                </div>
            ))}
          </div>

          {!filteredVideos.length && !loading && (
              <div className="text-center py-20 border-2 border-dashed border-slate-800 rounded-3xl">
                <p className="text-slate-500">Aucun flux détecté. Lancez le Provider.</p>
              </div>
          )}
        </main>

        {/* Settings Tab (Floating) */}
        <footer className="fixed bottom-6 right-6 group">
          <div className="bg-slate-900 border border-slate-700 p-4 rounded-2xl shadow-2xl opacity-0 translate-y-4 pointer-events-none group-hover:opacity-100 group-hover:translate-y-0 group-hover:pointer-events-auto transition-all w-80">
            <h4 className="font-bold text-white mb-3 flex items-center gap-2">
              <svg className="w-4 h-4 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
              Config API
            </h4>
            <form onSubmit={(e) => { e.preventDefault(); setApiBaseUrl(draftApiUrl); localStorage.setItem('vs.api', draftApiUrl); }} className="space-y-3">
              <input
                  className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white"
                  value={draftApiUrl}
                  onChange={(e) => setDraftApiUrl(e.target.value)}
              />
              <button className="w-full bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold py-2 rounded-lg transition">Mettre à jour</button>
            </form>
          </div>
          <div className="mt-4 flex justify-end">
            <div className="bg-indigo-600 p-3 rounded-full shadow-lg text-white cursor-help">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4"></path></svg>
            </div>
          </div>
        </footer>
      </div>
  )
}

export default App
