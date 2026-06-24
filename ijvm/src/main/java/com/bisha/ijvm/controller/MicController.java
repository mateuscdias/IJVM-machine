package com.bisha.ijvm.controller;

import com.bisha.ijvm.model.EstadoCiclo;
import com.bisha.ijvm.model.Registradores;
import com.bisha.ijvm.service.MicService;
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
 * REST Controller for MIC-1 21-bit instruction execution.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/mic/executar         – JSON payload (instructions + registers)</li>
 *   <li>POST /api/mic/executar-arquivo – multipart: program file + register file</li>
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

    private Map<String, Object> buildResponse(List<EstadoCiclo> log) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (EstadoCiclo c : log) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ciclo",           c.getCiclo());
            entry.put("ir",              c.getIr());
            entry.put("busBRegistrador", c.getBusBRegistrador());
            entry.put("busCRegistradores", c.getBusCRegistradores());
            entry.put("sd",              c.getSd());
            entry.put("sdHex",           String.format("%08X", c.getSd()));
            entry.put("inicio",          regsToMap(c.getInicio()));
            entry.put("fim",             regsToMap(c.getFim()));
            entries.add(entry);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("log",             entries);
        resp.put("totalInstrucoes", log.size());
        return resp;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String msg) {
        Map<String, Object> err = new HashMap<>();
        err.put("erro", msg);
        return ResponseEntity.badRequest().body(err);
    }
}