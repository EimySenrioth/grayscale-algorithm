package com.algoritmogris.service;

import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ScharrService - Aplica el operador Scharr para detección de bordes.
 *
 * El operador Scharr usa kernels 3×3 con pesos optimizados para mejor
 * simetría rotacional comparado con Sobel. Referencia:
 * H. Scharr, "Optimale Operatoren in der Digitalen Bildverarbeitung", 2000.
 */
public class ScharrService {

    // ─── Kernels de Scharr ─────────────────────────────────────────────────────
    // Kernel horizontal Gx: detecta cambios de intensidad en el eje X (bordes verticales)
    private static final int[][] KERNEL_X = {
        { -3,  0,  3 },
        {-10,  0, 10 },
        { -3,  0,  3 }
    };

    // Kernel vertical Gy: detecta cambios de intensidad en el eje Y (bordes horizontales)
    private static final int[][] KERNEL_Y = {
        { -3, -10, -3 },
        {  0,   0,  0 },
        {  3,  10,  3 }
    };

    // ─── Resultado del procesamiento ───────────────────────────────────────────
    /**
     * Contenedor de resultados: imagen procesada y tiempos de cada hilo.
     */
    public static class ProcessResult {
        public final BufferedImage image;   // Imagen con bordes detectados
        public final long t1Nanos;          // Tiempo del hilo 1
        public final long t2Nanos;          // Tiempo del hilo 2 (0 si secuencial)
        public final long t3Nanos;          // Tiempo del hilo 3 (0 si secuencial)
        public final long t4Nanos;          // Tiempo del hilo 4 (0 si secuencial)
        public final long totalNanos;       // Tiempo total de la operación completa

        public ProcessResult(BufferedImage image, long t1, long t2, long t3, long t4, long total) {
            this.image      = image;
            this.t1Nanos    = t1;
            this.t2Nanos    = t2;
            this.t3Nanos    = t3;
            this.t4Nanos    = t4;
            this.totalNanos = total;
        }
    }

    // ─── Paso 1: Conversión a Escala de Grises ─────────────────────────────────
    /**
     * Convierte una imagen a escala de grises usando la fórmula de luminancia ITU-R BT.601:
     *   Gray = 0.299·R + 0.587·G + 0.114·B
     * El ojo humano es más sensible al verde, por eso tiene mayor peso.
     *
     * @param src Imagen fuente en color (RGB o ARGB)
     * @return Arreglo plano de enteros con valores de gris [0..255]
     */
    private int[] toGrayPixels(BufferedImage src) {
        int width  = src.getWidth();
        int height = src.getHeight();
        int[] gray = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Obtener el valor RGB del píxel actual
                int rgb = src.getRGB(x, y);

                // Separar los canales usando máscaras de bits
                int r = (rgb >> 16) & 0xFF; // Canal Rojo
                int g = (rgb >>  8) & 0xFF; // Canal Verde
                int b = (rgb      ) & 0xFF; // Canal Azul

                // Aplicar la fórmula de luminancia
                gray[y * width + x] = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        return gray;
    }

    // ─── Paso 2: Convolución Scharr sobre un rango de filas ───────────────────
    /**
     * Aplica los kernels Scharr Gx y Gy sobre un rango de filas de la imagen.
     * Este método es llamado por cada hilo con su propio rango (startRow → endRow).
     *
     * Para cada píxel (x, y) dentro del rango:
     *   1. Recorre los 9 vecinos del kernel 3×3
     *   2. Multiplica cada vecino por su peso en Gx y Gy
     *   3. Calcula el gradiente: G = sqrt(Gx² + Gy²)
     *   4. Clampea G a [0, 255] y lo asigna al píxel de salida
     *
     * Los píxeles del borde (primera y última fila/columna) se ignoran
     * para evitar accesos fuera del buffer.
     *
     * @param gray     Arreglo de grises de la imagen completa (input)
     * @param out      Arreglo de salida donde se escriben los bordes detectados
     * @param width    Ancho de la imagen en píxeles
     * @param startRow Primera fila a procesar (inclusive)
     * @param endRow   Última fila a procesar (exclusive)
     */
    private void applyScharr(int[] gray, int[] out, int width, int startRow, int endRow) {
        // Empezar desde max(1, startRow) para no acceder a fila -1
        int fromRow = Math.max(1, startRow);
        // Terminar en min(endRow, height-1) para no acceder fuera del buffer
        int toRow   = endRow; // el llamador ya limitó a height-1

        for (int y = fromRow; y < toRow; y++) {
            for (int x = 1; x < width - 1; x++) {
                int gx = 0; // Acumulador para el gradiente horizontal
                int gy = 0; // Acumulador para el gradiente vertical

                // Recorrer los 9 píxeles vecinos del kernel 3×3
                for (int ky = -1; ky <= 1; ky++) {       // fila del kernel: -1, 0, 1
                    for (int kx = -1; kx <= 1; kx++) {   // columna del kernel: -1, 0, 1
                        // Valor de gris del vecino
                        int neighbor = gray[(y + ky) * width + (x + kx)];

                        // Acumular pesos de cada kernel
                        gx += KERNEL_X[ky + 1][kx + 1] * neighbor;
                        gy += KERNEL_Y[ky + 1][kx + 1] * neighbor;
                    }
                }

                // Calcular magnitud del gradiente: G = sqrt(Gx² + Gy²)
                int magnitude = (int) Math.sqrt(gx * gx + gy * gy);

                // Clampear el valor a [0, 255] para que sea un valor de píxel válido
                out[y * width + x] = Math.min(255, magnitude);
            }
        }
    }

