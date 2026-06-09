package com.bisha.ijvm;

import com.bisha.ijvm.controller.UlaController;
import com.bisha.ijvm.service.UlaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the ULA REST Controller.
 *
 * <p>This test class verifies the HTTP endpoints for program execution
 * including JSON payload processing and file upload functionality.</p>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 */
@WebMvcTest(UlaController.class)
@DisplayName("ULA Controller Tests")
class UlaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UlaService ulaService;

    @Test
    @DisplayName("POST /api/ula/executar should return 200 with valid instructions")
    void testExecutarProgramaSuccess() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("instrucoes", List.of("111100", "011100"));

        when(ulaService.processarPrograma(anyList(), anyInt(), anyInt()))
            .thenReturn(List.of());

        String jsonContent = Objects.requireNonNull(
            objectMapper.writeValueAsString(request),
            "JSON content cannot be null"
        );

        mockMvc.perform(post("/api/ula/executar")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON, "MediaType cannot be null"))
                .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.log").exists())
                .andExpect(jsonPath("$.totalInstrucoes").exists());
    }

    @Test
    @DisplayName("POST /api/ula/executar should return 400 with empty instructions")
    void testExecutarProgramaEmptyInstructions() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("instrucoes", List.of());

        String jsonContent = Objects.requireNonNull(
            objectMapper.writeValueAsString(request),
            "JSON content cannot be null"
        );

        mockMvc.perform(post("/api/ula/executar")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON, "MediaType cannot be null"))
                .content(jsonContent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Nenhuma instrução fornecida"));
    }

    @Test
    @DisplayName("POST /api/ula/executar should return 400 with missing instrucoes field")
    void testExecutarProgramaMissingField() throws Exception {
        Map<String, Object> request = new HashMap<>();

        String jsonContent = Objects.requireNonNull(
            objectMapper.writeValueAsString(request),
            "JSON content cannot be null"
        );

        mockMvc.perform(post("/api/ula/executar")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON, "MediaType cannot be null"))
                .content(jsonContent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Nenhuma instrução fornecida"));
    }
}
