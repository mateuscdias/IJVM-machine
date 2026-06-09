package com.bisha.ijvm.service;

import com.bisha.ijvm.model.EstadoULA;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer implementing the MIC-1 ALU (Arithmetic Logic Unit) logic.
 *
 * <p>This service simulates the complete behavior of the MIC-1 ALU as described
 * in Tanenbaum's "Structured Computer Organization". It processes 6-bit
 * control words that determine ALU operations, bus enables, and carry handling.</p>
 *
 * <p><b>Instruction Format (6 bits - X0 X1 X2 X3 X4 X5)</b></p>
 * <ul>
 *   <li>bit 5 (X0) - F0 - ALU function selector (MSB)</li>
 *   <li>bit 4 (X1) - F1 - ALU function selector (LSB)</li>
 *   <li>bit 3 (X2) - ENA - Enable A bus (0 forces A=0)</li>
 *   <li>bit 2 (X3) - ENB - Enable B bus (0 forces B=0)</li>
 *   <li>bit 1 (X4) - INVA - Invert A bus (bitwise complement)</li>
 *   <li>bit 0 (X5) - INC - Carry-in to full adder (vem-um)</li>
 * </ul>
 *
 * <p><b>ALU Operations (F0, F1 combination)</b></p>
 * <ul>
 *   <li>F0=0, F1=0 - A AND B - Bitwise logical AND</li>
 *   <li>F0=0, F1=1 - A OR B - Bitwise logical OR</li>
 *   <li>F0=1, F1=0 - NOT B - Bitwise complement of effective B</li>
 *   <li>F0=1, F1=1 - A + B - Arithmetic addition with carry-in</li>
 * </ul>
 *
 * <p><b>Processing Pipeline</b></p>
 * <ol>
 *   <li><b>Bus Enable:</b> A and B registers are forced to 0 if ENA/ENB = 0</li>
 *   <li><b>Invert A:</b> If INVA = 1, effective A is bitwise complemented</li>
 *   <li><b>Logic Unit:</b> Selected operation (AND/OR/NOT B/ADD) is performed</li>
 *   <li><b>Full Adder:</b> Logic unit output + INC (carry-in) gives final result</li>
 *   <li><b>Result Storage:</b> 32-bit result S and carry-out (vai-um) are stored</li>
 * </ol>
 *
 * <p><b>Note:</b> In Etapa 1 (Stage 1), there is no register file or feedback
 * between cycles. A and B remain constant throughout program execution.</p>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 * @since 1.0
 * @see com.bisha.ijvm.model.EstadoULA
 */
@Service
public class UlaService {

    /**
     * Default constructor for UlaService.
     *
     * <p>This constructor is used by Spring Framework for component instantiation
     * and dependency injection. The service contains no external dependencies
     * and is stateless, making it thread-safe for concurrent requests.</p>
     */
    public UlaService() {
        // Default constructor required by Spring Framework
    }

