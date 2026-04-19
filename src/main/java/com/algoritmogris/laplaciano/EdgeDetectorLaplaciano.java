package com.algoritmogris.laplaciano;

import java.awt.image.BufferedImage;

// Clase encargada de realizar la detección de bordes de manera secuencial
// utilizando el operador Laplaciano
public class EdgeDetectorLaplaciano {

    // Método estático que recibe una imagen y retorna la imagen con bordes detectados
    public static BufferedImage detectEdges(BufferedImage image) {

        // Obtener ancho de la imagen
        int width = image.getWidth();

        // Obtener alto de la imagen
        int height = image.getHeight();

        // Crear imagen resultado en escala de grises
        BufferedImage result =
                new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Máscara Laplaciana de 8 vecinos (detecta bordes en todas direcciones)
        int[][] laplacian = {
                {-1, -1, -1},
                {-1,  8, -1},
                {-1, -1, -1}
        };

        // Recorrer filas de la imagen (evitando bordes)
        for (int y = 1; y < height - 1; y++) {

            // Recorrer columnas de la imagen
            for (int x = 1; x < width - 1; x++) {

                // Variable para almacenar el valor del filtro Laplaciano
                int sum = 0;

                // Recorrer ventana 3x3 alrededor del pixel actual
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

                        // Aplicar máscara Laplaciana
                        sum += gray * laplacian[i + 1][j + 1];
                    }
                }

                // Tomar valor absoluto para resaltar bordes
                int magnitude = Math.min(255, Math.abs(sum));

                // Crear pixel en escala de grises
                int edge =
                        (magnitude << 16) |  // Canal rojo
                        (magnitude << 8)  |  // Canal verde
                        magnitude;           // Canal azul

                // Asignar pixel resultado en la imagen
                result.setRGB(x, y, edge);
            }
        }

        // Retornar imagen procesada
        return result;
    }
}
