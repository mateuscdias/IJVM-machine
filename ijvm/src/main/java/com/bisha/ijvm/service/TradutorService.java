package com.bisha.ijvm.service;

import com.bisha.ijvm.model.BlocoInstrucao;
import com.bisha.ijvm.model.EstadoCiclo;
import com.bisha.ijvm.model.Memoria;
import com.bisha.ijvm.model.Registradores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates high-level IJVM instructions into 23-bit MIC-1 microinstructions
 * and hands the result to {@link MicService} for execution.
 *
 * <h2>Supported IJVM instructions</h2>
 * <pre>
 *   ILOAD x      – pushes the local variable at LV+x onto the stack (x &gt;= 0)
 *   DUP          – duplicates the word currently on top of the stack
 *   BIPUSH byte  – pushes an 8-bit literal, written as 8 binary digits
 * </pre>
 *
 * <h2>Microinstruction sequences</h2>
 * <pre>
 *   ILOAD x  → H=LV, (H=H+1) × x, MAR=H;rd, MAR=SP=SP+1;wr, TOS=MDR
 *   DUP      → MAR=SP=SP+1, MDR=TOS;wr
 *   BIPUSH b → MAR=SP=SP+1, fetch(b), MDR=TOS=H;wr
 * </pre>
 * The number of {@code H=H+1} microinstructions emitted for an ILOAD is dynamic
 * and equal to its argument, so {@code ILOAD 0} expands to 4 microinstructions
 * and {@code ILOAD 3} to 7.
 *
 * <p>Every microinstruction produced here follows the 23-bit format documented
 * in {@link MicService}: 8 bits of ULA control, a 9-bit bus C selector, 2 bits
 * of memory control and a 4-bit bus B decoder value.</p>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 */
@Service
public class TradutorService {

    @Autowired
    private MicService micService;

    public TradutorService() {}

    // ── Microinstruction table ───────────────────────────────────────────────

    /**
     * {@code H = LV} — bus B drives LV through the ULA unchanged and bus C
     * writes the result into H. No memory operation.
     */
    public static final String H_RECEBE_LV = "00110100100000000000101";

    /**
     * {@code H = H + 1} — the ULA increments A (always H) and bus C writes the
     * result back into H. Emitted once per unit of the ILOAD argument so that H
     * ends up holding LV+x. No memory operation.
     */
    public static final String H_RECEBE_H_MAIS_1 = "00111001100000000000000";

    /**
     * {@code MAR = H; rd} — bus C copies H into MAR and the memory then reads,
     * leaving {@code MDR = mem[MAR]}. Memory control "01" (READ).
     */
    public static final String MAR_RECEBE_H_RD = "00111000000000001010000";

    /**
     * {@code MAR = SP = SP + 1; wr} — SP is incremented and the new value goes
     * to both SP and MAR, then the memory writes {@code mem[MAR] = MDR}.
     * Memory control "10" (WRITE).
     */
    public static final String MAR_SP_RECEBE_SP_MAIS_1_WR = "00110101000001001100100";

    /**
     * {@code TOS = MDR} — bus B drives MDR through the ULA unchanged and bus C
     * writes it into TOS, refreshing the top-of-stack cache. No memory operation.
     */
    public static final String TOS_RECEBE_MDR = "00110100001000000000000";

    /**
     * {@code MAR = SP = SP + 1} — same stack-pointer increment as
     * {@link #MAR_SP_RECEBE_SP_MAIS_1_WR}, but with no memory operation: it only
     * opens the new stack slot, which a later microinstruction fills.
     */
    public static final String MAR_SP_RECEBE_SP_MAIS_1 = "00110101000001001000100";

    /**
     * {@code MDR = TOS; wr} — bus B drives TOS through the ULA unchanged, bus C
     * writes it into MDR and the memory then stores it at MAR. Used by DUP.
     * Memory control "10" (WRITE).
     */
    public static final String MDR_RECEBE_TOS_WR = "00110100000000010101000";