    // ─── Paso 3a: Modo Paralelo — 4 hilos concurrentes ────────────────────────
    /**
     * Procesa la imagen dividiendo su altura en CUATRO franjas iguales:
     *  - Hilo 1 (T1): filas 0         → height/4     (cuarto superior)
     *  - Hilo 2 (T2): filas height/4  → height/2     (segundo cuarto)
     *  - Hilo 3 (T3): filas height/2  → 3*height/4   (tercer cuarto)
     *  - Hilo 4 (T4): filas 3*height/4 → height      (cuarto inferior)
     *
     * Los 4 hilos se lanzan simultáneamente con ExecutorService.invokeAll().
     * El tiempo total refleja el cuello de botella (el hilo más lento).
     *
     * @param input Imagen de entrada (cualquier tamaño m×n)
     * @return ProcessResult con la imagen fusionada, T1-T4 y tiempo total
     */
    public ProcessResult processParallel(BufferedImage input) throws Exception {
        int width  = input.getWidth();
        int height = input.getHeight();

        // Dividir la imagen en 4 franjas horizontales iguales
        int q1 = height / 4;         // fin del 1er cuarto
        int q2 = height / 2;         // fin del 2do cuarto
        int q3 = 3 * height / 4;     // fin del 3er cuarto

        // Convertir toda la imagen a grises antes de procesar
        int[] gray = toGrayPixels(input);

        // Arreglo de salida compartido (cada hilo escribe en su propio rango)
        int[] out = new int[width * height];

        // Tiempo de inicio de la operación completa
        long appStart = System.nanoTime();

        // Pool de exactamente 4 hilos
        ExecutorService executor = Executors.newFixedThreadPool(4);

        // Hilo 1 (T1): filas 0 → q1
        Callable<Long> task1 = () -> {
            long start = System.nanoTime();
            applyScharr(gray, out, width, 0, q1);
            return System.nanoTime() - start;
        };

        // Hilo 2 (T2): filas q1 → q2
        Callable<Long> task2 = () -> {
            long start = System.nanoTime();
            applyScharr(gray, out, width, q1, q2);
            return System.nanoTime() - start;
        };

        // Hilo 3 (T3): filas q2 → q3
        Callable<Long> task3 = () -> {
            long start = System.nanoTime();
            applyScharr(gray, out, width, q2, q3);
            return System.nanoTime() - start;
        };

        // Hilo 4 (T4): filas q3 → height-1
        Callable<Long> task4 = () -> {
            long start = System.nanoTime();
            applyScharr(gray, out, width, q3, height - 1);
            return System.nanoTime() - start;
        };

        // Lanzar los 4 hilos al mismo tiempo y esperar a que todos terminen
        var futures = executor.invokeAll(java.util.List.of(task1, task2, task3, task4));
        executor.shutdown();

        long t1 = futures.get(0).get();
        long t2 = futures.get(1).get();
        long t3 = futures.get(2).get();
        long t4 = futures.get(3).get();

        // Tiempo total medido desde el lanzamiento hasta que el último hilo terminó
        long totalTime = System.nanoTime() - appStart;

        BufferedImage result = buildImage(out, width, height);
        return new ProcessResult(result, t1, t2, t3, t4, totalTime);
    }

    // ─── Paso 3b: Modo Secuencial — 1 solo hilo ───────────────────────────────
    /**
     * Procesa toda la imagen en un solo hilo, sin dividir filas.
     * T2, T3 y T4 son 0 porque no existen.
     *
     * @param input Imagen de entrada
     * @return ProcessResult con la imagen procesada (t2,t3,t4=0) y tiempo total
     */
    public ProcessResult processSequential(BufferedImage input) {
        int width  = input.getWidth();
        int height = input.getHeight();
        int[] gray = toGrayPixels(input);
        int[] out  = new int[width * height];

        long start = System.nanoTime();
        applyScharr(gray, out, width, 1, height - 1);
        long t1 = System.nanoTime() - start;

        BufferedImage result = buildImage(out, width, height);
        // t2, t3, t4 = 0 porque no hay hilos adicionales en modo secuencial
        return new ProcessResult(result, t1, 0L, 0L, 0L, t1);
    }

    // ─── Paso 4: Construir el BufferedImage de salida ─────────────────────────
    /**
     * Convierte el arreglo plano de magnitudes [0..255] a un BufferedImage
     * en escala de grises (TYPE_BYTE_GRAY).
     *
     * @param pixels Arreglo con magnitudes del gradiente Scharr por píxel
     * @param width  Ancho de la imagen
     * @param height Alto de la imagen
     * @return BufferedImage lista para ser guardada como PNG
     */
    private BufferedImage buildImage(int[] pixels, int width, int height) {
        // Crear imagen de salida en escala de grises
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v = pixels[y * width + x]; // Valor de magnitud del gradiente

                // Empaquetar el valor en formato RGB (R=G=B=v para gris)
                int rgb = (v << 16) | (v << 8) | v;
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }
}
