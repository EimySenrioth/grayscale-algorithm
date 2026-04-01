# Scharr Edge Detection — Fullstack

Sistema fullstack de **detección de bordes** en imágenes digitales usando el operador **Scharr** con procesamiento paralelo de **4 hilos concurrentes** en Java.

---

## 🗂 Estructura del proyecto

```
algoritmogris/
├── pom.xml                               ← Maven WAR, Java 21, Jakarta EE
├── frontend/                             ← React + Vite (UI)
│   ├── package.json
│   └── src/
│       ├── App.jsx                       ← Interfaz principal
│       └── App.css                       ← Estilos dark/glassmorphism
└── src/main/
    ├── java/com/algoritmogris/
    │   ├── servlet/
    │   │   ├── EdgeDetectionServlet.java ← REST endpoint POST /api/process
    │   │   └── CORSFilter.java           ← Filtro CORS para el frontend
    │   ├── service/
    │   │   └── ScharrService.java        ← Algoritmo Scharr + 4 hilos
    │   └── util/
    │       └── StorageUtil.java          ← Guarda imágenes en samples/
    └── webapp/WEB-INF/
        └── web.xml
```

---

## ⚙️ Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Java 21 · Jakarta Servlet 6.0 · Apache Tomcat 11 |
| Build | Maven 3 · WAR packaging |
| Frontend | React 18 · Vite · CSS vanilla |
| IDE | NetBeans 28 |

---

## 🧠 Algoritmo

El operador **Scharr** aplica dos kernels 3×3 (Gx, Gy) sobre cada píxel en escala de grises y calcula la magnitud del gradiente:

```
G = √(Gx² + Gy²)   →   clamp a [0, 255]
```

La imagen se convierte a grises con la fórmula de luminancia ITU-R BT.601 antes del procesamiento.

### Procesamiento paralelo — 4 hilos

La imagen se divide en **4 franjas horizontales iguales**, cada una procesada por un hilo independiente con `ExecutorService`:

```
┌──────────────────────┐ ← fila 0
│  Hilo 1 (T1)         │   Cuarto superior
├──────────────────────┤ ← fila h/4
│  Hilo 2 (T2)         │   Segundo cuarto
├──────────────────────┤ ← fila h/2
│  Hilo 3 (T3)         │   Tercer cuarto
├──────────────────────┤ ← fila 3h/4
│  Hilo 4 (T4)         │   Cuarto inferior
└──────────────────────┘ ← fila h-1
```

Los hilos escriben en rangos exclusivos del arreglo de salida → **sin condiciones de carrera**.

---

## 🚀 Cómo correr el proyecto

### Backend (NetBeans + Tomcat 11)

1. Abrir la carpeta `algoritmogris/` como proyecto Maven en NetBeans
2. Configurar Apache Tomcat 11 en **Tools → Servers → Add Server**
3. Click derecho sobre el proyecto → **Clean and Build**
4. Presionar **F6** para desplegar en Tomcat

El backend estará disponible en:
```
http://localhost:8080/api/process
```

### Frontend (React + Vite)

```bash
cd frontend
npm install       # solo la primera vez
npm run dev
```

Abrir en el navegador: **http://localhost:5173**

---

## 📡 API REST

### `POST /api/process`

**Parámetros (multipart/form-data):**

| Campo | Tipo | Descripción |
|---|---|---|
| `image` | File | Imagen `.rgb` (raw), PNG o JPG |
| `mode` | String | `parallel` o `sequential` |
| `width` | Integer | Ancho en px (solo para `.rgb`) |
| `height` | Integer | Alto en px (solo para `.rgb`) |

**Respuesta JSON:**

```json
{
  "imagePath":   "C:\\...\\samples\\scharr_20260401_parallel.png",
  "imageBase64": "data:image/png;base64,...",
  "t1Ms": 12.453,
  "t2Ms": 13.102,
  "t3Ms": 12.891,
  "t4Ms": 13.345,
  "totalMs": 14.220,
  "mode": "parallel",
  "width": 640,
  "height": 480
}
```

---

## 🖼 Soporte de imágenes RAW `.rgb`

Los archivos `.rgb` son datos crudos sin cabecera: `[R, G, B, R, G, B, ...]` por píxel.  
El usuario debe proporcionar el **ancho** y **alto** exactos para que el backend pueda reconstituir la imagen.

Validación: `tamaño_archivo == width × height × 3 bytes`

---

## 🖥 Interfaz

- **Drag & Drop** de imágenes (`.rgb`, PNG, JPG) de cualquier tamaño m×n
- **Preview** de archivos `.rgb` renderizado con Canvas
- **Botón Paralelo** → lanza 4 hilos concurrentes
- **Botón Secuencial** → 1 solo hilo (para comparar)
- **Tabla de tiempos** → T1, T2, T3, T4 y Application End Time
- **Comparativa automática** → cuántas veces más rápido fue el paralelo
- **Panel de Depuración** → log de cada petición HTTP con URL, status y respuesta
- **Vista lado a lado** → imagen original vs. imagen de bordes en escala de grises

---

## 📚 Referencias

1. H. Scharr, *Optimale Operatoren in der Digitalen Bildverarbeitung*, PhD thesis, Universität Heidelberg, 2000.
2. B. Goetz et al., *Java Concurrency in Practice*, Addison-Wesley, 2006.
3. R.C. Gonzalez & R.E. Woods, *Digital Image Processing*, 4th ed., Pearson, 2018.
4. Oracle, *Java SE 21 — ExecutorService*, https://docs.oracle.com/en/java/api/java.base/java/util/concurrent/ExecutorService.html
5. Jakarta EE, *Jakarta Servlet 6.0 Specification*, https://jakarta.ee/specifications/servlet/6.0/