    /**
     * {@code MDR = TOS = H; wr} — the ULA passes A (always H) through, bus C
     * writes it into both MDR and TOS, and the memory stores it at MAR. Used by
     * BIPUSH to push the literal that {@code fetch} left in H.
     * Memory control "10" (WRITE).
     */
    public static final String MDR_TOS_RECEBE_H_WR = "00111000001000010100000";

    /** Bus C selector, memory control and bus B fields of a {@code fetch}. */
    private static final String SUFIXO_FETCH = "000000000" + "11" + "0000";

    /**
     * Builds the {@code fetch} microinstruction that loads an 8-bit literal.
     *
     * <p>The literal occupies the high 8 bits of the microinstruction and the
     * memory control field is "11", which {@link MicService} treats as the FETCH
     * special case: the ULA and the data memory are bypassed, MBR receives those
     * 8 bits and H receives the same value zero-extended to 32 bits.</p>
     *
     * @param bits 8-character binary string holding the literal
     * @return the 23-bit fetch microinstruction
     */
    public static String fetch(String bits) {
        return bits + SUFIXO_FETCH;
    }

    // ── Translation ──────────────────────────────────────────────────────────

    /**
     * Translates a list of IJVM instruction lines into the 23-bit
     * microinstructions that implement them, in execution order.
     *
     * <p>Blank lines and lines starting with {@code #} are ignored. The opcode
     * is case-insensitive.</p>
     *
     * @param linhas one IJVM instruction per line
     * @return the microinstructions, in order
     * @throws IllegalArgumentException if a line holds an unknown opcode or an
     *                                  argument that is not valid for it
     */
    public List<String> traduzir(List<String> linhas) {
        List<String> microinstrucoes = new ArrayList<>();
        int numeroLinha = 0;
        for (String linha : linhas) {
            numeroLinha++;
            String limpa = linha.trim();
            if (limpa.isEmpty() || limpa.startsWith("#")) continue;
            microinstrucoes.addAll(traduzirInstrucao(limpa, numeroLinha));
        }
        return microinstrucoes;
    }

    /**
     * Translates a single IJVM instruction into its microinstruction sequence.
     *
     * @param linha       the instruction, already trimmed and non-empty
     * @param numeroLinha 1-indexed line number, quoted in error messages
     * @return the microinstructions implementing this instruction
     */
    private List<String> traduzirInstrucao(String linha, int numeroLinha) {
        String[] partes = linha.split("\\s+");
        String opcode = partes[0].toUpperCase();

        switch (opcode) {
            case "ILOAD":
                return traduzirIload(exigirArgumento(partes, opcode, numeroLinha), numeroLinha);
            case "DUP":
                exigirSemArgumento(partes, opcode, numeroLinha);
                return traduzirDup();
            case "BIPUSH":
                return traduzirBipush(exigirArgumento(partes, opcode, numeroLinha), numeroLinha);
            default:
                throw new IllegalArgumentException(
                    "linha " + numeroLinha + ": opcode desconhecido: " + partes[0]);
        }
    }

    /**
     * {@code ILOAD x} — walks H from LV up to LV+x, reads the local variable
     * from that address and pushes it onto the stack.
     */
    private List<String> traduzirIload(String argumento, int numeroLinha) {
        int x;
        try {
            x = Integer.parseInt(argumento);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "linha " + numeroLinha + ": ILOAD espera um índice inteiro, recebeu: " + argumento);
        }
        if (x < 0) {
            throw new IllegalArgumentException(
                "linha " + numeroLinha + ": ILOAD espera um índice não negativo, recebeu: " + x);
        }

