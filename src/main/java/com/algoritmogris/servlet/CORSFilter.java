package com.algoritmogris.servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

/**
 * CORSFilter - Componente Interceptor de Servlet a capa de Red.
 * Es crucial funcionalmente para sortear el error "Cross-Origin Resource Sharing".
 * En sistemas Web React + Java, dado a los puertos asimétricos usados (ej, Frontend 5173 / Tomcat 8080), 
 * los navegadores rechazan los AJAX considerándolos maliciosos. Este Filtro anula ese candado.
 */
public class CORSFilter implements Filter {

    /**
     * Interferencia automática ejecutada previamente a CUALQUIER enrutamiento HttpServlet.
     * En este bloque interceptamos y decoramos respuesta añadiendo tokens/visas "Access-Controls" 
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
                
        // Casting forzado hacia especificaciones HTTP puras y modernas 
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest  request  = (HttpServletRequest)  req;

        // Declaración "Origin": "*" indica que nuestra API es permisiva con host remotos o locales (Entorno Testing/Dev) 
        response.setHeader("Access-Control-Allow-Origin", "*");
        
        // Explicita concretamente los verbos que el Servlet asimilará en su pool (Rechaza PUT/DELETE implícitamente aquí por seguridad)
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        
        // Instruye al cliente React qué etiquetas exóticas son aceptables procesar (E.g permitiendo dictar "Content-Type" manuales ej Json vs Blob).
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        // Regla OPTIONS : El motor Web (FetchAPI) dispara a veces un Request Previo ciego de prueba de choque (Aprobación pre-flight).
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            // Se le cede un status nominal rápido y exitoso SC_OK (200), diciéndole "es factible hacer CORS"
            response.setStatus(HttpServletResponse.SC_OK);
            // Aborta ruta de red ahorrando procesar servlets inútilmente.
            return;
        }

        // Una vez modificados los headers, indicamos al middleware de Jakarta "sigue rotando" o ve al Servlet normal (FilterChain.continue)
        chain.doFilter(req, res);
    }

    // Constructor virtual (No requerido por diseño stateless)
    @Override public void init(FilterConfig fc) {}
    
    // Destructor destructor recolección de chatarra (Igualmente nulo en lógica per se)
    @Override public void destroy() {}
}
