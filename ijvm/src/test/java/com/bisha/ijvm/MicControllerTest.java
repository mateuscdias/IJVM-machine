package com.bisha.ijvm;

import com.bisha.ijvm.controller.MicController;
import com.bisha.ijvm.service.MicService;
import com.bisha.ijvm.service.UlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MIC Controller Tests")
class MicControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MicService micService = new MicService();
        ReflectionTestUtils.setField(micService, "ulaService", new UlaService());

        MicController controller = new MicController();
        ReflectionTestUtils.setField(controller, "micService", micService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/mic/executar-arquivo23 returns memory-aware log")
    void executarArquivo23ReturnsMemoryAwareLog() throws Exception {
        MockMultipartFile programa = textFile("programa", "00110101000001000010100\n");
        MockMultipartFile registradores = textFile("registradores",
            "mar = 00000000000000000000000000000100\n" +
            "mdr = 00000000000000000000000000000000\n" +
            "pc = 00000000000000000000000000000000\n" +
            "mbr = 00000000\n" +
            "sp = 00000000000000000000000000000100\n" +
            "lv = 00000000000000000000000000000000\n" +
            "cpp = 00000000000000000000000000000000\n" +
            "tos = 00000000000000000000000000000000\n" +
            "opc = 00000000000000000000000000000000\n" +
            "h = 00000000000000000000000000000000\n");
        MockMultipartFile memoria = textFile("memoria",
            "00000000000000000000000000000000\n" +
            "00000000000000000000000000000000\n" +
            "00000000000000000000000000000000\n" +
            "00000000000000000000000000000000\n" +
            "00000000000000000000000000000010\n");

        mockMvc.perform(multipart("/api/mic/executar-arquivo23")
                .file(programa)
                .file(registradores)
                .file(memoria))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registradoresIniciais.MAR").value(4))
            .andExpect(jsonPath("$.memoriaInicial[4]").value("00000000000000000000000000000010"))
            .andExpect(jsonPath("$.totalInstrucoes").value(1))
            .andExpect(jsonPath("$.log[0].fim.SP").value(5))
            .andExpect(jsonPath("$.log[0].fim.MDR").value(2))
            .andExpect(jsonPath("$.log[0].operacaoMemoria").value("read"))
            .andExpect(jsonPath("$.log[0].memoriaFim[4]").value("00000000000000000000000000000010"));
    }

    private static MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile(
            name,
            name + ".txt",
            "text/plain",
            content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
