package com.bisha.ijvm.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo do Banco de Registradores da microarquitetura Mic-1 modificada.
 *
 * <p>Gerencia os 10 registradores do caminho de dados:</p>
 * <ul>
 *   <li><b>Registradores de 32 bits:</b> H, OPC, TOS, CPP, LV, SP, PC, MDR, MAR</li>
 *   <li><b>Registrador de 8 bits:</b> MBR (Memory Buffer Register)</li>
 * </ul>
 *
 * <h2>Barramento B — Decodificador de 4 bits</h2>
 * <p>Seleciona qual registrador injeta seu valor na Entrada B da ULA:</p>
 * <pre>
 *   0: MDR  | 1: PC   | 2: MBR (extensão de sinal)
 *   3: MBRU | 4: SP   | 5: LV  | 6: CPP | 7: TOS | 8: OPC
 * </pre>
 *
 * <h2>Barramento C — Seletor de 9 bits</h2>
 * <p>Ativa escrita paralela do resultado Sd nos registradores habilitados:</p>
 * <pre>
 *   Bit 8: H | Bit 7: OPC | Bit 6: TOS | Bit 5: CPP | Bit 4: LV
 *   Bit 3: SP | Bit 2: PC | Bit 1: MDR | Bit 0: MAR
 * </pre>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 2.0
 * @since 2.0
 */
public class BancoRegistradores {

    // -------------------------------------------------------------------------
    // Campos — valores atuais dos registradores
    // -------------------------------------------------------------------------

    /** Registrador H (32 bits) — entrada fixa da Entrada A da ULA. */
    private int h;

    /** Registrador OPC (32 bits) — armazena opcode de instrução IJVM. */
    private int opc;

    /** Registrador TOS (32 bits) — topo da pilha (Top of Stack). */
    private int tos;

    /** Registrador CPP (32 bits) — ponteiro para a Constant Pool. */
    private int cpp;

    /** Registrador LV (32 bits) — ponteiro para as variáveis locais (Local Variables). */
    private int lv;

    /** Registrador SP (32 bits) — ponteiro de pilha (Stack Pointer). */
    private int sp;

    /**
     * Registrador PC (32 bits) — contador de programa interno da Mic-1.
     * <p><b>Atenção:</b> Distinto do contador de instruções do emulador (ciclo).</p>
     */
    private int pc;

    /** Registrador MDR (32 bits) — registrador de dados da memória (Memory Data Register). */
    private int mdr;

    /** Registrador MAR (32 bits) — registrador de endereço da memória (Memory Address Register). */
    private int mar;

    /**
     * Registrador MBR (8 bits) — registrador de bytes da memória (Memory Byte Register).
     * <p>Armazenado como {@code int}, mas apenas os 8 bits menos significativos são válidos.</p>
     */
    private int mbr;

    // -------------------------------------------------------------------------
    // Construtores
    // -------------------------------------------------------------------------

    /**
     * Constrói um banco de registradores com todos os valores zerados.
     */
    public BancoRegistradores() {
        // Todos os campos int são inicializados em 0 pelo Java por padrão.
    }

    /**
     * Construtor de cópia — cria um snapshot (instantâneo) do banco recebido.
     *
     * <p>Usado para registrar o estado antes/depois de cada instrução no log.</p>
     *
     * @param outro Banco de registradores a ser copiado
     */
    public BancoRegistradores(BancoRegistradores outro) {
        this.h   = outro.h;
        this.opc = outro.opc;
        this.tos = outro.tos;
        this.cpp = outro.cpp;
        this.lv  = outro.lv;
        this.sp  = outro.sp;
        this.pc  = outro.pc;
        this.mdr = outro.mdr;
        this.mar = outro.mar;
        this.mbr = outro.mbr;
    }

    // -------------------------------------------------------------------------
    // Barramento B — leitura
    // -------------------------------------------------------------------------

