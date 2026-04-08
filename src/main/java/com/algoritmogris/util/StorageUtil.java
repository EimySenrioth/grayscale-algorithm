package com.algoritmogris.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * StorageUtil - Utilidad para guardar las imágenes procesadas en el disco.
 *
 * Las imágenes resultantes se guardan en la carpeta `samples/` del proyecto
 * con un nombre único basado en la fecha y hora actual para evitar colisiones.
 */
public class StorageUtil {

    // Formateador para generar nombres de archivo únicos tipo: 20240401_120530
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Guarda un BufferedImage como archivo PNG en la carpeta samples/.
     *
     * La ruta de samples/ se resuelve relativa al directorio raíz del proyecto.
     * Si la carpeta no existe, se crea automáticamente.
     *
     * @param image     Imagen resultante del procesamiento Scharr
     * @param suffix    Sufijo descriptivo para el nombre de archivo
     * @return          Ruta absoluta del archivo guardado
     * @throws IOException si no se puede escribir el archivo en disco
     */
    public static String save(BufferedImage image, String suffix) throws IOException {
        // Construir la ruta de la carpeta samples/ junto al proyecto
        String samplesPath = System.getProperty("catalina.home") != null
            // En servidor Tomcat: usar la carpeta samples/ dentro del webapps
            ? System.getProperty("catalina.home") + File.separator + "webapps"
              + File.separator + "algoritmogris" + File.separator + "samples"
            // En ejecución local directa: carpeta samples/ en el directorio raíz
            : "samples";

        File samplesDir = new File(samplesPath);

        // Crear la carpeta samples/ si no existe
        if (!samplesDir.exists()) {
            samplesDir.mkdirs();
        }

        // Generar nombre único usando la fecha y hora actual + sufijo del modo de proceso
        String fileName = "scharr_" + LocalDateTime.now().format(FMT) + "_" + suffix + ".png";
        File output = new File(samplesDir, fileName);

        // Escribir la imagen en disco en formato PNG (sin pérdida de calidad)
        ImageIO.write(image, "PNG", output);

        // Retornar la ruta completa para que el servlet la incluya en la respuesta JSON
        return output.getAbsolutePath();
    }
}
