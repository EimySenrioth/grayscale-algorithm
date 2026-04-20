package com.algoritmogris.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * StorageUtil - Minitool utilitario encargado interactuar con el FileSystem general 
 * Funcionalidad orientada exclusivamente a serializar binarios sobre la memoria rígida (Discouro)
 */
public class StorageUtil {

    // Variable global inmutable estática (Thread-Safe para Date Formatting Time)
    // Instanciado sobre un patrón claro (AñoMesDia_HoraMinuto)  e.g 20261130_091522. Útil por prevenciones colisionales.
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Graba el buffer flotante de nuestra RAM al disco en formato PNG codificado local.
     * Busca la ruta de despliegue para determinar si estamos en testeo o productivo Tomcat.
     *
     * @param image     Matriz Buffer de píxeles resultante 
     * @param suffix    Sufijo o modalidad de marca "Sobel", "Canny", "Mosaico" para inyectar su nombre 
     * @return          String referencial de puntero indicando la ruta Windows/Unix absoluta y final grabada 
     * @throws IOException Obligatoriedad sintáctica frente excepciones IO (Imposibilidad disco o escritura prohibida)
     */
    public static String save(BufferedImage image, String suffix) throws IOException {
        
        // El conector revisa si corremos incrustados sobre variables globales como Tomcat (.catalina property)
        String samplesPath = System.getProperty("catalina.home") != null
            // Flujo Positivo Apache Tomcat : Construye el path base al directorio AppWeb \algoritmogris\samples\ 
            ? System.getProperty("catalina.home") + File.separator + "webapps"
              + File.separator + "algoritmogris" + File.separator + "samples"
            // Flujo Negativo Directo Testing Local : Guardará al mismo escalón relativo `./samples` ejecutado en CMD o IDE
            : "samples";

        // Preparamos objeto explorador de nodo apuntado
        File samplesDir = new File(samplesPath);

        // Si la carpeta estricta "Samples" no existiese, procedemos a forzar su anexo 
        if (!samplesDir.exists()) {
            samplesDir.mkdirs(); // Permite concatenar carpetas anidadas de forma segura
        }

        // Armamos el String base uniendo nombre universal "Scharr", timestamp y etiqueta con "png" estático
        String fileName = "scharr_" + LocalDateTime.now().format(FMT) + "_" + suffix + ".png";
        
        // Declaramos Objeto Salida asociando Ruta + Nombre
        File output = new File(samplesDir, fileName);

        // Activamos función codificadora ImageIO sobre nuestro array pasándole meta-etiqueta tipo PNG, inyectable a la instancia File targetizada.
        ImageIO.write(image, "PNG", output);

        // Devuelve ruta física para base de datos o FrontEnd (útil como Link tag <a href= / descarga>) 
        return output.getAbsolutePath();
    }
}
