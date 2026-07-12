package com.bisha.ijvm.model;

import java.util.List;

/**
 * Captures the complete execution state of one 21-bit or 23-bit MIC-1
 * instruction cycle.
 *
 * <p>Records:</p>
 * <ul>
 *   <li>Cycle number and IR string (21-bit or 23-bit)</li>
 *   <li>Register snapshot <b>before</b> execution (inicio)</li>
 *   <li>Register snapshot <b>after</b> execution (fim)</li>
 *   <li>Bus B source register name</li>
 *   <li>Bus C destination register names</li>
 *   <li>Shifted ALU output Sd written to bus C</li>
 *   <li>Data memory snapshot <b>after</b> execution (stage 3, 23-bit path)</li>
 *   <li>Name of the memory operation performed (stage 3, 23-bit path)</li>
 * </ul>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 3.0
 */

public class EstadoCiclo {

    private final int ciclo;
    private final String ir;
    private final Registradores inicio;
    private final Registradores fim;
    private final String busBRegistrador;
    private final List<String> busCRegistradores;
    private final int sd;
    private final List<String> memoriaFim;
    private final String operacaoMemoria;

    /**
     * Constructor for the 21-bit datapath (stage 2), without data memory.
     */
    public EstadoCiclo(int ciclo, String ir, Registradores inicio, Registradores fim,
        String bBusRegistrador, List<String> busCRegistradores, int sd){
            this(ciclo, ir, inicio, fim, bBusRegistrador, busCRegistradores, sd, null, null);
        }

    /**
     * Full constructor for the 23-bit datapath (stage 3), additionally carrying
     * the data memory snapshot taken at the end of the cycle and the name of the
     * memory operation performed ("read", "write", "fetch" or "none").
     */
    public EstadoCiclo(int ciclo, String ir, Registradores inicio, Registradores fim,
        String bBusRegistrador, List<String> busCRegistradores, int sd,
        List<String> memoriaFim, String operacaoMemoria){

            this.ciclo = ciclo;
            this.ir = ir;
            this.inicio = inicio;
            this.fim = fim;
            this.busBRegistrador = bBusRegistrador;
            this.busCRegistradores = busCRegistradores;
            this.sd = sd;
            this.memoriaFim = memoriaFim;
            this.operacaoMemoria = operacaoMemoria;
        }

    // Getters
    public int getCiclo(){ return ciclo; }
    public String getIr(){ return ir; }
    public Registradores getInicio(){ return inicio; }
    public Registradores getFim(){ return fim; }
    public String getBusBRegistrador(){ return busBRegistrador; }
    public List<String> getBusCRegistradores(){ return busCRegistradores; }
    public int getSd(){ return sd; }
    public List<String> getMemoriaFim(){ return memoriaFim; }
    public String getOperacaoMemoria(){ return operacaoMemoria; }

    /** Formats a Registradores snapshot as a human-readable multi-line block. */
    public static String formatRegs(Registradores r) {
        return String.format(
            "  H=%08X  OPC=%08X  TOS=%08X  CPP=%08X  LV=%08X%n" +
            "  SP=%08X  PC=%08X  MDR=%08X  MAR=%08X  MBR=%02X",
            r.getH(), r.getOpc(), r.getTos(), r.getCpp(), r.getLv(),
            r.getSp(), r.getPc(), r.getMdr(), r.getMar(), r.getMbr());
    }
    
}
