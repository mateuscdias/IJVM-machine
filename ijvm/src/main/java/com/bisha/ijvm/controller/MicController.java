package com.bisha.ijvm.controller;

import com.bisha.ijvm.model.BlocoInstrucao;
import com.bisha.ijvm.model.EstadoCiclo;
import com.bisha.ijvm.model.Memoria;
import com.bisha.ijvm.model.Registradores;
import com.bisha.ijvm.service.MicService;
import com.bisha.ijvm.service.TradutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for MIC-1 instruction execution.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/mic/executar           – JSON payload (21-bit instructions + registers)</li>
 *   <li>POST /api/mic/executar-arquivo   – multipart: 21-bit program file + register file</li>
 *   <li>POST /api/mic/executar-arquivo23 – multipart: 23-bit program file + register file + memory file</li>
 *   <li>POST /api/mic/executar-ijvm      – multipart: IJVM instruction file + register file + memory file</li>
 * </ul>
 *
 * <h2>Register file format (plain text)</h2>
 * <p>One register per line: {@code NOME=valor_decimal} or {@code NOME=0xHEX}.
 * Lines starting with {@code #} are ignored. Example:</p>
 * <pre>
 * H=0
 * OPC=0
 * TOS=0
 * CPP=0
 * LV=0
 * SP=0
 * PC=0
 * MDR=10
 * MAR=0
 * MBR=0
 * </pre>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 */
@RestController
@RequestMapping("/api/mic")
public class MicController {

    @Autowired
    private MicService micService;

    @Autowired
    private TradutorService tradutorService;

    public MicController() {}

    // =========================================================================
    // JSON endpoint
    // =========================================================================

    /**
     * Executes a list of 21-bit instructions from a JSON body.
     *
     * <p>Request body:</p>
     * <pre>
     * {
     *   "instrucoes": ["001101001010000000000", ...],
     *   "registradores": {
     *     "H":0, "OPC":0, "TOS":0, "CPP":0, "LV":0,
     *     "SP":0, "PC":0, "MDR":10, "MAR":0, "MBR":0
     *   }
     * }
     * </pre>
     */
    @PostMapping("/executar")
    public ResponseEntity<Map<String, Object>> executar(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<String> instrucoes = (List<String>) req.get("instrucoes");
        if (instrucoes == null || instrucoes.isEmpty()) {
            return badRequest("Nenhuma instrução fornecida");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> regsMap = (Map<String, Object>) req.getOrDefault("registradores", new HashMap<>());
        Registradores regs = parseRegistradoresMap(regsMap);

        List<EstadoCiclo> log = micService.executarPrograma(instrucoes, regs);
        return ResponseEntity.ok(buildResponse(log));
    }

    // =========================================================================
    // File upload endpoint
    // =========================================================================

    /**
     * Executes a program from two uploaded text files.
     *
     * <p>The {@code programa} file contains one 21-bit binary instruction per line.
     * The {@code registradores} file contains register initialisations
     * ({@code NOME=value}, one per line). Both files ignore lines starting
     * with {@code #} and blank lines.</p>
     *
     * <pre>
     * curl -F "programa=@programa_etapa2_tarefa2.txt" \
     *      -F "registradores=@registradores_etapa2_tarefa2.txt" \
     *      http://localhost:8080/api/mic/executar-arquivo
     * </pre>
     */
    @PostMapping("/executar-arquivo")
    public ResponseEntity<Map<String, Object>> executarArquivo(
            @RequestParam("programa")      MultipartFile programaFile,
            @RequestParam("registradores") MultipartFile registradoresFile) {
        try {
            List<String> instrucoes = readLines(programaFile);
            if (instrucoes.isEmpty()) return badRequest("Arquivo de programa vazio");

            List<String> regLines = readLines(registradoresFile);
            Registradores regs = parseRegistradoresLines(regLines);

            List<EstadoCiclo> log = micService.executarPrograma(instrucoes, regs);
            return ResponseEntity.ok(buildResponse(log));

        } catch (Exception e) {
            return badRequest("Erro ao processar arquivos: " + e.getMessage());
        }
    }

    /**
     * Executes a 23-bit MIC-1 program from instruction, register and memory files.
     */
    @PostMapping("/executar-arquivo23")
    public ResponseEntity<Map<String, Object>> executarArquivo23(
            @RequestParam("programa")      MultipartFile programaFile,
            @RequestParam("registradores") MultipartFile registradoresFile,
            @RequestParam("memoria")       MultipartFile memoriaFile) {
        try {
            List<String> instrucoes = readLines(programaFile);
            if (instrucoes.isEmpty()) return badRequest("Arquivo de programa vazio");
            validarInstrucoes(instrucoes, 23);

            List<String> regLines = readLines(registradoresFile);
            Registradores regs = parseRegistradoresLines(regLines);
            Registradores regsIniciais = regs.copy();

            List<String> memoriaLines = readLines(memoriaFile);
            if (memoriaLines.isEmpty()) return badRequest("Arquivo de memória vazio");
            validarMemoria(memoriaLines);
            Memoria memoria = Memoria.carregar(memoriaLines);
            List<String> memoriaInicial = memoria.snapshot();

            List<EstadoCiclo> log = micService.executarPrograma23(instrucoes, regs, memoria);
            return ResponseEntity.ok(buildResponse23(log, regsIniciais, memoriaInicial));

        } catch (Exception e) {
            return badRequest("Erro ao processar arquivos: " + e.getMessage());
        }
    }

    /**
     * Translates an IJVM program and executes it on the MIC-1, from three
     * uploaded text files.
     *
     * <p>The {@code instrucoes} file contains one IJVM instruction per line
     * ({@code ILOAD x}, {@code DUP} or {@code BIPUSH byte}). The
     * {@code registradores} file contains register initialisations
     * ({@code NOME=value}, one per line) and the {@code memoria} file one 32-bit
     * binary word per line. All three ignore blank lines and lines starting with
     * {@code #}.</p>
     *
     * <p>The response carries the initial memory, the initial registers and one
     * block per IJVM instruction. Each block holds the cycles of the
     * microinstructions that implement it and the data memory as it stood once
     * that instruction finished, which is what the deliverable log prints.</p>
     *
     * <pre>
     * curl -F "instrucoes=@instruções.txt" \
     *      -F "registradores=@registradores_etapa3_atualizado.txt" \
     *      -F "memoria=@dados_etapa3_atualizado.txt" \
     *      http://localhost:8080/api/mic/executar-ijvm
     * </pre>
     */
    @PostMapping("/executar-ijvm")
    public ResponseEntity<Map<String, Object>> executarIjvm(
            @RequestParam("instrucoes")    MultipartFile instrucoesFile,
            @RequestParam("registradores") MultipartFile registradoresFile,
            @RequestParam("memoria")       MultipartFile memoriaFile) {
        try {
            List<String> instrucoes = readLines(instrucoesFile);
            if (instrucoes.isEmpty()) return badRequest("Arquivo de instruções vazio");

            List<String> regLines = readLines(registradoresFile);
            Registradores regs = parseRegistradoresLines(regLines);
            Registradores regsIniciais = regs.copy();

            List<String> memoriaLines = readLines(memoriaFile);
            if (memoriaLines.isEmpty()) return badRequest("Arquivo de memória vazio");
            validarMemoria(memoriaLines);
            Memoria memoria = Memoria.carregar(memoriaLines);
            List<String> memoriaInicial = memoria.snapshot();

            List<BlocoInstrucao> blocos =
                tradutorService.montarEExecutarBlocos(instrucoes, regs, memoria);
            return ResponseEntity.ok(buildResponseIjvm(blocos, regsIniciais, memoriaInicial));

        } catch (Exception e) {
            return badRequest("Erro ao processar arquivos: " + e.getMessage());
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private List<String> readLines(MultipartFile file) throws Exception {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return r.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .collect(Collectors.toList());
        }
    }

    private void validarInstrucoes(List<String> instrucoes, int tamanho) {
        for (String instrucao : instrucoes) {
            if (!instrucao.matches("[01]{" + tamanho + "}")) {
                throw new IllegalArgumentException(
                    "instrução inválida: esperado binário de " + tamanho + " bits");
            }
        }
    }

    private void validarMemoria(List<String> linhas) {
        for (String linha : linhas) {
            if (!linha.matches("[01]{32}")) {
                throw new IllegalArgumentException(
                    "memória inválida: esperado uma palavra binária de 32 bits por linha");
            }
        }
    }

    /** Parses a list of "NAME=value" strings into a Registradores. */
    private Registradores parseRegistradoresLines(List<String> lines) {
        Map<String, Integer> m = new HashMap<>();
        for (String line : lines) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String name = parts[0].trim().toUpperCase();
                String val  = parts[1].trim();
                int parsed;
                if (val.startsWith("0x") || val.startsWith("0X")) {
                    parsed = (int) Long.parseLong(val.substring(2), 16);
                } else if (val.matches("[01]{8}") || val.matches("[01]{32}")) {
                    parsed = (int) Long.parseLong(val, 2);
                } else {
                    parsed = Integer.parseInt(val);
                }
                m.put(name, parsed);
            }
        }
        return registradoresFromMap(m);
    }

    /** Parses a JSON map of register names → values into a Registradores. */
    private Registradores parseRegistradoresMap(Map<String, Object> m) {
        Map<String, Integer> parsed = new HashMap<>();
        m.forEach((k, v) -> parsed.put(k.toUpperCase(), ((Number) v).intValue()));
        return registradoresFromMap(parsed);
    }

    private Registradores registradoresFromMap(Map<String, Integer> m) {
        return new Registradores(
            m.getOrDefault("H",   0),
            m.getOrDefault("OPC", 0),
            m.getOrDefault("TOS", 0),
            m.getOrDefault("CPP", 0),
            m.getOrDefault("LV",  0),
            m.getOrDefault("SP",  0),
            m.getOrDefault("PC",  0),
            m.getOrDefault("MDR", 0),
            m.getOrDefault("MAR", 0),
            m.getOrDefault("MBR", 0)
        );
    }

    /** Converts a Registradores snapshot to a JSON-serialisable map. */
    private Map<String, Object> regsToMap(Registradores r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("H",   r.getH());
        m.put("OPC", r.getOpc());
        m.put("TOS", r.getTos());
        m.put("CPP", r.getCpp());
        m.put("LV",  r.getLv());
        m.put("SP",  r.getSp());
        m.put("PC",  r.getPc());
        m.put("MDR", r.getMdr());
        m.put("MAR", r.getMar());
        m.put("MBR", r.getMbr());
        return m;
    }

    /**
     * Converts one cycle to a JSON-serialisable map.
     *
     * @param incluirMemoriaFim whether to carry the per-cycle memory snapshot;
     *                          the IJVM log reports memory once per high-level
     *                          instruction, so it does not need it per cycle
     */
    private Map<String, Object> cicloToMap(EstadoCiclo c, boolean incluirMemoriaFim) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ciclo",           c.getCiclo());
        entry.put("ir",              c.getIr());
        entry.put("busBRegistrador", c.getBusBRegistrador());
        entry.put("busCRegistradores", c.getBusCRegistradores());
        entry.put("sd",              c.getSd());
        entry.put("sdHex",           String.format("%08X", c.getSd()));
        entry.put("inicio",          regsToMap(c.getInicio()));
        entry.put("fim",             regsToMap(c.getFim()));
        if (c.getOperacaoMemoria() != null) {
            entry.put("operacaoMemoria", c.getOperacaoMemoria());
        }
        if (incluirMemoriaFim && c.getMemoriaFim() != null) {
            entry.put("memoriaFim", c.getMemoriaFim());
        }
        return entry;
    }

    private Map<String, Object> buildResponse(List<EstadoCiclo> log) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (EstadoCiclo c : log) {
            entries.add(cicloToMap(c, true));
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("log",             entries);
        resp.put("totalInstrucoes", log.size());
        return resp;
    }

    private Map<String, Object> buildResponse23(
            List<EstadoCiclo> log, Registradores regsIniciais, List<String> memoriaInicial) {
        Map<String, Object> resp = buildResponse(log);
        resp.put("registradoresIniciais", regsToMap(regsIniciais));
        resp.put("memoriaInicial", memoriaInicial);
        return resp;
    }

    /**
     * Builds the IJVM response: the initial state plus one block per high-level
     * instruction, each carrying its cycles and the memory left behind by that
     * instruction.
     */
    private Map<String, Object> buildResponseIjvm(
            List<BlocoInstrucao> blocos, Registradores regsIniciais, List<String> memoriaInicial) {
        List<Map<String, Object>> entries = new ArrayList<>();
        int totalCiclos = 0;
        for (BlocoInstrucao b : blocos) {
            List<Map<String, Object>> ciclos = new ArrayList<>();
            for (EstadoCiclo c : b.getCiclos()) {
                ciclos.add(cicloToMap(c, false));
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("instrucao",   b.getInstrucao());
            entry.put("ciclos",      ciclos);
            entry.put("memoriaApos", b.getMemoriaApos());
            entries.add(entry);
            totalCiclos += b.totalCiclos();
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("memoriaInicial",        memoriaInicial);
        resp.put("registradoresIniciais", regsToMap(regsIniciais));
        resp.put("blocos",                entries);
        resp.put("totalInstrucoes",       blocos.size());
        resp.put("totalCiclos",           totalCiclos);
        return resp;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String msg) {
        Map<String, Object> err = new HashMap<>();
        err.put("erro", msg);
        return ResponseEntity.badRequest().body(err);
    }
}
