package com.algoritmogris.laplaciano;

import java.awt.image.BufferedImage;

/**
 * Clase encargada de realizar la detección de bordes mediante la segunda derivada espacial.
 * Se implementa secuencialmente utilizando el Operador o Laplaciano con vecindad-8.
 * Resalta regiones de cambio rápido de intensidad, pero es muy sensible al ruido visual.
 */
public class EdgeDetectorLaplaciano {

    /**
     * Aplica el kernel analítico filtro Laplaciano sobre toda la imagen enviada.
     * Genera la detección topográfica completa sin sesgo direccional unidimensional.
     *
     * @param image Imagen fuente color a escanear (BufferedImage).
     * @return Una de monocromo con un marcado de bordes no direccional estricto.
     */
    public static BufferedImage detectEdges(BufferedImage image) {

        // Extraer y almacenar los linderos de máximo escaneo
        int width = image.getWidth();
        int height = image.getHeight();

        // Instanciar matriz virtual con TYPE_BYTE_GRAY reservando memoria solo iluminada
        BufferedImage result =
                new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Máscara Laplaciana de conectividad 8 (Vecindad Total)
        // Resta un entorno exterior (-1 perimetral) vs un centro altamente contrastado (+8)
        // Cuando las celdas alrededor son homogéneas al centro, la suma se anula a cero.
        int[][] laplacian = {
                {-1, -1, -1},
                {-1,  8, -1},
                {-1, -1, -1}
        };

        // ─── BARRIDO LINEAL ───
        
        // Desplazarse secuencialmente limitando el margen seguro para prevenir desbordes de array IOB()
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {

                // Acumulador variable que recolectará fuerza de convolución general Laplaciana
                int sum = 0;

                // Ciclos anidados 3x3 centrados originados en coordenada [x,y] 
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {

                        // Analizar el pixel perimetral adjunto a vecindad 
                        int pixel = image.getRGB(x + j, y + i);

                        // Filtrado bit a bit para desglosar el entero cromático gigante en luz RGB 
                        int r = (pixel >> 16) & 0xff; // Banda Roja
                        int g = (pixel >> 8) & 0xff;  // Banda Verde
                        int b = pixel & 0xff;         // Banda Azul final

                        // Uniformidad aritmética lumínica
                        int gray = (r + g + b) / 3;

                        // Procedimiento núcleo: Multiplicamos el gris aislado por nuestro coeficiente 
                        // Laplaciano predeterminado en esa posición y lo guardamos sumando.
                        sum += gray * laplacian[i + 1][j + 1];
                    }
                }

                // Obturador y corrector limitante matemático.
                // Usamos Math.abs() debido a que el laplaciano produce tanto caídas negativas 
                // abruptas como picos positivos, y limitamos con un techo (min) de 255 (Blanco Puro).
                int magnitude = Math.min(255, Math.abs(sum));

                // Desplazamiento a empaquetado de byte color formato Java Nativo
                int edge =
                        (magnitude << 16) |  // Slot canal rojo
                        (magnitude << 8)  |  // Slot canal verde
                        magnitude;           // Slot canal azul

                // Punteado local resultante pintado a matriz
                result.setRGB(x, y, edge);
            }
        }

        // Devolución buffer ensamblado
        return result;
    }
}
