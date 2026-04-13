package com.algoritmogris.canny;

import java.awt.image.BufferedImage;

// Clase encargada de realizar la detección de bordes utilizando el operador Canny
public class EdgeDetectorCanny {

    // Método principal que detecta bordes
    public static BufferedImage detectEdges(BufferedImage image) {

        int width = image.getWidth();
        int height = image.getHeight();

        // Crear imagen resultado
        BufferedImage result =
                new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Paso 1: Convertir imagen a escala de grises
        int[][] gray = new int[width][height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                int pixel = image.getRGB(x, y);

                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                gray[x][y] = (r + g + b) / 3;
            }
        }

        // Paso 2: Aplicar Filtro Gaussiano (suavizado)
        int[][] gaussian = {
                {1, 2, 1},
                {2, 4, 2},
                {1, 2, 1}
        };

        int[][] smooth = new int[width][height];

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                int sum = 0;

                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {

                        sum += gray[x + j][y + i] *
                                gaussian[i + 1][j + 1];
                    }
                }

                smooth[x][y] = sum / 16;
            }
        }

        // Paso 3: Calcular gradiente usando Sobel
        int[][] sobelX = {
                {-1, 0, 1},
                {-2, 0, 2},
                {-1, 0, 1}
        };

        int[][] sobelY = {
                {-1, -2, -1},
                {0, 0, 0},
                {1, 2, 1}
        };

        int[][] magnitude = new int[width][height];

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                int gx = 0;
                int gy = 0;

                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {

                        gx += smooth[x + j][y + i] *
                                sobelX[i + 1][j + 1];

                        gy += smooth[x + j][y + i] *
                                sobelY[i + 1][j + 1];
                    }
                }

                magnitude[x][y] =
                        (int) Math.sqrt(gx * gx + gy * gy);
            }
        }

        // Paso 4: Umbral doble
        int highThreshold = 100;
        int lowThreshold = 50;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                int value = magnitude[x][y];

                int edge = 0;

                // Bordes fuertes
                if (value >= highThreshold) {
                    edge = 255;
                }
                // Bordes débiles
                else if (value >= lowThreshold) {
                    edge = 128;
                }
                // No borde
                else {
                    edge = 0;
                }

                int pixel =
                        (edge << 16) |
                        (edge << 8) |
                        edge;

                result.setRGB(x, y, pixel);
            }
        }

        // Retornar imagen procesada
        return result;
    }
}
