package com.bisha.ijvm;

import com.bisha.ijvm.model.EstadoULA;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the EstadoULA model class.
 *
 * <p>Verifies proper formatting of register values in hexadecimal
 * and binary representations.</p>
 *
 * @author Miguel Mochizuki Silva
 * @version 1.0
 */
@DisplayName("EstadoULA Model Tests")
class EstadoULATest {

    @Test
    @DisplayName("Should format hexadecimal values correctly")
    void testHexFormatting() {
        EstadoULA estado = new EstadoULA(60, 1, 0xFFFFFFFF, 0x00000001, 0xFFFFFFFE, 0);

        assertEquals("FFFFFFFF", estado.getAHex());
        assertEquals("00000001", estado.getBHex());
        assertEquals("FFFFFFFE", estado.getSHex());
    }

    @Test
    @DisplayName("Should format binary values correctly")
    void testBinaryFormatting() {
        EstadoULA estado = new EstadoULA(60, 1, 0xFFFFFFFF, 0x00000001, 0xFFFFFFFE, 0);

        assertEquals("111100", estado.getIRBin());
        assertEquals("11111111111111111111111111111111", estado.getABin());
        assertEquals("00000000000000000000000000000001", estado.getBBin());
        assertEquals("11111111111111111111111111111110", estado.getSBin());
    }

    @Test
    @DisplayName("Should handle zero values correctly")
    void testZeroValues() {
        EstadoULA estado = new EstadoULA(0, 1, 0, 0, 0, 0);

        assertEquals("000000", estado.getIRBin());
        assertEquals("00000000", estado.getAHex());
        assertEquals("00000000", estado.getBHex());
        assertEquals("00000000", estado.getSHex());
    }

    @Test
    @DisplayName("ToString should provide readable output")
    void testToString() {
        EstadoULA estado = new EstadoULA(60, 1, 0xFFFFFFFF, 0x00000001, 0xFFFFFFFE, 1);
        String str = estado.toString();

        assertTrue(str.contains("PC=1"));
        assertTrue(str.contains("IR=111100"));
        assertTrue(str.contains("A=FFFFFFFF"));
        assertTrue(str.contains("B=00000001"));
        assertTrue(str.contains("S=FFFFFFFE"));
        assertTrue(str.contains("VaiUm=1"));
    }
}
