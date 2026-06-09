package com.bisha.ijvm.controller;

import com.bisha.ijvm.model.EstadoULA;
import com.bisha.ijvm.service.UlaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for managing IJVM ALU (Arithmetic Logic Unit) operations.
 *
 * <p>This controller provides HTTP endpoints for executing MIC-1 architecture
 * programs either through direct JSON input or file upload. The ALU simulates
 * the behavior described by Tanenbaum's MIC-1 specification.</p>
 *
 * <p><b>Endpoint Overview:</b></p>
 * <ul>
 *   <li><b>POST /api/ula/executar</b> - Execute program from JSON payload</li>
 *   <li><b>POST /api/ula/executar-arquivo</b> - Execute program from uploaded text file</li>
 * </ul>
 *
 * <p><b>Example Usage with cURL:</b></p>
 * <pre>
 * curl -X POST http://localhost:8080/api/ula/executar \
 *      -H "Content-Type: application/json" \
 *      -d '{"instrucoes": ["111100", "110101", "110100", "011100"]}'
 *
 * curl -F "arquivo=@programa.txt" http://localhost:8080/api/ula/executar-arquivo
 * </pre>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 * @since 1.0
 * @see com.bisha.ijvm.service.UlaService
 * @see com.bisha.ijvm.model.EstadoULA
 */
@RestController
@RequestMapping("/api/ula")
public class UlaController {

    /**
     * The service layer that implements the MIC-1 ALU logic.
     * This dependency is injected by Spring's dependency injection container.
     */
    @Autowired
    private UlaService ulaService;

    /**
     * Default constructor for UlaController.
     *
     * <p>This constructor is used by Spring Framework for component instantiation
     * and dependency injection. The actual service dependency is injected via
     * field injection using {@link Autowired}.</p>
     */
    public UlaController() {
        // Default constructor required by Spring Framework
    }

