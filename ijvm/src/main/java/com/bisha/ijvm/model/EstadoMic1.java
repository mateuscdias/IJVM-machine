package com.bisha.ijvm.model;

import java.util.List;
import java.util.Map;

/**
 * DTO (Data Transfer Object) representando o estado completo de um ciclo de execução
 * da microarquitetura Mic-1 (Etapa 2, Tarefa 2).
 *
 * <p>Cada instância captura o estado imutável de um único ciclo, incluindo:</p>
 * <ul>
 *   <li>IR (palavra de controle de 21 bits)</li>
 *   <li>Snapshots do banco de registradores antes e após a instrução</li>
 *   <li>Registrador que alimentou o Barramento B</li>
 *   <li>Registradores escritos pelo Barramento C</li>
 *   <li>Valores intermediários da ULA (effA, effB, S) e do deslocador (Sd)</li>
 *   <li>Flags N (negativo) e Z (zero), e carry-out (vai-um)</li>
 * </ul>
 *
 * <h2>Formato do log textual gerado por {@link #gerarLogTexto(int)}</h2>
 * <pre>
 * =====================================================
 * Cycle N
 * ir = XXXXXXXX XXXXXXXXX XXXX
 *
 * b_bus = &lt;nome&gt;
 * c_bus = &lt;nome1&gt;, &lt;nome2&gt;, ...
 *
 * &gt; Registers before instruction
 * mar = &lt;32-bit binary&gt;
 * ...
 *
 * &gt; Registers after instruction
 * mar = &lt;32-bit binary&gt;
 * ...
 * =====================================================
 * </pre>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 2.0
 * @since 2.0
 * @see BancoRegistradores
 */
public class EstadoMic1 {

    // -------------------------------------------------------------------------
    // Campos imutáveis
    // -------------------------------------------------------------------------

    /** Número do ciclo (1-indexado). */
    private final int ciclo;

    /** Palavra de controle completa (21 bits). */
    private final int ir;

    /** Nome do registrador que alimentou o Barramento B (ex: "mbr", "tos"). */
    private final String nomeBBus;

    /** Nomes dos registradores escritos pelo Barramento C (ex: ["h", "sp"]). */
    private final List<String> nomesCBus;

    /** Snapshot do banco de registradores <b>antes</b> da execução da instrução. */
    private final Map<String, Integer> registradoresAntes;

    /** Snapshot do banco de registradores <b>após</b> a execução da instrução. */
    private final Map<String, Integer> registradoresDepois;

    /** Valor efetivo na Entrada A da ULA (sempre = H, com ENA/INVA aplicados). */
    private final int entradaA;

    /** Valor efetivo na Entrada B da ULA (registrador do Barramento B, com ENB aplicado). */
    private final int entradaB;

    /** Resultado da ULA <b>antes</b> do deslocador (S). */
    private final int s;

    /**
     * Resultado <b>após</b> o deslocador (Sd).
     * <ul>
     *   <li>Se SLL8=1: {@code Sd = S << 8}</li>
     *   <li>Se SRA1=1: {@code Sd = S >> 1} (aritmético, preserva sinal)</li>
     *   <li>Caso contrário: {@code Sd = S}</li>
     * </ul>
     */
    private final int sd;

    /** Carry-out do somador completo (vai-um): 0 ou 1. */
    private final int vaiUm;

    /**
     * Flag N (negativo): 1 se o bit mais significativo de Sd for 1, 0 caso contrário.
     * Calculado como {@code (sd >>> 31) & 1} para evitar problemas de sinal do Java.
     */
    private final int flagN;

    /**
     * Flag Z (zero): 1 se Sd == 0, 0 caso contrário.
     */
    private final int flagZ;

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    /**
     * Constrói o estado imutável de um ciclo Mic-1.
     *
     * @param ciclo              Número do ciclo (1-indexado)
     * @param ir                 Palavra de controle de 21 bits
     * @param nomeBBus           Nome do registrador do Barramento B
     * @param nomesCBus          Lista de nomes dos registradores do Barramento C
     * @param registradoresAntes Snapshot do banco antes da instrução
     * @param registradoresDepois Snapshot do banco após a instrução
     * @param entradaA           Valor efetivo da Entrada A da ULA
     * @param entradaB           Valor efetivo da Entrada B da ULA
     * @param s                  Resultado da ULA (antes do deslocador)
     * @param sd                 Resultado do deslocador (Sd)
     * @param vaiUm              Carry-out (0 ou 1)
     * @param flagN              Flag N (bit mais significativo de Sd)
     * @param flagZ              Flag Z (Sd == 0)
     */
    public EstadoMic1(int ciclo, int ir, String nomeBBus, List<String> nomesCBus,
                      Map<String, Integer> registradoresAntes, Map<String, Integer> registradoresDepois,
                      int entradaA, int entradaB, int s, int sd, int vaiUm, int flagN, int flagZ) {
        this.ciclo               = ciclo;
        this.ir                  = ir;
        this.nomeBBus            = nomeBBus;
        this.nomesCBus           = List.copyOf(nomesCBus);
        this.registradoresAntes  = Map.copyOf(registradoresAntes);
        this.registradoresDepois = Map.copyOf(registradoresDepois);
        this.entradaA            = entradaA;
        this.entradaB            = entradaB;
        this.s                   = s;
        this.sd                  = sd;
        this.vaiUm               = vaiUm;
        this.flagN               = flagN;
        this.flagZ               = flagZ;
    }

    // -------------------------------------------------------------------------
    // Métodos de formatação
    // -------------------------------------------------------------------------

