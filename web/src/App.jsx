import { useEffect, useMemo, useState } from 'react'

const API_DEFAULT = 'http://localhost:8080'

function App() {
  const [apiBaseUrl, setApiBaseUrl] = useState(() => localStorage.getItem('vs.api') || API_DEFAULT)
  const [draftApiUrl, setDraftApiUrl] = useState(apiBaseUrl)
  const [videos, setVideos] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [lastSync, setLastSync] = useState('')

  const filteredVideos = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return videos
    return videos.filter((video) => {
      return (
        video.title.toLowerCase().includes(q) ||
        video.host.toLowerCase().includes(q) ||
        String(video.port).includes(q)
      )
    })
  }, [videos, query])

  async function fetchVideos() {
    setLoading(true)
    setError('')
    try {
      const response = await fetch(`${apiBaseUrl}/api/videos`)
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      const payload = await response.json()
      setVideos(Array.isArray(payload.videos) ? payload.videos : [])
      setLastSync(new Date().toLocaleTimeString('fr-FR'))
    } catch (err) {
      setVideos([])
      setError(`Impossible de joindre l'API (${err.message}). Vérifiez Diary + Web API.`)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchVideos()
  }, [apiBaseUrl])

  useEffect(() => {
    if (!autoRefresh) return
    const id = setInterval(fetchVideos, 5000)
    return () => clearInterval(id)
  }, [autoRefresh, apiBaseUrl])

  function saveApiUrl(event) {
    event.preventDefault()
    const normalized = draftApiUrl.trim().replace(/\/$/, '')
    if (!normalized.startsWith('http://') && !normalized.startsWith('https://')) {
      setError('L\'URL API doit commencer par http:// ou https://')
      return
    }
    localStorage.setItem('vs.api', normalized)
    setApiBaseUrl(normalized)
  }

  return (
    <main className="hero-bg min-h-screen px-4 py-6 sm:px-6 lg:px-10">
      <section className="mx-auto flex w-full max-w-7xl flex-col gap-5">
        <header className="glass-panel animate-fadeUp p-6">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">
              <p className="font-mono text-xs uppercase tracking-[0.2em] text-ocean">VideoStreaming Platform</p>
              <h1 className="mt-2 font-display text-3xl font-bold sm:text-4xl">Dashboard Web Moderne</h1>
              <p className="mt-3 text-sm text-slate-700 sm:text-base">
                Visualisez en temps réel les vidéos disponibles, ouvrez instantanément les flux et contrôlez la connectivité API.
              </p>
            </div>
            <div className="flex items-center gap-3">
              <span className={error ? 'badge-err' : 'badge-ok'}>{error ? 'API indisponible' : 'API connectée'}</span>
              <button
                onClick={fetchVideos}
                disabled={loading}
                className="rounded-xl bg-ink px-4 py-2 text-sm font-semibold text-white transition hover:bg-ocean disabled:cursor-not-allowed disabled:opacity-60"
              >
                {loading ? 'Chargement...' : 'Actualiser'}
              </button>
            </div>
          </div>
        </header>

        <div className="grid gap-5 lg:grid-cols-[2fr_1fr]">
          <section className="glass-panel animate-fadeUp p-5 [animation-delay:120ms]">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="font-display text-xl font-bold">Catalogue des flux</h2>
                <p className="text-sm text-slate-600">
                  {filteredVideos.length} résultat(s) • Dernière synchro: {lastSync || 'n/a'}
                </p>
              </div>
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Filtrer par titre, hôte, port"
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none ring-ocean transition focus:ring sm:max-w-xs"
              />
            </div>

            {error ? <p className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p> : null}

            <div className="mt-4 overflow-x-auto rounded-xl border border-slate-200">
              <table className="min-w-full bg-white text-sm">
                <thead className="bg-ink text-left text-xs uppercase tracking-wide text-white">
                  <tr>
                    <th className="px-4 py-3">Titre</th>
                    <th className="px-4 py-3">Hôte</th>
                    <th className="px-4 py-3">Port</th>
                    <th className="px-4 py-3">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredVideos.map((video, index) => (
                    <tr key={`${video.title}-${video.host}-${video.port}`} className={index % 2 ? 'bg-slate-50' : 'bg-white'}>
                      <td className="px-4 py-3 font-semibold text-slate-800">{video.title}</td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-700">{video.host}</td>
                      <td className="px-4 py-3 text-slate-700">{video.port}</td>
                      <td className="px-4 py-3">
                        <a
                          href={video.url}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center rounded-lg bg-ember px-3 py-1.5 text-xs font-semibold text-white transition hover:brightness-95"
                        >
                          Ouvrir le flux
                        </a>
                      </td>
                    </tr>
                  ))}
                  {!filteredVideos.length && !loading ? (
                    <tr>
                      <td colSpan="4" className="px-4 py-8 text-center text-slate-500">
                        Aucune vidéo disponible pour le moment.
                      </td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </div>
          </section>

          <aside className="flex flex-col gap-5">
            <section className="glass-panel animate-fadeUp p-5 [animation-delay:220ms]">
              <h3 className="font-display text-lg font-bold">Configuration API</h3>
              <form onSubmit={saveApiUrl} className="mt-3 flex flex-col gap-3">
                <label className="text-sm font-medium text-slate-700" htmlFor="api-url">
                  Base URL
                </label>
                <input
                  id="api-url"
                  value={draftApiUrl}
                  onChange={(event) => setDraftApiUrl(event.target.value)}
                  className="rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none ring-ocean transition focus:ring"
                  placeholder="http://localhost:8080"
                />
                <button className="rounded-xl bg-ocean px-4 py-2 text-sm font-semibold text-white transition hover:bg-cyan-700" type="submit">
                  Enregistrer
                </button>
              </form>
            </section>

            <section className="glass-panel animate-fadeUp p-5 [animation-delay:320ms]">
              <h3 className="font-display text-lg font-bold">État</h3>
              <ul className="mt-3 space-y-2 text-sm text-slate-700">
                <li className="flex items-center justify-between">
                  <span>Auto-refresh (5s)</span>
                  <input
                    type="checkbox"
                    checked={autoRefresh}
                    onChange={(event) => setAutoRefresh(event.target.checked)}
                    className="h-4 w-4 accent-ocean"
                  />
                </li>
                <li className="flex items-center justify-between">
                  <span>Flux total</span>
                  <strong>{videos.length}</strong>
                </li>
                <li className="flex items-center justify-between">
                  <span>API active</span>
                  <strong>{apiBaseUrl}</strong>
                </li>
              </ul>
            </section>

            <section className="glass-panel animate-float p-5">
              <h3 className="font-display text-lg font-bold">Conseil exploitation</h3>
              <p className="mt-2 text-sm text-slate-700">
                Démarrez toujours dans cet ordre: Diary, Provider GUI, API Web, puis Frontend.
              </p>
            </section>
          </aside>
        </div>
      </section>
    </main>
  )
}

export default App
