package com.algoritmogris.servlet;

// Importaciones de los diferentes algoritmos de detección de bordes
import com.algoritmogris.canny.EdgeDetectorCanny;
import com.algoritmogris.sobel.EdgeDetectorSobel;
import com.algoritmogris.laplaciano.EdgeDetectorLaplaciano;
import com.algoritmogris.prewitt.EdgeDetectorPrewitt;

// Servicio que encapsula la lógica asíncrona/secuencial del operador Scharr
import com.algoritmogris.service.ScharrService;
import com.algoritmogris.service.ScharrService.ProcessResult;

// Utilidad para guardar localmente las imágenes de resultado
import com.algoritmogris.util.StorageUtil;

// Utilidad para empaquetar respuestas en formato JSON
import org.json.JSONObject;

// Lector y escritor base de interfaces BufferedImage
import javax.imageio.ImageIO;

// Clases nativas de Jakarta EE para el manejo de los Servlets HTTP
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

// Componentes de procesamiento en memoria para imágenes en Java
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;


// Define la ruta en que escuchará este servlet para recibir peticiones REST
@WebServlet("/api/process")
// Habilita y configura el soporte Multipart para recibir archivos pesados (imágenes via FormData)
@MultipartConfig(
    maxFileSize    = 100 * 1024 * 1024, // Limita el tamaño de cada archivo individual subido a 100 MB máximo
    maxRequestSize = 105 * 1024 * 1024  // Limita el cuerpo entero de la petición sumando archivo y meta campos a 105 MB
)
public class EdgeDetectionServlet extends HttpServlet {

    // Instancia del servicio Scharr en el hilo del servlet (no guarda estado local) para ser re-utilizado velozmente.
    private final ScharrService scharrService = new ScharrService();

    // Sobrescritura obligatoria para poder contestar a tráfico HTTP usando verbo POST (donde viajan los bytes del archivo) 
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Anunciamos a la interface o navegador cliente que le devolveremos un objeto serializado en JSON
        resp.setContentType("application/json");
        // Aseguramos que el streaming devuelto use caracteres en formato UTF-8 (prevenir problemas con acentos y emojis)
        resp.setCharacterEncoding("UTF-8");
        // Nos conectamos al ducto de escritura del response para imprimir datos al cliente al final
        PrintWriter out = resp.getWriter();

