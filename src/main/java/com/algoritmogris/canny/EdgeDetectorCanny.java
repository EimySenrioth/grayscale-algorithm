package com.algoritmogris.canny;

import java.awt.image.BufferedImage;

/**
 * Clase delegada a aplicar el clásico proceso algorítmico multicapa computacional de Canny
 * Detección avanzada de bordes en cuatro pasos analíticos para maximizar ruido vs figura.
 */
public class EdgeDetectorCanny {

    /**
     * Motor funcional principal. Genera bordes continuos bien definidos mediante 
     * gaussian softening, mapeo sobel, supresión no máxima (implícita/parcial) y threshold doble.
     *
     * @param image Entrada gráfica a analizar.
     * @return El mapa procesado resultante en canal monocromo.
     */
    public static BufferedImage detectEdges(BufferedImage image) {

        // Recolección y registro estático de medidas
        int width = image.getWidth();
        int height = image.getHeight();

        // Solicitud en la memoria HEAP del nuevo lienzo vacío gris de salida
        BufferedImage result =
                new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // ─── PASO 1: LUMINISCENCIA (Escala de grises) ───
        
        // Mapeo virtualizado int[][] de cada componente fotolumínico
        int[][] gray = new int[width][height];

        // Bucles de extracción de coordenadas matriz bruta original
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                // Píxel íntegro actual
                int pixel = image.getRGB(x, y);

                // Rebanada aritmética de canales RGB (R=16, G=8, B=0 bits)
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                // Promediamos rudimentariamente la luz por píxel
                gray[x][y] = (r + g + b) / 3;
            }
        }

        // ─── PASO 2: DIFUMINACIÓN GAUSSIANA (Noise Reduction) ───
        
        // Máscara 3x3 normalizada diseñada para aplastar granizado visual/suavizado base
        // El núcleo centro es 4 y el radio decae (2 y 1) dando prioridad direccional 
        int[][] gaussian = {
                {1, 2, 1},
                {2, 4, 2},
                {1, 2, 1}
        };

        // Matriz de puente transicional procesada libre de ruido extremo 
        int[][] smooth = new int[width][height];

        // Margen de protección padding pixel en bordes perimetrales X/Y
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                // Contador escalar temporal de ponderación Gauss
                int sum = 0;

                // Proyección ventana de kernel (radio local)
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        // Multiplicamos intensidad luminosa por coeficiente campana gaussiana 
                        sum += gray[x + j][y + i] *
                                gaussian[i + 1][j + 1];
                    }
                }

                // Normalizado de peso total divisorio = 16 (1+2+1 + 2+4+2 + 1+2+1 = 16)
                smooth[x][y] = sum / 16;
            }
        }

        // ─── PASO 3: GRADIENTES DE SOBEL (Derivativas 1ras direccionales) ───
        
        // Pesos para detección contraste Eje X.
        int[][] sobelX = {
                {-1, 0, 1},
                {-2, 0, 2},
                {-1, 0, 1}
        };

        // Pesos para detección contraste Eje Y.
        int[][] sobelY = {
                {-1, -2, -1},
                {0, 0, 0},
                {1, 2, 1}
        };

        // Matriz receptora del gradiente magnitudinal puro
        int[][] magnitude = new int[width][height];

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                // Variables vectoriales locales
                int gx = 0; // Vector Fuerza Horizontal
                int gy = 0; // Vector Fuerza Vertical

                // Proceso algorítmico convolucional en matriz 3x3 (barrido kernel)
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        // Inyectar convolución con datos suavizados previamente
                        gx += smooth[x + j][y + i] *
                                sobelX[i + 1][j + 1];
                        gy += smooth[x + j][y + i] *
                                sobelY[i + 1][j + 1];
                    }
                }

                // Cuantificación absoluta de vector inclinado a magnitud (Magnitud Hipotenusa)
                magnitude[x][y] =
                        (int) Math.sqrt(gx * gx + gy * gy);
            }
        }

        // ─── PASO 4: THRESHOLDING (Umbralización y rasterizado de Borde) ───
        
        // Todo pixel que supere esta marca es forzado incondicionalmente a dibujarse blanco
        int highThreshold = 100;
        
        // Línea roja permisiva, grises intermedios en transición
        int lowThreshold = 50;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                // Lectura escalar magnitudinal mapeada 
                int value = magnitude[x][y];

                // Byte virtual del color objetivo de este punto
                int edge = 0;

                // Aplicación Regla (Hysteresis Simplificada)
                
                // Opción 1: Bordes definidos indudables
                if (value >= highThreshold) {
                    edge = 255;
                }
                // Opción 2: Bordes dudosos se renderizan tenues (128 = gris medio)
                else if (value >= lowThreshold) {
                    edge = 128; // Típicamente los algoritmos canny aquí conectan con bordes fuertes adyacentes para decidir.
                }
                // Opción 3: Fondo oscuro liso nulo. Abortamos línea
                else {
                    edge = 0;
                }

                // Construcción forzada y empaquetado bits de pixel ARGB
                int pixel =
                        (edge << 16) |
                        (edge << 8) |
                        edge;

                // Pintamos al búfer destino final uniendo matriz entera resultante.
                result.setRGB(x, y, pixel);
            }
        }

        // Despachamos componente mapeado BufferedImage.
        return result;
    }
}