        List<String> micro = new ArrayList<>();
        micro.add(H_RECEBE_LV);
        for (int i = 0; i < x; i++) {
            micro.add(H_RECEBE_H_MAIS_1);
        }
        micro.add(MAR_RECEBE_H_RD);
        micro.add(MAR_SP_RECEBE_SP_MAIS_1_WR);
        micro.add(TOS_RECEBE_MDR);
        return micro;
    }

    /** {@code DUP} — opens a new stack slot and writes the cached TOS into it. */
    private List<String> traduzirDup() {
        return List.of(
            MAR_SP_RECEBE_SP_MAIS_1,
            MDR_RECEBE_TOS_WR
        );
    }

    /**
     * {@code BIPUSH b} — opens a new stack slot, fetches the 8-bit literal into
     * H and writes it to the top of the stack.
     */
    private List<String> traduzirBipush(String argumento, int numeroLinha) {
        if (!argumento.matches("[01]{8}")) {
            throw new IllegalArgumentException(
                "linha " + numeroLinha + ": BIPUSH espera um byte de 8 bits binários, recebeu: "
                + argumento);
        }
        return List.of(
            MAR_SP_RECEBE_SP_MAIS_1,
            fetch(argumento),
            MDR_TOS_RECEBE_H_WR
        );
    }

    /** Returns the single argument of {@code opcode}, or fails if it is absent. */
    private String exigirArgumento(String[] partes, String opcode, int numeroLinha) {
        if (partes.length != 2) {
            throw new IllegalArgumentException(
                "linha " + numeroLinha + ": " + opcode + " espera exatamente um argumento");
        }
        return partes[1];
    }

    /** Fails if {@code opcode}, which takes no argument, was given one. */
    private void exigirSemArgumento(String[] partes, String opcode, int numeroLinha) {
        if (partes.length != 1) {
            throw new IllegalArgumentException(
                "linha " + numeroLinha + ": " + opcode + " não aceita argumentos");
        }
    }

    // ── Orchestration ────────────────────────────────────────────────────────

    /**
     * Translates an IJVM program and executes it on the MIC-1, mutating
     * {@code regs} and {@code memoria} across the cycles, keeping the result
     * grouped by high-level instruction.
     *
     * <p>Each block carries the cycles of the microinstructions that implement
     * one IJVM instruction plus the data memory as it stood once that
     * instruction finished. Cycle numbering is continuous across the whole
     * program: it does not restart at each instruction.</p>
     *
     * @param linhas   one IJVM instruction per line
     * @param regs     initial register file (mutated)
     * @param memoria  initial data memory (mutated)
     * @return one block per IJVM instruction, in program order
     * @throws IllegalArgumentException if the program fails to translate
     */
    public List<BlocoInstrucao> montarEExecutarBlocos(
            List<String> linhas, Registradores regs, Memoria memoria) {
        List<BlocoInstrucao> blocos = new ArrayList<>();
        int ciclo = 1;
        int numeroLinha = 0;

        for (String linha : linhas) {
            numeroLinha++;
            String limpa = linha.trim();
            if (limpa.isEmpty() || limpa.startsWith("#")) continue;

            List<EstadoCiclo> ciclos = new ArrayList<>();
            for (String microinstrucao : traduzirInstrucao(limpa, numeroLinha)) {
                ciclos.add(micService.executarInstrucao23(microinstrucao, ciclo++, regs, memoria));
            }
            blocos.add(new BlocoInstrucao(limpa, ciclos, memoria.snapshot()));
        }
        return blocos;
    }

    /**
     * Translates an IJVM program and executes it on the MIC-1, mutating
     * {@code regs} and {@code memoria} across the cycles.
     *
     * <p>Same execution as {@link #montarEExecutarBlocos}, with the blocks
     * flattened into a single list of cycles.</p>
     *
     * @param linhas   one IJVM instruction per line
     * @param regs     initial register file (mutated)
     * @param memoria  initial data memory (mutated)
     * @return one cycle log entry per microinstruction executed
     * @throws IllegalArgumentException if the program fails to translate
     */
    public List<EstadoCiclo> montarEExecutar(
            List<String> linhas, Registradores regs, Memoria memoria) {
        List<EstadoCiclo> log = new ArrayList<>();
        for (BlocoInstrucao bloco : montarEExecutarBlocos(linhas, regs, memoria)) {
            log.addAll(bloco.getCiclos());
        }
        return log;
    }
}
