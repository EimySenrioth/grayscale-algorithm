import { useState, useCallback, useRef } from 'react'
import './App.css'

const API_CANDIDATES = [
  'http://localhost:8080/api/process',
  'http://localhost:8080/algoritmogris/api/process',
]

// ── Configuración de algoritmos ───────────────────────────────────────────────
const ALGORITHMS = {
  scharr:     { label: 'Scharr',      icon: '⚡', sub: '4 hilos paralelos',       color: 'scharr'     },
  canny:      { label: 'Canny',       icon: '🔍', sub: 'Gaussiano + Sobel + Umbral', color: 'canny'   },
  sobel:      { label: 'Sobel',       icon: '📐', sub: 'Gradiente Sobel',          color: 'sobel'      },
  laplaciano: { label: 'Laplaciano',  icon: '∇²', sub: 'Laplaciano 8-vecinos',    color: 'laplaciano' },
  prewitt:    { label: 'Prewitt',     icon: '🔲', sub: 'Gradiente uniforme',       color: 'prewitt'    },
}

function fmtMs(ms) {
  if (ms === null || ms === undefined || ms === 0) return '—'
  return `${Number(ms).toFixed(3)} ms`
}

function timestamp() {
  return new Date().toLocaleTimeString()
}

export default function App() {
  const [file, setFile]           = useState(null)
  const [preview, setPreview]     = useState(null)
  const [isRaw, setIsRaw]         = useState(false)
  const [width, setWidth]         = useState('')
  const [height, setHeight]       = useState('')
  const [algorithm, setAlgorithm] = useState('scharr')
  const [result, setResult]       = useState(null)
  const [history, setHistory]     = useState({
    parallel: null, sequential: null,
    canny: null, sobel: null, laplaciano: null, prewitt: null,
  })
  const [gallery, setGallery]     = useState({
    scharr: [], canny: [], sobel: [], laplaciano: [], prewitt: [],
  })
  const [galleryTab, setGalleryTab] = useState('scharr')
  const [loading, setLoading]     = useState(false)
  const [error, setError]         = useState(null)
  const [dragging, setDragging]   = useState(false)
  const [logs, setLogs]           = useState([])
  const [showLogs, setShowLogs]   = useState(false)
  const logRef = useRef(null)

  const log = (level, msg) => {
    const entry = { ts: timestamp(), level, msg }
    setLogs(prev => {
      const next = [...prev, entry]
      setTimeout(() => logRef.current?.scrollTo(0, logRef.current.scrollHeight), 50)
      return next
    })
  }
  const clearLogs = () => setLogs([])

  const handleFile = (f) => {
    if (!f) return
    setFile(f); setResult(null); setError(null)
    const raw = f.name.toLowerCase().endsWith('.rgb')
    setIsRaw(raw)
    setPreview(raw ? null : URL.createObjectURL(f))
    log('info', `Archivo: ${f.name} (${(f.size/1024).toFixed(1)} KB, ${raw ? 'RAW RGB' : 'estándar'})`)
  }

  const onDrop      = useCallback((e) => { e.preventDefault(); setDragging(false); handleFile(e.dataTransfer.files[0]) }, [])
  const onDragOver  = (e) => { e.preventDefault(); setDragging(true) }
  const onDragLeave = () => setDragging(false)
  const onFileInput = (e) => handleFile(e.target.files[0])

  const buildRgbPreview = () => {
    if (!file || !isRaw || !width || !height) return
    const w = parseInt(width), h = parseInt(height)
    if (!w || !h) return
    log('info', `Generando preview RGB: ${w}×${h}`)
    const reader = new FileReader()
    reader.onload = (e) => {
      const bytes = new Uint8Array(e.target.result)
      const canvas = document.createElement('canvas')
      canvas.width = w; canvas.height = h
      const ctx = canvas.getContext('2d')
      const data = ctx.createImageData(w, h)
      for (let i = 0; i < w * h; i++) {
        data.data[i*4] = bytes[i*3]; data.data[i*4+1] = bytes[i*3+1]
        data.data[i*4+2] = bytes[i*3+2]; data.data[i*4+3] = 255
      }
      ctx.putImageData(data, 0, 0)
      setPreview(canvas.toDataURL())
      log('ok', 'Preview generado')
    }
    reader.readAsArrayBuffer(file)
  }

  const process = async (mode) => {
    if (!file) { setError('Selecciona una imagen primero.'); return }
    if (isRaw && (!width || !height)) { setError('Ingresa ancho y alto para .rgb.'); return }

    const loadingKey = algorithm === 'scharr' ? mode : algorithm
    setLoading(loadingKey); setError(null); setShowLogs(true)
    log('info', `Algoritmo: ${algorithm.toUpperCase()} · Modo: ${mode.toUpperCase()}`)

    const form = new FormData()
    form.append('image', file)
    form.append('algorithm', algorithm)
    if (isRaw) { form.append('width', width); form.append('height', height) }
    if (algorithm === 'scharr') form.append('mode', mode)

    let lastError = null

    for (const url of API_CANDIDATES) {
      const fullUrl = algorithm === 'scharr'
        ? `${url}?algorithm=scharr&mode=${mode}`
        : `${url}?algorithm=${algorithm}`
      log('info', `POST ${fullUrl}`)

      try {
        const res = await fetch(fullUrl, { method: 'POST', body: form })
        const contentType = res.headers.get('content-type') || ''
        if (!contentType.includes('application/json')) {
          const text = await res.text()
          log('error', `No es JSON: ${text.substring(0, 200)}`)
          lastError = `404 en ${fullUrl}`; continue
        }

        const json = await res.json()
        log('info', `JSON: ${JSON.stringify(json)}`)
        if (!res.ok) throw new Error(json.error || 'Error del servidor')

        const entry = { ...json, timestamp: timestamp() }
        setResult(entry)

        // Actualizar historial
        if (algorithm === 'scharr') {
          setHistory(prev => ({ ...prev, [mode]: entry }))
          log('ok', `✅ Scharr T1=${json.t1Ms}ms T2=${json.t2Ms}ms T3=${json.t3Ms}ms T4=${json.t4Ms}ms Total=${json.totalMs}ms`)
        } else {
          setHistory(prev => ({ ...prev, [algorithm]: entry }))
          log('ok', `✅ ${algorithm.toUpperCase()} Total=${json.totalMs}ms`)
        }

        // Agregar a galería
        const galleryKey = algorithm === 'scharr' ? 'scharr' : algorithm
        setGallery(prev => ({
          ...prev,
          [galleryKey]: [entry, ...prev[galleryKey]],
        }))
        setGalleryTab(galleryKey)

        log('ok', `Guardado en: ${json.imagePath}`)
        setLoading(false)
        return

      } catch (err) {
        if (err.name === 'TypeError' && err.message.includes('fetch')) {
          log('error', `Sin conexión a ${url}`)
          lastError = `No se puede conectar a ${url}`
        } else {
          log('error', `Error: ${err.message}`)
          lastError = err.message
        }
      }
    }

    setError(lastError || 'No se pudo conectar al backend.')
    setLoading(false)
  }

  const totalGalleryImages = Object.values(gallery).reduce((s, arr) => s + arr.length, 0)

  return (
    <div className="app">
      {/* Header */}
      <header className="header">
        <span className={`logo-badge logo-badge--${algorithm}`}>
          {ALGORITHMS[algorithm].icon} {ALGORITHMS[algorithm].label}
        </span>
        <h1>Detección de Bordes</h1>
        <p className="subtitle">Scharr · Canny · Sobel · Laplaciano · Prewitt · RGB Raw</p>
      </header>

      <main className="main">

        {/* Drop Zone */}
        <section className="card drop-section">
          <div
            id="drop-zone"
            className={`drop-zone ${dragging ? 'dragging' : ''} ${preview ? 'has-image' : ''}`}
            onDrop={onDrop} onDragOver={onDragOver} onDragLeave={onDragLeave}
            onClick={() => document.getElementById('file-input').click()}
          >
            {preview
              ? <img src={preview} alt="Vista previa" className="preview-img" />
              : <div className="drop-placeholder">
                  <div className="drop-icon">🖼️</div>
                  <p className="drop-text">Arrastra tu imagen aquí</p>
                  <p className="drop-hint">Cualquier tamaño m × n · <strong>.rgb</strong> raw · PNG · JPG</p>
                </div>
            }
          </div>
          <input id="file-input" type="file" accept=".rgb,image/*" style={{ display:'none' }} onChange={onFileInput} />
          {file && (
            <p className="file-name">
              📁 {file.name} — {(file.size/1024).toFixed(1)} KB
              {isRaw && <span className="rgb-badge">RAW RGB</span>}
            </p>
          )}
        </section>

        {/* Dimensiones .rgb */}
        {isRaw && (
          <section className="card dims-section">
            <h2 className="section-title">📐 Dimensiones RAW</h2>
            <p className="dims-hint">Los archivos <code>.rgb</code> no tienen cabecera. Ingresa el ancho y alto en píxeles.</p>
            <div className="dims-row">
              <div className="dim-field">
                <label htmlFor="input-width">Ancho (px)</label>
                <input id="input-width" type="number" min="1" placeholder="ej: 640"
                  value={width} onChange={e => setWidth(e.target.value)} onBlur={buildRgbPreview} />
              </div>
              <span className="dim-sep">×</span>
              <div className="dim-field">
                <label htmlFor="input-height">Alto (px)</label>
                <input id="input-height" type="number" min="1" placeholder="ej: 480"
                  value={height} onChange={e => setHeight(e.target.value)} onBlur={buildRgbPreview} />
              </div>
              <button className="btn btn-secondary dim-preview-btn" onClick={buildRgbPreview}>👁 Previsualizar</button>
            </div>
          </section>
        )}

        {/* Selector de Algoritmo */}
        <section className="card algo-section">
          <h2 className="section-title">Algoritmo</h2>
          <div className="algo-toggle">
            {Object.entries(ALGORITHMS).map(([key, cfg]) => (
              <button
                key={key}
                id={`btn-algo-${key}`}
                className={`algo-btn ${algorithm === key ? `algo-btn--active-${key}` : ''}`}
                onClick={() => { setAlgorithm(key); setResult(null); setError(null) }}
              >
                {cfg.icon} {cfg.label}
                <span className="algo-sub">{cfg.sub}</span>
              </button>
            ))}
          </div>
        </section>

        {/* Botones de Procesamiento */}
        <section className="card controls-section">
          <h2 className="section-title">Modo de Procesamiento</h2>
          {algorithm === 'scharr' ? (
            <div className="buttons-row">
              <button id="btn-parallel" className="btn btn-primary"
                onClick={() => process('parallel')} disabled={!!loading}>
                {loading === 'parallel' ? <span className="spinner" /> : '⚡'} Scharr — Paralelo (4 hilos)
              </button>
              <button id="btn-sequential" className="btn btn-secondary"
                onClick={() => process('sequential')} disabled={!!loading}>
                {loading === 'sequential' ? <span className="spinner" /> : '▶'} Scharr — Secuencial
              </button>
            </div>
          ) : (
            <div className="buttons-row">
              <button id={`btn-${algorithm}`} className={`btn btn-algo btn-algo--${algorithm}`}
                onClick={() => process('sequential')} disabled={!!loading}>
                {loading === algorithm ? <span className="spinner" /> : ALGORITHMS[algorithm].icon}
                Ejecutar {ALGORITHMS[algorithm].label}
              </button>
            </div>
          )}
          {loading && <p className="loading-text">Procesando con {ALGORITHMS[algorithm]?.label || algorithm}…</p>}
          {error   && <p className="error-text">⚠ {error}</p>}
        </section>

        {/* ── Cuadrícula comparativa 3×n ─────────────────────────────────── */}
        {Object.values(history).some(v => v !== null) && (
          <section className="card cmp-section">
            <h2 className="section-title">📊 Comparación de resultados</h2>

            <div className="cmp-grid">

              {/* Celda: imagen original */}
              <div className="cmp-card cmp-card--original">
                <div className="cmp-card-header">
                  <span className="cmp-algo-badge cmp-algo-badge--original">🖼️ Original</span>
                </div>
                <div className="cmp-img-wrap">
                  {preview
                    ? <img src={preview} alt="Original" className="cmp-photo" />
                    : <div className="cmp-photo cmp-photo--empty">Sin vista previa</div>
                  }
                </div>
                <div className="cmp-card-footer">
                  <span className="cmp-footer-label">Imagen de entrada</span>
                </div>
              </div>

              {/* Scharr Paralelo */}
              {history.parallel && (
                <div className="cmp-card cmp-card--scharr">
                  <div className="cmp-card-header">
                    <span className="cmp-algo-badge cmp-algo-badge--scharr">⚡ Scharr Paralelo</span>
                  </div>
                  <div className="cmp-img-wrap">
                    {history.parallel.imageBase64
                      ? <img src={history.parallel.imageBase64} alt="Scharr P" className="cmp-photo" />
                      : <div className="cmp-photo cmp-photo--empty">—</div>}
                  </div>
                  <div className="cmp-card-footer">
                    <div className="cmp-times">
                      <span className="cmp-time cmp-time--scharr">{fmtMs(history.parallel.totalMs)}</span>
                      <span className="cmp-time-label">total</span>
                    </div>
                    <div className="cmp-threads">
                      <span className="cmp-thread">T1 {fmtMs(history.parallel.t1Ms)}</span>
                      <span className="cmp-thread">T2 {fmtMs(history.parallel.t2Ms)}</span>
                      <span className="cmp-thread">T3 {fmtMs(history.parallel.t3Ms)}</span>
                      <span className="cmp-thread">T4 {fmtMs(history.parallel.t4Ms)}</span>
                    </div>
                    <div className="cmp-footer-row">
                      <span className="cmp-footer-ts">{history.parallel.timestamp}</span>
                      {history.parallel.imageBase64 &&
                        <a className="cmp-dl cmp-dl--scharr" href={history.parallel.imageBase64}
                          download={`scharr_paralelo_${history.parallel.timestamp?.replace(/:/g,'-')}.png`}>⬇ PNG</a>}
                    </div>
                  </div>
                </div>
              )}

              {/* Scharr Secuencial */}
              {history.sequential && (
                <div className="cmp-card cmp-card--scharr">
                  <div className="cmp-card-header">
                    <span className="cmp-algo-badge cmp-algo-badge--scharr">▶ Scharr Secuencial</span>
                  </div>
                  <div className="cmp-img-wrap">
                    {history.sequential.imageBase64
                      ? <img src={history.sequential.imageBase64} alt="Scharr S" className="cmp-photo" />
                      : <div className="cmp-photo cmp-photo--empty">—</div>}
                  </div>
                  <div className="cmp-card-footer">
                    <div className="cmp-times">
                      <span className="cmp-time cmp-time--scharr">{fmtMs(history.sequential.totalMs)}</span>
                      <span className="cmp-time-label">total</span>
                    </div>
                    <div className="cmp-threads">
                      <span className="cmp-thread">T1 {fmtMs(history.sequential.t1Ms)}</span>
                    </div>
                    <div className="cmp-footer-row">
                      <span className="cmp-footer-ts">{history.sequential.timestamp}</span>
                      {history.sequential.imageBase64 &&
                        <a className="cmp-dl cmp-dl--scharr" href={history.sequential.imageBase64}
                          download={`scharr_secuencial_${history.sequential.timestamp?.replace(/:/g,'-')}.png`}>⬇ PNG</a>}
                    </div>
                  </div>
                </div>
              )}

              {/* Canny, Sobel, Laplaciano, Prewitt */}
              {['canny','sobel','laplaciano','prewitt'].filter(a => history[a]).map(alg => (
                <div key={alg} className={`cmp-card cmp-card--${alg}`}>
                  <div className="cmp-card-header">
                    <span className={`cmp-algo-badge cmp-algo-badge--${alg}`}>
                      {ALGORITHMS[alg].icon} {ALGORITHMS[alg].label}
                    </span>
                  </div>
                  <div className="cmp-img-wrap">
                    {history[alg].imageBase64
                      ? <img src={history[alg].imageBase64} alt={alg} className="cmp-photo" />
                      : <div className="cmp-photo cmp-photo--empty">—</div>}
                  </div>
                  <div className="cmp-card-footer">
                    <div className="cmp-times">
                      <span className={`cmp-time cmp-time--${alg}`}>{fmtMs(history[alg].totalMs)}</span>
                      <span className="cmp-time-label">tiempo total</span>
                    </div>
                    <div className="cmp-footer-row">
                      <span className="cmp-footer-ts">{history[alg].timestamp}</span>
                      {history[alg].imageBase64 &&
                        <a className={`cmp-dl cmp-dl--${alg}`} href={history[alg].imageBase64}
                          download={`${alg}_${history[alg].timestamp?.replace(/:/g,'-')}.png`}>⬇ PNG</a>}
                    </div>
                  </div>
                </div>
              ))}

            </div>
          </section>
        )}

        {/* Debug */}

        <section className="card debug-section">
          <div className="debug-header" onClick={() => setShowLogs(v => !v)}>
            <h2 className="section-title" style={{margin:0}}>🐛 Panel de Depuración</h2>
            <div style={{display:'flex', gap:'0.5rem', alignItems:'center'}}>
              <span className="log-count">{logs.length} entradas</span>
              <button className="btn-icon" onClick={e => { e.stopPropagation(); clearLogs() }}>🗑</button>
              <span className="debug-toggle">{showLogs ? '▲' : '▼'}</span>
            </div>
          </div>
          {showLogs && (
            <div className="log-panel" ref={logRef}>
              {logs.length === 0
                ? <p className="log-empty">Sin entradas aún.</p>
                : logs.map((l, i) => (
                  <div key={i} className={`log-entry log-${l.level}`}>
                    <span className="log-ts">{l.ts}</span>
                    <span className="log-msg">{l.msg}</span>
                  </div>
                ))
              }
            </div>
          )}
        </section>

      </main>
    </div>
  )
}
