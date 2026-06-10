package com.bisha.ijvm.service;

import com.bisha.ijvm.model.BancoRegistradores;
import com.bisha.ijvm.model.EstadoMic1;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Camada de serviço da Etapa 2 — implementa o caminho de dados completo da Mic-1 modificada.
 *
 * <p>Esta classe é responsável por dois módulos distintos:</p>
 *
 * <h2>Tarefa 1 — ULA expandida com Deslocador (palavra de 8 bits)</h2>
 * <p>Formato: {@code [SLL8 | SRA1 | F0 | F1 | ENA | ENB | INVA | INC]} (MSB → LSB)</p>
 * <ul>
 *   <li>Reutiliza a lógica da ULA da Etapa 1 (6 bits inferiores).</li>
 *   <li>Aplica o deslocador pós-ULA: SLL8 (lógico esquerdo 8 bits) ou SRA1 (aritmético direito 1 bit).</li>
 *   <li>SLL8 e SRA1 nunca podem ser ativados simultaneamente — isso gera erro.</li>
 *   <li>Calcula as flags N (bit 31 de Sd) e Z (Sd == 0).</li>
 * </ul>
 *
 * <h2>Tarefa 2 — Banco de Registradores + Barramentos (palavra de 21 bits)</h2>
 * <p>Formato: {@code [ULA 8 bits | Barramento C 9 bits | Barramento B 4 bits]} (bits 20..0)</p>
 * <ol>
 *   <li>Entrada A = H (sempre).</li>
 *   <li>Entrada B = registrador selecionado pelo Barramento B.</li>
 *   <li>ULA + Deslocador → Sd.</li>
 *   <li>Sd é escrito em todos os registradores habilitados pelo Barramento C.</li>
 *   <li>Um log de ciclos ({@link EstadoMic1}) é gerado após cada instrução.</li>
 * </ol>
 *
 * @author Miguel Mochizuki Silva
 * @author Mateus C. Dias
 * @author Joaquim Germano Félix
 * @author Gabriel Bringel Gonçalves
 * @version 2.0
 * @since 2.0
 * @see BancoRegistradores
 * @see EstadoMic1
 */
@Service
public class Mic1Service {

    /**
     * Construtor padrão — necessário para injeção de dependências do Spring.
     */
    public Mic1Service() {
        // sem inicializações adicionais
    }

    // =========================================================================
    // TAREFA 1 — ULA + Deslocador (8 bits)
    // =========================================================================

