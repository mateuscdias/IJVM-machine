package com.bisha.ijvm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the MIC-1 data memory as an array of 32-bit words.
 *
 * <p>Each word is stored as a Java {@code int} (32 bits). The memory size is
 * decided when the memory is built — typically by the number of lines in the
 * initialisation file — and is never assumed to be a fixed value.</p>
 *
 * <p>Word access goes through {@link #read(int)} and {@link #write(int, int)},
 * both of which validate the address against the memory bounds. The internal
 * array is never exposed directly; callers obtain an independent
 * {@link #snapshot()} for logging purposes.</p>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 */
public class Memoria {

    /** Backing store: one 32-bit word per position. */
    private final int[] palavras;

    /**
     * Constructs a data memory with {@code tamanho} words, all initialised to zero.
     *
     * @param tamanho number of 32-bit words
     */
    public Memoria(int tamanho) {
        this.palavras = new int[tamanho];
    }

    /**
     * Constructs a data memory from an existing array of words.
     *
     * <p>The supplied array is cloned, so later changes to it do not affect
     * this memory.</p>
     *
     * @param valores initial word values
     */
    public Memoria(int[] valores) {
        this.palavras = valores.clone();
    }

    /**
     * Builds a data memory from a list of binary text lines, one 32-bit word
     * per line. As many words are created as there are lines, so the caller
     * controls the memory size through the input file.
     *
     * @param linhas list of 32-character binary strings (MSB first)
     * @return a memory initialised with the parsed words
     */
    public static Memoria carregar(List<String> linhas) {
        int[] valores = new int[linhas.size()];
        for (int i = 0; i < linhas.size(); i++) {
            valores[i] = (int) Long.parseLong(linhas.get(i).trim(), 2);
        }
        return new Memoria(valores);
    }

    /**
     * Reads the 32-bit word stored at {@code endereco}.
     *
     * @param endereco word address (0-based)
     * @return the stored word
     * @throws IndexOutOfBoundsException if the address is outside the memory
     */
    public int read(int endereco) {
        verificarEndereco(endereco);
        return palavras[endereco];
    }

    /**
     * Writes {@code valor} into the 32-bit word at {@code endereco}.
     *
     * @param endereco word address (0-based)
     * @param valor    value to store
     * @throws IndexOutOfBoundsException if the address is outside the memory
     */
    public void write(int endereco, int valor) {
        verificarEndereco(endereco);
        palavras[endereco] = valor;
    }

    /** Returns the number of 32-bit words held by this memory. */
    public int tamanho() {
        return palavras.length;
    }

    /**
     * Produces an independent snapshot of the whole memory for logging, each
     * word formatted as a 32-bit binary string (MSB first). The internal array
     * is not exposed.
     *
     * @return a new list with one binary string per word, in address order
     */
    public List<String> snapshot() {
        List<String> linhas = new ArrayList<>(palavras.length);
        for (int palavra : palavras) {
            linhas.add(String.format("%32s",
                Integer.toBinaryString(palavra)).replace(' ', '0'));
        }
        return linhas;
    }

    /** Validates that {@code endereco} falls within the memory bounds. */
    private void verificarEndereco(int endereco) {
        if (endereco < 0 || endereco >= palavras.length) {
            throw new IndexOutOfBoundsException(
                "Endereço de memória fora do intervalo: " + endereco
                + " (tamanho " + palavras.length + ")");
        }
    }
}
