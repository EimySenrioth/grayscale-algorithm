package com.algoritmogris.service;

import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ScharrService - Servicio dedicado para aplicar el operador matricial de Scharr.
 * Es crucial y destaca porque integra un algoritmo avanzado de concurrencia 
 * separando la imagen en cuadrantes procesados matemáticamente por vías separadas (hilos).
 */
public class ScharrService {

    // ─── KERNELS DE SCHARR ─────────────────────────────────────────────────────
    
    // Matriz Convolutiva Horizontal Gx
    // Es matemáticamente idéntica en rol predictivo a Sobel, pero optimiza la sensibilidad
    // con un pico (10 y 3 vs 2 y 1) dando mucha mejor isotropía rotacional a curvas diagonales sutiles.
    private static final int[][] KERNEL_X = {
        { -3,  0,  3 },
        {-10,  0, 10 },
        { -3,  0,  3 }
    };

    // Matriz Convolutiva Vertical Gy
    private static final int[][] KERNEL_Y = {
        { -3, -10, -3 },
        {  0,   0,  0 },
        {  3,  10,  3 }
    };

    // ─── CLASE ENVOLTORIO RESULTADOS ───────────────────────────────────────────
    /**
     * DTO (Data Transfer Object) estático para la respuesta.
     * Transporta un manojo de datos unificados desde los métodos algorítmicos hacia el Servlet,
     * especialmente valiosos cuando queremos mapear demoras transaccionales de paralelismo por Hilo.
     */
    public static class ProcessResult {
        public final BufferedImage image;   // Imagen final procesada de mapa de bits (unidos)
        public final long t1Nanos;          // Cuello de botella en resolver porción 1 
        public final long t2Nanos;          // ... porción 2 (Omitido/0L si corremos Secuencialmente)
        public final long t3Nanos;          // ... porción 3 
        public final long t4Nanos;          // ... porción 4 
        public final long totalNanos;       // Sumatoria completa transcurrida en reloj absoluto

        public ProcessResult(BufferedImage image, long t1, long t2, long t3, long t4, long total) {
            this.image      = image;
            this.t1Nanos    = t1;
            this.t2Nanos    = t2;
            this.t3Nanos    = t3;
            this.t4Nanos    = t4;
            this.totalNanos = total;
        }
    }

    // ─── PASO 1: LUMINANCIA (Básica plana) ─────────────────────────────────
    /**
     * Motor iterativo plano de conversión a escala de grises bajo la norma fotométrica ITU-R BT.601
     * Genera Array de Integers (velocidad RAM altísima vs setRGB en tiempo real)
     *
     * @param src Objeto de Imagen nativo Java.
     * @return Arreglo primitivo aplanado (flat map vector) de intensidades lumínicas.
     */
    private int[] toGrayPixels(BufferedImage src) {
        int width  = src.getWidth();
        int height = src.getHeight();
        
        // Multiplicamos como 1 dimensión gigante para evitar ineficiencias de memoria de doble punteros [][]
        int[] gray = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y);

                // Shifts bitwise a la derecha 
                int r = (rgb >> 16) & 0xFF; 
                int g = (rgb >>  8) & 0xFF;
                int b = (rgb      ) & 0xFF; 

