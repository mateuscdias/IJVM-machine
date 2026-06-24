package com.bisha.ijvm.service;

import com.bisha.ijvm.model.EstadoCiclo;
import com.bisha.ijvm.model.Registradores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes 21-bit MIC-1 instructions against a live register file.
 *
 * <h2>21-bit Instruction Format</h2>
 * <pre>
 *   bits [0:7]   – ULA 8-bit control: SLL8 SRA1 F0 F1 ENA ENB INVA INC
 *   bits [8:16]  – Bus C 9-bit selector, written left-to-right as:
 *                  str[0]=H  str[1]=OPC  str[2]=TOS  str[3]=CPP  str[4]=LV
 *                  str[5]=SP  str[6]=PC  str[7]=MDR  str[8]=MAR
 *   bits [17:20] – Bus B 4-bit decoder value (0–8)
 * </pre>
 *
 * <h2>Bus B decoder (verified against reference output)</h2>
 * <pre>
 *   0=MDR  1=PC  2=MBR(zero-ext)  3=MBRU(sign-ext)
 *   4=SP   5=LV  6=CPP            7=OPC             8=TOS
 * </pre>
 * Note: MBR (2) is zero-extended; MBRU (3) is sign-extended.
 *
 * <h2>Bus C selector</h2>
 * The 9-character substring str[8..16] is read left-to-right:
 * str[0]='1' enables H, str[1]='1' enables OPC, ..., str[8]='1' enables MAR.
 *
 * @version 2.0
 */
@Service
public class MicService {

    @Autowired
    private UlaService ulaService;

    public MicService() {}

    // ── Bus B ────────────────────────────────────────────────────────────────

    /** Display names indexed by decoder value 0–8. */
    private static final String[] BUS_B_NAMES =
        {"mdr", "pc", "mbr", "mbru", "sp", "lv", "cpp", "opc", "tos"};

    /**
     * Returns the 32-bit value driven onto bus B.
     *
     * MBR  (value 2) → zero-extended to 32 bits.
     * MBRU (value 3) → sign-extended to 32 bits.
     */
    public int decodeBusB(int val, Registradores regs) {
        switch (val) {
            case 0: return regs.getMdr();
            case 1: return regs.getPc();
            case 2: // MBR zero-extended
                return regs.getMbr() & 0xFF;
            case 3: // MBRU sign-extended
                int mbr = regs.getMbr() & 0xFF;
                return (mbr & 0x80) != 0 ? (mbr | 0xFFFFFF00) : mbr;
            case 4: return regs.getSp();
            case 5: return regs.getLv();
            case 6: return regs.getCpp();
            case 7: return regs.getOpc();
            case 8: return regs.getTos();
            default:
                throw new IllegalArgumentException("Bus B decoder out of range: " + val);
        }
    }

    public String busBName(int val) {
        if (val < 0 || val > 8) return "?";
        return BUS_B_NAMES[val];
    }

    // ── Bus C ────────────────────────────────────────────────────────────────

    /**
     * Bus C register order matching the instruction string positions [0..8]:
     * position 0 = H, 1 = OPC, 2 = TOS, 3 = CPP, 4 = LV,
     * position 5 = SP, 6 = PC, 7 = MDR, 8 = MAR.
     */
    private static final String[] BUS_C_NAMES =
        {"h", "opc", "tos", "cpp", "lv", "sp", "pc", "mdr", "mar"};

    /**
     * Writes {@code sd} to every register whose bit is '1' in the 9-char
     * busC string (read left-to-right: position 0 = H … position 8 = MAR).
     *
     * @return names of registers written, in position order
     */
    public List<String> writeBusC(String busCStr, int sd, Registradores regs) {
        List<String> written = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (busCStr.charAt(i) == '1') {
                writeReg(i, sd, regs);
                written.add(BUS_C_NAMES[i]);
            }
        }
        return written;
    }

    private void writeReg(int pos, int value, Registradores regs) {
        switch (pos) {
            case 0: regs.setH(value);   break;
            case 1: regs.setOpc(value); break;
            case 2: regs.setTos(value); break;
            case 3: regs.setCpp(value); break;
            case 4: regs.setLv(value);  break;
            case 5: regs.setSp(value);  break;
            case 6: regs.setPc(value);  break;
            case 7: regs.setMdr(value); break;
            case 8: regs.setMar(value); break;
        }
    }

    // ── Execution ────────────────────────────────────────────────────────────

    /**
     * Executes one 21-bit instruction, mutating {@code regs} in place.
     *
     * @param instrBin 21-character binary string
     * @param ciclo    1-indexed cycle number
     * @param regs     live register file (mutated)
     * @return cycle log
     */
    public EstadoCiclo executarInstrucao(String instrBin, int ciclo, Registradores regs) {
        String ulaCtrl  = instrBin.substring(0, 8);   // [0:7]
        String busCStr  = instrBin.substring(8, 17);  // [8:16] — H OPC TOS CPP LV SP PC MDR MAR
        int    busBVal  = Integer.parseInt(instrBin.substring(17, 21), 2);

        int ulaWord = Integer.parseInt(ulaCtrl, 2);

        // Snapshot before
        Registradores inicio = regs.copy();

        // A = H (always), B = decoded bus B register
        int aValue  = regs.getH();
        int bValue  = decodeBusB(busBVal, regs);
        String bName = busBName(busBVal);

        // Execute ALU
        com.bisha.ijvm.model.EstadoULA aluState =
            ulaService.processarInstrucao8(ulaWord, ciclo, aValue, bValue);
        int sd = aluState.getSd();

        // Write Sd to bus C registers
        List<String> cNames = writeBusC(busCStr, sd, regs);

        // Snapshot after
        Registradores fim = regs.copy();

        return new EstadoCiclo(ciclo, instrBin, inicio, fim, bName, cNames, sd);
    }

    /**
     * Executes a full program, mutating {@code regs} across cycles.
     */
    public List<EstadoCiclo> executarPrograma(List<String> instrucoesBin, Registradores regs) {
        List<EstadoCiclo> log = new ArrayList<>();
        int ciclo = 1;
        for (String bin : instrucoesBin) {
            log.add(executarInstrucao(bin, ciclo++, regs));
        }
        return log;
    }
}