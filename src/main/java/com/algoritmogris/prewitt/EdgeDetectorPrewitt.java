package com.algoritmogris.prewitt;

import java.awt.image.BufferedImage;

// Clase encargada de realizar la detección de bordes de manera secuencial
// utilizando el operador Prewitt
public class EdgeDetectorPrewitt {

    // Método estático que recibe una imagen y retorna la imagen con bordes detectados
    public static BufferedImage detectEdges(BufferedImage image) {

        // Obtener ancho de la imagen
        int width = image.getWidth();

        // Obtener alto de la imagen
        int height = image.getHeight();

        // Crear imagen resultado en escala de grises
        BufferedImage result =
                new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Máscara Prewitt para detección de bordes verticales (Gradiente X)
        int[][] prewittX = {
                {-1, 0, 1},
                {-1, 0, 1},
                {-1, 0, 1}
        };

        // Máscara Prewitt para detección de bordes horizontales (Gradiente Y)
        int[][] prewittY = {
                {-1, -1, -1},
                { 0,  0,  0},
                { 1,  1,  1}
        };

        // Recorrer filas de la imagen (evitando bordes para no salir de rango)
        for (int y = 1; y < height - 1; y++) {

            // Recorrer columnas de la imagen
            for (int x = 1; x < width - 1; x++) {

                // Gradiente horizontal
                int gx = 0;

                // Gradiente vertical
                int gy = 0;

                // Recorrer ventana de 3x3 alrededor del píxel actual
                for (int i = -1; i <= 1; i++) {

                    for (int j = -1; j <= 1; j++) {

                        // Obtener pixel vecino
                        int pixel = image.getRGB(x + j, y + i);

                        // Extraer componente rojo
                        int r = (pixel >> 16) & 0xff;

                        // Extraer componente verde
                        int g = (pixel >> 8) & 0xff;

                        // Extraer componente azul
                        int b = pixel & 0xff;

                        // Convertir pixel a escala de grises
                        int gray = (r + g + b) / 3;

                        // Aplicar máscara Prewitt en X
                        gx += gray * prewittX[i + 1][j + 1];

                        // Aplicar máscara Prewitt en Y
                        gy += gray * prewittY[i + 1][j + 1];
                    }
                }

                // Calcular magnitud del gradiente
                int magnitude =
                        (int) Math.min(255, Math.sqrt(gx * gx + gy * gy));

                // Crear pixel en escala de grises
                int edge =
                        (magnitude << 16) |  // Canal rojo
                        (magnitude << 8)  |  // Canal verde
                        magnitude;           // Canal azul

                // Asignar pixel resultado en la imagen
                result.setRGB(x, y, edge);
            }
        }

        // Retornar imagen procesada con bordes detectados
        return result;
    }
}
