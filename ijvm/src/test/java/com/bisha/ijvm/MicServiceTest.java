package com.bisha.ijvm;

import com.bisha.ijvm.model.EstadoCiclo;
import com.bisha.ijvm.model.Memoria;
import com.bisha.ijvm.model.Registradores;
import com.bisha.ijvm.service.MicService;
import com.bisha.ijvm.service.UlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MIC Service Tests")
class MicServiceTest {

    private MicService micService;

    @BeforeEach
    void setUp() {
        micService = new MicService();
        ReflectionTestUtils.setField(micService, "ulaService", new UlaService());
    }

    @Test
    @DisplayName("23-bit READ uses MAR after bus C writes and stores memory word in MDR")
    void executarInstrucao23ReadAfterBusCWrite() {
        Registradores regs = new Registradores(0, 0, 0, 0, 0, 4, 0, 0, 4, 0);
        Memoria memoria = new Memoria(new int[] {0, 0, 0, 0, 2, 0, 0, 0});

        EstadoCiclo ciclo = micService.executarInstrucao23(
            "00110101000001000010100", 1, regs, memoria);

        assertEquals(5, regs.getSp());
        assertEquals(4, regs.getMar());
        assertEquals(2, regs.getMdr());
        assertEquals("read", ciclo.getOperacaoMemoria());
        assertEquals("sp", ciclo.getBusBRegistrador());
        assertEquals(List.of("sp"), ciclo.getBusCRegistradores());
        assertEquals(memoria.snapshot(), ciclo.getMemoriaFim());
        assertEquals(4, ciclo.getInicio().getSp());
        assertEquals(5, ciclo.getFim().getSp());
    }

    @Test
    @DisplayName("23-bit WRITE uses MAR and MDR after bus C writes")
    void executarInstrucao23WriteAfterBusCWrite() {
        Registradores regs = new Registradores(0, 0, 0, 0, 0, 5, 0, 2, 4, 0);
        Memoria memoria = new Memoria(new int[] {0, 0, 0, 0, 2, 0, 0, 0});

        EstadoCiclo ciclo = micService.executarInstrucao23(
            "00110100000000001100100", 2, regs, memoria);

        assertEquals(5, regs.getSp());
        assertEquals(5, regs.getMar());
        assertEquals(2, regs.getMdr());
        assertEquals(2, memoria.read(5));
        assertEquals("write", ciclo.getOperacaoMemoria());
        assertEquals("sp", ciclo.getBusBRegistrador());
        assertEquals(List.of("mar"), ciclo.getBusCRegistradores());
        assertEquals(memoria.snapshot(), ciclo.getMemoriaFim());
    }

    @Test
    @DisplayName("23-bit FETCH loads high byte into MBR and zero-extended H without touching memory")
    void executarInstrucao23FetchBypassesAluAndMemory() {
        Registradores regs = new Registradores(0, 0, 0, 0, 0, 4, 0, 7, 3, 0);
        Memoria memoria = new Memoria(new int[] {0, 1, 2, 3, 4, 5, 6, 7});
        List<String> memoriaAntes = memoria.snapshot();

        EstadoCiclo ciclo = micService.executarInstrucao23(
            "10000000000000000110000", 3, regs, memoria);

        assertEquals(0x80, regs.getMbr());
        assertEquals(0x80, regs.getH());
        assertEquals(7, regs.getMdr());
        assertEquals(3, regs.getMar());
        assertEquals(memoriaAntes, memoria.snapshot());
        assertEquals("(none)", ciclo.getBusBRegistrador());
        assertTrue(ciclo.getBusCRegistradores().isEmpty());
        assertEquals(0, ciclo.getSd());
        assertEquals("fetch", ciclo.getOperacaoMemoria());
        assertEquals(memoriaAntes, ciclo.getMemoriaFim());
    }

    @Test
    @DisplayName("Bus B decoder sign-extends MBR and zero-extends MBRU")
    void decodeBusBUsesMbrAndMbruExtensionFromPdf() {
        Registradores regs = new Registradores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0x80);

        assertEquals(0xFFFFFF80, micService.decodeBusB(2, regs));
        assertEquals(0x00000080, micService.decodeBusB(3, regs));
    }
}
