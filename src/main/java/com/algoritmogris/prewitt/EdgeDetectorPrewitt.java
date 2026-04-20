package com.algoritmogris.prewitt;

import java.awt.image.BufferedImage;

/**
 * Operador de gradiente espacial para detección de bordes Prewitt.
 * Es un procesador secuencial más rudimentario que Sobel (pues no enfatiza o pondera 
 * el píxel central central 2 vs periféricos 1) brindando una detección de contorno más dura.
 */
public class EdgeDetectorPrewitt {

    /**
     * Motor estático secuencial donde se ejecuta la lectura de bits del buffer primigenio.
     * Computa el diferencial derivado cruzando Gx y Gy en magnitud resultante.
     *
     * @param image Imagen digital plana ingresada.
     * @return El render o matriz post-procesada iluminando las irregularidades de línea.
     */
    public static BufferedImage detectEdges(BufferedImage image) {

        // Recolección de límites topográficos
        int width = image.getWidth();
        int height = image.getHeight();

        // Canvas final asignado en memoria, formato gris byte optimizado (1 Byte)
        BufferedImage result =
                new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // ─── NÚCLEOS MATRICIALES (PREWITT KERNELS) ───
        
        // Máscara horizontal Gx
        // Detecta discrepancias luminosas transversales (líneas verticales) ponderadas lineal / uniformemente
        int[][] prewittX = {
                {-1, 0, 1},
                {-1, 0, 1},
                {-1, 0, 1}
        };

        // Máscara vertical Gy
        // Analiza el tope y el fondo buscando transicion oscura-clara (líneas horizontales)
        int[][] prewittY = {
                {-1, -1, -1},
                { 0,  0,  0},
                { 1,  1,  1}
        };

        /** 
         * ALGORITMO CONVOLUTIVO DESPLAZANTE
         * Restringimos -1 iteraciones a orillas superior/inferior (y) 
         * e izquierda/derecha (x) para encuadrar kernel y obviar padding fantasma 
         */
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                // Magnitud vectorial parcial para eje X y para eje Y
                int gx = 0;
                int gy = 0;

                // Loop Kernel interno barriendo una rejilla de tamaño 3x3 circunscrita en [x,y]
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {

                        // Obtenemos hex / integer literal con bits cromáticos ARGB mezclados 
                        int pixel = image.getRGB(x + j, y + i);

                        // Filtros binarios a nivel bit para pelar canal sobre posiciones R, G, B
                        int r = (pixel >> 16) & 0xff; // Shift sobre ROJO
                        int g = (pixel >> 8) & 0xff;  // Shift sobre VERDE
                        int b = pixel & 0xff;         // Tail base para AZUL

                        // Interpolado neutro simplista del componente claro. 
                        int gray = (r + g + b) / 3;

                        // Adición convolutiva, escalamos factor x por coeficiente lineal fijo de array paramétrico.
                        gx += gray * prewittX[i + 1][j + 1];

                        // Adición matemática Prewitt Eje Vertical 
                        gy += gray * prewittY[i + 1][j + 1];
                    }
                }

                // Fusión y vectorización estricta Pitagórica calculando valor resultante neto final del pixel
                // Se somete a estrangulación (Math.min) si por saturación el pitágoras desborda lo visualizable (255)
                int magnitude =
                        (int) Math.min(255, Math.sqrt(gx * gx + gy * gy));

                // Re-empaquetado RGB usando la magnitud como triplete idéntico (forjando el monocromo artificial)
                int edge =
                        (magnitude << 16) |  
                        (magnitude << 8)  |  
                        magnitude;           

                // Aplique e imprimación irreversible del pin sobre tablero base de retorno 
                result.setRGB(x, y, edge);
            }
        }

        // Devolución matriz a Servlet
        return result;
    }
}