    /**
     * Processa uma única instrução da Tarefa 1 (palavra de 8 bits com deslocador).
     *
     * <p><b>Formato do IR (8 bits, MSB → LSB):</b></p>
     * <pre>
     *   Bit 7: SLL8 | Bit 6: SRA1 | Bit 5: F0 | Bit 4: F1
     *   Bit 3: ENA  | Bit 2: ENB  | Bit 1: INVA | Bit 0: INC
     * </pre>
     *
     * <p><b>Pipeline de execução:</b></p>
     * <ol>
     *   <li>Valida conflito SLL8 &amp; SRA1 simultâneos.</li>
     *   <li>Aplica enables (ENA/ENB) e inversão (INVA).</li>
     *   <li>Unidade lógica: AND / OR / NOT-B / ADD conforme F0 e F1.</li>
     *   <li>Somador completo: resultado lógico + carry-in (INC).</li>
     *   <li>Deslocador: aplica SLL8 ou SRA1 sobre S → Sd.</li>
     *   <li>Flags: N = MSB(Sd), Z = (Sd == 0).</li>
     * </ol>
     *
     * @param instrucao Código de controle de 8 bits (0-255)
     * @param pc        Número do ciclo atual (1-indexado), usado apenas para log
     * @param a         Valor de 32 bits da Entrada A (inicial do teste)
     * @param b         Valor de 32 bits da Entrada B (inicial do teste)
     * @return Mapa com os campos: ir, irBin, pc, a, b, effA, effB, s, sd, n, z, vaiUm
     * @throws IllegalArgumentException se SLL8=1 e SRA1=1 simultaneamente
     */
    public Map<String, Object> processarInstrucaoMic1T1(int instrucao, int pc, int a, int b) {

        // ---- Extração dos bits de controle --------------------------------
        int sll8 = (instrucao >> 7) & 1;   // bit 7 — shift left logical  8 bits
        int sra1 = (instrucao >> 6) & 1;   // bit 6 — shift right arithmetic 1 bit
        int f0   = (instrucao >> 5) & 1;   // bit 5 — seletor da unidade lógica (MSB)
        int f1   = (instrucao >> 4) & 1;   // bit 4 — seletor da unidade lógica (LSB)
        int ena  = (instrucao >> 3) & 1;   // bit 3 — enable do barramento A
        int enb  = (instrucao >> 2) & 1;   // bit 2 — enable do barramento B
        int inva = (instrucao >> 1) & 1;   // bit 1 — inversão do barramento A
        int inc  =  instrucao       & 1;   // bit 0 — carry-in (INC / vem-um)

        // ---- Validação: SLL8 e SRA1 nunca ativados juntos -----------------
        if (sll8 == 1 && sra1 == 1) {
            throw new IllegalArgumentException(
                "Sinais de controle inválidos: SLL8=1 e SRA1=1 simultâneos na instrução "
                + String.format("%8s", Integer.toBinaryString(instrucao)).replace(' ', '0'));
        }

        // ---- ULA: lógica compartilhada com a Etapa 1 ----------------------
        int[] ulaResult = executarULA(f0, f1, ena, enb, inva, inc, a, b);
        int effA  = ulaResult[0];
        int effB  = ulaResult[1];
        int s     = ulaResult[2];
        int vaiUm = ulaResult[3];

        // ---- Deslocador pós-ULA -------------------------------------------
        int sd = aplicarDeslocador(s, sll8, sra1);

        // ---- Flags de condição --------------------------------------------
        int flagN = (sd >>> 31) & 1;   // bit 31 sem propagação de sinal (>>>)
        int flagZ = (sd == 0) ? 1 : 0;

        // ---- Monta resultado para o controller ----------------------------
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("pc",     pc);
        resultado.put("ir",     instrucao);
        resultado.put("irBin",  String.format("%8s", Integer.toBinaryString(instrucao)).replace(' ', '0'));
        resultado.put("sll8",   sll8);
        resultado.put("sra1",   sra1);
        resultado.put("a",      a);
        resultado.put("aBin",   formatarBin32(a));
        resultado.put("b",      b);
        resultado.put("bBin",   formatarBin32(b));
        resultado.put("effA",   effA);
        resultado.put("effABin", formatarBin32(effA));
        resultado.put("effB",   effB);
        resultado.put("effBBin", formatarBin32(effB));
        resultado.put("s",      s);
        resultado.put("sBin",   formatarBin32(s));
        resultado.put("sd",     sd);
        resultado.put("sdBin",  formatarBin32(sd));
        resultado.put("n",      flagN);
        resultado.put("z",      flagZ);
        resultado.put("vaiUm",  vaiUm);
        return resultado;
    }

