package com.bisha.ijvm;

import com.bisha.ijvm.model.EstadoULA;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the EstadoULA model class.
 *
 * <p>Covers both the legacy 6-bit constructor (Etapa 1) and the extended
 * 8-bit constructor (Etapa 2) including SLL8/SRA1 shifter outputs, N/Z flags,
 * and invalid-control-signal detection.</p>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Goncalves
 * 
 * @version 2.0
 */
@DisplayName("EstadoULA Model Tests")
class EstadoULATest {

    // =========================================================================
    // 6-bit legacy constructor
    // =========================================================================

    @Nested
    @DisplayName("6-bit mode (legacy constructor)")
    class SixBitMode {

        @Test
        @DisplayName("Should format hexadecimal values correctly")
        void testHexFormatting() {
            EstadoULA estado = new EstadoULA(60, 1, 0xFFFFFFFF, 0x00000001, 0xFFFFFFFE, 0);
            assertEquals("FFFFFFFF", estado.getAHex());
            assertEquals("00000001", estado.getBHex());
            assertEquals("FFFFFFFE", estado.getSHex());
            // In 6-bit mode Sd == S
            assertEquals("FFFFFFFE", estado.getSdHex());
        }

        @Test
        @DisplayName("Should format binary values correctly")
        void testBinaryFormatting() {
            EstadoULA estado = new EstadoULA(60, 1, 0xFFFFFFFF, 0x00000001, 0xFFFFFFFE, 0);
            assertEquals("111100", estado.getIRBin());
            assertEquals("11111111111111111111111111111111", estado.getABin());
            assertEquals("00000000000000000000000000000001", estado.getBBin());
            assertEquals("11111111111111111111111111111110", estado.getSBin());
            assertEquals("11111111111111111111111111111110", estado.getSdBin());
        }

        @Test
        @DisplayName("Should handle zero values correctly")
        void testZeroValues() {
            EstadoULA estado = new EstadoULA(0, 1, 0, 0, 0, 0);
            assertEquals("000000", estado.getIRBin());
            assertEquals("00000000", estado.getAHex());
            assertEquals("00000000", estado.getBHex());
            assertEquals("00000000", estado.getSHex());
            assertEquals(0, estado.getN());
            assertEquals(1, estado.getZ());  // S == 0 → Z = 1
        }

        @Test
        @DisplayName("toString should provide readable output with Sd, N, Z fields")
        void testToString() {
            // S = 0xFFFFFFFE → negative, so N=1, Z=0; Sd == S in 6-bit mode
            EstadoULA estado = new EstadoULA(60, 1, 0xFFFFFFFF, 0x00000001, 0xFFFFFFFE, 1);
            String str = estado.toString();
            assertTrue(str.contains("PC=1"),       "Should contain PC");
            assertTrue(str.contains("IR=111100"),  "Should contain IR in binary");
            assertTrue(str.contains("A=FFFFFFFF"), "Should contain A in hex");
            assertTrue(str.contains("B=00000001"), "Should contain B in hex");
            assertTrue(str.contains("S=FFFFFFFE"), "Should contain S in hex");
            assertTrue(str.contains("Sd=FFFFFFFE"),"Should contain Sd in hex");
            assertTrue(str.contains("N=1"),        "Should contain N flag");
            assertTrue(str.contains("Z=0"),        "Should contain Z flag");
            assertTrue(str.contains("VaiUm=1"),    "Should contain carry-out");
        }

        @Test
        @DisplayName("N flag should be 0 for positive S, 1 for negative S")
        void testNFlag() {
            EstadoULA pos = new EstadoULA(0, 1, 0, 0, 1,          0);
            EstadoULA neg = new EstadoULA(0, 1, 0, 0, 0xFFFFFFFF, 0);
            assertEquals(0, pos.getN(), "Positive result → N = 0");
            assertEquals(1, neg.getN(), "Negative result (MSB=1) → N = 1");
        }

        @Test
        @DisplayName("Z flag should be 1 only when S is zero")
        void testZFlag() {
            EstadoULA zero    = new EstadoULA(0, 1, 0, 0, 0, 0);
            EstadoULA nonZero = new EstadoULA(0, 1, 0, 0, 1, 0);
            assertEquals(1, zero.getZ(),    "S == 0 → Z = 1");
            assertEquals(0, nonZero.getZ(), "S != 0 → Z = 0");
        }

        @Test
        @DisplayName("isIrWidth8 should be false for 6-bit constructor")
        void testIrWidthFlag() {
            EstadoULA estado = new EstadoULA(60, 1, 0, 0, 0, 0);
            assertFalse(estado.isIrWidth8());
            assertFalse(estado.isInvalidSignals());
        }
    }

    // =========================================================================
    // 8-bit extended constructor
    // =========================================================================

