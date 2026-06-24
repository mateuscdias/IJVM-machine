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

@RestController
@RequestMapping("/api/ula")
public class UlaController {

    @Autowired
    private UlaService ulaService;

    public UlaController() {}

    // =========================================================================
    // 6-bit legacy endpoints (unchanged)
    // =========================================================================

    @PostMapping("/executar")
    public ResponseEntity<Map<String, Object>> executarPrograma(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> instrucoes = (List<String>) request.get("instrucoes");

        if (instrucoes == null || instrucoes.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("erro", "Nenhuma instrução fornecida");
            return ResponseEntity.badRequest().body(error);
        }

        int initialA = 0xFFFFFFFF;
        int initialB = 0x00000001;

        List<EstadoULA> log = ulaService.processarPrograma(instrucoes, initialA, initialB);
        return ResponseEntity.ok(buildResponse6(log));
    }

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
            return ResponseEntity.ok(buildResponse6(log));

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("erro", "Erro ao processar arquivo: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // =========================================================================
    // 8-bit extended endpoints
    // =========================================================================

    @PostMapping("/executar8")
    public ResponseEntity<Map<String, Object>> executarPrograma8(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> instrucoes = (List<String>) request.get("instrucoes");

        if (instrucoes == null || instrucoes.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("erro", "Nenhuma instrução fornecida");
            return ResponseEntity.badRequest().body(error);
        }

        int initialA = request.containsKey("a") ? ((Number) request.get("a")).intValue() : 0x80000000;
        int initialB = request.containsKey("b") ? ((Number) request.get("b")).intValue() : 0x00000001;

        List<EstadoULA> log = ulaService.processarPrograma8(instrucoes, initialA, initialB);
        return ResponseEntity.ok(buildResponse8(log, initialA, initialB));
    }

    @PostMapping("/executar-arquivo8")
    public ResponseEntity<Map<String, Object>> executarArquivo8(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "a", required = false) Integer initialAParam,
            @RequestParam(value = "b", required = false) Integer initialBParam) {

        int initialA = (initialAParam != null) ? initialAParam : 0x80000000;
        int initialB = (initialBParam != null) ? initialBParam : 0x00000001;

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

            List<EstadoULA> log = ulaService.processarPrograma8(instrucoes, initialA, initialB);
            return ResponseEntity.ok(buildResponse8(log, initialA, initialB));

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("erro", "Erro ao processar arquivo: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Map<String, Object> buildResponse6(List<EstadoULA> log) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (EstadoULA e : log) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("pc",    e.getPc());
            entry.put("ir",    e.getIr());
            entry.put("irBin", e.getIRBin());
            entry.put("a",     e.getA());
            entry.put("aHex",  e.getAHex());
            entry.put("aBin",  e.getABin());
            entry.put("b",     e.getB());
            entry.put("bHex",  e.getBHex());
            entry.put("bBin",  e.getBBin());
            entry.put("s",     e.getS());
            entry.put("sHex",  e.getSHex());
            entry.put("sBin",  e.getSBin());
            entry.put("vaiUm", e.getVaiUm());
            entries.add(entry);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("log", entries);
        response.put("totalInstrucoes", log.size());
        return response;
    }

    private Map<String, Object> buildResponse8(List<EstadoULA> log, int initialA, int initialB) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (EstadoULA e : log) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("pc",             e.getPc());
            entry.put("ir",             e.getIr());
            entry.put("irBin",          e.getIRBin());
            entry.put("invalidSignals", e.isInvalidSignals());
            entry.put("a",              e.getA());
            entry.put("aHex",           e.getAHex());
            entry.put("aBin",           e.getABin());
            entry.put("b",              e.getB());
            entry.put("bHex",           e.getBHex());
            entry.put("bBin",           e.getBBin());
            entry.put("s",              e.getS());
            entry.put("sHex",           e.getSHex());
            entry.put("sBin",           e.getSBin());
            entry.put("sd",             e.getSd());
            entry.put("sdHex",          e.getSdHex());
            entry.put("sdBin",          e.getSdBin());
            entry.put("n",              e.getN());
            entry.put("z",              e.getZ());
            entry.put("vaiUm",          e.getVaiUm());
            entries.add(entry);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("log",             entries);
        response.put("totalInstrucoes", log.size());
        response.put("initialA",        initialA);
        response.put("initialB",        initialB);
        return response;
    }
}