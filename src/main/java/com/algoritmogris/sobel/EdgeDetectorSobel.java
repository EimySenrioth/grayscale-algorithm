package com.algoritmogris.sobel;

import java.awt.image.BufferedImage;

/**
 * Clase responsable de la detección de bordes usando el operador de Sobel.
 * Es un filtro espacial de primer orden (convolutivo) que resalta transiciones abruptas.
 */
public class EdgeDetectorSobel {

    /**
     * Aplica geométricamente el filtro de Sobel a la imagen suministrada.
     * Toma en cuenta tanto el gradiente horizontal como el vertical.
     *
     * @param image El mapa de bits (BufferedImage) de la imagen origen
     * @return Una nueva BufferedImage renderizada con los contornos extraídos.
     */
    public static BufferedImage detectEdges(BufferedImage image) {
        
        // Se capturan las dimensiones de la imagen fuente recibida (X e Y máximos)
        int width = image.getWidth();
        int height = image.getHeight();

        // Reservamos en memoria la matriz visual predeterminada para monocromo.
        // Se utiliza GRAY_BYTE param maximizar rendimiento ahorrando canales de color inútiles.
        BufferedImage result =
                new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Definición estática de la Máscara Convolutiva: Gx (Sobel horizontal)
        // Valores negativos en la izquierda y positivos a la derecha captan transiciones verticales.
        int[][] sobelX = {
                {-1, 0, 1},
                {-2, 0, 2},
                {-1, 0, 1}
        };

        // Definición estática de la Máscara Convolutiva: Gy (Sobel vertical)
        // Valores negativos arriba y positivos abajo captan transiciones horizontales.
        int[][] sobelY = {
                {-1, -2, -1},
                { 0,  0,  0},
                { 1,  2,  1}
        };

        // ─── FASE CONVOLUTIVA ───
        
        // Ciclo principal eje Y interando imagen (evitando índice 0 y height-1 por padding del kernel 3x3)
        for (int y = 1; y < height - 1; y++) {
            // Ciclo anidado eje X (al igual, restando los bordes perimetrales absolutos)
            for (int x = 1; x < width - 1; x++) {

                // Inicializamos los valores cumulativos para ambos gradientes direccionales en cero
                int gx = 0; // Delta horizontal
                int gy = 0; // Delta vertical

                // Desplazamiento local de nuestro campo "ventana" 3x3 que interactúa alrededor de la coordenada "x,y"
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        
                        // Extraemos la información decimal del pixel real (ARGB entero de 32 bits) 
                        int pixel = image.getRGB(x + j, y + i);
                        
                        // Mediante bitwise operator sacamos la banda de los luminosos
                        // Desplazamiento right bits >> 16 recorta para tomar segmento canal Rojo
                        int r = (pixel >> 16) & 0xff;
                        // Desplazamiento right bits >> 8 toma bloque del canal Verde
                        int g = (pixel >> 8) & 0xff;
                        // Toma el tailing directo como valor del canal Azul original
                        int b = pixel & 0xff;
                        
                        // Obtenemos luminosidad global neutral sumando r, g, b dividido la trilogía
                        int gray = (r + g + b) / 3;

                        // Aplicamos peso al aplicar gradiente local
                        // Multiplicamos el gris por el factor dictaminado de nuestra matriz constante Sobel X 
                        gx += gray * sobelX[i + 1][j + 1];
                        // Operamos idénticamente inyectando el valor con peso Sobel Y
                        gy += gray * sobelY[i + 1][j + 1];
                    }
                }

                // Ecuación Pitagórica (hipotenusa de deltas espaciales) : magnitude = √(x² + y²)
                // Limitamos severamente a máximo teórico 255 (blanco puro) para no exceder gama de 8-bits
                int magnitude =
                        (int) Math.min(255, Math.sqrt(gx * gx + gy * gy));

                // Re-empaquetado ARGB visual para el formato destino simulando el monocromo
                int edge =
                        (magnitude << 16) |  // Posicionando magnitud roja
                        (magnitude << 8)  |  // Posicionando magnitud verde
                        magnitude;           // Posicionando magnitud azul (tail de 8 bits)

                // Pintando el pixel nativo resultante como pixel resuelto dentro de matriz general
                result.setRGB(x, y, edge);
            }
        }
        
        // Entregamos el búfer de imagen construido a nivel mem
        return result;
    }
}