        try {
            // ─── 1. Leer el archivo subido ───────────────────────────────────
            
            // Extraemos de la petición multipart el dato adjunto llamado "image" a un objeto Part
            Part filePart = req.getPart("image");
            // Revisamos si el archivo no existe o no fue empaquetado en el formulario
            if (filePart == null) {
                // Lanzamos la función para fabricar de inmediato una respuesta abortando el request, devolviendo Bad Request
                respondError(resp, out, "No se recibió ninguna imagen (campo 'image' vacío).");
                // Finalizamos el thread por prevención
                return;
            }

            // Consultar el nombre real con el cual se guardó del lado del cliente antes de subir
            String fileName = filePart.getSubmittedFileName();
            // Boolean temporal donde analizamos, sin distinguir minúsculas/mayúsculas, si la extensión dictamina ".rgb" pura
            boolean isRaw   = fileName != null && fileName.toLowerCase().endsWith(".rgb");

            // Molde reservado en la memoria buffer de la máquina virtual para colocar los pixeles descodificados
            BufferedImage inputImage;

            // Bloque de procesamiento especial de archivo tipo raw RGB binario
            if (isRaw) {
                // ─── 2a. Leer archivo .rgb raw ───────────────────────────────
                
                // Extraemos manualmente la metadata adicional obligatoria "width" enlazada al input type text/number del HTML
                String wParam = req.getParameter("width");
                // Extraemos componente complementario "height"
                String hParam = req.getParameter("height");

                // Validar nulidad (es decir, el frontend omitió los campos obligatorios)
                if (wParam == null || hParam == null) {
                    // Detener ejecución si la data es insuficiente para procesar un RAW (pues carece de matriz para leerse)
                    respondError(resp, out, "Para archivos .rgb debes enviar los parámetros 'width' y 'height'.");
                    // Abortamos la ejecución de la función POST actual
                    return;
                }

                // Pasamos del string recibido al dato primitivo Integer depurando antes los espacios indeseados (trim)
                int width  = Integer.parseInt(wParam.trim());
                int height = Integer.parseInt(hParam.trim());

                // Absorbemos de un solo destello todos los megabytes recibidos por Part y creamos un Array binario gigante
                byte[] rawBytes = filePart.getInputStream().readAllBytes();

                // Evaluamos por lógica matemática que el producto de [ancho * alto * 3 pixeles de canal RGB] empate.
                int expected = width * height * 3;
                // Comparamos el peso real de los bytes bajados contra la fórmula, para prevenir archivos corruptos.
                if (rawBytes.length != expected) {
                    // Informamos del descalce aritmético para avisar al usuario subió un archivo roto o introdujo mal las escalas
                    respondError(resp, out, "Tamaño incorrecto: el archivo tiene " + rawBytes.length 
                        + " bytes, pero " + width + "×" + height + "×3 = " + expected + " bytes.");
                    // Retornamos sin causar null pointers posteriores
                    return;
                }

                // Generamos ya asegurado nuestro molde Java estándar dictaminando un mapeo entero a tricolor RGB
                inputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                // Ciclo principal que navega filas del alto (Y)
                for (int y = 0; y < height; y++) {
                    // Ciclo secundario anidado que escanea iterativamente horizontal (X)
                    for (int x = 0; x < width; x++) {
                        // Saltamos de a grupos binarios de 3 multiplicados por las coordenadas para encontrar la matriz lineal
                        int idx = (y * width + x) * 3;
                        // Transformar el byte binario a Entero Decimal con el bitwise AND 0xFF para la banda del color Rojo.
                        int r = rawBytes[idx    ] & 0xFF; 
                        // Mismo esquema un paso a la derecha del arreglo temporal para extraer el Verde.
                        int g = rawBytes[idx + 1] & 0xFF; 
                        // Mismo esquema dos pasos a la derecha para empalmar con el último canal, el Azul.
                        int b = rawBytes[idx + 2] & 0xFF; 
                        // Fusionamos en un Integer codificado estilo ARGB, forzando opacidad Alpha sólida total (0xFF << 24)
                        inputImage.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
                    }
                }

            } else {
                // ─── 2b. Imagen estándar (PNG, JPG, BMP, RAW) ──────────────
                // Delegamos la engorrosa detección de cabeceras tradicionales con el asistente integrado a de Java (ImageIO)
                inputImage = ImageIO.read(filePart.getInputStream());
                // ImageIO fracasó al parsear el encabezado o le han pasado un archivo falso e.g un .TXT renombrado a .PNG
                if (inputImage == null) {
                    // Detonamos el rechazo final si no obtuvimos una superficie visual procesable
                    respondError(resp, out, "El archivo no es una imagen válida. Para raw RGB usa extensión .rgb");
                    // Abortar hilo.
                    return;
                }
            }

            // ─── 3. Leer algoritmo y modo ─────────────────────────────────
            // Cargar de form-data la intención selectiva a despachar. 
            String algorithm = req.getParameter("algorithm");
            // Si no me pasaron nada en la variable asignarle valor neutro principal ("scharr") protegiendo contra nulidades.
            if (algorithm == null || algorithm.isEmpty()) algorithm = "scharr";

            // Cargar de form-data el hilo de ejecución deseado (que puede aplicar o no a este contexto actual)
            String mode = req.getParameter("mode");
            // Autoasignar un modo predeterminado "parallel" si el posteo venía sin él 
            if (mode == null || mode.isEmpty()) mode = "parallel";

            // Creamos un diccionario JSON al vuelo pre-alocado que se irá rellenando conforme a la lógica descendente
            JSONObject json = new JSONObject();

            // Desviador lógico múltiple de condicionales de cadena en base a algoritmo
            if ("canny".equalsIgnoreCase(algorithm)) {
                // ─── 4a. Canny ─────────────────────────────────────────────
                // Marcar el inicio exacto en nanosegundos el reloj
                long cannyStart = System.nanoTime();
                // Bloquea hilo solicitando a nuestra clase algoritmica detectBordes estática de Canny 
                BufferedImage cannyResult = EdgeDetectorCanny.detectEdges(inputImage);
                // Restar reloj actual contra marca de inicio para determinar vida del bloque asíncrono interior.
                long cannyTotal = System.nanoTime() - cannyStart;
                // Delega formateo general JSON + Base64 + guardado al Helper pasándole data
                buildSimpleResponse(json, cannyResult, "canny", cannyTotal);

            } else if ("sobel".equalsIgnoreCase(algorithm)) {
                // ─── 4b. Sobel ─────────────────────────────────────────────
                // Congela cronómetro inicio
                long start = System.nanoTime();
                // Invoca pasándole el contenedor BufferedImage base para el operador general Sobel 
                BufferedImage sobelResult = EdgeDetectorSobel.detectEdges(inputImage);
                // Congela el fin temporizador
                long total = System.nanoTime() - start;
                // Armar datos estándar
                buildSimpleResponse(json, sobelResult, "sobel", total);

            } else if ("laplaciano".equalsIgnoreCase(algorithm)) {
                // ─── 4c. Laplaciano ─────────────────────────────────────────
                // Mismo formato repetitivo de tracking de hardware de CPU temporal
                long start = System.nanoTime();
                // Petición exclusiva al operador estático referencial a método 8-vecinos laplaciano
                BufferedImage lapResult = EdgeDetectorLaplaciano.detectEdges(inputImage);
                // Medir peso transcurrido del proceso algorítmo puro, excluyendo overhead I/O
                long total = System.nanoTime() - start;
                // Empaquetar todo al json general
                buildSimpleResponse(json, lapResult, "laplaciano", total);

            } else if ("prewitt".equalsIgnoreCase(algorithm)) {
                // ─── 4d. Prewitt ───────────────────────────────────────────
                // Tiempo de CPU
                long start = System.nanoTime();
                // Procesar la variante convolutiva de gradiente Prewitt
                BufferedImage prewittResult = EdgeDetectorPrewitt.detectEdges(inputImage);
                // Cerrar tiempo transcurrido
                long total = System.nanoTime() - start;
                // Enchufar a respuesta JSON serializada
                buildSimpleResponse(json, prewittResult, "prewitt", total);

            } else if ("todos".equalsIgnoreCase(algorithm)) {
                // ─── 4e. Todos los 5 algoritmos (Secuencial o Concurrente) ────
                // Activar marca general temporizadora maestra appStart para englobar el peso de la concurrencia a nivel clase.
                long appStart = System.nanoTime();
                // Crear un saco JSON secundario de resultados iterativos para no manchar el flujo principal de variables de objeto.
                JSONObject resultsObj = new JSONObject();

                // Evaluamos rama si el lote se solicita en modo un solo hilo por iteración "secuencial".
                if ("sequential".equalsIgnoreCase(mode)) {
                    // Scharr Secuencial
                    // Cronometramos al nivel individual también.
                    long st = System.nanoTime();
                    // Invocar Scharr limitándolo explícitamente y bajo promesa al modo secuencial tradicional
                    ProcessResult rScharr = scharrService.processSequential(inputImage);
                    // Rellenar llave index dict "scharr" utilizando utilidad que codifica automáticamente el obj devuelto.
                    resultsObj.put("scharr", buildSingleResultJson(rScharr.image, "scharr", System.nanoTime() - st));

                    // Sobel
                    // Reseteo corto de tiempo nanosegundo local a la nueva ronda
                    st = System.nanoTime();
                    // Invocar Sobel que traba este hilo unánime en el servidor 
                    BufferedImage rSobel = EdgeDetectorSobel.detectEdges(inputImage);
                    // Ensamblar respuesta individual en key "sobel"
                    resultsObj.put("sobel", buildSingleResultJson(rSobel, "sobel", System.nanoTime() - st));

                    // Canny
                    // Apuntar comienzo hilo lineal para medir fase
                    st = System.nanoTime();
                    // Mandar Canny (bastante lento y pesado comparativamente a correr un vector normal en CPU)
                    BufferedImage rCanny = EdgeDetectorCanny.detectEdges(inputImage);
                    // Guardar serialización en saco de empaque Canny
                    resultsObj.put("canny", buildSingleResultJson(rCanny, "canny", System.nanoTime() - st));

                    // Laplaciano
                    // Resetear inicio de Laplaciano
                    st = System.nanoTime();
                    // Proceso convolutivo único
                    BufferedImage rLap = EdgeDetectorLaplaciano.detectEdges(inputImage);
                    // Armado final Laplaciano
                    resultsObj.put("laplaciano", buildSingleResultJson(rLap, "laplaciano", System.nanoTime() - st));

                    // Prewitt
                    // Control de hardware Prewitt iteración 5
                    st = System.nanoTime();
                    // Detonación procesamiento de matriz sobre buffer primigenio
                    BufferedImage rPrewitt = EdgeDetectorPrewitt.detectEdges(inputImage);
                    // Se ingresa último item al mapa interno list resultsObj.
                    resultsObj.put("prewitt", buildSingleResultJson(rPrewitt, "prewitt", System.nanoTime() - st));

                } else {
                    // ── LOTE CONCURRENTE (Mosaico de 5 hilos) ────────────────
                    // Declaramos un pool de trabajadores de sub-hilos de servidor fijo y estricto atado a las 5 únicas tareas.
                    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(5);
                    
                    // Diseñamos tarea encapsulada Scharr de tipo Future que promete retornar un Obj JSON ya construido. 
                    java.util.concurrent.Callable<JSONObject> taskScharr = () -> {
                        long st = System.nanoTime();  // Medición interior del hijo encapsulado
                        ProcessResult r = scharrService.processSequential(inputImage);   // Ejecución directa de componente procesable
                        return buildSingleResultJson(r.image, "scharr", System.nanoTime() - st); // Finalizado
                    };
                    // Encapsulamiento Callable nativo para Sobel, de idéntica logística temporizada.
                    java.util.concurrent.Callable<JSONObject> taskSobel = () -> {
                        long st = System.nanoTime();
                        return buildSingleResultJson(EdgeDetectorSobel.detectEdges(inputImage), "sobel", System.nanoTime() - st);
                    };
                    // Callable de encapsulamiento asíncrono virtual reservado el scope Canny.
                    java.util.concurrent.Callable<JSONObject> taskCanny = () -> {
                        long st = System.nanoTime();
                        return buildSingleResultJson(EdgeDetectorCanny.detectEdges(inputImage), "canny", System.nanoTime() - st);
                    };
                    // Encapsulamiento Callable para la rutina base de Laplaciano.
                    java.util.concurrent.Callable<JSONObject> taskLap = () -> {
                        long st = System.nanoTime();
                        return buildSingleResultJson(EdgeDetectorLaplaciano.detectEdges(inputImage), "laplaciano", System.nanoTime() - st);
                    };
                    // Encapsulamiento asíncrono para delegar trabajo intensivo de Prewitt y pre-generar su base64
                    java.util.concurrent.Callable<JSONObject> taskPrewitt = () -> {
                        long st = System.nanoTime();
                        return buildSingleResultJson(EdgeDetectorPrewitt.detectEdges(inputImage), "prewitt", System.nanoTime() - st);
                    };

                    // Mandamos lista entera de 5 dependientes asíncronos juntos al Executor invokeAll, deteniendo nuestro thread maestro HTTP en este renglón... 
                    java.util.List<java.util.concurrent.Future<JSONObject>> futures = executor.invokeAll(
                        java.util.Arrays.asList(taskScharr, taskSobel, taskCanny, taskLap, taskPrewitt)
                    );
                    // Una vez resuelto (gracias bloqueo `invokeAll`), solicitamos bajar switches y cerrar tubería del pool para drenar CPU.
                    executor.shutdown();

                    // Procedemos a jalar el Object Promise (.get() extrae la data) y las indexamos firmes con su key.
                    resultsObj.put("scharr", futures.get(0).get()); 
                    // Jalar promesa en la posición respectiva para Sobel resuelta
                    resultsObj.put("sobel", futures.get(1).get());
                    // Jalar la tercera promesa encapsula canny completada
                    resultsObj.put("canny", futures.get(2).get());
                    // Ingreso de la respuesta final asíncrona de hilo 4 (laplaciano)
                    resultsObj.put("laplaciano", futures.get(3).get());
                    // Jalar finalmente Prewitt. Listos para despachar todo.
                    resultsObj.put("prewitt", futures.get(4).get());
                }

                // Inyectamos como llave principal maestra un indicador visible y nominal 
                json.put("algorithm", "todos");
                // Etiquetamos modal si vino seq. o paralelo.
                json.put("mode", mode);
                // Extraemos cronómetro total real usando nuestra misma referencia general appStart
                json.put("totalMs", nanosToMs(System.nanoTime() - appStart));
                // Volcamos el contenedor gigante repleto de string Base64 dentro del array general que lee React en JSON "results".
                json.put("results", resultsObj);

            } else {
                // ─── 4f. Scharr (paralelo 4 hilos o secuencial) individual normal ─
                // Estructura envolvente propia de Scharr para alojar respuestas complejas de multihilo T1 a T4 interno.
                ProcessResult result;
                // Switch condicional puro para la variable paramétrica `mode` 
                if ("sequential".equalsIgnoreCase(mode)) {
                    // Ejecuta el componente lineal lento.
                    result = scharrService.processSequential(inputImage);
                } else {
                    // Distribuye la foto nativamente en matriz y paraleliza matemáticamente con ForkJoins al operador multihilos propio de Scharr.
                    result = scharrService.processParallel(inputImage);
                }

                // Despacha físicamente salvándolo al filesystem o disco un png usando Util de Storage, capturando la ruta Windows 
                String savedPath = StorageUtil.save(result.image, mode);

                // Despliega una tubería local volátil en la RAM donde renderizara una foto completa transitoria
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                // Embutir el buffer interno crudo para conformar header visual de clase PNG
                ImageIO.write(result.image, "PNG", baos);
                // String que define el schema o protocolo MIME + la cascada de bytes volcadas y subidas a estándar ascii Base 64
                String imageBase64 = "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(baos.toByteArray());

                // Anexar meta identidades del nodo al objeto grande que irá via HTTP al cliente:
                json.put("algorithm",   "scharr");
                // Anexionar ruta fisica 
                json.put("imagePath",   savedPath);
                // Suministrar código ASCII (Base 64 String)
                json.put("imageBase64", imageBase64);
                // Proveer tracking analítico del sub-hilo 1 de Scharr interior 
                json.put("t1Ms",        nanosToMs(result.t1Nanos));
                // Proveer hilo paralelo seccion 2 T2
                json.put("t2Ms",        nanosToMs(result.t2Nanos));
                // Proveer peso proceso superior mitad hilo 3 
                json.put("t3Ms",        nanosToMs(result.t3Nanos));
                // Proveer extremo sector de la foto de un multihilo T4 
                json.put("t4Ms",        nanosToMs(result.t4Nanos));
                // Sumatoria interna consolidada.
                json.put("totalMs",     nanosToMs(result.totalNanos));
                // El String selector paralelo o seq nativo 
                json.put("mode",        mode);
                // Data info de anchos a píxeles exactos.
                json.put("width",       result.image.getWidth());
                // Altura lograda a píxeles exactos.
                json.put("height",      result.image.getHeight());
            }

            // Descarga a través del ducto de red HTTP Printwriter toda nuestra estructura virtual JSON de respuesta general
            out.print(json.toString());
            // Fuerza o empuja purga y vacía buffer de conexión, notificando cese incondicional a la red
            out.flush();

        } catch (NumberFormatException e) {
            // Manejador del parseo numérico Integer fallido en raw
            respondError(resp, out, "Los parámetros 'width' y 'height' deben ser números enteros.");
        } catch (Exception e) {
            // Manejador de colapsos genéricos del sistema durante los procesos I/O
            respondError(resp, out, "Error interno: " + e.getMessage());
            // Volcar pila de depuración por consola del back end para traceo del bug.
            e.printStackTrace();
        }
    }

    // ─── Helpers  ─────────────────────────────────────────────────────────────────

    /**
     * Construye la respuesta JSON estándar para algoritmos de 1 hilo
     * (Canny, Sobel, Laplaciano, Prewitt): guarda, codifica y rellena el JSON.
     */
    private void buildSimpleResponse(JSONObject json, BufferedImage output,
                                     String algorithmName, long totalNanos)
            throws Exception {
        // Ejecuciona y registra el guardado físico hacia local
        String savedPath = StorageUtil.save(output, algorithmName);
        // Despliega memoria array transitoria 
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Escribe PNG estructurad y lo inyecta a stream buffer (baos)
        ImageIO.write(output, "PNG", baos);
        // Combina string decorativo HTML DOM base y codifica formato binario a texto legible 
        String imageBase64 = "data:image/png;base64,"
            + Base64.getEncoder().encodeToString(baos.toByteArray());
        // Agrega a un json mutado parametrico identificador estáticos
        json.put("algorithm",   algorithmName);
        // Anclaje al filepath en HDD
        json.put("imagePath",   savedPath);
        // String puro y duro del file 64
        json.put("imageBase64", imageBase64);
        // Transformar al vuelo total temporal
        json.put("totalMs",     nanosToMs(totalNanos));
        // Agrega specs de tamaño
        json.put("width",       output.getWidth());
        // Info final de size Y
        json.put("height",      output.getHeight());
    }

    /** 
     * Helper para retornar JSONObject individual para procesamiento en lote, 
     * invoca indirectamente funcionalidad para agilizar sub-promesas. 
     */
    private JSONObject buildSingleResultJson(BufferedImage output, String algo, long totalNanos) throws Exception {
        // Inicializa caja muerta vacía 
        JSONObject obj = new JSONObject();
        // Carga la información pidiendo y abusando del helper anterior superior
        buildSimpleResponse(obj, output, algo, totalNanos);
        // Regresa la bola armada
        return obj;
    }

    /** 
     * Convierte nanosegundos a milisegundos tabulados con 3 decimales fijos 
     */
    private double nanosToMs(long nanos) {
        // Dividir, redondear y reposicionar milesimas para 3.5ms vs 35,000ns.
        return Math.round(nanos / 1_000.0) / 1_000.0;
    }

    /** 
     * Responde con un JSON de error limpio y código HTTP 400. 
     */
    private void respondError(HttpServletResponse resp, PrintWriter out, String message)
            throws IOException {
        // Mutar valor status response original de 200 a 400 Bad Request
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        // Fabrica el esqueleto vacío
        JSONObject err = new JSONObject();
        // Declara la llave general asincrona para el parseo del try / catch catch blocks del FETCH React
        err.put("error", message);
        // Imprime y emite el aborto
        out.print(err.toString());
        // Expulsar red
        out.flush();
    }
}