    /**
     * Processes a single MIC-1 ALU instruction.
     *
     * <p>This method implements the complete ALU microarchitecture including
     * bus management, logic unit operations, and full adder with carry handling.
     * All 32-bit values are treated as unsigned for arithmetic operations to
     * properly handle overflow and carry generation.</p>
     *
     * <p><b>Processing Steps:</b></p>
     * <ol>
     *   <li>Extract control bits: F0, F1, ENA, ENB, INVA, INC from the 6-bit instruction</li>
     *   <li>Apply bus enables: effA = (ENA==1) ? a : 0; effB = (ENB==1) ? b : 0</li>
     *   <li>Apply inversion: if (INVA==1) effA = ~effA</li>
     *   <li>Execute logic unit operation based on F0/F1:
     *     <ul>
     *       <li>00: AND - effA &amp; effB</li>
     *       <li>01: OR - effA | effB</li>
     *       <li>10: NOT B - ~effB</li>
     *       <li>11: ADD - effA + effB</li>
     *     </ul>
     *   </li>
     *   <li>Perform full addition with carry-in: result = logic_out + INC</li>
     *   <li>Extract 32-bit result S and carry-out (vai-um)</li>
     * </ol>
     *
     * <p><b>Important Implementation Details:</b></p>
     * <ul>
     *   <li>Long (64-bit) arithmetic prevents overflow during addition</li>
     *   <li>Masking with 0xFFFFFFFFL ensures proper 32-bit unsigned handling</li>
     *   <li>Carry-out is extracted from bit 32 of the 64-bit sum</li>
     *   <li>INC (carry-in) affects all operations, not just addition</li>
     * </ul>
     *
     * @param instrucao 6-bit control word as integer (0-63), typically parsed from binary string
     * @param pc Program counter value for the current cycle (starting from 1)
     * @param a Raw value on the A bus (32-bit signed integer, treated as unsigned for operations)
     * @param b Raw value on the B bus (32-bit signed integer, treated as unsigned for operations)
     * @return EstadoULA object containing complete execution state including:
     *         effective A and B values, ALU result S, carry-out, and instruction metadata
     * @throws IllegalArgumentException if instrucao is outside valid range (0-63)
     * @see EstadoULA
     */
    public EstadoULA processarInstrucao(int instrucao, int pc, int a, int b) {

        // Bit extraction (spec: F0 F1 ENA ENB INVA INC = X0...X5, MSB first)
        int f0   = (instrucao >> 5) & 1;   // X0
        int f1   = (instrucao >> 4) & 1;   // X1
        int ena  = (instrucao >> 3) & 1;   // X2
        int enb  = (instrucao >> 2) & 1;   // X3
        int inva = (instrucao >> 1) & 1;   // X4
        int inc  =  instrucao       & 1;   // X5

        // Bus enables: drive to 0 when disabled
        int effA = (ena == 1) ? a : 0;
        int effB = (enb == 1) ? b : 0;

        // INVA: bitwise complement of A (after ENA)
        if (inva == 1) effA = ~effA;

        // Logic unit: select one of three outputs
        // (unsigned 32-bit value; masking prevents sign-extension artifacts)
        long logicOut;
        if      (f0 == 0 && f1 == 0) logicOut = (effA & effB)  & 0xFFFFFFFFL;  // AND
        else if (f0 == 0 && f1 == 1) logicOut = (effA | effB)  & 0xFFFFFFFFL;  // OR
        else if (f0 == 1 && f1 == 0) logicOut = (~effB)        & 0xFFFFFFFFL;  // NOT B
        else                          logicOut = (effA & 0xFFFFFFFFL)           // A + B
                                               + (effB & 0xFFFFFFFFL);

        // Full adder: logic-unit output + carry-in (INC / vem-um)
        long soma = logicOut + inc;

        int s      = (int)(soma          & 0xFFFFFFFFL);
        int vaiUm  = (int)((soma >> 32)  & 1);

        // effA/effB are logged because they are what the ALU actually operated on
        return new EstadoULA(instrucao, pc, effA, effB, s, vaiUm);
    }

    /**
     * Executes a complete sequence of MIC-1 instructions.
     *
     * <p>This method processes a list of binary instruction strings sequentially,
     * maintaining a program counter (PC) that increments with each instruction.
     * In Stage 1 (Etapa 1), there is no register feedback between cycles, so
     * A and B remain constant throughout the entire program execution.</p>
     *
     * <p><b>Execution Flow:</b></p>
     * <ol>
     *   <li>Initialize program counter to 1</li>
     *   <li>For each instruction in the list:
     *     <ul>
     *       <li>Parse binary string to integer (base 2)</li>
     *       <li>Process instruction with current A, B, and PC values</li>
     *       <li>Record execution state in log</li>
     *       <li>Increment program counter</li>
     *     </ul>
     *   </li>
     *   <li>Return complete execution log</li>
     * </ol>
     *
     * <p><b>Input Validation:</b></p>
     * <p>This method assumes all instruction strings are valid 6-bit binary
     * strings. Invalid formats will throw {@link NumberFormatException}.</p>
     *
     * @param instrucoesBin List of 6-character binary strings (e.g., "111100"),
     *                      each representing one ALU instruction
     * @param initialA Initial value for the A register (32-bit, preserved throughout execution)
     * @param initialB Initial value for the B register (32-bit, preserved throughout execution)
     * @return List of EstadoULA objects representing the execution state after each instruction,
     *         ordered by execution sequence
     * @throws NumberFormatException if any binary string is invalid (not proper binary format)
     * @throws NullPointerException if instrucoesBin is null
     * @see #processarInstrucao(int, int, int, int)
     */
    public List<EstadoULA> processarPrograma(
            List<String> instrucoesBin,
            int initialA,
            int initialB) {

        List<EstadoULA> log = new ArrayList<>();
        int pc = 1;

        for (String instrucaoBin : instrucoesBin) {
            int instrucao = Integer.parseInt(instrucaoBin, 2);
            log.add(processarInstrucao(instrucao, pc, initialA, initialB));
            pc++;
        }

        return log;
    }
}
