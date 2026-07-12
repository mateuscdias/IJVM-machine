package com.bisha.ijvm;

import com.bisha.ijvm.model.BlocoInstrucao;
import com.bisha.ijvm.model.EstadoCiclo;
import com.bisha.ijvm.model.Memoria;
import com.bisha.ijvm.model.Registradores;
import com.bisha.ijvm.service.MicService;
import com.bisha.ijvm.service.TradutorService;
import com.bisha.ijvm.service.UlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tradutor Service Tests")
class TradutorServiceTest {

    private TradutorService tradutorService;

    @BeforeEach
    void setUp() {
        MicService micService = new MicService();
        ReflectionTestUtils.setField(micService, "ulaService", new UlaService());

        tradutorService = new TradutorService();
        ReflectionTestUtils.setField(tradutorService, "micService", micService);
    }

    @Test
    @DisplayName("BIPUSH, DUP and ILOAD run end to end against registers and memory")
    void montarEExecutarProgramaCompleto() {
        // LV=2, SP=7, TOS=0, remaining registers zeroed
        Registradores regs = new Registradores(0, 0, 0, 0, 2, 7, 0, 0, 0, 0);

        int[] palavras = new int[16];
        palavras[2] = 100;
        palavras[3] = 200;
        palavras[4] = 300;
        Memoria memoria = new Memoria(palavras);

        List<EstadoCiclo> log = tradutorService.montarEExecutar(
            List.of("BIPUSH 00000101", "DUP", "ILOAD 1"), regs, memoria);

        // 3 (BIPUSH) + 2 (DUP) + 5 (ILOAD 1) microinstructions
        assertEquals(10, log.size());

        assertEquals(10, regs.getSp());
        assertEquals(200, regs.getTos());
        assertEquals(3, regs.getH());
        assertEquals(0b00000101, regs.getMbr());

        assertEquals(5, memoria.read(8));    // BIPUSH pushed the literal 5
        assertEquals(5, memoria.read(9));    // DUP duplicated the top of the stack
        assertEquals(200, memoria.read(10)); // ILOAD 1 pushed mem[LV+1] = mem[3]

        // the local variables the program read must be left untouched
        assertEquals(100, memoria.read(2));
        assertEquals(200, memoria.read(3));
        assertEquals(300, memoria.read(4));
    }