    /**
     * Lê o valor a ser colocado na Entrada B da ULA conforme o código do Barramento B.
     *
     * <p>Regras de extensão para MBR (8 bits → 32 bits):</p>
     * <ul>
     *   <li><b>Código 2 — MBR (extensão de sinal):</b> replica o bit 7 nos bits 31..8,
     *       transformando um byte com sinal em um inteiro com sinal equivalente.</li>
     *   <li><b>Código 3 — MBRU (extensão sem sinal / zero):</b> preenche os bits 31..8
     *       com zeros, tratando o byte como valor sem sinal.</li>
     * </ul>
     *
     * @param codigo Código de 4 bits (0-8) selecionando o registrador fonte
     * @return Valor de 32 bits a ser usado como Entrada B da ULA
     * @throws IllegalArgumentException se o código não estiver no intervalo válido [0..8]
     */
    public int lerBarramentoB(int codigo) {
        return switch (codigo) {
            case 0 -> mdr;
            case 1 -> pc;
            // MBR com extensão de sinal: bit 7 é o bit de sinal
            case 2 -> ((mbr & 0x80) != 0) ? (mbr | 0xFFFFFF00) : (mbr & 0xFF);
            // MBRU sem extensão de sinal: preenche com zeros
            case 3 -> mbr & 0xFF;
            case 4 -> sp;
            case 5 -> lv;
            case 6 -> cpp;
            case 7 -> tos;
            case 8 -> opc;
            default -> throw new IllegalArgumentException(
                "Código inválido para o Barramento B: " + codigo + " (esperado 0-8)");
        };
    }

    /**
     * Retorna o nome do registrador selecionado pelo código do Barramento B.
     *
     * @param codigo Código de 4 bits (0-8)
     * @return Nome do registrador (em minúsculas), ex: "mdr", "mbr", "mbru"
     */
    public String nomeBBarramento(int codigo) {
        return switch (codigo) {
            case 0 -> "mdr";
            case 1 -> "pc";
            case 2 -> "mbr";
            case 3 -> "mbru";
            case 4 -> "sp";
            case 5 -> "lv";
            case 6 -> "cpp";
            case 7 -> "tos";
            case 8 -> "opc";
            default -> "?";
        };
    }

    // -------------------------------------------------------------------------
    // Barramento C — escrita
    // -------------------------------------------------------------------------

    /**
     * Escreve o valor {@code sd} (saída do deslocador) em todos os registradores
     * habilitados pela máscara de 9 bits do Barramento C.
     *
     * <p>Mapeamento de bits (do mais significativo ao menos significativo da máscara):</p>
     * <pre>
     *   Bit 8: H   | Bit 7: OPC | Bit 6: TOS | Bit 5: CPP | Bit 4: LV
     *   Bit 3: SP  | Bit 2: PC  | Bit 1: MDR | Bit 0: MAR
     * </pre>
     *
     * @param mascara Máscara de 9 bits — cada bit '1' indica um registrador destino
     * @param sd      Valor de 32 bits a ser escrito nos registradores selecionados
     */
    public void escreverBarramentoC(int mascara, int sd) {
        if ((mascara & (1 << 8)) != 0) h   = sd;
        if ((mascara & (1 << 7)) != 0) opc = sd;
        if ((mascara & (1 << 6)) != 0) tos = sd;
        if ((mascara & (1 << 5)) != 0) cpp = sd;
        if ((mascara & (1 << 4)) != 0) lv  = sd;
        if ((mascara & (1 << 3)) != 0) sp  = sd;
        if ((mascara & (1 << 2)) != 0) pc  = sd;
        if ((mascara & (1 << 1)) != 0) mdr = sd;
        if ((mascara & (1 << 0)) != 0) mar = sd;
    }

    /**
     * Retorna a lista de nomes dos registradores habilitados pela máscara do Barramento C.
     *
     * @param mascara Máscara de 9 bits do Barramento C
     * @return Lista ordenada de nomes (do bit mais significativo ao menos), ex: ["h", "sp"]
     */
    public List<String> nomesBarramentoC(int mascara) {
        List<String> nomes = new ArrayList<>();
        if ((mascara & (1 << 8)) != 0) nomes.add("h");
        if ((mascara & (1 << 7)) != 0) nomes.add("opc");
        if ((mascara & (1 << 6)) != 0) nomes.add("tos");
        if ((mascara & (1 << 5)) != 0) nomes.add("cpp");
        if ((mascara & (1 << 4)) != 0) nomes.add("lv");
        if ((mascara & (1 << 3)) != 0) nomes.add("sp");
        if ((mascara & (1 << 2)) != 0) nomes.add("pc");
        if ((mascara & (1 << 1)) != 0) nomes.add("mdr");
        if ((mascara & (1 << 0)) != 0) nomes.add("mar");
        return nomes;
    }

    // -------------------------------------------------------------------------
    // Serialização / Carregamento
    // -------------------------------------------------------------------------

