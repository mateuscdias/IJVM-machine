package com.bisha.ijvm.service;

import com.bisha.ijvm.model.EstadoULA;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer implementing the MIC-1 ALU logic, extended to support
 * 8-bit control words with SLL8 and SRA1 shifter outputs, plus N and Z flags.
 *
 * <p><b>8-bit Instruction Format (X0..X7, MSB first):</b></p>
 * <pre>
 *   bit 7 (X0) - SLL8 - Shift Left Logical 8 bits on output S
 *   bit 6 (X1) - SRA1 - Shift Right Arithmetic 1 bit on output S
 *   bit 5 (X2) - F0   - ALU function selector (MSB)
 *   bit 4 (X3) - F1   - ALU function selector (LSB)
 *   bit 3 (X4) - ENA  - Enable A bus
 *   bit 2 (X5) - ENB  - Enable B bus
 *   bit 1 (X6) - INVA - Invert A bus
 *   bit 0 (X7) - INC  - Carry-in
 * </pre>
 *
 * <p><b>Constraint:</b> SLL8 and SRA1 must never both be 1; if they are,
 * the instruction is flagged as invalid.</p>
 *
 * <p>6-bit instructions are still fully supported via the legacy API.</p>
 * 
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 *
 * @version 2.0
 */

@Service
public class UlaService {
 
    public UlaService() {}
 
    // -------------------------------------------------------------------------
    // 6-bit legacy path (unchanged behaviour)
    // -------------------------------------------------------------------------
 
    /**
     * Processes a single 6-bit MIC-1 ALU instruction (legacy path).
     */
    public EstadoULA processarInstrucao(int instrucao, int pc, int a, int b) {
 
        int f0   = (instrucao >> 5) & 1;
        int f1   = (instrucao >> 4) & 1;
        int ena  = (instrucao >> 3) & 1;
        int enb  = (instrucao >> 2) & 1;
        int inva = (instrucao >> 1) & 1;
        int inc  =  instrucao       & 1;
 
        int effA = (ena == 1) ? a : 0;
        int effB = (enb == 1) ? b : 0;
        if (inva == 1) effA = ~effA;
 
        long logicOut;
        if      (f0 == 0 && f1 == 0) logicOut = (effA & effB)  & 0xFFFFFFFFL;
        else if (f0 == 0 && f1 == 1) logicOut = (effA | effB)  & 0xFFFFFFFFL;
        else if (f0 == 1 && f1 == 0) logicOut = (~effB)        & 0xFFFFFFFFL;
        else                          logicOut = (effA & 0xFFFFFFFFL)
                                               + (effB & 0xFFFFFFFFL);
 
        long soma = logicOut + inc;
        int s     = (int)(soma         & 0xFFFFFFFFL);
        int vaiUm = (int)((soma >> 32) & 1);
 
        return new EstadoULA(instrucao, pc, effA, effB, s, vaiUm);
    }
 
    /**
     * Executes a list of 6-bit binary instructions (legacy path).
     */
    public List<EstadoULA> processarPrograma(List<String> instrucoesBin, int initialA, int initialB) {
        List<EstadoULA> log = new ArrayList<>();
        int pc = 1;
        for (String bin : instrucoesBin) {
            int instrucao = Integer.parseInt(bin, 2);
            log.add(processarInstrucao(instrucao, pc, initialA, initialB));
            pc++;
        }
        return log;
    }
 
    // -------------------------------------------------------------------------
    // 8-bit extended path
    // -------------------------------------------------------------------------
 
    /**
     * Processes a single 8-bit MIC-1 ALU instruction.
     *
     * <p>Bit layout (MSB → LSB): SLL8 SRA1 F0 F1 ENA ENB INVA INC</p>
     *
     * <p>If SLL8 and SRA1 are both 1 the instruction is invalid and an
     * {@link EstadoULA} with {@code invalidSignals=true} is returned;
     * all other fields are set to 0 / the raw IR value.</p>
     *
     * @param instrucao 8-bit control word (0-255)
     * @param pc        Program counter for this cycle (1-indexed)
     * @param a         Raw A register value
     * @param b         Raw B register value
     * @return          Complete ALU state including Sd, N, Z
     */
    public EstadoULA processarInstrucao8(int instrucao, int pc, int a, int b) {
 
        int sll8 = (instrucao >> 7) & 1;  // X0
        int sra1 = (instrucao >> 6) & 1;  // X1
        int f0   = (instrucao >> 5) & 1;  // X2
        int f1   = (instrucao >> 4) & 1;  // X3
        int ena  = (instrucao >> 3) & 1;  // X4
        int enb  = (instrucao >> 2) & 1;  // X5
        int inva = (instrucao >> 1) & 1;  // X6
        int inc  =  instrucao       & 1;  // X7
 
        // Constraint: SLL8 and SRA1 must not both be high
        if (sll8 == 1 && sra1 == 1) {
            return new EstadoULA(instrucao, pc, 0, 0, 0, 0, 0, 0, 0, true, true);
        }
 
        // Bus enables
        int effA = (ena == 1) ? a : 0;
        int effB = (enb == 1) ? b : 0;
        if (inva == 1) effA = ~effA;
 
        // Logic/arithmetic unit (same as 6-bit core)
        long logicOut;
        if      (f0 == 0 && f1 == 0) logicOut = (effA & effB)  & 0xFFFFFFFFL;
        else if (f0 == 0 && f1 == 1) logicOut = (effA | effB)  & 0xFFFFFFFFL;
        else if (f0 == 1 && f1 == 0) logicOut = (~effB)        & 0xFFFFFFFFL;
        else                          logicOut = (effA & 0xFFFFFFFFL)
                                               + (effB & 0xFFFFFFFFL);
 
        // Full adder with carry-in
        long soma = logicOut + inc;
        int s     = (int)(soma         & 0xFFFFFFFFL);
        int vaiUm = (int)((soma >> 32) & 1);
 
        // Shifter stage (applied AFTER ALU output)
        int sd;
        if (sll8 == 1) {
            // Logical left shift 8: lower 24 bits become upper, lower 8 filled with 0
            sd = s << 8;
        } else if (sra1 == 1) {
            // Arithmetic right shift 1: MSB is replicated (sign-extension)
            sd = s >> 1;
        } else {
            sd = s;
        }
 
        // Flags are computed on the SHIFTED output Sd
        int n = (sd < 0)  ? 1 : 0;
        int z = (sd == 0) ? 1 : 0;
 
        return new EstadoULA(instrucao, pc, effA, effB, s, sd, vaiUm, n, z, true, false);
    }
 
    /**
     * Executes a list of 8-bit binary instructions.
     *
     * @param instrucoesBin List of 8-character binary strings
     * @param initialA      Initial A register value
     * @param initialB      Initial B register value
     * @return              Execution log
     */
    public List<EstadoULA> processarPrograma8(
            List<String> instrucoesBin, int initialA, int initialB) {
 
        List<EstadoULA> log = new ArrayList<>();
        int pc = 1;
        for (String bin : instrucoesBin) {
            int instrucao = Integer.parseInt(bin, 2);
            log.add(processarInstrucao8(instrucao, pc, initialA, initialB));
            pc++;
        }
        return log;
    }
}