package com.bisha.ijvm;

import com.bisha.ijvm.controller.MicController;
import com.bisha.ijvm.service.MicService;
import com.bisha.ijvm.service.TradutorService;
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

        TradutorService tradutorService = new TradutorService();
        ReflectionTestUtils.setField(tradutorService, "micService", micService);

        MicController controller = new MicController();
        ReflectionTestUtils.setField(controller, "micService", micService);
        ReflectionTestUtils.setField(controller, "tradutorService", tradutorService);

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

    @Test
    @DisplayName("POST /api/mic/executar-ijvm returns one block per IJVM instruction")
    void executarIjvmReturnsBlocksPerInstruction() throws Exception {
        MockMultipartFile instrucoes = textFile("instrucoes",
            "BIPUSH 00000101\n" +
            "DUP\n" +
            "ILOAD 1\n");
        MockMultipartFile registradores = textFile("registradores",
            "lv = 00000000000000000000000000000010\n" +
            "sp = 00000000000000000000000000000111\n" +
            "tos = 00000000000000000000000000000000\n");
        MockMultipartFile memoria = textFile("memoria", memoriaInicial());

        mockMvc.perform(multipart("/api/mic/executar-ijvm")
                .file(instrucoes)
                .file(registradores)
                .file(memoria))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registradoresIniciais.LV").value(2))
            .andExpect(jsonPath("$.registradoresIniciais.SP").value(7))
            .andExpect(jsonPath("$.memoriaInicial[3]").value(palavra(200)))
            .andExpect(jsonPath("$.totalInstrucoes").value(3))
            .andExpect(jsonPath("$.totalCiclos").value(10))
            // one block per IJVM instruction, cycles numbered continuously
            .andExpect(jsonPath("$.blocos[0].instrucao").value("BIPUSH 00000101"))
            .andExpect(jsonPath("$.blocos[0].ciclos.length()").value(3))
            .andExpect(jsonPath("$.blocos[0].ciclos[0].ciclo").value(1))
            .andExpect(jsonPath("$.blocos[0].ciclos[1].operacaoMemoria").value("fetch"))
            .andExpect(jsonPath("$.blocos[1].instrucao").value("DUP"))
            .andExpect(jsonPath("$.blocos[1].ciclos.length()").value(2))
            .andExpect(jsonPath("$.blocos[2].instrucao").value("ILOAD 1"))
            .andExpect(jsonPath("$.blocos[2].ciclos.length()").value(5))
            .andExpect(jsonPath("$.blocos[2].ciclos[4].ciclo").value(10))
            // memory is reported once per instruction, reflecting its end state
            .andExpect(jsonPath("$.blocos[0].memoriaApos[8]").value(palavra(5)))
            .andExpect(jsonPath("$.blocos[0].memoriaApos[9]").value(palavra(0)))
            .andExpect(jsonPath("$.blocos[1].memoriaApos[9]").value(palavra(5)))
            .andExpect(jsonPath("$.blocos[2].memoriaApos[10]").value(palavra(200)))
            // the per-cycle memory snapshot is left out of this response
            .andExpect(jsonPath("$.blocos[0].ciclos[0].memoriaFim").doesNotExist())
            .andExpect(jsonPath("$.blocos[2].ciclos[4].fim.TOS").value(200))
            .andExpect(jsonPath("$.blocos[2].ciclos[4].fim.SP").value(10));
    }

    /** 16 words with mem[2]=100, mem[3]=200, mem[4]=300 and the rest zeroed. */
    private static String memoriaInicial() {
        int[] palavras = new int[16];
        palavras[2] = 100;
        palavras[3] = 200;
        palavras[4] = 300;

        StringBuilder sb = new StringBuilder();
        for (int p : palavras) {
            sb.append(palavra(p)).append("\n");
        }
        return sb.toString();
    }

    /** Formats a word as a 32-bit binary string, as the memory file expects. */
    private static String palavra(int valor) {
        return String.format("%32s", Integer.toBinaryString(valor)).replace(' ', '0');
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