    /**
     * Inicializa o banco de registradores a partir do conteúdo de um arquivo de texto.
     *
     * <p>Formato esperado por linha: {@code nome = <valor_binario>}<br>
     * Exemplo:</p>
     * <pre>
     *   h   = 00000000000000000000000000000001
     *   mbr = 10000001
     *   sp  = 00000000000000000000000000000000
     * </pre>
     *
     * <p>Linhas vazias ou mal-formatadas são ignoradas silenciosamente.
     * O MBR aceita apenas 8 bits; os demais aceitam 32 bits.</p>
     *
     * @param conteudo Texto completo do arquivo de registradores
     * @throws NumberFormatException se um valor não for uma string binária válida
     */
    public void carregarDeString(String conteudo) {
        String[] linhas = conteudo.split("\\r?\\n");
        for (String linha : linhas) {
            linha = linha.trim();
            if (linha.isEmpty() || !linha.contains("=")) continue;

            // Divide apenas no primeiro '='
            int sep = linha.indexOf('=');
            String nome  = linha.substring(0, sep).trim().toLowerCase();
            String valor = linha.substring(sep + 1).trim();

            // Usa Long.parseLong para tratar corretamente valores com bit 31 em 1
            // (que seriam negativos se parseados diretamente como int)
            int intVal = (int) Long.parseLong(valor, 2);

            switch (nome) {
                case "h"   -> h   = intVal;
                case "opc" -> opc = intVal;
                case "tos" -> tos = intVal;
                case "cpp" -> cpp = intVal;
                case "lv"  -> lv  = intVal;
                case "sp"  -> sp  = intVal;
                case "pc"  -> pc  = intVal;
                case "mdr" -> mdr = intVal;
                case "mar" -> mar = intVal;
                // MBR: armazena apenas os 8 bits menos significativos
                case "mbr" -> mbr = intVal & 0xFF;
                default    -> { /* nome desconhecido — ignora */ }
            }
        }
    }

    /**
     * Retorna um mapa ordenado nome→valor de todos os registradores.
     *
     * <p>A ordem segue o padrão exibido no log: MAR, MDR, PC, MBR, SP, LV, CPP, TOS, OPC, H.
     * O MBR é retornado com o valor inteiro do byte (0-255).</p>
     *
     * @return {@link LinkedHashMap} preservando a ordem de inserção
     */
    public Map<String, Integer> toMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("mar", mar);
        m.put("mdr", mdr);
        m.put("pc",  pc);
        m.put("mbr", mbr & 0xFF);
        m.put("sp",  sp);
        m.put("lv",  lv);
        m.put("cpp", cpp);
        m.put("tos", tos);
        m.put("opc", opc);
        m.put("h",   h);
        return m;
    }

    // -------------------------------------------------------------------------
    // Getters e Setters
    // -------------------------------------------------------------------------

    /** @return Valor atual do registrador H (32 bits) */
    public int getH()   { return h; }

    /** @return Valor atual do registrador OPC (32 bits) */
    public int getOpc() { return opc; }

    /** @return Valor atual do registrador TOS (32 bits) */
    public int getTos() { return tos; }

    /** @return Valor atual do registrador CPP (32 bits) */
    public int getCpp() { return cpp; }

    /** @return Valor atual do registrador LV (32 bits) */
    public int getLv()  { return lv; }

    /** @return Valor atual do registrador SP (32 bits) */
    public int getSp()  { return sp; }

    /** @return Valor atual do registrador PC (32 bits) — contador da Mic-1 */
    public int getPc()  { return pc; }

    /** @return Valor atual do registrador MDR (32 bits) */
    public int getMdr() { return mdr; }

    /** @return Valor atual do registrador MAR (32 bits) */
    public int getMar() { return mar; }

    /** @return Valor atual do registrador MBR (apenas 8 bits inferiores são válidos) */
    public int getMbr() { return mbr & 0xFF; }

    /** @param h Novo valor do registrador H */
    public void setH(int h)     { this.h = h; }

    /** @param opc Novo valor do registrador OPC */
    public void setOpc(int opc) { this.opc = opc; }

    /** @param tos Novo valor do registrador TOS */
    public void setTos(int tos) { this.tos = tos; }

    /** @param cpp Novo valor do registrador CPP */
    public void setCpp(int cpp) { this.cpp = cpp; }

    /** @param lv Novo valor do registrador LV */
    public void setLv(int lv)   { this.lv = lv; }

    /** @param sp Novo valor do registrador SP */
    public void setSp(int sp)   { this.sp = sp; }

    /** @param pc Novo valor do registrador PC (Mic-1 interno) */
    public void setPc(int pc)   { this.pc = pc; }

    /** @param mdr Novo valor do registrador MDR */
    public void setMdr(int mdr) { this.mdr = mdr; }

    /** @param mar Novo valor do registrador MAR */
    public void setMar(int mar) { this.mar = mar; }

    /**
     * Define o valor do registrador MBR.
     *
     * @param mbr Valor a armazenar — apenas os 8 bits inferiores são mantidos
     */
    public void setMbr(int mbr) { this.mbr = mbr & 0xFF; }
}
