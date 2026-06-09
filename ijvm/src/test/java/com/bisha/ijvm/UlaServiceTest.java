package com.bisha.ijvm;

import com.bisha.ijvm.model.EstadoULA;
import com.bisha.ijvm.service.UlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the UlaService ALU implementation.
 *
 * <p>This test class verifies the MIC-1 ALU operations including:
 * <ul>
 *   <li>AND, OR, NOT B, and ADD operations</li>
 *   <li>Bus enables (ENA, ENB)</li>
 *   <li>Inversion (INVA)</li>
 *   <li>Carry-in and carry-out (INC, vaiUm)</li>
 *   <li>Program execution sequences</li>
 * </ul>
 * </p>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 */
@DisplayName("ULA Service Tests")
class UlaServiceTest {

    private UlaService ulaService;

    @BeforeEach
    void setUp() {
        ulaService = new UlaService();
    }

    // ==================== BASIC ALU OPERATIONS ====================

    @Test
    @DisplayName("AND operation should compute bitwise AND correctly")
    void testAndOperation() {
        // Instruction: F0=0, F1=0, ENA=1, ENB=1, INVA=0, INC=0
        // Binary: 00 1 1 0 0 = 001100 (12 decimal)
        int instrucao = 0b001100; // 12 decimal
        int a = 0xFFFFFFFF; // All ones
        int b = 0x00000001; // 1

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0x00000001, result.getS(), "AND of FFFFFFFF and 1 should be 1");
        assertEquals(0, result.getVaiUm(), "AND should not produce carry");
    }

    @Test
    @DisplayName("OR operation should compute bitwise OR correctly")
    void testOrOperation() {
        // Instruction: F0=0, F1=1, ENA=1, ENB=1, INVA=0, INC=0
        // Binary: 01 1 1 0 0 = 011100 (28 decimal)
        int instrucao = 0b011100; // 28 decimal
        int a = 0xFFFF0000;
        int b = 0x0000FFFF;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0xFFFFFFFF, result.getS(), "OR of FFFF0000 and 0000FFFF should be FFFFFFFF");
        assertEquals(0, result.getVaiUm(), "OR should not produce carry");
    }

    @Test
    @DisplayName("NOT B operation should compute bitwise complement of B")
    void testNotBOperation() {
        // Instruction: F0=1, F1=0, ENA=1, ENB=1, INVA=0, INC=0
        // Binary: 10 1 1 0 0 = 101100 (44 decimal)
        int instrucao = 0b101100; // 44 decimal
        int a = 0xFFFFFFFF;
        int b = 0x00000001;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0xFFFFFFFE, result.getS(), "NOT of 1 should be FFFFFFFE");
        assertEquals(0, result.getVaiUm(), "NOT B should not produce carry");
    }

    @Test
    @DisplayName("ADD operation with INC=0 should perform addition without carry-in")
    void testAdditionWithoutCarry() {
        // Instruction: F0=1, F1=1, ENA=1, ENB=1, INVA=0, INC=0
        // Binary: 11 1 1 0 0 = 111100 (60 decimal)
        int instrucao = 0b111100; // 60 decimal
        int a = 0x00000001;
        int b = 0x00000002;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0x00000003, result.getS(), "1 + 2 should be 3");
        assertEquals(0, result.getVaiUm(), "No overflow expected");
    }

    @Test
    @DisplayName("ADD operation with INC=1 should perform addition with carry-in")
    void testAdditionWithCarryIn() {
        // Instruction: F0=1, F1=1, ENA=1, ENB=1, INVA=0, INC=1
        // Binary: 11 1 1 0 1 = 111101 (61 decimal)
        int instrucao = 0b111101; // 61 decimal
        int a = 0x00000001;
        int b = 0x00000002;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0x00000004, result.getS(), "1 + 2 + 1 should be 4");
        assertEquals(0, result.getVaiUm(), "No overflow expected");
    }

    @Test
    @DisplayName("Addition should handle overflow with carry-out")
    void testAdditionWithOverflow() {
        // Instruction: F0=1, F1=1, ENA=1, ENB=1, INVA=0, INC=0
        int instrucao = 0b111100; // 60 decimal
        int a = 0xFFFFFFFF; // -1 in two's complement
        int b = 0x00000001;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0x00000000, result.getS(), "FFFFFFFF + 1 should be 0 (overflow)");
        assertEquals(1, result.getVaiUm(), "Should produce carry-out");
    }

    // ==================== BUS ENABLE TESTS ====================

    @Test
    @DisplayName("ENA=0 should force A bus to 0")
    void testEnableA() {
        // Instruction: AND operation with ENA=0, ENB=1
        // Bits: F0=0, F1=0 (AND), ENA=0, ENB=1, INVA=0, INC=0
        // Binary: 00 0 1 0 0 = 000100 (4 decimal)
        int instrucao = 0b000100; // 4 decimal
        int a = 0xFFFFFFFF;
        int b = 0x00000001;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0x00000000, result.getA(), "ENA=0 should force A to 0");
        assertEquals(0x00000000, result.getS(), "0 AND 1 should be 0");
    }

    @Test
    @DisplayName("ENB=0 should force B bus to 0")
    void testEnableB() {
        // Instruction: AND operation with ENA=1, ENB=0
        // Bits: F0=0, F1=0 (AND), ENA=1, ENB=0, INVA=0, INC=0
        // Binary: 00 1 0 0 0 = 001000 (8 decimal)
        int instrucao = 0b001000; // 8 decimal
        int a = 0xFFFFFFFF;
        int b = 0x00000001;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0x00000000, result.getB(), "ENB=0 should force B to 0");
        assertEquals(0x00000000, result.getS(), "FFFFFFFF AND 0 should be 0");
    }

    @Test
    @DisplayName("Both ENA=0 and ENB=0 should force both buses to 0")
    void testBothDisabled() {
        // Instruction: AND operation with ENA=0, ENB=0
        // Bits: F0=0, F1=0 (AND), ENA=0, ENB=0, INVA=0, INC=0
        // Binary: 00 0 0 0 0 = 000000 (0 decimal)
        int instrucao = 0b000000; // 0 decimal
        int a = 0xFFFFFFFF;
        int b = 0x00000001;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0x00000000, result.getA(), "ENA=0 should force A to 0");
        assertEquals(0x00000000, result.getB(), "ENB=0 should force B to 0");
        assertEquals(0x00000000, result.getS(), "0 AND 0 should be 0");
    }

    // ==================== INVERSION TESTS ====================

    @Test
    @DisplayName("INVA=1 should invert A bus")
    void testInvertA() {
        // Instruction: AND operation with INVA=1
        // Bits: F0=0, F1=0 (AND), ENA=1, ENB=1, INVA=1, INC=0
        // Binary: 00 1 1 1 0 = 001110 (14 decimal)
        int instrucao = 0b001110; // 14 decimal
        int a = 0x00000000;
        int b = 0xFFFFFFFF;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(~0x00000000, result.getA(), "INVA should complement A");
        assertEquals(0xFFFFFFFF, result.getS(), "~0 AND FFFFFFFF should be FFFFFFFF");
    }

    @Test
    @DisplayName("INVA=1 with ENA=0 should invert the forced 0 value")
    void testInvertWithDisabled() {
        // Instruction: AND operation with ENA=0, INVA=1
        // Bits: F0=0, F1=0 (AND), ENA=0, ENB=1, INVA=1, INC=0
        // Binary: 00 0 1 1 0 = 000110 (6 decimal)
        int instrucao = 0b000110; // 6 decimal
        int a = 0xFFFFFFFF;
        int b = 0x00000001;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        // ENA=0 forces A=0 first, then INVA=1 inverts 0 to -1 (0xFFFFFFFF)
        assertEquals(0xFFFFFFFF, result.getA(), "ENA=0 forces A=0, then INVA inverts to all ones");
        assertEquals(0x00000001, result.getS(), "0xFFFFFFFF AND 1 should be 1");
    }

    // ==================== PROGRAM EXECUTION TESTS ====================

    @Test
    @DisplayName("Should execute multiple instructions sequentially")
    void testProgramExecution() {
        List<String> instrucoes = Arrays.asList(
            "111100", // ADD: 0xFFFFFFFF + 1 = 0 with carry
            "111100"  // ADD again
        );

        List<EstadoULA> log = ulaService.processarPrograma(instrucoes, 0xFFFFFFFF, 0x00000001);

        assertEquals(2, log.size(), "Should have 2 cycles");

        // First instruction: 0xFFFFFFFF + 1 = 0, carry=1
        assertEquals(1, log.get(0).getPc());
        assertEquals(0x00000000, log.get(0).getS());
        assertEquals(1, log.get(0).getVaiUm());

        // Second instruction: same operation
        assertEquals(2, log.get(1).getPc());
    }

    @Test
    @DisplayName("Program counter should increment correctly")
    void testProgramCounter() {
        List<String> instrucoes = Arrays.asList("111100", "011100", "101100");

        List<EstadoULA> log = ulaService.processarPrograma(instrucoes, 0xFFFFFFFF, 0x00000001);

        assertEquals(1, log.get(0).getPc());
        assertEquals(2, log.get(1).getPc());
        assertEquals(3, log.get(2).getPc());
    }

    @Test
    @DisplayName("A and B registers should remain constant throughout execution")
    void testRegistersConstant() {
        List<String> instrucoes = Arrays.asList("111100", "011100", "101100");
        int initialA = 0x12345678;
        int initialB = 0x87654321;

        List<EstadoULA> log = ulaService.processarPrograma(instrucoes, initialA, initialB);

        for (EstadoULA estado : log) {
            assertEquals(initialA, estado.getA(), "A should remain constant in Etapa 1");
            assertEquals(initialB, estado.getB(), "B should remain constant in Etapa 1");
        }
    }

    // ==================== EDGE CASE TESTS ====================

    @ParameterizedTest
    @DisplayName("Should handle all valid instruction values")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                         16, 32, 48, 60, 61, 62, 63})
    void testAllValidInstructions(int instrucao) {
        assertDoesNotThrow(() -> {
            EstadoULA result = ulaService.processarInstrucao(instrucao, 1, 0xFFFFFFFF, 0x00000001);
            assertNotNull(result);
        }, "All valid 6-bit instructions (0-63) should be accepted");
    }

    @ParameterizedTest
    @DisplayName("Binary strings should parse correctly")
    @CsvSource({
        "111100, 60",
        "011100, 28",
        "101100, 44",
        "000000, 0",
        "111111, 63"
    })
    void testBinaryParsing(String binaryString, int expectedValue) {
        int instrucao = Integer.parseInt(binaryString, 2);
        assertEquals(expectedValue, instrucao);

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, 0, 0);
        assertEquals(instrucao, result.getIr());
    }

    @Test
    @DisplayName("Empty program should return empty log")
    void testEmptyProgram() {
        List<String> instrucoes = List.of();
        List<EstadoULA> log = ulaService.processarPrograma(instrucoes, 0, 0);

        assertTrue(log.isEmpty(), "Empty program should produce empty log");
    }

    @Test
    @DisplayName("Should handle maximum values correctly")
    void testMaximumValues() {
        int instrucao = 0b111100; // ADD
        int a = 0x7FFFFFFF; // Max positive 32-bit
        int b = 0x7FFFFFFF;

        EstadoULA result = ulaService.processarInstrucao(instrucao, 1, a, b);

        assertEquals(0xFFFFFFFE, result.getS(), "7FFFFFFF + 7FFFFFFF = FFFFFFFE");
        assertEquals(0, result.getVaiUm(), "Should not overflow to carry");
    }
}
