package com.bisha.ijvm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for the IJVM ALU (Arithmetic Logic Unit).
 *
 * <p>This application implements a virtual machine that simulates the Arithmetic
 * Logic Unit (ALU) of the MIC-1 architecture as described by Andrew S. Tanenbaum
 * in "Structured Computer Organization". The system provides REST endpoints for
 * processing programs composed of 6-bit instructions that control ALU operations.</p>
 *
 * <h2>Architecture Overview</h2>
 * <p>The MIC-1 ALU operates on 32-bit values and supports the following operations:</p>
 * <ul>
 *   <li><b>AND</b> - Bitwise logical AND (F0=0, F1=0)</li>
 *   <li><b>OR</b>  - Bitwise logical OR  (F0=0, F1=1)</li>
 *   <li><b>NOT B</b> - Bitwise complement of B register (F0=1, F1=0)</li>
 *   <li><b>ADD</b>  - Arithmetic addition with carry (F0=1, F1=1)</li>
 * </ul>
 *
 * <h2>Available Endpoints</h2>
 * <ul>
 *   <li><b>POST /api/ula/executar</b> - Execute program from JSON payload
 *     <pre>curl -X POST http://localhost:8080/api/ula/executar \
 *   -H 'Content-Type: application/json' \
 *   -d '{"instrucoes": ["111100", "111101"]}'</pre>
 *   </li>
 *   <li><b>POST /api/ula/executar-arquivo</b> - Upload and execute .txt file
 *     <pre>curl -F 'arquivo=@programa.txt' http://localhost:8080/api/ula/executar-arquivo</pre>
 *   </li>
 * </ul>
 *
 * <h2>Utility Scripts</h2>
 * <p>To generate detailed execution logs in .log format:</p>
 * <pre>python3 gerar_log.py etapa1.txt</pre>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 * @since 1.0
 * @see com.bisha.ijvm.controller.UlaController
 * @see com.bisha.ijvm.service.UlaService
 * @see com.bisha.ijvm.model.EstadoULA
 */
@SpringBootApplication
public class UlaApplication {

    /**
     * Default constructor for the application.
     *
     * <p>This constructor is required by Spring Boot for component scanning
     * and dependency injection. It does not perform any initialization as
     * the application context is managed by the Spring framework.</p>
     */
    public UlaApplication() {
        // Constructor required by Spring Boot framework
        // Initialization is handled by Spring's dependency injection container
    }

    /**
     * Main entry point for the IJVM ALU application.
     *
     * <p>This method bootstraps the Spring Boot application, initializes the
     * embedded web server (typically Tomcat), and displays startup information
     * including available endpoints and usage examples.</p>
     *
     * <p><b>Startup Information Displayed:</b></p>
     * <ul>
     *   <li>Application name and status</li>
     *   <li>Available REST endpoints with HTTP methods</li>
     *   <li>cURL command examples for testing</li>
     *   <li>Utility script usage instructions</li>
     * </ul>
     *
     * <p><b>Default Server Configuration:</b></p>
     * <ul>
     *   <li>Port: 8080 (configurable via application.properties)</li>
     *   <li>Context path: / (root)</li>
     *   <li>API base path: /api/ula</li>
     * </ul>
     *
     * @param args Command line arguments passed to the Spring Boot application
     *             (not used in standard configuration, can be used for profile selection)
     * @see SpringApplication#run(Class, String[])
     */
    public static void main(String[] args) {
        SpringApplication.run(UlaApplication.class, args);
        System.out.println("=== ULA IJVM Inicializada ===");
        System.out.println("Endpoints disponíveis:");
        System.out.println("  POST /api/ula/executar - Executar programa via JSON");
        System.out.println("  POST /api/ula/executar-arquivo - Upload e execução de arquivo .txt");
        System.out.println("\nExemplos de uso:");
        System.out.println("  curl -X POST http://localhost:8080/api/ula/executar \\");
        System.out.println("    -H 'Content-Type: application/json' \\");
        System.out.println("    -d '{\"instrucoes\": [\"111100\", \"111101\"]}'");
        System.out.println("\n  curl -F 'arquivo=@etapa1.txt' http://localhost:8080/api/ula/executar-arquivo");
        System.out.println("\nPara gerar arquivo .log:");
        System.out.println("  python3 gerar_log.py etapa1.txt");
    }
}