    @Test
    @DisplayName("Cycles come grouped per IJVM instruction, each with the memory it left behind")
    void montarEExecutarBlocosAgrupaPorInstrucao() {
        Registradores regs = new Registradores(0, 0, 0, 0, 2, 7, 0, 0, 0, 0);

        int[] palavras = new int[16];
        palavras[2] = 100;
        palavras[3] = 200;
        palavras[4] = 300;
        Memoria memoria = new Memoria(palavras);

        List<BlocoInstrucao> blocos = tradutorService.montarEExecutarBlocos(
            List.of("BIPUSH 00000101", "DUP", "ILOAD 1"), regs, memoria);

        assertEquals(3, blocos.size());

        BlocoInstrucao bipush = blocos.get(0);
        assertEquals("BIPUSH 00000101", bipush.getInstrucao());
        assertEquals(3, bipush.totalCiclos());
        assertEquals(5, palavraEm(bipush, 8));      // the literal is already on the stack
        assertEquals(0, palavraEm(bipush, 9));      // DUP has not run yet
        assertEquals(0, palavraEm(bipush, 10));

        BlocoInstrucao dup = blocos.get(1);
        assertEquals("DUP", dup.getInstrucao());
        assertEquals(2, dup.totalCiclos());
        assertEquals(5, palavraEm(dup, 8));
        assertEquals(5, palavraEm(dup, 9));         // top of stack duplicated
        assertEquals(0, palavraEm(dup, 10));

        BlocoInstrucao iload = blocos.get(2);
        assertEquals("ILOAD 1", iload.getInstrucao());
        assertEquals(5, iload.totalCiclos());
        assertEquals(5, palavraEm(iload, 8));
        assertEquals(5, palavraEm(iload, 9));
        assertEquals(200, palavraEm(iload, 10));    // mem[LV+1] pushed

        // cycle numbering is continuous across the whole program, 1..10
        List<Integer> ciclos = new ArrayList<>();
        for (BlocoInstrucao bloco : blocos) {
            for (EstadoCiclo ciclo : bloco.getCiclos()) {
                ciclos.add(ciclo.getCiclo());
            }
        }
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), ciclos);

        // the fetch cycle of BIPUSH writes no register through bus C
        EstadoCiclo fetch = bipush.getCiclos().get(1);
        assertEquals("fetch", fetch.getOperacaoMemoria());
        assertTrue(fetch.getBusCRegistradores().isEmpty());
    }

    @Test
    @DisplayName("ILOAD emits one H=H+1 per unit of its argument")
    void traduzirIloadContagemDinamica() {
        List<String> iload0 = tradutorService.traduzir(List.of("ILOAD 0"));
        assertEquals(4, iload0.size());
        assertEquals(0, contarOcorrencias(iload0, TradutorService.H_RECEBE_H_MAIS_1));

        List<String> iload3 = tradutorService.traduzir(List.of("ILOAD 3"));
        assertEquals(7, iload3.size());
        assertEquals(3, contarOcorrencias(iload3, TradutorService.H_RECEBE_H_MAIS_1));

        assertEquals(
            List.of(
                TradutorService.H_RECEBE_LV,
                TradutorService.H_RECEBE_H_MAIS_1,
                TradutorService.H_RECEBE_H_MAIS_1,
                TradutorService.H_RECEBE_H_MAIS_1,
                TradutorService.MAR_RECEBE_H_RD,
                TradutorService.MAR_SP_RECEBE_SP_MAIS_1_WR,
                TradutorService.TOS_RECEBE_MDR),
            iload3);
    }

    @Test
    @DisplayName("DUP and BIPUSH expand to their microinstruction sequences")
    void traduzirDupEBipush() {
        assertEquals(
            List.of(
                TradutorService.MAR_SP_RECEBE_SP_MAIS_1,
                TradutorService.MDR_RECEBE_TOS_WR),
            tradutorService.traduzir(List.of("DUP")));

        assertEquals(
            List.of(
                TradutorService.MAR_SP_RECEBE_SP_MAIS_1,
                "00110011" + "000000000" + "11" + "0000",
                TradutorService.MDR_TOS_RECEBE_H_WR),
            tradutorService.traduzir(List.of("BIPUSH 00110011")));
    }

    @Test
    @DisplayName("Every microinstruction produced is a 23-bit binary string")
    void traduzirProduzMicroinstrucoesDe23Bits() {
        List<String> micro = tradutorService.traduzir(
            List.of("BIPUSH 00000101", "DUP", "ILOAD 2"));

        for (String instrucao : micro) {
            assertTrue(instrucao.matches("[01]{23}"),
                "microinstrução fora do formato de 23 bits: " + instrucao);
        }
    }

    @Test
    @DisplayName("Invalid programs are rejected with a clear message")
    void traduzirRejeitaEntradaInvalida() {
        assertThrows(IllegalArgumentException.class,
            () -> tradutorService.traduzir(List.of("ISTORE 1")));   // unknown opcode
        assertThrows(IllegalArgumentException.class,
            () -> tradutorService.traduzir(List.of("BIPUSH 101"))); // not 8 bits
        assertThrows(IllegalArgumentException.class,
            () -> tradutorService.traduzir(List.of("BIPUSH 00000002"))); // not binary
        assertThrows(IllegalArgumentException.class,
            () -> tradutorService.traduzir(List.of("ILOAD -1")));   // negative index
        assertThrows(IllegalArgumentException.class,
            () -> tradutorService.traduzir(List.of("ILOAD")));      // missing argument
        assertThrows(IllegalArgumentException.class,
            () -> tradutorService.traduzir(List.of("DUP 1")));      // unexpected argument
    }

    @Test
    @DisplayName("Blank lines and comments are ignored")
    void traduzirIgnoraLinhasVaziasEComentarios() {
        assertEquals(
            tradutorService.traduzir(List.of("DUP")),
            tradutorService.traduzir(List.of("# empilha de novo o topo", "", "  DUP  ")));
    }

    private long contarOcorrencias(List<String> microinstrucoes, String alvo) {
        return microinstrucoes.stream().filter(alvo::equals).count();
    }

    /** Reads word {@code endereco} out of the memory snapshot carried by a block. */
    private int palavraEm(BlocoInstrucao bloco, int endereco) {
        return Integer.parseInt(bloco.getMemoriaApos().get(endereco), 2);
    }
}