    /**
     * Processa uma lista de instruções da Tarefa 1 sequencialmente.
     *
     * <p>Os valores de A e B são fixos durante toda a execução (sem feedback de registradores).
     * Linhas vazias encerram a execução (EOP — End of Program).</p>
     *
     * @param instrucoesBin Lista de strings binárias de 8 bits
     * @param initialA      Valor inicial do registrador A (32 bits)
     * @param initialB      Valor inicial do registrador B (32 bits)
     * @return Mapa com "log" (lista de ciclos), "logTexto" (log formatado) e "totalCiclos"
     */
    public Map<String, Object> processarProgramaMic1T1(
            List<String> instrucoesBin, int initialA, int initialB) {

        List<Map<String, Object>> log = new ArrayList<>();
        StringBuilder textoLog = new StringBuilder();
        String sep = "============================================================";

        // Cabeçalho
        textoLog.append("b = ").append(formatarBin32(initialB)).append("\n");
        textoLog.append("a = ").append(formatarBin32(initialA)).append("\n\n");
        textoLog.append("Start of Program\n");

        int pc = 1;
        for (String instrucaoBin : instrucoesBin) {
            textoLog.append(sep).append("\n");
            textoLog.append("Cycle ").append(pc).append("\n\n");
            textoLog.append("PC = ").append(pc).append("\n");

            // Linha vazia → EOP
            if (instrucaoBin.isBlank()) {
                textoLog.append("> Line is empty, EOP.\n\n");
                break;
            }

            int instrucao = Integer.parseInt(instrucaoBin, 2);
            textoLog.append("IR = ").append(
                String.format("%8s", instrucaoBin.trim()).replace(' ', '0')).append("\n");

            try {
                Map<String, Object> ciclo = processarInstrucaoMic1T1(instrucao, pc, initialA, initialB);
                log.add(ciclo);

                textoLog.append("b = ").append(ciclo.get("bBin")).append("\n");
                textoLog.append("a = ").append(ciclo.get("aBin")).append("\n");
                textoLog.append("s = ").append(ciclo.get("sBin")).append("\n");
                textoLog.append("sd = ").append(ciclo.get("sdBin")).append("\n");
                textoLog.append("n = ").append(ciclo.get("n")).append("\n");
                textoLog.append("z = ").append(ciclo.get("z")).append("\n");
                textoLog.append("co = ").append(ciclo.get("vaiUm")).append("\n");

            } catch (IllegalArgumentException e) {
                textoLog.append("> Error, invalid control signals.\n");
                Map<String, Object> erroEntry = new LinkedHashMap<>();
                erroEntry.put("pc", pc);
                erroEntry.put("erro", e.getMessage());
                log.add(erroEntry);
            }

            pc++;
        }

        // Ciclo extra de EOP se não encontrou linha vazia
        if (!instrucoesBin.isEmpty()) {
            String ultima = instrucoesBin.get(instrucoesBin.size() - 1);
            if (!ultima.isBlank()) {
                textoLog.append(sep).append("\n");
                textoLog.append("Cycle ").append(pc).append("\n\n");
                textoLog.append("PC = ").append(pc).append("\n");
                textoLog.append("> Line is empty, EOP.\n\n");
            }
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("log", log);
        resposta.put("logTexto", textoLog.toString());
        resposta.put("totalCiclos", log.size());
        return resposta;
    }

    // =========================================================================
    // TAREFA 2 — Banco de Registradores + Barramentos (21 bits)
    // =========================================================================

    /**
     * Processa uma única instrução da Tarefa 2 (palavra de 21 bits) usando o banco de registradores.
     *
     * <p><b>Decomposição dos 21 bits (MSB → LSB):</b></p>
     * <pre>
     *   bits [20..13] = controle da ULA (8 bits): SLL8 SRA1 F0 F1 ENA ENB INVA INC
     *   bits [12.. 4] = controle do Barramento C (9 bits): H OPC TOS CPP LV SP PC MDR MAR
     *   bits [ 3.. 0] = controle do Barramento B (4 bits): código 0-8
     * </pre>
     *
     * <p><b>Pipeline:</b></p>
     * <ol>
     *   <li>Tira snapshot do banco antes.</li>
     *   <li>Decodifica ULA, Barramento C e Barramento B.</li>
     *   <li>EntradaA = H (após ENA/INVA); EntradaB = registrador selecionado (após ENB).</li>
     *   <li>ULA: lógica + somador → S, vaiUm.</li>
     *   <li>Deslocador → Sd.</li>
     *   <li>Barramento C: escreve Sd nos registradores habilitados.</li>
     *   <li>Tira snapshot do banco depois.</li>
     *   <li>Retorna {@link EstadoMic1} imutável.</li>
     * </ol>
     *
     * @param ir    Palavra de controle de 21 bits (já convertida de binário para inteiro)
     * @param ciclo Número do ciclo atual (1-indexado)
     * @param banco Banco de registradores (mutável — é atualizado pelo Barramento C)
     * @return Estado imutável do ciclo executado
     * @throws IllegalArgumentException se SLL8=1 e SRA1=1 simultaneamente
     */
    public EstadoMic1 processarInstrucaoMic1T2(int ir, int ciclo, BancoRegistradores banco) {

        // ---- Decomposição da palavra de 21 bits ---------------------------
        int ulaControl = (ir >> 13) & 0xFF;   // 8 bits mais significativos (bits 20..13)
        int cBusMask   = (ir >>  4) & 0x1FF;  // 9 bits centrais (bits 12..4)
        int bBusCodigo = ir & 0xF;             // 4 bits menos significativos (bits 3..0)

        // ---- Bits de controle da ULA --------------------------------------
        int sll8 = (ulaControl >> 7) & 1;
        int sra1 = (ulaControl >> 6) & 1;
        int f0   = (ulaControl >> 5) & 1;
        int f1   = (ulaControl >> 4) & 1;
        int ena  = (ulaControl >> 3) & 1;
        int enb  = (ulaControl >> 2) & 1;
        int inva = (ulaControl >> 1) & 1;
        int inc  =  ulaControl       & 1;

        // ---- Validação SLL8 ⊕ SRA1 ----------------------------------------
        if (sll8 == 1 && sra1 == 1) {
            throw new IllegalArgumentException(
                "Sinais de controle inválidos: SLL8=1 e SRA1=1 simultâneos (ciclo " + ciclo + ")");
        }

        // ---- Snapshot ANTES -----------------------------------------------
        BancoRegistradores bancoAntes = new BancoRegistradores(banco);

        // ---- Barramento B: lê o valor do registrador fonte ----------------
        int valorB = banco.lerBarramentoB(bBusCodigo);
        String nomeBBus = banco.nomeBBarramento(bBusCodigo);
        List<String> nomesCBus = banco.nomesBarramentoC(cBusMask);

        // ---- Entrada A: sempre H ------------------------------------------
        int valorA = banco.getH();

        // ---- ULA ----------------------------------------------------------
        int[] ulaResult = executarULA(f0, f1, ena, enb, inva, inc, valorA, valorB);
        int effA  = ulaResult[0];
        int effB  = ulaResult[1];
        int s     = ulaResult[2];
        int vaiUm = ulaResult[3];

        // ---- Deslocador ---------------------------------------------------
        int sd = aplicarDeslocador(s, sll8, sra1);

        // ---- Flags --------------------------------------------------------
        int flagN = (sd >>> 31) & 1;
        int flagZ = (sd == 0) ? 1 : 0;

        // ---- Barramento C: escreve Sd nos registradores selecionados ------
        banco.escreverBarramentoC(cBusMask, sd);

        // ---- Snapshot DEPOIS ----------------------------------------------
        BancoRegistradores bancoDepois = new BancoRegistradores(banco);

        return new EstadoMic1(
            ciclo, ir, nomeBBus, nomesCBus,
            bancoAntes.toMap(), bancoDepois.toMap(),
            effA, effB, s, sd, vaiUm, flagN, flagZ
        );
    }

    /**
     * Processa um programa completo da Tarefa 2 (lista de instruções de 21 bits).
     *
     * <p>O banco de registradores acumula estado entre ciclos. Linhas vazias encerram a execução.</p>
     *
     * @param instrucoesBin Lista de strings binárias de 21 bits
     * @param banco         Banco de registradores com estado inicial carregado
     * @return Mapa com "log" ({@code List<EstadoMic1>}), "logTexto" (texto formatado),
     *         "registradoresIniciais" e "totalCiclos"
     */
    public Map<String, Object> processarProgramaMic1(
            List<String> instrucoesBin, BancoRegistradores banco) {

        List<EstadoMic1> log = new ArrayList<>();
        String sep = "=====================================================";

        // Snapshot inicial para exibição no log
        BancoRegistradores bancoInicial = new BancoRegistradores(banco);

        StringBuilder textoLog = new StringBuilder();

        // Cabeçalho: lista de instruções do programa
        for (String instrucao : instrucoesBin) {
            textoLog.append(instrucao.trim()).append("\n");
        }
        textoLog.append("\n");

        // Estado inicial dos registradores
        textoLog.append(sep).append("\n");
        textoLog.append("> Initial register states\n");
        textoLog.append(formatarMapaRegistradores(bancoInicial.toMap()));
        textoLog.append("\n");

        textoLog.append(sep).append("\n");
        textoLog.append("Start of program\n");

        int ciclo = 1;
        for (String instrucaoBin : instrucoesBin) {

            instrucaoBin = instrucaoBin.trim();

            // Linha vazia → EOP
            if (instrucaoBin.isEmpty()) {
                textoLog.append(sep).append("\n");
                textoLog.append("Cycle ").append(ciclo).append("\n");
                textoLog.append("No more lines, EOP.\n");
                break;
            }

            int ir = (int) Long.parseLong(instrucaoBin, 2);
            EstadoMic1 estado = processarInstrucaoMic1T2(ir, ciclo, banco);
            log.add(estado);

            textoLog.append(estado.gerarLogTexto(instrucoesBin.size()));
            ciclo++;
        }

        // EOP ao final da lista sem linha vazia explícita
        boolean temLinhaVaziaExplicita = instrucoesBin.stream().anyMatch(String::isBlank);
        if (!instrucoesBin.isEmpty() && !temLinhaVaziaExplicita) {
            textoLog.append(sep).append("\n");
            textoLog.append("Cycle ").append(ciclo).append("\n");
            textoLog.append("No more lines, EOP.\n");
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("registradoresIniciais", bancoInicial.toMap());
        resposta.put("log", log);
        resposta.put("logTexto", textoLog.toString());
        resposta.put("totalCiclos", log.size());
        return resposta;
    }

    // =========================================================================
    // Métodos internos compartilhados
    // =========================================================================

    /**
     * Executa a lógica interna da ULA Mic-1 sobre os valores de entrada.
     *
     * <p>Esta implementação é compartilhada entre a Tarefa 1 e a Tarefa 2.
     * Os valores A e B recebidos são os valores BRUTOS dos barramentos; os enables
     * (ENA/ENB) e a inversão (INVA) são aplicados internamente.</p>
     *
     * <p><b>Operações da unidade lógica (F0, F1):</b></p>
     * <ul>
     *   <li>00 → AND:   effA &amp; effB</li>
     *   <li>01 → OR:    effA | effB</li>
     *   <li>10 → NOT B: ~effB</li>
     *   <li>11 → ADD:   effA + effB</li>
     * </ul>
     *
     * <p><b>Carry:</b> O somador completo opera com {@code long} (64 bits) para capturar
     * corretamente o carry-out do bit 32 sem overflow do Java.</p>
     *
     * @param f0   Bit mais significativo do seletor de operação (0 ou 1)
     * @param f1   Bit menos significativo do seletor de operação (0 ou 1)
     * @param ena  Enable do barramento A (0 força effA = 0)
     * @param enb  Enable do barramento B (0 força effB = 0)
     * @param inva Inversão bitwise de A (aplicada após ENA)
     * @param inc  Carry-in (vem-um) do somador completo
     * @param a    Valor bruto do barramento A (32 bits)
     * @param b    Valor bruto do barramento B (32 bits)
     * @return Vetor {@code int[4]} contendo: [effA, effB, s (32 bits), vaiUm (0 ou 1)]
     */
    private int[] executarULA(int f0, int f1, int ena, int enb, int inva, int inc, int a, int b) {

        // Aplica enables: força 0 quando desabilitado
        int effA = (ena == 1) ? a : 0;
        int effB = (enb == 1) ? b : 0;

        // Inversão de A (após ENA)
        if (inva == 1) effA = ~effA;

        // Unidade lógica — opera em long para manter a semântica sem sinal de 32 bits
        long logicOut;
        if      (f0 == 0 && f1 == 0) logicOut = (effA & effB)  & 0xFFFFFFFFL;  // AND
        else if (f0 == 0 && f1 == 1) logicOut = (effA | effB)  & 0xFFFFFFFFL;  // OR
        else if (f0 == 1 && f1 == 0) logicOut = (~effB)        & 0xFFFFFFFFL;  // NOT B
        else                          logicOut = (effA & 0xFFFFFFFFL)           // A + B (ADD)
                                               + (effB & 0xFFFFFFFFL);

        // Somador completo: lógica + carry-in (INC)
        long soma   = logicOut + inc;
        int  s      = (int)(soma         & 0xFFFFFFFFL);
        int  vaiUm  = (int)((soma >> 32) & 1L);

        return new int[]{ effA, effB, s, vaiUm };
    }

    /**
     * Aplica o deslocador pós-ULA sobre o resultado S.
     *
     * <ul>
     *   <li>SLL8=1: deslocamento lógico para a esquerda em 8 bits ({@code s << 8}).
     *       Os 8 bits mais significativos de S são descartados.</li>
     *   <li>SRA1=1: deslocamento aritmético para a direita em 1 bit ({@code s >> 1}).
     *       O bit de sinal (bit 31) é preservado pelo operador {@code >>} do Java.</li>
     *   <li>Ambos 0: passa direto ({@code Sd = S}).</li>
     * </ul>
     *
     * <p><b>Pré-condição:</b> SLL8 e SRA1 não podem ser 1 simultaneamente
     * (deve ser validado antes desta chamada).</p>
     *
     * @param s    Resultado de 32 bits da ULA
     * @param sll8 Sinal de controle SLL8 (0 ou 1)
     * @param sra1 Sinal de controle SRA1 (0 ou 1)
     * @return Valor deslocado Sd (32 bits)
     */
    private int aplicarDeslocador(int s, int sll8, int sra1) {
        if (sll8 == 1) return s << 8;   // lógico: os 8 MSBs de S são perdidos
        if (sra1 == 1) return s >> 1;   // aritmético: bit 31 é replicado no bit 30
        return s;                        // sem deslocamento
    }

    // =========================================================================
    // Utilitários de formatação
    // =========================================================================

    /**
     * Formata um valor de 32 bits como string binária de 32 caracteres (zeros à esquerda).
     *
     * @param valor Valor inteiro (32 bits com sinal)
     * @return String de 32 caracteres, ex: "11111111111111111111111110000001"
     */
    private String formatarBin32(int valor) {
        return String.format("%32s", Integer.toBinaryString(valor & 0xFFFFFFFF))
                     .replace(' ', '0');
    }

    /**
     * Formata um mapa de registradores como bloco de texto de log.
     * O registrador MBR é exibido com 8 bits; os demais com 32 bits.
     *
     * @param registradores Mapa ordenado nome→valor (ex: do {@link BancoRegistradores#toMap()})
     * @return Bloco de texto com uma linha por registrador
     */
    private String formatarMapaRegistradores(Map<String, Integer> registradores) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : registradores.entrySet()) {
            String nome  = entry.getKey();
            int valor    = entry.getValue();
            String binStr;
            if ("mbr".equals(nome)) {
                binStr = String.format("%8s", Integer.toBinaryString(valor & 0xFF)).replace(' ', '0');
            } else {
                binStr = formatarBin32(valor);
            }
            sb.append(nome).append(" = ").append(binStr).append("\n");
        }
        return sb.toString();
    }
}