    /**
     * Executes a program provided as a JSON payload.
     *
     * <p>This endpoint processes a list of 6-bit binary instructions using the
     * MIC-1 ALU simulation. The execution supports forwarding of results between
     * instructions as specified in the MIC-1 architecture.</p>
     *
     * <p><b>Initial Register Values:</b></p>
     * <ul>
     *   <li>Register A: 0xFFFFFFFF (all ones, 32-bit)</li>
     *   <li>Register B: 0x00000001 (value 1, 32-bit)</li>
     * </ul>
     *
     * <p><b>Request Format (JSON):</b></p>
     * <pre>
     * {
     *   "instrucoes": ["111100", "110101", "110100", "011100"]
     * }
     * </pre>
     *
     * <p><b>Response Format:</b></p>
     * <pre>
     * {
     *   "log": [
     *     {
     *       "pc": 1,
     *       "ir": 60,
     *       "irBin": "111100",
     *       "a": -1,
     *       "aHex": "FFFFFFFF",
     *       "aBin": "11111111111111111111111111111111",
     *       "b": 1,
     *       "bHex": "00000001",
     *       "bBin": "00000000000000000000000000000001",
     *       "s": -2,
     *       "sHex": "FFFFFFFE",
     *       "sBin": "11111111111111111111111111111110",
     *       "vaiUm": 0
     *     }
     *   ],
     *   "totalInstrucoes": 4
     * }
     * </pre>
     *
     * @param request Map containing the "instrucoes" key with a list of binary instruction strings
     * @return ResponseEntity containing execution log with PC, register states, and ALU results,
     *         or error response if no instructions are provided
     * @throws ClassCastException if the "instrucoes" value is not a List of Strings
     * @see UlaService#processarPrograma(List, int, int)
     */
    @PostMapping("/executar")
    public ResponseEntity<Map<String, Object>> executarPrograma(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> instrucoes = (List<String>) request.get("instrucoes");

        if (instrucoes == null || instrucoes.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("erro", "Nenhuma instrução fornecida");
            return ResponseEntity.badRequest().body(error);
        }

        // Valores iniciais conforme especificação MIC-1
        int initialA = 0xFFFFFFFF;
        int initialB = 0x00000001;

        // Usar forwarding de resultados
        List<EstadoULA> log = ulaService.processarPrograma(instrucoes, initialA, initialB);

        // Converter para formato de resposta serializável
        List<Map<String, Object>> logResponse = new ArrayList<>();
        for (EstadoULA estado : log) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("pc", estado.getPc());
            entry.put("ir", estado.getIr());
            entry.put("irBin", estado.getIRBin());
            entry.put("a", estado.getA());
            entry.put("aHex", estado.getAHex());
            entry.put("aBin", estado.getABin());
            entry.put("b", estado.getB());
            entry.put("bHex", estado.getBHex());
            entry.put("bBin", estado.getBBin());
            entry.put("s", estado.getS());
            entry.put("sHex", estado.getSHex());
            entry.put("sBin", estado.getSBin());
            entry.put("vaiUm", estado.getVaiUm());
            logResponse.add(entry);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("log", logResponse);
        response.put("totalInstrucoes", log.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Executes a program from an uploaded text file.
     *
     * <p>This endpoint accepts text files where each line contains a 6-bit binary
     * instruction (e.g., "111100"). Lines starting with '#' are treated as comments
     * and ignored. Empty lines are also skipped automatically.</p>
     *
     * <p><b>File Format Example:</b></p>
     * <pre>
     * # Simple addition program
     * 111100  # AND operation
     * 110101  # OR operation
     * 110100  # NOT B operation
     * 011100  # Addition with carry
     * </pre>
     *
     * <p><b>Processing Details:</b></p>
     * <ul>
     *   <li>Files are read with UTF-8 encoding</li>
     *   <li>Each line is trimmed before processing</li>
     *   <li>Empty lines and comment lines (starting with #) are filtered out</li>
     *   <li>The program counter (PC) starts at 1 and increments for each instruction</li>
     *   <li>Register A initial value: 0xFFFFFFFF (32-bit all ones)</li>
     *   <li>Register B initial value: 0x00000001 (value 1)</li>
     * </ul>
     *
     * <p><b>Response Format:</b> Same as the {@link #executarPrograma(Map)} endpoint,
     * containing the execution log with all register states.</p>
     *
     * @param arquivo Multipart file containing the program instructions (text/plain format)
     * @return ResponseEntity containing execution log with PC, register states, and ALU results,
     *         or error response if file is empty, contains no valid instructions, or processing fails
     * @throws IllegalArgumentException if file content cannot be parsed as binary instructions
     */
    @PostMapping("/executar-arquivo")
    public ResponseEntity<Map<String, Object>> executarArquivo(@RequestParam("arquivo") MultipartFile arquivo) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {

            List<String> instrucoes = reader.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toList());

            if (instrucoes.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("erro", "Arquivo vazio ou sem instruções válidas");
                return ResponseEntity.badRequest().body(error);
            }

            int initialA = 0xFFFFFFFF;
            int initialB = 0x00000001;

            List<EstadoULA> log = ulaService.processarPrograma(instrucoes, initialA, initialB);

            List<Map<String, Object>> logResponse = new ArrayList<>();
            for (EstadoULA estado : log) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("pc", estado.getPc());
                entry.put("ir", estado.getIr());
                entry.put("irBin", estado.getIRBin());
                entry.put("a", estado.getA());
                entry.put("aHex", estado.getAHex());
                entry.put("b", estado.getB());
                entry.put("bHex", estado.getBHex());
                entry.put("s", estado.getS());
                entry.put("sHex", estado.getSHex());
                entry.put("vaiUm", estado.getVaiUm());
                logResponse.add(entry);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("log", logResponse);
            response.put("totalInstrucoes", log.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("erro", "Erro ao processar arquivo: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