                // Al crear grises, ponderamos por la sensibilidad y curvatura visual humana al VERDE.
                gray[y * width + x] = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        return gray;
    }

    // ─── PASO 2: CONVOLUCIÓN SCHARR SEGMENTADA ──────────────────────────────────
    /**
     * Aplica el barrido matricial X,Y pero delimitadamente a índices definidos (startRow a endRow).
     * Por lo cual, es una función multi-instanciable ("Thread-Safe") y compartible a subprocesos!
     *
     * @param gray     Matriz plana input de origen
     * @param out      Objeto compartido flat-array por referencia para guardar el output concurrente 
     * @param width    Variable vital para deducir la grilla y saltar renglón matemáticamente (y * width)
     * @param startRow Límite techo superior que este thead tocará
     * @param endRow   Límite base inferior intocable que se delegará a otro thead
     */
    private void applyScharr(int[] gray, int[] out, int width, int startRow, int endRow) {
        // Obviar desbordar primera línea general (-1) obligando el Math.Max a arrancar seguro de mínimo 1
        int fromRow = Math.max(1, startRow);
        int toRow   = endRow; 

        for (int y = fromRow; y < toRow; y++) {
            for (int x = 1; x < width - 1; x++) {
                int gx = 0; 
                int gy = 0; 

                for (int ky = -1; ky <= 1; ky++) {       
                    for (int kx = -1; kx <= 1; kx++) {   
                        
                        // Extraer el índice lineal (1D simulando 2D matrix) para posicionarse.
                        int neighbor = gray[(y + ky) * width + (x + kx)];

                        gx += KERNEL_X[ky + 1][kx + 1] * neighbor;
                        gy += KERNEL_Y[ky + 1][kx + 1] * neighbor;
                    }
                }

                // Vector resultante del calculo local pitagórico (intensidad luminosa detectada en gradientes)
                int magnitude = (int) Math.sqrt(gx * gx + gy * gy);

                // Capping y fijado final a la variable de Referencias que escupira al Main Thread original general `out`.
                out[y * width + x] = Math.min(255, magnitude);
            }
        }
    }

    // ─── PASO 3-A: CONCURRENCIA MATEMÁTICA ──────────────────────────────────
    /**
     * Fracciona explícitamente en partes porcentuales y las distribuye.
     * Funciona fantástico para acelerar procesamiento masivo en hardware multinúcleo.
     *
     * @param input Buffered crudo.
     * @return Wrapper Result lleno de tiempos por Thead paralelos evaluados.
     */
    public ProcessResult processParallel(BufferedImage input) throws Exception {
        int width  = input.getWidth();
        int height = input.getHeight();

        // 4 Cuadrantes exactos
        int q1 = height / 4;         
        int q2 = height / 2;         
        int q3 = 3 * height / 4;     

        int[] gray = toGrayPixels(input);
        int[] out = new int[width * height];

        // Anclaje temporal de inicio de flujo complejo
        long appStart = System.nanoTime();

        // Alocación de 4 CPUs virtuales Java con el pool FIJO estático (No ampliable)
        ExecutorService executor = Executors.newFixedThreadPool(4);

        // Hilo 1 - Ejecutando su cuarta parte de cielo
        Callable<Long> task1 = () -> {
            long start = System.nanoTime();
            applyScharr(gray, out, width, 0, q1);
            return System.nanoTime() - start; // Retorna duración interna de subhilo
        };

        // Hilo 2 - Ecuador superior
        Callable<Long> task2 = () -> {
            long start = System.nanoTime();
            applyScharr(gray, out, width, q1, q2);
            return System.nanoTime() - start;
        };

        // Hilo 3 - Ecuador inferior
        Callable<Long> task3 = () -> {
            long start = System.nanoTime();
            applyScharr(gray, out, width, q2, q3);
            return System.nanoTime() - start;
        };

        // Hilo 4 - Suelo
        Callable<Long> task4 = () -> {
            long start = System.nanoTime();
            applyScharr(gray, out, width, q3, height - 1);
            return System.nanoTime() - start;
        };

        // Activamos todos los theads suspendiendo a la espera que el más lento concluya (invokeAll sync block)
        var futures = executor.invokeAll(java.util.List.of(task1, task2, task3, task4));
        executor.shutdown(); // Limpiando huella RAM

        // Recolectar lo que las promesas (.get()) arrojaron como valor long
        long t1 = futures.get(0).get();
        long t2 = futures.get(1).get();
        long t3 = futures.get(2).get();
        long t4 = futures.get(3).get();

        long totalTime = System.nanoTime() - appStart;

        // Mandar el mapa vectorial out plano a rasterizar.
        BufferedImage result = buildImage(out, width, height);
        return new ProcessResult(result, t1, t2, t3, t4, totalTime);
    }

    // ─── PASO 3-B: LEGACY SECUENCIAL NATIVO ──────────────────────────────────
    /**
     * Un bypass monohilo directo sin particionar, enviando toda la altura de golpe.
     */
    public ProcessResult processSequential(BufferedImage input) {
        int width  = input.getWidth();
        int height = input.getHeight();
        
        int[] gray = toGrayPixels(input);
        int[] out  = new int[width * height];

        // Inversión de bloque secuencial midiendo la tarea en bloque macizo de código
        long start = System.nanoTime();
        applyScharr(gray, out, width, 1, height - 1);
        long t1 = System.nanoTime() - start;

        BufferedImage result = buildImage(out, width, height);
        // Semánticamente los T2, T3, T4 pierden existencia objetiva. Son 0 L.
        return new ProcessResult(result, t1, 0L, 0L, 0L, t1);
    }

    // ─── RE-ENSAMBLE (Conversión Flat a Matrix Rasterizada) ────────────────
    /**
     * Trasladar array primitivo lógico en BufferedImage objeto que JAVA e IO entiendan.
     */
    private BufferedImage buildImage(int[] pixels, int width, int height) {
        
        // Emplea TYPE_BYTE_GRAY para consumir apenas 1 B per pixel en lugar de cuadruplicarlo (ARGB)
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Despeje
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Saca de línea vectorial plana por matemática 
                int v = pixels[y * width + x]; 

                // Imposiciona repetición hexadecimal idéntica forzando colores grises equivalentes.
                int rgb = (v << 16) | (v << 8) | v;
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }
}