    @Nested
    @DisplayName("8-bit mode (extended constructor)")
    class EightBitMode {

        /** Convenience factory – mirrors processarInstrucao8 results manually. */
        private EstadoULA make8(int ir, int pc, int a, int b,
                                int s, int sd, int vaiUm, int n, int z) {
            return new EstadoULA(ir, pc, a, b, s, sd, vaiUm, n, z, true, false);
        }

        @Test
        @DisplayName("IR should be formatted as 8-bit binary string")
        void testIRBin8() {
            // 0b10111100 = 188
            EstadoULA estado = make8(0b10111100, 1, 0x80000001, 0x00000001,
                                     0x80000001, 0x00000100, 0, 0, 0);
            assertEquals("10111100", estado.getIRBin());
            assertTrue(estado.isIrWidth8());
        }

        @Test
        @DisplayName("SLL8: Sd should equal S shifted left 8 bits")
        void testSLL8Shift() {
            // S = 0x80000001 → SLL8 → Sd = 0x00000100
            int s  = 0x80000001;
            int sd = (s << 8) & 0xFFFFFFFF; // 0x00000100
            EstadoULA estado = make8(0b10111100, 1, 0x80000000, 0x00000001,
                                     s, sd, 0, 0, 0);
            assertEquals("00000000000000000000000100000000", estado.getSdBin());
            assertEquals(0, estado.getN()); // Sd is positive
            assertEquals(0, estado.getZ()); // Sd != 0
        }

        @Test
        @DisplayName("SRA1: Sd should equal S arithmetic-right-shifted 1 bit")
        void testSRA1Shift() {
            // S = 0x80000001 (negative) → SRA1 → Sd = 0xC0000000
            int s        = 0x80000001;
            int sSignedShift = s >> 1; // Java >> preserves sign bit → 0xC0000000
            int sd       = sSignedShift & 0xFFFFFFFF;
            EstadoULA estado = make8(0b01111100, 1, 0x80000000, 0x00000001,
                                     s, sd, 0, 1, 0);
            assertEquals("11000000000000000000000000000000", estado.getSdBin());
            assertEquals(1, estado.getN()); // Sd is negative (MSB=1)
            assertEquals(0, estado.getZ());
        }

        @Test
        @DisplayName("Z flag should be 1 when Sd is zero")
        void testZFlagOnSd() {
            EstadoULA estado = make8(0b00000000, 1, 0, 0, 0, 0, 0, 0, 1);
            assertEquals(1, estado.getZ());
            assertEquals(0, estado.getN());
        }

        @Test
        @DisplayName("N flag should be 1 when Sd is negative (MSB = 1)")
        void testNFlagOnSd() {
            EstadoULA estado = make8(0b00111100, 1, 0, 0, 0xFFFFFFFF, 0xFFFFFFFF, 0, 1, 0);
            assertEquals(1, estado.getN());
            assertEquals(0, estado.getZ());
        }

        @Test
        @DisplayName("Invalid signals: SLL8 and SRA1 both high")
        void testInvalidSignals() {
            // SLL8=1, SRA1=1 → ir = 0b11xxxxxx
            EstadoULA estado = new EstadoULA(0b11111100, 1, 0, 0, 0, 0, 0, 0, 0, true, true);
            assertTrue(estado.isInvalidSignals());
            assertEquals(0, estado.getS());
            assertEquals(0, estado.getSd());
        }

        @Test
        @DisplayName("toString should report error for invalid control signals")
        void testToStringInvalid() {
            EstadoULA estado = new EstadoULA(0b11111100, 3, 0, 0, 0, 0, 0, 0, 0, true, true);
            String str = estado.toString();
            assertTrue(str.contains("PC=3"));
            assertTrue(str.contains("IR=11111100"));
            assertTrue(str.contains("Error, invalid control signals"));
        }

        @Test
        @DisplayName("toString for valid 8-bit instruction should include all fields")
        void testToStringValid8() {
            EstadoULA estado = make8(0b10111100, 1, 0x80000000, 0x00000001,
                                     0x80000001, 0x00000100, 0, 0, 0);
            String str = estado.toString();
            assertTrue(str.contains("PC=1"));
            assertTrue(str.contains("IR=10111100"));
            assertTrue(str.contains("Sd=00000100"));
            assertTrue(str.contains("N=0"));
            assertTrue(str.contains("Z=0"));
            assertTrue(str.contains("VaiUm=0"));
        }

        @Test
        @DisplayName("getSdHex should return 8-char uppercase hex of Sd")
        void testSdHex() {
            EstadoULA estado = make8(0b01111100, 1, 0, 0, 0xFFFFFFFF, 0xC0000000, 0, 1, 0);
            assertEquals("C0000000", estado.getSdHex());
        }
    }
}