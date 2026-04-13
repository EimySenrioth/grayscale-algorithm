package com.algoritmogris.servlet;

import com.algoritmogris.canny.EdgeDetectorCanny;
import com.algoritmogris.service.ScharrService;
import com.algoritmogris.service.ScharrService.ProcessResult;
import com.algoritmogris.util.StorageUtil;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;

/**
 * EdgeDetectionServlet - Endpoint REST para procesar imágenes.
 *
 * Soporta dos algoritmos:
 *   - Scharr (paralelo 4 hilos o secuencial)
 *   - Canny  (un solo hilo, tiempo total)
 *
 * Soporta dos tipos de imagen:
 *   1. Archivos .rgb (raw binario sin cabecera): requiere parámetros width y height.
 *   2. Imágenes estándar (PNG, JPG, BMP, etc.): no requiere dimensiones.
 *
 * Ruta: POST /api/process
 *
 * Parámetros form-data:
 *   - image     : archivo de imagen (.rgb u otro formato)
 *   - algorithm : "scharr" (default) o "canny"
 *   - mode      : "parallel" o "sequential" (solo aplica a Scharr)
 *   - width     : ancho en píxeles (solo para .rgb)
 *   - height    : alto en píxeles  (solo para .rgb)
 *
 * Respuesta JSON Scharr:
 *   { imagePath, imageBase64, algorithm, mode, t1Ms, t2Ms, t3Ms, t4Ms, totalMs, width, height }
 *
 * Respuesta JSON Canny:
 *   { imagePath, imageBase64, algorithm, totalMs, width, height }
 */
@WebServlet("/api/process")
@MultipartConfig(
    maxFileSize    = 100 * 1024 * 1024, // Máximo 100 MB por imagen
    maxRequestSize = 105 * 1024 * 1024  // Máximo 105 MB por petición completa
)
public class EdgeDetectionServlet extends HttpServlet {

    // Instancia del servicio de procesamiento (stateless, se puede reutilizar)
    private final ScharrService scharrService = new ScharrService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            // ─── 1. Leer el archivo subido ───────────────────────────────────
            Part filePart = req.getPart("image");
            if (filePart == null) {
                respondError(resp, out, "No se recibió ninguna imagen (campo 'image' vacío).");
                return;
            }

            // Detectar si el archivo es .rgb (raw sin cabecera)
            String fileName = filePart.getSubmittedFileName();
            boolean isRaw   = fileName != null && fileName.toLowerCase().endsWith(".rgb");

            BufferedImage inputImage;

