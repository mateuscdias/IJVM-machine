package com.bisha.ijvm.controller;

import com.bisha.ijvm.model.BancoRegistradores;
import com.bisha.ijvm.model.EstadoMic1;
import com.bisha.ijvm.model.EstadoULA;
import com.bisha.ijvm.service.Mic1Service;
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
import java.util.LinkedHashMap;
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
     * Serviço da Etapa 1 — ULA básica de 6 bits.
     * Injetado pelo container de dependências do Spring.
     */
    @Autowired
    private UlaService ulaService;

    /**
     * Serviço da Etapa 2 — caminho de dados completo da Mic-1 (Tarefas 1 e 2).
     * Injetado pelo container de dependências do Spring.
     */
    @Autowired
    private Mic1Service mic1Service;

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

    // =========================================================================
    // ETAPA 2 — TAREFA 1: ULA expandida + Deslocador (8 bits)
    // =========================================================================

    /**
     * Executa um programa da Tarefa 1 da Etapa 2 (palavras de controle de 8 bits).
     *
     * <p>Expande a ULA da Etapa 1 com dois sinais de deslocador pós-ULA:
     * {@code SLL8} (deslocamento lógico esquerdo de 8 bits) e
     * {@code SRA1} (deslocamento aritmético direito de 1 bit).
     * Gera as flags de condição {@code N} (negativo) e {@code Z} (zero) com base em Sd.</p>
     *
     * <p><b>Formato da requisição JSON:</b></p>
     * <pre>
     * {
     *   "instrucoes": ["10111100", "01111100"],
     *   "a": 1,
     *   "b": -2147483648
     * }
     * </pre>
     * <p>Os campos {@code a} e {@code b} são opcionais; o padrão é
     * {@code a=0x00000001} e {@code b=0x80000000}.</p>
     *
     * <p><b>Exemplo de uso com cURL:</b></p>
     * <pre>
     * curl -X POST http://localhost:8080/api/ula/mic1/tarefa1/executar \
     *   -H 'Content-Type: application/json' \
     *   -d '{"instrucoes": ["10111100", "01111100"]}'
     * </pre>
     *
     * @param request Mapa JSON com "instrucoes" (List&lt;String&gt;) e opcionalmente "a" e "b" (int)
     * @return JSON com "log" (lista de ciclos), "logTexto" (log formatado) e "totalCiclos"
     */
    @PostMapping("/mic1/tarefa1/executar")
    public ResponseEntity<Map<String, Object>> executarMic1T1(
            @RequestBody Map<String, Object> request) {

        @SuppressWarnings("unchecked")
        List<String> instrucoes = (List<String>) request.get("instrucoes");

        if (instrucoes == null || instrucoes.isEmpty()) {
            Map<String, Object> erro = new LinkedHashMap<>();
            erro.put("erro", "Nenhuma instrução fornecida.");
            return ResponseEntity.badRequest().body(erro);
        }

        // Valores iniciais — padrão do teste da Tarefa 1 (podem ser sobrescritos via JSON)
        int initialA = request.containsKey("a")
            ? ((Number) request.get("a")).intValue()
            : 0x00000001;                         // a = 1
        int initialB = request.containsKey("b")
            ? ((Number) request.get("b")).intValue()
            : 0x80000000;                         // b = -2147483648 (MSB=1)

        try {
            Map<String, Object> resultado =
                mic1Service.processarProgramaMic1T1(instrucoes, initialA, initialB);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            Map<String, Object> erro = new LinkedHashMap<>();
            erro.put("erro", "Erro ao executar programa Mic-1 T1: " + e.getMessage());
            return ResponseEntity.badRequest().body(erro);
        }
    }

    // =========================================================================
    // ETAPA 2 — TAREFA 2: Banco de Registradores + Barramentos (21 bits)
    // =========================================================================

    /**
     * Executa um programa da Tarefa 2 da Etapa 2 a partir de dois arquivos enviados via upload.
     *
     * <p>Simula o caminho de dados completo da Mic-1: decodifica palavras de 21 bits,
     * gerencia o banco de registradores e aplica os Barramentos B e C.</p>
     *
     * <p><b>Formato do arquivo {@code programa}:</b> cada linha contém uma instrução
     * binária de 21 bits. Linhas vazias encerram a execução (EOP).</p>
     *
     * <p><b>Formato do arquivo {@code registradores}:</b></p>
     * <pre>
     * h   = 00000000000000000000000000000001
     * mbr = 10000001
     * sp  = 00000000000000000000000000000000
     * ...
     * </pre>
     *
     * <p><b>Exemplo de uso com cURL:</b></p>
     * <pre>
     * curl -X POST http://localhost:8080/api/ula/mic1/tarefa2/executar-arquivo \
     *   -F 'programa=@programa_etapa2_tarefa2.txt' \
     *   -F 'registradores=@registradores_etapa2_tarefa2.txt'
     * </pre>
     *
     * @param programa      Arquivo com instruções de 21 bits (texto puro)
     * @param registradores Arquivo com os valores iniciais dos registradores
     * @return JSON com "log" (lista de {@link EstadoMic1}), "logTexto" (texto formatado),
     *         "registradoresIniciais" e "totalCiclos"
     */
    @PostMapping("/mic1/tarefa2/executar-arquivo")
    public ResponseEntity<Map<String, Object>> executarMic1T2Arquivo(
            @RequestParam("programa")      MultipartFile programa,
            @RequestParam("registradores") MultipartFile registradores) {

        try {
            // Lê o arquivo de programa — filtra comentários e brancos
            List<String> instrucoes;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(programa.getInputStream(), StandardCharsets.UTF_8))) {
                instrucoes = reader.lines()
                    .map(String::trim)
                    .filter(l -> !l.startsWith("#"))   // ignora comentários
                    .collect(Collectors.toList());
            }

            if (instrucoes.isEmpty()) {
                Map<String, Object> erro = new LinkedHashMap<>();
                erro.put("erro", "Arquivo de programa vazio ou sem instruções válidas.");
                return ResponseEntity.badRequest().body(erro);
            }

            // Lê e carrega os registradores iniciais
            String conteudoRegistradores;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(registradores.getInputStream(), StandardCharsets.UTF_8))) {
                conteudoRegistradores = reader.lines()
                    .collect(Collectors.joining("\n"));
            }

            BancoRegistradores banco = new BancoRegistradores();
            banco.carregarDeString(conteudoRegistradores);

            // Executa o programa
            Map<String, Object> resultado =
                mic1Service.processarProgramaMic1(instrucoes, banco);

            // Serializa a lista de EstadoMic1 para JSON-friendly
            @SuppressWarnings("unchecked")
            List<EstadoMic1> logEstados = (List<EstadoMic1>) resultado.get("log");
            List<Map<String, Object>> logSerializado = new ArrayList<>();
            for (EstadoMic1 estado : logEstados) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("ciclo",               estado.getCiclo());
                entry.put("ir",                  estado.getIr());
                entry.put("irFormatado",         estado.getIRFormatado());
                entry.put("bBus",                estado.getNomeBBus());
                entry.put("cBus",                estado.getNomesCBus());
                entry.put("registradoresAntes",  converterRegistradores(estado.getRegistradoresAntes()));
                entry.put("registradoresDepois", converterRegistradores(estado.getRegistradoresDepois()));
                entry.put("entradaA",  estado.getEntradaA());
                entry.put("entradaB",  estado.getEntradaB());
                entry.put("s",         estado.getS());
                entry.put("sBin",      EstadoMic1.formatarBin32(estado.getS()));
                entry.put("sd",        estado.getSd());
                entry.put("sdBin",     EstadoMic1.formatarBin32(estado.getSd()));
                entry.put("n",         estado.getFlagN());
                entry.put("z",         estado.getFlagZ());
                entry.put("vaiUm",     estado.getVaiUm());
                logSerializado.add(entry);
            }

            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("registradoresIniciais", resultado.get("registradoresIniciais"));
            resposta.put("log",         logSerializado);
            resposta.put("logTexto",    resultado.get("logTexto"));
            resposta.put("totalCiclos", resultado.get("totalCiclos"));
            return ResponseEntity.ok(resposta);

        } catch (Exception e) {
            Map<String, Object> erro = new LinkedHashMap<>();
            erro.put("erro", "Erro ao processar arquivos Mic-1 T2: " + e.getMessage());
            return ResponseEntity.internalServerError().body(erro);
        }
    }

    /**
     * Converte o mapa de registradores (nome → int) em mapa de strings binárias
     * para serialização JSON amigável ao frontend.
     * O registrador MBR é formatado com 8 bits; os demais com 32 bits.
     *
     * @param registradores Mapa nome→valor (inteiro)
     * @return Mapa nome→string binária
     */
    private Map<String, String> converterRegistradores(Map<String, Integer> registradores) {
        Map<String, String> resultado = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : registradores.entrySet()) {
            String nome  = entry.getKey();
            int valor    = entry.getValue();
            String binStr = "mbr".equals(nome)
                ? EstadoMic1.formatarBin8(valor)
                : EstadoMic1.formatarBin32(valor);
            resultado.put(nome, binStr);
        }
        return resultado;
    }
}
