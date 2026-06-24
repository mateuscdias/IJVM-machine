package com.bisha.ijvm.model;

/**
 * Holds the complete register file of the MIC-1 datapath.
 *
 * <p>Nine 32-bit registers: H, OPC, TOS, CPP, LV, SP, PC, MDR, MAR.<br>
 * One 8-bit register: MBR (stored as {@code int}, only the low 8 bits are used).</p>
 *
 * <p>This class is mutable; {@link #copy()} produces an independent snapshot.</p>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 1.0
 */


public class Registradores {
    
    // 32-bit registers
    private int h;
    private int opc;
    private int tos;
    private int cpp;
    private int lv;
    private int sp;
    private int pc;
    private int mdr;
    private int mar;

    // 8-bit register
    private int mbr;

    /** Constructs a register file with all registers initialised to zero. */
    public Registradores(){}

    /**
     * Constructs a register file with explicit initial values.
     *
     * @param h   H register (32-bit)
     * @param opc OPC register (32-bit)
     * @param tos TOS register (32-bit)
     * @param cpp CPP register (32-bit)
     * @param lv  LV register (32-bit)
     * @param sp  SP register (32-bit)
     * @param pc  PC register (32-bit)
     * @param mdr MDR register (32-bit)
     * @param mar MAR register (32-bit)
     * @param mbr MBR register (8-bit, only low 8 bits used)
     */
    public Registradores(int h, int opc, int tos, int cpp, int lv,
                         int sp, int pc, int mdr, int mar, int mbr) {
        this.h   = h;
        this.opc = opc;
        this.tos = tos;
        this.cpp = cpp;
        this.lv  = lv;
        this.sp  = sp;
        this.pc  = pc;
        this.mdr = mdr;
        this.mar = mar;
        this.mbr = mbr & 0xFF;
    }
 

    // Getters
    public int getH()   { return h; }
    public int getOpc() { return opc; }
    public int getTos() { return tos; }
    public int getCpp() { return cpp; }
    public int getLv()  { return lv; }
    public int getSp()  { return sp; }
    public int getPc()  { return pc; }
    public int getMdr() { return mdr; }
    public int getMar() { return mar; }
    /** Returns the raw 8-bit MBR value (unsigned, 0–255 as int). */
    public int getMbr() { return mbr & 0xFF; }

    // Setters
    public void setH(int v)   { this.h   = v; }
    public void setOpc(int v) { this.opc = v; }
    public void setTos(int v) { this.tos = v; }
    public void setCpp(int v) { this.cpp = v; }
    public void setLv(int v)  { this.lv  = v; }
    public void setSp(int v)  { this.sp  = v; }
    public void setPc(int v)  { this.pc  = v; }
    public void setMdr(int v) { this.mdr = v; }
    public void setMar(int v) { this.mar = v; }
    /** Only the low 8 bits of {@code v} are stored. */
    public void setMbr(int v) { this.mbr = v & 0xFF; }

    /**
     * Returns an independent deep copy of this register file.
     * Used to capture state snapshots before/after instruction execution.
     */
    public Registradores copy() {
        return new Registradores(h, opc, tos, cpp, lv, sp, pc, mdr, mar, mbr);
    }

    @Override
    public String toString() {
        return String.format(
            "H=%08X OPC=%08X TOS=%08X CPP=%08X LV=%08X SP=%08X PC=%08X MDR=%08X MAR=%08X MBR=%02X",
            h, opc, tos, cpp, lv, sp, pc, mdr, mar, mbr & 0xFF);
    }
 
}
