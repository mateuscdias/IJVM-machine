package com.bisha.ijvm;

import com.bisha.ijvm.model.BlocoInstrucao;
import com.bisha.ijvm.model.EstadoCiclo;
import com.bisha.ijvm.model.EstadoULA;
import com.bisha.ijvm.model.Memoria;
import com.bisha.ijvm.model.Registradores;
import com.bisha.ijvm.service.MicService;
import com.bisha.ijvm.service.TradutorService;
import com.bisha.ijvm.service.UlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Project example acceptance tests")
class ExemplosProjetoAcceptanceTest {

    private UlaService ulaService;
    private MicService micService;
    private TradutorService tradutorService;

    @BeforeEach
    void setUp() {
        ulaService = new UlaService();
        micService = new MicService();
        ReflectionTestUtils.setField(micService, "ulaService", ulaService);
        tradutorService = new TradutorService();
        ReflectionTestUtils.setField(tradutorService, "micService", micService);
    }

    @Test
    @DisplayName("Stage 1 bundled program produces the reference ALU results")
    void etapa1() {
        List<EstadoULA> log = ulaService.processarPrograma(
            recurso("programa_etapa1.txt"), 0xFFFFFFFF, 1);

        assertEquals(List.of(0, 2, 1, -1),
            log.stream().map(EstadoULA::getS).toList());
        assertEquals(List.of(1, 0, 0, 0),
            log.stream().map(EstadoULA::getVaiUm).toList());
    }

    @Test
    @DisplayName("Stage 2 bundled program exercises both shifts and invalid controls")
    void etapa2Tarefa1() {
        List<EstadoULA> log = ulaService.processarPrograma8(
            recurso("programa_etapa2_tarefa1.txt"), 0x80000000, 1);

        assertEquals(0x00000100, log.get(0).getSd());
        assertEquals(0xC0000000, log.get(1).getSd());
        assertFalse(log.get(0).isInvalidSignals());
        assertTrue(log.get(2).isInvalidSignals());
    }

    @Test
    @DisplayName("Stage 3 bundled files execute READ and WRITE in order")
    void etapa3Tarefa1() {
        Registradores regs = registradores("registradores_etapa3_tarefa1.txt");
        Memoria memoria = Memoria.carregar(recurso("dados_etapa3_tarefa1.txt"));

        List<EstadoCiclo> log = micService.executarPrograma23(
            recurso("microinstruções_etapa3_tarefa1.txt"), regs, memoria);

        assertEquals(List.of("read", "write"),
            log.stream().map(EstadoCiclo::getOperacaoMemoria).toList());
        assertEquals(8, regs.getSp());
        assertEquals(8, regs.getMar());
        assertEquals(0, memoria.read(8));
    }

    @Test
    @DisplayName("Final bundled IJVM program translates, executes and groups its output")
    void entregavelIjvm() {
        Registradores regs = registradores("registradores_etapa3_atualizado.txt");
        Memoria memoria = Memoria.carregar(recurso("dados_etapa3_atualizado.txt"));

        List<BlocoInstrucao> blocos = tradutorService.montarEExecutarBlocos(
            recurso("instruções.txt"), regs, memoria);

        assertEquals(List.of("BIPUSH 00110011", "DUP", "ILOAD 1"),
            blocos.stream().map(BlocoInstrucao::getInstrucao).toList());
        assertEquals(List.of(3, 2, 5),
            blocos.stream().map(BlocoInstrucao::totalCiclos).toList());
        assertEquals(51, memoria.read(5));
        assertEquals(51, memoria.read(6));
        assertEquals(0, memoria.read(7));
        assertEquals(7, regs.getSp());
        assertEquals(0, regs.getTos());
        assertEquals(0x33, regs.getMbr());
    }

    private static List<String> recurso(String nome) {
        String caminho = "/exemplos_projeto/" + nome;
        InputStream input = Objects.requireNonNull(
            ExemplosProjetoAcceptanceTest.class.getResourceAsStream(caminho), caminho);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler " + caminho, e);
        }
    }

    private static Registradores registradores(String nome) {
        Registradores regs = new Registradores();
        for (String linha : recurso(nome)) {
            String[] partes = linha.split("=", 2);
            String registrador = partes[0].trim().toUpperCase();
            int valor = (int) Long.parseLong(partes[1].trim(), 2);
            switch (registrador) {
                case "H" -> regs.setH(valor);
                case "OPC" -> regs.setOpc(valor);
                case "TOS" -> regs.setTos(valor);
                case "CPP" -> regs.setCpp(valor);
                case "LV" -> regs.setLv(valor);
                case "SP" -> regs.setSp(valor);
                case "PC" -> regs.setPc(valor);
                case "MDR" -> regs.setMdr(valor);
                case "MAR" -> regs.setMar(valor);
                case "MBR" -> regs.setMbr(valor);
                default -> throw new IllegalArgumentException("Registrador desconhecido: " + registrador);
            }
        }
        return regs;
    }
}
