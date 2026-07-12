package com.bisha.ijvm.model;

import java.util.List;

/**
 * Groups every cycle produced by a single high-level IJVM instruction.
 *
 * <p>One IJVM instruction expands into several 23-bit microinstructions, each of
 * which yields one {@link EstadoCiclo}. This block keeps that boundary visible
 * to the caller, so a log can show the registers of every microinstruction while
 * printing the data memory only once per IJVM instruction.</p>
 *
 * <p>Records:</p>
 * <ul>
 *   <li>The IJVM instruction as written in the source file (e.g. {@code ILOAD 1})</li>
 *   <li>The cycles of the microinstructions that implement it, in order</li>
 *   <li>Data memory snapshot taken <b>after</b> the whole instruction has run</li>
 * </ul>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 */

public class BlocoInstrucao {

    private final String instrucao;
    private final List<EstadoCiclo> ciclos;
    private final List<String> memoriaApos;

    /**
     * Constructs a block for one IJVM instruction.
     *
     * @param instrucao   the IJVM instruction as written in the source file
     * @param ciclos      cycles of the microinstructions implementing it, in order
     * @param memoriaApos memory snapshot taken after the last of those cycles
     */
    public BlocoInstrucao(String instrucao, List<EstadoCiclo> ciclos, List<String> memoriaApos) {
        this.instrucao = instrucao;
        this.ciclos = ciclos;
        this.memoriaApos = memoriaApos;
    }

    // Getters
    public String getInstrucao(){ return instrucao; }
    public List<EstadoCiclo> getCiclos(){ return ciclos; }
    public List<String> getMemoriaApos(){ return memoriaApos; }

    /** Returns how many cycles this IJVM instruction took. */
    public int totalCiclos(){ return ciclos.size(); }
}
