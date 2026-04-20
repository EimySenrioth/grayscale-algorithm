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
    todos_seq: null, todos_par: null,
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

  const process = async (mode, overrideAlgorithm = null) => {
    if (!file) { setError('Selecciona una imagen primero.'); return }
    if (isRaw && (!width || !height)) { setError('Ingresa ancho y alto para .rgb.'); return }

    const algoToUse = overrideAlgorithm || algorithm
    const loadingKey = (algoToUse === 'scharr' || algoToUse === 'todos') ? `${algoToUse}_${mode}` : algoToUse
    setLoading(loadingKey); setError(null); setShowLogs(true)
    log('info', `Algoritmo: ${algoToUse.toUpperCase()} · Modo: ${mode ? mode.toUpperCase() : 'SINGLE'}`)

    const form = new FormData()
    form.append('image', file)
    form.append('algorithm', algoToUse)
    if (isRaw) { form.append('width', width); form.append('height', height) }
    if (algoToUse === 'scharr' || algoToUse === 'todos') form.append('mode', mode)

    let lastError = null

    for (const url of API_CANDIDATES) {
      const fullUrl = (algoToUse === 'scharr' || algoToUse === 'todos')
        ? `${url}?algorithm=${algoToUse}&mode=${mode}`
        : `${url}?algorithm=${algoToUse}`
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
        if (algoToUse === 'scharr') {
          setHistory(prev => ({ ...prev, [mode]: entry }))
          log('ok', `✅ Scharr T1=${json.t1Ms}ms T2=${json.t2Ms}ms T3=${json.t3Ms}ms T4=${json.t4Ms}ms Total=${json.totalMs}ms`)
        } else if (algoToUse === 'todos') {
          const histKey = mode === 'sequential' ? 'todos_seq' : 'todos_par'
          setHistory(prev => ({ ...prev, [histKey]: entry }))
          log('ok', `✅ Todos (${mode}) Total=${json.totalMs}ms`)
        } else {
          setHistory(prev => ({ ...prev, [algoToUse]: entry }))
          log('ok', `✅ ${algoToUse.toUpperCase()} Total=${json.totalMs}ms`)
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
    <div className="layout-dashboard">
      
      {/* ── SIDEBAR ── */}
      <aside className="sidebar">
        
        <div className="sidebar-header">
           <span className={`logo-badge logo-badge--${algorithm}`}>
             Edge Detection
           </span>
           <h2>Algoritmo Gris</h2>
        </div>

        <div className="sidebar-scroll">

          {/* Subir Archivo */}
          <section className="sb-card">
            <h3 className="sb-subtitle">Fuente de Entrada</h3>
            <div
              id="drop-zone"
              className={`drop-zone ${dragging ? 'dragging' : ''} ${preview ? 'has-image' : ''}`}
              onDrop={onDrop} onDragOver={onDragOver} onDragLeave={onDragLeave}
              onClick={() => document.getElementById('file-input').click()}
            >
              {preview
                ? <img src={preview} alt="Vista previa" className="preview-img" />
                : <div className="drop-placeholder">
                    <p className="drop-hint">Arrastra o haz clic</p>
                  </div>
              }
            </div>
            <input id="file-input" type="file" accept=".rgb,image/*" style={{ display:'none' }} onChange={onFileInput} />
            {file && (
              <p className="file-name">
                {(file.size/1024).toFixed(1)} KB {isRaw && <span className="rgb-badge">RAW</span>}
              </p>
            )}
          </section>

          {/* Dimensiones .rgb */}
          {isRaw && (
            <section className="sb-card dims-section">
              <h3 className="sb-subtitle">Dimensiones RAW</h3>
              <div className="dims-row">
                <input id="input-width" type="number" min="1" placeholder="W"
                    value={width} onChange={e => setWidth(e.target.value)} onBlur={buildRgbPreview} />
                <span className="dim-sep">×</span>
                <input id="input-height" type="number" min="1" placeholder="H"
                    value={height} onChange={e => setHeight(e.target.value)} onBlur={buildRgbPreview} />
              </div>
            </section>
          )}

          {/* Elegir y Procesar Individual */}
          <section className="sb-card">
            <h3 className="sb-subtitle">Filtro Individual</h3>
            <div className="sb-field">
               <div className="select-wrapper">
                 <select value={algorithm} onChange={(e) => {setAlgorithm(e.target.value); setResult(null); setError(null);}}>
                   {Object.entries(ALGORITHMS).map(([key, cfg]) => (
                     <option key={key} value={key}>{cfg.icon} {cfg.label}</option>
                   ))}
                 </select>
               </div>
            </div>

            <div className="algo-controls">
              {algorithm === 'scharr' ? (
                <div className="buttons-col">
                  <button onClick={() => process('parallel')} disabled={!!loading} className="btn-lotus btn-lotus-blue">
                     {loading === 'scharr_parallel' ? <span className="spinner"/> : '⚡'} Paralelo (4 hilos)
                  </button>
                  <button onClick={() => process('sequential')} disabled={!!loading} className="btn-lotus btn-lotus-outline">
                     {loading === 'scharr_sequential' ? <span className="spinner"/> : '▶'} Secuencial
                  </button>
                </div>
              ) : (
                <div className="buttons-col">
                  <button onClick={() => process('sequential')} disabled={!!loading} className="btn-lotus btn-lotus-outline">
                    {loading === algorithm ? <span className="spinner"/> : ALGORITHMS[algorithm]?.icon} Ejecutar {ALGORITHMS[algorithm]?.label}
                  </button>
                </div>
              )}
            </div>
            {loading && !String(loading).startsWith('todos_') && <p className="loading-text">Procesando {ALGORITHMS[algorithm]?.label}…</p>}
            {error && <p className="error-text">⚠ {error}</p>}
          </section>

          {/* Múltiples algoritmos */}
          <section className="sb-card sb-card-batch">
             <h3 className="sb-subtitle" style={{color:'#ed8936'}}>Mosaico (5 Algoritmos)</h3>
             <div className="buttons-col">
                <button onClick={() => process('parallel', 'todos')} disabled={!!loading} className="btn-lotus btn-lotus-accent">
                   {loading === 'todos_parallel' ? <span className="spinner"/> : '⚡'} Concurrente (5 hilos)
                </button>
                <button onClick={() => process('sequential', 'todos')} disabled={!!loading} className="btn-lotus btn-lotus-secondary">
                   {loading === 'todos_sequential' ? <span className="spinner"/> : '▶'} Secuencial
                </button>
             </div>
             {loading && String(loading).startsWith('todos_') && <p className="loading-text" style={{color:'#ed8936'}}>Procesando Mosaico…</p>}
          </section>

        </div>
        
        <div className="sidebar-footer">
           <span className="info-text">E Sincronizado. Always Available.</span>
           <span className="info-text" style={{textAlign: 'right'}}>User Itano</span>
        </div>
      </aside>

      {/* ── MAIN AREA ── */}
      <main className="main-area">

         {/* Top Bar Lotus Style */}
         <div className="topbar">
           <div className="topbar-logo">Detección de Bordes</div>
           <div className="topbar-actions">
           </div>
         </div>

         {/* Contenido / Rejilla */}
         <div className="dashboard-content">
            {totalGalleryImages === 0 && !preview && (
               <div className="empty-state">
                  <span className="empty-icon">🖼️</span>
                  <p>Sube una imagen y selecciona un algoritmo para visualizar los resultados.</p>
               </div>
            )}

            <div className="lotus-grid">

               {/* 1. Tarjeta Original */}
               {preview && (
                  <div className="lotus-card lotus-card-original">
                     <div className="lotus-img-area">
                        <img src={preview} alt="Original" />
                        <div className="lotus-overlay-badge">Original</div>
                     </div>
                     <div className="lotus-data-area">
                        <h4>Imagen de Entrada</h4>
                        <p>Documento de Origen</p>
                        <div className="lotus-stats">
                           <div className="lotus-stat">
                              <span>Peso</span>
                              <strong>{(file?.size/1024).toFixed(1)} KB</strong>
                           </div>
                           <div className="lotus-stat">
                              <span>Modo</span>
                              <strong>{isRaw ? 'RAW' : 'Img'}</strong>
                           </div>
                        </div>
                     </div>
                  </div>
               )}

               {/* Recorrer history para renderizar resultados */}
               {['canny', 'sobel', 'laplaciano', 'prewitt'].filter(a => history[a]).map(a => (
                  <div key={a} className={`lotus-card lotus-card-${a}`}>
                     <div className="lotus-img-area">
                        <img src={history[a].imageBase64} alt={a} />
                        <div className="lotus-overlay-badge badge-algo">{ALGORITHMS[a].icon} {ALGORITHMS[a].label}</div>
                     </div>
                     <div className="lotus-data-area">
                        <h4>{ALGORITHMS[a].label}</h4>
                        <p>{ALGORITHMS[a].sub}</p>
                        <div className="lotus-stats">
                           <div className="lotus-stat">
                              <span>Total</span>
                              <strong>{fmtMs(history[a].totalMs)}</strong>
                           </div>
                           <div className="lotus-stat">
                              <span>Link</span>
                              <a href={history[a].imageBase64} download={`${a}.png`}>⬇ PNG</a>
                           </div>
                        </div>
                     </div>
                  </div>
               ))}

               {history.parallel && (
                  <div className="lotus-card lotus-card-scharr">
                     <div className="lotus-img-area">
                        <img src={history.parallel.imageBase64} alt="Scharr Paralelo" />
                        <div className="lotus-overlay-badge badge-algo">⚡ Scharr P.</div>
                     </div>
                     <div className="lotus-data-area">
                        <h4>Scharr Paralelo</h4>
                        <p>Segmentado en 4 hilos</p>
                        <div className="lotus-stats">
                           <div className="lotus-stat"><span>Time</span><strong>{fmtMs(history.parallel.totalMs)}</strong></div>
                           <div className="lotus-stat"><span>T1</span><strong>{fmtMs(history.parallel.t1Ms)}</strong></div>
                           <div className="lotus-stat"><span>T2</span><strong>{fmtMs(history.parallel.t2Ms)}</strong></div>
                        </div>
                     </div>
                  </div>
               )}
               
               {history.sequential && (
                  <div className="lotus-card lotus-card-scharr">
                     <div className="lotus-img-area">
                        <img src={history.sequential.imageBase64} alt="Scharr Secuencial" />
                        <div className="lotus-overlay-badge badge-algo">▶ Scharr S.</div>
                     </div>
                     <div className="lotus-data-area">
                        <h4>Scharr Secuencial</h4>
                        <p>1 solo hilo</p>
                        <div className="lotus-stats">
                           <div className="lotus-stat"><span>Time</span><strong>{fmtMs(history.sequential.totalMs)}</strong></div>
                        </div>
                     </div>
                  </div>
               )}

            </div>
            
            {/* Si existe historial lote, lo pintamos al fondo como una row grande o integrado */}
            {['todos_seq', 'todos_par'].filter(k => history[k]).map(cat => {
               const isSeq = cat === 'todos_seq'
               const res = history[cat]
               const inner = res.results
               if (!inner) return null;

               return (
                  <div key={cat} className="mosaico-section-wrapper mt-4">
                     <h3 className="mosaico-group-title">{isSeq ? '▶ Mosaico 5 Algoritmos (Secuencial)' : '⚡ Mosaico 5 Algoritmos (Concurrente)'} — <span style={{color: '#ed8936'}}>{fmtMs(res.totalMs)}</span></h3>
                     <div className="lotus-grid">
                        {['scharr', 'sobel', 'canny', 'laplaciano', 'prewitt'].map(a => (
                           <div key={a} className={`lotus-card lotus-card-${a}`}>
                              <div className="lotus-img-area">
                                 <img src={inner[a].imageBase64} alt={a} />
                                 <div className="lotus-overlay-badge badge-algo">{ALGORITHMS[a].icon} {ALGORITHMS[a].label}</div>
                              </div>
                              <div className="lotus-data-area">
                                 <h4>{ALGORITHMS[a].label}</h4>
                                 <p>Tiempo de ejecución</p>
                                 <div className="lotus-stats">
                                    <div className="lotus-stat"><span>Ms</span><strong>{fmtMs(inner[a].totalMs)}</strong></div>
                                 </div>
                              </div>
                           </div>
                        ))}
                     </div>
                  </div>
               )
            })}

         </div>
      </main>
    </div>
  )
}
