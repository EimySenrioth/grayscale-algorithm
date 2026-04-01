import { useState, useCallback, useRef } from 'react'
import './App.css'

// ── Configuración de URLs a intentar ─────────────────────────────────────────
// NetBeans puede desplegar en / (raíz) o en /algoritmogris según el context path.
// Se intentan ambas en orden hasta obtener respuesta JSON.
const API_CANDIDATES = [
  'http://localhost:8080/api/process',
  'http://localhost:8080/algoritmogris/api/process',
]

function fmtMs(ms) {
  if (ms === null || ms === undefined || ms === 0) return '—'
  return `${Number(ms).toFixed(3)} ms`
}

function timestamp() {
  return new Date().toLocaleTimeString()
}

export default function App() {
  const [file, setFile]         = useState(null)
  const [preview, setPreview]   = useState(null)
  const [isRaw, setIsRaw]       = useState(false)
  const [width, setWidth]       = useState('')
  const [height, setHeight]     = useState('')
  const [result, setResult]     = useState(null)
  const [history, setHistory]   = useState({ parallel: null, sequential: null })
  const [loading, setLoading]   = useState(false)
  const [error, setError]       = useState(null)
  const [dragging, setDragging] = useState(false)
  const [logs, setLogs]         = useState([])
  const [showLogs, setShowLogs] = useState(false)
  const logRef = useRef(null)

  // ── Logger ─────────────────────────────────────────────────────────────────
  const log = (level, msg) => {
    const entry = { ts: timestamp(), level, msg }
    setLogs(prev => {
      const next = [...prev, entry]
      // Scroll al final del log panel
      setTimeout(() => logRef.current?.scrollTo(0, logRef.current.scrollHeight), 50)
      return next
    })
  }

  const clearLogs = () => setLogs([])

  // ── Manejar archivo ────────────────────────────────────────────────────────
  const handleFile = (f) => {
    if (!f) return
    setFile(f)
    setResult(null)
    setError(null)
    const raw = f.name.toLowerCase().endsWith('.rgb')
    setIsRaw(raw)
    setPreview(raw ? null : URL.createObjectURL(f))
    log('info', `Archivo seleccionado: ${f.name} (${(f.size/1024).toFixed(1)} KB, ${raw ? 'RAW RGB' : 'imagen estándar'})`)
  }

  // ── Drag & Drop ───────────────────────────────────────────────────────────
  const onDrop      = useCallback((e) => { e.preventDefault(); setDragging(false); handleFile(e.dataTransfer.files[0]) }, [])
  const onDragOver  = (e) => { e.preventDefault(); setDragging(true) }
  const onDragLeave = ()  => setDragging(false)
  const onFileInput = (e) => handleFile(e.target.files[0])

  // ── Preview .rgb via Canvas ────────────────────────────────────────────────
  const buildRgbPreview = () => {
    if (!file || !isRaw || !width || !height) return
    const w = parseInt(width), h = parseInt(height)
    if (!w || !h) return
    log('info', `Generando preview RGB: ${w}×${h} px`)
    const reader = new FileReader()
    reader.onload = (e) => {
      const bytes = new Uint8Array(e.target.result)
      const canvas = document.createElement('canvas')
      canvas.width = w; canvas.height = h
      const ctx = canvas.getContext('2d')
      const data = ctx.createImageData(w, h)
      for (let i = 0; i < w * h; i++) {
        data.data[i*4]   = bytes[i*3]
        data.data[i*4+1] = bytes[i*3+1]
        data.data[i*4+2] = bytes[i*3+2]
        data.data[i*4+3] = 255
      }
      ctx.putImageData(data, 0, 0)
      setPreview(canvas.toDataURL())
      log('ok', `Preview generado correctamente`)
    }
    reader.readAsArrayBuffer(file)
  }

  // ── Llamada al backend ────────────────────────────────────────────────────
  const process = async (mode) => {
    if (!file) { setError('Arrastra o selecciona una imagen primero.'); return }
    if (isRaw && (!width || !height)) {
      setError('Para archivos .rgb debes ingresar el ancho y alto.'); return
    }

    setLoading(mode)
    setError(null)
    setShowLogs(true)
    log('info', `Iniciando procesamiento en modo: ${mode.toUpperCase()}`)

    const form = new FormData()
    form.append('image', file)
    if (isRaw) { form.append('width', width); form.append('height', height) }

    // ── Intentar cada URL candidata ─────────────────────────────────────────
    let lastError = null

    for (const url of API_CANDIDATES) {
      const fullUrl = `${url}?mode=${mode}`
      log('info', `Intentando: POST ${fullUrl}`)

      try {
        const res = await fetch(fullUrl, { method: 'POST', body: form })
        log('info', `Respuesta HTTP: ${res.status} ${res.statusText}`)
        log('info', `Content-Type: ${res.headers.get('content-type') || 'desconocido'}`)

        const contentType = res.headers.get('content-type') || ''

        if (!contentType.includes('application/json')) {
          const text = await res.text()
          log('error', `No es JSON. HTML recibido (primeros 200 chars): ${text.substring(0, 200)}`)
          lastError = `404 en ${fullUrl} — el servlet no está en esa ruta`
          continue // Intentar siguiente URL
        }

        const json = await res.json()
        log('info', `JSON recibido: ${JSON.stringify(json)}`)

        if (!res.ok) {
          log('error', `Error del servidor: ${json.error}`)
          throw new Error(json.error || 'Error del servidor')
        }

        const entry = { ...json, mode, timestamp: timestamp() }
        setResult(entry)
        setHistory(prev => ({ ...prev, [mode]: entry }))
        log('ok', `✅ Proceso completado. T1=${json.t1Ms}ms T2=${json.t2Ms}ms Total=${json.totalMs}ms`)
        log('ok', `Imagen guardada en: ${json.imagePath}`)
        setLoading(false)
        return // Éxito, salir

      } catch (err) {
        if (err.name === 'TypeError' && err.message.includes('fetch')) {
          log('error', `No se pudo conectar a ${url} — Tomcat no está corriendo o rechazó la conexión`)
          lastError = `No se puede conectar a ${url}`
        } else {
          log('error', `Error: ${err.message}`)
          lastError = err.message
        }
      }
    }

    // Si ninguna URL funcionó
    setError(lastError || 'No se pudo conectar al backend. Revisa el panel de depuración.')
    setLoading(false)
  }

  return (
    <div className="app">
      {/* ── Header ──────────────────────────────────────────────────────────── */}
      <header className="header">
        <span className="logo-badge">Scharr</span>
        <h1>Detección de Bordes</h1>
        <p className="subtitle">Algoritmo Scharr · 2 Hilos · Soporte RGB Raw (m × n)</p>
      </header>

      <main className="main">

        {/* ── Drop Zone ─────────────────────────────────────────────────────── */}
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
          <input id="file-input" type="file" accept=".rgb,image/*"
            style={{ display:'none' }} onChange={onFileInput} />
          {file && (
            <p className="file-name">
              📁 {file.name} — {(file.size/1024).toFixed(1)} KB
              {isRaw && <span className="rgb-badge">RAW RGB</span>}
            </p>
          )}
        </section>

        {/* ── Dimensiones (solo para .rgb) ──────────────────────────────────── */}
        {isRaw && (
          <section className="card dims-section">
            <h2 className="section-title">📐 Dimensiones de la imagen RAW</h2>
            <p className="dims-hint">
              Los archivos <code>.rgb</code> no tienen cabecera. Ingresa el ancho y alto exactos en píxeles.
            </p>
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
              <button className="btn btn-secondary dim-preview-btn" onClick={buildRgbPreview}>
                👁 Previsualizar
              </button>
            </div>
          </section>
        )}

        {/* ── Botones de Control ────────────────────────────────────────────── */}
        <section className="card controls-section">
          <h2 className="section-title">Modo de Procesamiento</h2>
          <div className="buttons-row">
            <button id="btn-parallel" className="btn btn-primary"
              onClick={() => process('parallel')} disabled={!!loading}>
              {loading === 'parallel' ? <span className="spinner" /> : '⚡'}
              Paralelo (2 hilos)
            </button>
            <button id="btn-sequential" className="btn btn-secondary"
              onClick={() => process('sequential')} disabled={!!loading}>
              {loading === 'sequential' ? <span className="spinner" /> : '▶'}
              No paralelo (Secuencial)
            </button>
          </div>
          {loading && <p className="loading-text">Procesando en modo {loading}…</p>}
          {error   && <p className="error-text">⚠ {error}</p>}
        </section>

        {/* ── Resultado ─────────────────────────────────────────────────────── */}
        {result && (
          <section className="card results-section">
            <h2 className="section-title">
              Último resultado — <span className="badge">{result.mode}</span>
              &nbsp;·&nbsp;{result.width}×{result.height} px
            </h2>
            <div className="result-grid">
              <div className="result-panel">
                <p className="panel-label">Input</p>
                {preview
                  ? <img src={preview} alt="Original" className="result-img" />
                  : <div className="result-img placeholder-output"><span className="saved-path">Sin preview</span></div>
                }
              </div>
              <div className="result-panel">
                <p className="panel-label">Output — Scharr (escala de grises)</p>
                {result.imageBase64
                  ? <img src={result.imageBase64} alt="Resultado Scharr" className="result-img" />
                  : <div className="placeholder-output result-img">
                      <span className="saved-path">✅<br /><code>{result.imagePath}</code></span>
                    </div>
                }
              </div>
            </div>
          </section>
        )}

        {/* ── Tiempos ───────────────────────────────────────────────────────── */}
        {(history.parallel || history.sequential) && (
          <section className="card times-section">
            <h2 className="section-title">⏱ Tiempos de ejecución</h2>
            <div className="times-grid">
              <div className={`time-block ${history.parallel ? '' : 'time-block--empty'}`}>
                <p className="time-block-title">⚡ Paralelo (4 hilos)</p>
                {history.parallel ? (<>
                  <div className="time-row"><span>T1 — Hilo 1 <span className="chip">Cuarto superior</span></span><span className="time-val">{fmtMs(history.parallel.t1Ms)}</span></div>
                  <div className="time-row"><span>T2 — Hilo 2 <span className="chip">2do cuarto</span></span><span className="time-val">{fmtMs(history.parallel.t2Ms)}</span></div>
                  <div className="time-row"><span>T3 — Hilo 3 <span className="chip">3er cuarto</span></span><span className="time-val">{fmtMs(history.parallel.t3Ms)}</span></div>
                  <div className="time-row"><span>T4 — Hilo 4 <span className="chip">Cuarto inferior</span></span><span className="time-val">{fmtMs(history.parallel.t4Ms)}</span></div>
                  <div className="time-row time-row--total"><span>Application End Time</span><span className="time-val accent">{fmtMs(history.parallel.totalMs)}</span></div>
                  <p className="time-stamp">Ejecutado a las {history.parallel.timestamp}</p>
                </>) : <p className="time-pending">Aún no ejecutado</p>}
              </div>
              <div className={`time-block ${history.sequential ? '' : 'time-block--empty'}`}>
                <p className="time-block-title">▶ No Paralelo (Secuencial)</p>
                {history.sequential ? (<>
                  <div className="time-row"><span>T1 — Hilo único</span><span className="time-val">{fmtMs(history.sequential.t1Ms)}</span></div>
                  <div className="time-row time-row--total"><span>Application End Time</span><span className="time-val accent">{fmtMs(history.sequential.totalMs)}</span></div>
                  <p className="time-stamp">Ejecutado a las {history.sequential.timestamp}</p>
                </>) : <p className="time-pending">Aún no ejecutado</p>}
              </div>
            </div>
            {history.parallel && history.sequential && (
              <div className="comparison-bar">
                <p className="comparison-label">
                  El paralelo fue&nbsp;
                  <strong className="comparison-value">
                    {(history.sequential.totalMs / history.parallel.totalMs).toFixed(2)}×
                  </strong>
                  &nbsp;más rápido que el secuencial
                </p>
              </div>
            )}
          </section>
        )}

        {/* ── Panel de Depuración ───────────────────────────────────────────── */}
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
                ? <p className="log-empty">Sin entradas aún. Ejecuta un proceso para ver los logs.</p>
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