    /**
     * Formata um valor de 32 bits como string binária de 32 caracteres (com zeros à esquerda).
     *
     * @param valor Valor a formatar
     * @return String de 32 caracteres, ex: "00000000000000000000000000000001"
     */
    public static String formatarBin32(int valor) {
        return String.format("%32s", Integer.toBinaryString(valor & 0xFFFFFFFF))
                     .replace(' ', '0');
    }

    /**
     * Formata um valor de 8 bits como string binária de 8 caracteres (com zeros à esquerda).
     * Usado exclusivamente para o registrador MBR.
     *
     * @param valor Valor a formatar (apenas os 8 bits inferiores são usados)
     * @return String de 8 caracteres, ex: "10000001"
     */
    public static String formatarBin8(int valor) {
        return String.format("%8s", Integer.toBinaryString(valor & 0xFF))
                     .replace(' ', '0');
    }

    /**
     * Formata o IR (21 bits) com espaços separando as três partes:
     * {@code "XXXXXXXX XXXXXXXXX XXXX"}.
     *
     * @return String formatada do IR, ex: "00110100 000011000 0010"
     */
    public String getIRFormatado() {
        // ULA (8 bits mais significativos = bits 20..13)
        int ula = (ir >> 13) & 0xFF;
        // C bus (9 bits = bits 12..4)
        int cBus = (ir >> 4) & 0x1FF;
        // B bus (4 bits = bits 3..0)
        int bBus = ir & 0xF;

        String ulaStr  = String.format("%8s",  Integer.toBinaryString(ula)).replace(' ', '0');
        String cBusStr = String.format("%9s",  Integer.toBinaryString(cBus)).replace(' ', '0');
        String bBusStr = String.format("%4s",  Integer.toBinaryString(bBus)).replace(' ', '0');

        return ulaStr + " " + cBusStr + " " + bBusStr;
    }

    /**
     * Gera o bloco de log textual deste ciclo no formato padrão da Etapa 2 Tarefa 2.
     *
     * @param totalCiclos Número total de ciclos no programa (para formatação de separadores)
     * @return Bloco de texto formatado para este ciclo
     */
    public String gerarLogTexto(int totalCiclos) {
        String sep = "=====================================================";
        StringBuilder sb = new StringBuilder();

        sb.append(sep).append("\n");
        sb.append("Cycle ").append(ciclo).append("\n");
        sb.append("ir = ").append(getIRFormatado()).append("\n");
        sb.append("\n");

        // Barramento B
        sb.append("b_bus = ").append(nomeBBus).append("\n");

        // Barramento C (lista separada por vírgula)
        sb.append("c_bus = ").append(String.join(", ", nomesCBus)).append("\n");
        sb.append("\n");

        // Registradores antes
        sb.append("> Registers before instruction\n");
        sb.append(formatarRegistradores(registradoresAntes));
        sb.append("\n");

        // Registradores depois
        sb.append("> Registers after instruction\n");
        sb.append(formatarRegistradores(registradoresDepois));

        return sb.toString();
    }

    /**
     * Formata o mapa de registradores como bloco de texto.
     * MBR é exibido com 8 bits; os demais com 32 bits.
     *
     * @param registradores Mapa ordenado nome→valor
     * @return Bloco de texto com uma linha por registrador
     */
    private String formatarRegistradores(Map<String, Integer> registradores) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : registradores.entrySet()) {
            String nome  = entry.getKey();
            int valor    = entry.getValue();
            String binStr = "mbr".equals(nome) ? formatarBin8(valor) : formatarBin32(valor);
            sb.append(nome).append(" = ").append(binStr).append("\n");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return Número do ciclo (1-indexado) */
    public int getCiclo()  { return ciclo; }

    /** @return Palavra de controle de 21 bits */
    public int getIr()     { return ir; }

    /** @return Nome do registrador do Barramento B */
    public String getNomeBBus() { return nomeBBus; }

    /** @return Lista de nomes dos registradores do Barramento C */
    public List<String> getNomesCBus() { return nomesCBus; }

    /** @return Snapshot dos registradores antes da instrução */
    public Map<String, Integer> getRegistradoresAntes()  { return registradoresAntes; }

    /** @return Snapshot dos registradores após a instrução */
    public Map<String, Integer> getRegistradoresDepois() { return registradoresDepois; }

    /** @return Valor efetivo na Entrada A da ULA */
    public int getEntradaA() { return entradaA; }

    /** @return Valor efetivo na Entrada B da ULA */
    public int getEntradaB() { return entradaB; }

    /** @return Resultado da ULA antes do deslocador (S) */
    public int getS()      { return s; }

    /** @return Resultado após o deslocador (Sd) */
    public int getSd()     { return sd; }

    /** @return Carry-out do somador completo (vai-um): 0 ou 1 */
    public int getVaiUm()  { return vaiUm; }

    /** @return Flag N: 1 se Sd < 0 (bit 31 de Sd = 1), caso contrário 0 */
    public int getFlagN()  { return flagN; }

    /** @return Flag Z: 1 se Sd == 0, caso contrário 0 */
    public int getFlagZ()  { return flagZ; }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    /**
     * Representação textual resumida do ciclo para depuração.
     *
     * @return String no formato "Cycle=N, IR=..., BBus=..., CBus=[...], Sd=..., N=x, Z=x"
     */
    @Override
    public String toString() {
        return String.format(
            "Cycle=%d, IR=%s, BBus=%s, CBus=%s, S=%08X, Sd=%08X, N=%d, Z=%d, VaiUm=%d",
            ciclo, getIRFormatado(), nomeBBus, nomesCBus,
            s, sd, flagN, flagZ, vaiUm
        );
    }
}
