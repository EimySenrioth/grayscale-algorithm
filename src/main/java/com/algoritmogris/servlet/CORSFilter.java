package com.algoritmogris.servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

/**
 * CORSFilter - Filtro que permite peticiones Cross-Origin Resource Sharing.
 *
 * Necesario para que el frontend React (en localhost:5173) pueda
 * comunicarse con el backend Java (en localhost:8080) sin errores de CORS.
 */
public class CORSFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest  request  = (HttpServletRequest)  req;

        // Permitir peticiones desde cualquier origen (desarrollo)
        response.setHeader("Access-Control-Allow-Origin", "*");
        // Métodos HTTP permitidos
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        // Cabeceras permitidas en la petición
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        // Las peticiones OPTIONS son pre-flight de CORS, responder con 200 directamente
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(req, res);
    }

    @Override public void init(FilterConfig fc) {}
    @Override public void destroy() {}
}