            if (isRaw) {
                // ─── 2a. Leer archivo .rgb raw ───────────────────────────────
                // Un archivo .rgb no tiene cabecera: son solo bytes R,G,B consecutivos.
                // El frontend debe enviar width y height como parámetros adicionales.
                String wParam = req.getParameter("width");
                String hParam = req.getParameter("height");

                if (wParam == null || hParam == null) {
                    respondError(resp, out,
                        "Para archivos .rgb debes enviar los parámetros 'width' y 'height'.");
                    return;
                }

                int width  = Integer.parseInt(wParam.trim());
                int height = Integer.parseInt(hParam.trim());

                // Leer todos los bytes del archivo (R, G, B intercalados por pixel)
                byte[] rawBytes = filePart.getInputStream().readAllBytes();

                // Validar que el tamaño del archivo coincida con las dimensiones
                int expected = width * height * 3;
                if (rawBytes.length != expected) {
                    respondError(resp, out,
                        "Tamaño incorrecto: el archivo tiene " + rawBytes.length
                        + " bytes, pero " + width + "×" + height + "×3 = " + expected + " bytes.");
                    return;
                }

                // Construir un BufferedImage a partir de los bytes raw RGB
                // Cada 3 bytes representan un pixel: [R, G, B]
                inputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int idx = (y * width + x) * 3;
                        int r = rawBytes[idx    ] & 0xFF; // Rojo
                        int g = rawBytes[idx + 1] & 0xFF; // Verde
                        int b = rawBytes[idx + 2] & 0xFF; // Azul
                        // Empaquetar en formato ARGB con Alpha=255 (opaco)
                        inputImage.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
                    }
                }

            } else {
                // ─── 2b. Imagen estándar (PNG, JPG, BMP, etc.) ──────────────
                inputImage = ImageIO.read(filePart.getInputStream());
                if (inputImage == null) {
                    respondError(resp, out,
                        "El archivo no es una imagen válida. Para raw RGB usa extensión .rgb");
                    return;
                }
            }

            // ─── 3. Leer algoritmo y modo ─────────────────────────────────
            String algorithm = req.getParameter("algorithm");
            if (algorithm == null || algorithm.isEmpty()) algorithm = "scharr";

            String mode = req.getParameter("mode");
            if (mode == null || mode.isEmpty()) mode = "parallel";

            JSONObject json = new JSONObject();

            if ("canny".equalsIgnoreCase(algorithm)) {
                // ─── 4a. Procesar con Canny (1 hilo, tiempo total) ───────────
                long cannyStart = System.nanoTime();
                BufferedImage cannyResult = EdgeDetectorCanny.detectEdges(inputImage);
                long cannyTotal = System.nanoTime() - cannyStart;

                // ─── 5a. Guardar resultado en samples/ ───────────────────────
                String savedPath = StorageUtil.save(cannyResult, "canny");

                // ─── 6a. Codificar en Base64 ─────────────────────────────────
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(cannyResult, "PNG", baos);
                String imageBase64 = "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(baos.toByteArray());

                // ─── 7a. JSON Canny: solo totalMs (sin T1-T4) ────────────────
                json.put("algorithm",   "canny");
                json.put("imagePath",   savedPath);
                json.put("imageBase64", imageBase64);
                json.put("totalMs",     nanosToMs(cannyTotal));
                json.put("width",       cannyResult.getWidth());
                json.put("height",      cannyResult.getHeight());

            } else {
                // ─── 4b. Procesar con Scharr (paralelo 4 hilos o secuencial) ─
                ProcessResult result;
                if ("sequential".equalsIgnoreCase(mode)) {
                    result = scharrService.processSequential(inputImage);
                } else {
                    result = scharrService.processParallel(inputImage);
                }

                // ─── 5b. Guardar resultado en samples/ ───────────────────────
                String savedPath = StorageUtil.save(result.image, mode);

                // ─── 6b. Codificar en Base64 ─────────────────────────────────
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(result.image, "PNG", baos);
                String imageBase64 = "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(baos.toByteArray());

                // ─── 7b. JSON Scharr: T1-T4 + totalMs ───────────────────────
                json.put("algorithm",   "scharr");
                json.put("imagePath",   savedPath);
                json.put("imageBase64", imageBase64);
                json.put("t1Ms",        nanosToMs(result.t1Nanos));
                json.put("t2Ms",        nanosToMs(result.t2Nanos));
                json.put("t3Ms",        nanosToMs(result.t3Nanos));
                json.put("t4Ms",        nanosToMs(result.t4Nanos));
                json.put("totalMs",     nanosToMs(result.totalNanos));
                json.put("mode",        mode);
                json.put("width",       result.image.getWidth());
                json.put("height",      result.image.getHeight());
            }

            out.print(json.toString());
            out.flush();

        } catch (NumberFormatException e) {
            respondError(resp, out, "Los parámetros 'width' y 'height' deben ser números enteros.");
        } catch (Exception e) {
            respondError(resp, out, "Error interno: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Convierte nanosegundos a milisegundos con 3 decimales. */
    private double nanosToMs(long nanos) {
        return Math.round(nanos / 1_000.0) / 1_000.0;
    }

    /** Responde con un JSON de error y código HTTP 400. */
    private void respondError(HttpServletResponse resp, PrintWriter out, String message)
            throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        JSONObject err = new JSONObject();
        err.put("error", message);
        out.print(err.toString());
        out.flush();
    }
}
