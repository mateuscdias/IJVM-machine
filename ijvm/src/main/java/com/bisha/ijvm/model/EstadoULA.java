package com.bisha.ijvm.model;


/**
 * Data Transfer Object (DTO) representing the complete execution state
 * of the MIC-1 ALU after processing a single instruction (6-bit or 8-bit).
 *
 * <p>For 8-bit instructions, two additional control signals are supported:</p>
 * <ul>
 *   <li><b>SLL8</b> - Shift Left Logical 8 bits (applied to S after ALU)</li>
 *   <li><b>SRA1</b> - Shift Right Arithmetic 1 bit (applied to S after ALU)</li>
 * </ul>
 *
 * <p>Additional output flags:</p>
 * <ul>
 *   <li><b>N</b> - Negative flag: 1 when the shifted result Sd is negative</li>
 *   <li><b>Z</b> - Zero flag: 1 when the shifted result Sd is zero</li>
 *   <li><b>Sd</b> - Shifted output (S after applying SLL8 or SRA1)</li>
 * </ul>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 2.0
 * @since 1.0
 */
public class EstadoULA {
    private final int ir;
    private final int pc;
    private final int a;
    private final int b;
    private final int s;
    private final int vaiUm;
    private final int sd;
    private final int n;
    private final int z;
    private final boolean irWidth8;
    private final boolean invalidSignals;

    /**
     * Constructor for 6-bit backwards-compatible mode.
     */
    public EstadoULA(int ir, int pc, int a, int b, int s, int vaiUm) {
        this.ir = ir;
        this.pc = pc;
        this.a = a;
        this.b = b;
        this.s = s;
        this.vaiUm = vaiUm;
        this.n = (s < 0) ? 1 : 0;
        this.z = (s == 0) ? 1 : 0;
        this.irWidth8 = false;
        this.invalidSignals = false;
        this.sd = s;
    }
    /*
    Full constructor for 8-bit instruction mode
    */
   public EstadoULA(int ir, int pc, int a, int b, int s, int sd,
                     int vaiUm, int n, int z, boolean irWidth8, boolean invalidSignals) {
        this.ir = ir;
        this.pc = pc;
        this.a = a;
        this.b = b;
        this.s = s;
        this.sd = sd;
        this.vaiUm = vaiUm;
        this.n = n;
        this.z = z;
        this.irWidth8 = irWidth8;
        this.invalidSignals = invalidSignals;
    }
    public int getIr()         { return ir; }
    public int getPc()         { return pc; }
    public int getA()          { return a; }
    public int getB()          { return b; }
    public int getS()          { return s; }
    public int getSd()         { return sd; }
    public int getVaiUm()      { return vaiUm; }
    public int getN()          { return n; }
    public int getZ()          { return z; }
    public boolean isIrWidth8()      { return irWidth8; }
    public boolean isInvalidSignals(){ return invalidSignals; }
 
    public String getAHex()  { return String.format("%08X", a); }
    public String getBHex()  { return String.format("%08X", b); }
    public String getSHex()  { return String.format("%08X", s); }
    public String getSdHex() { return String.format("%08X", sd); }
 
    public String getIRBin() {
        int width = irWidth8 ? 8 : 6;
        return String.format("%" + width + "s", Integer.toBinaryString(ir)).replace(' ', '0');
    }
 
    public String getABin() {
        return String.format("%32s", Integer.toBinaryString(a & 0xFFFFFFFF)).replace(' ', '0');
    }
 
    public String getBBin() {
        return String.format("%32s", Integer.toBinaryString(b & 0xFFFFFFFF)).replace(' ', '0');
    }
 
    public String getSBin() {
        return String.format("%32s", Integer.toBinaryString(s & 0xFFFFFFFF)).replace(' ', '0');
    }
 
    public String getSdBin() {
        return String.format("%32s", Integer.toBinaryString(sd & 0xFFFFFFFF)).replace(' ', '0');
    }
 
    @Override
    public String toString() {
        if (invalidSignals) {
            return String.format("PC=%d, IR=%s, > Error, invalid control signals.", pc, getIRBin());
        }
        return String.format(
            "PC=%d, IR=%s, A=%08X, B=%08X, S=%08X, Sd=%08X, N=%d, Z=%d, VaiUm=%d",
            pc, getIRBin(), a, b, s, sd, n, z, vaiUm);
    }
}
