#!/usr/bin/env python3
"""
MIC-1 Execution Log Generator
==============================

Automatiza a execução de programas Mic-1 e gera logs detalhados
interagindo com a API REST Spring Boot em http://localhost:8080.

Modos de operação
-----------------
  Etapa 1  (6 bits)        — endpoint original, A/B fixos:
    python3 gerar_log.py programa_etapa1.txt

  Etapa 2 Tarefa 1 (8 bits + deslocador), A/B padrão ou customizados:
    python3 gerar_log.py --etapa2-t1 programa_etapa2_tarefa1.txt
    python3 gerar_log.py --etapa2-t1 programa_etapa2_tarefa1.txt --a 1 --b -2147483648

  Etapa 2 Tarefa 2 (21 bits, banco de registradores):
    python3 gerar_log.py --etapa2-t2 programa_etapa2_tarefa2.txt registradores_etapa2_tarefa2.txt

Saída
-----
  Todos os logs são salvos em ./logs/<nome_base>_<timestamp>.log

Dependências
------------
  pip install requests
  Python 3.8+
  Servidor Spring Boot rodando em http://localhost:8080

Autores: Miguel Mochizuki Silva, Mateus C. Dias,
         Joaquim Germano Félix, Gabriel Bringel Gonçalves
Versão: 2.0
"""

import sys
import os
import argparse
import requests
from datetime import datetime
from typing import Optional

# ---------------------------------------------------------------------------
# Constantes
# ---------------------------------------------------------------------------

BASE_URL     = "http://localhost:8080"
TIMEOUT      = 30       # segundos
LOGS_DIR     = "logs"
SEPARATOR_60 = "=" * 60
SEPARATOR_53 = "=" * 53


# ---------------------------------------------------------------------------
# Utilitários compartilhados
# ---------------------------------------------------------------------------

def to_bin32(valor: int) -> str:
    """Converte inteiro para string binária de 32 caracteres (com zeros à esquerda)."""
    return format(valor & 0xFFFFFFFF, "032b")


def to_bin8(valor: int) -> str:
    """Converte inteiro para string binária de 8 caracteres (usado no MBR)."""
    return format(valor & 0xFF, "08b")


def criar_diretorio_logs() -> bool:
    """Cria o diretório de logs se não existir. Retorna True em caso de sucesso."""
    if not os.path.exists(LOGS_DIR):
        try:
            os.makedirs(LOGS_DIR)
        except OSError as e:
            print(f"❌ Erro ao criar diretório '{LOGS_DIR}': {e}")
            return False
    return True


def caminho_saida(nome_arquivo: str, sufixo: str = "") -> str:
    """Gera o caminho do arquivo de log com timestamp único."""
    nome_base = os.path.basename(nome_arquivo).replace(".txt", "")
    if sufixo:
        nome_base = f"{nome_base}_{sufixo}"
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return os.path.join(LOGS_DIR, f"{nome_base}_{timestamp}.log")


def verificar_arquivo(caminho: str) -> bool:
    """Verifica se o arquivo existe. Exibe erro e retorna False se não existir."""
    if not os.path.exists(caminho):
        print(f"❌ Arquivo não encontrado: {caminho}")
        return False
    return True


def tratar_erro_conexao(e: Exception) -> None:
    """Exibe mensagem de erro de conexão amigável."""
    if isinstance(e, requests.exceptions.ConnectionError):
        print("❌ Erro: não foi possível conectar ao servidor Spring Boot.")
        print("   Certifique-se que a aplicação está rodando em http://localhost:8080")
        print("   Comando: cd ijvm && ./mvnw spring-boot:run")
    elif isinstance(e, requests.exceptions.Timeout):
        print("❌ Erro: timeout aguardando resposta do servidor.")
    else:
        print(f"❌ Erro inesperado: {e}")


def salvar_log(caminho: str, conteudo: str) -> bool:
    """Salva o log no arquivo. Retorna True em caso de sucesso."""
    try:
        with open(caminho, "w", encoding="utf-8", newline="\r\n") as f:
            f.write(conteudo)
        return True
    except IOError as e:
        print(f"❌ Erro ao escrever arquivo de log: {e}")
        return False


# ---------------------------------------------------------------------------
# Modo Etapa 1 — 6 bits, A/B fixos
# ---------------------------------------------------------------------------

def etapa1(arquivo_entrada: str) -> None:
    """
    Envia um programa de 6 bits para /api/ula/executar-arquivo e gera log.

    Formato do arquivo: uma instrução binária de 6 bits por linha.
    Linhas com '#' são comentários.

    Exemplo:
        python3 gerar_log.py programa_etapa1.txt
    """
    if not verificar_arquivo(arquivo_entrada):
        return

    print(f"📤 [Etapa 1] Enviando: {arquivo_entrada}")

    try:
        with open(arquivo_entrada, "rb") as f:
            files = {"arquivo": (os.path.basename(arquivo_entrada), f, "text/plain")}
            resp = requests.post(
                f"{BASE_URL}/api/ula/executar-arquivo",
                files=files,
                timeout=TIMEOUT,
            )

        if resp.status_code != 200:
            print(f"❌ Erro HTTP {resp.status_code}: {resp.text}")
            return

        data = resp.json()

    except Exception as e:
        tratar_erro_conexao(e)
        return

    # Valores iniciais fixos da Etapa 1
    val_a = 0xFFFFFFFF
    val_b = 0x00000001

    linhas = []
    linhas.append(f"b = {to_bin32(val_b)}")
    linhas.append(f"a = {to_bin32(val_a)}")
    linhas.append("")
    linhas.append("Start of Program")
    linhas.append(SEPARATOR_60)

    for estado in data["log"]:
        linhas.append(f"Cycle {estado['pc']}")
        linhas.append("")
        linhas.append(f"PC = {estado['pc']}")
        linhas.append(f"IR = {estado['irBin']}")
        linhas.append(f"b = {to_bin32(estado['b'])}")
        linhas.append(f"a = {to_bin32(estado['a'])}")
        linhas.append(f"s = {to_bin32(estado['s'])}")
        linhas.append(f"co = {estado['vaiUm']}")
        linhas.append(SEPARATOR_60)

    ultimo = data["totalInstrucoes"] + 1
    linhas.append(f"Cycle {ultimo}")
    linhas.append("")
    linhas.append(f"PC = {ultimo}")
    linhas.append("> Line is empty, EOP.")
    linhas.append("")

    if not criar_diretorio_logs():
        return

    saida = caminho_saida(arquivo_entrada)
    if salvar_log(saida, "\n".join(linhas)):
        print(f"✅ Log gerado: {saida}")
        print(f"   Total de ciclos: {data['totalInstrucoes']}")


# ---------------------------------------------------------------------------
# Modo Etapa 2 Tarefa 1 — 8 bits (ULA + Deslocador)
# ---------------------------------------------------------------------------

def etapa2_tarefa1(arquivo_entrada: str, val_a: int, val_b: int) -> None:
    """
    Envia instruções de 8 bits para /api/ula/mic1/tarefa1/executar e gera log.

    Formato da instrução: [SLL8|SRA1|F0|F1|ENA|ENB|INVA|INC]
    O log inclui S (resultado da ULA), Sd (após deslocador), flags N e Z.

    Exemplos:
        python3 gerar_log.py --etapa2-t1 programa_etapa2_tarefa1.txt
        python3 gerar_log.py --etapa2-t1 programa_etapa2_tarefa1.txt --a 1 --b -2147483648
    """
    if not verificar_arquivo(arquivo_entrada):
        return

    print(f"📤 [Etapa 2 – Tarefa 1] Enviando: {arquivo_entrada}")
    print(f"   a = {to_bin32(val_a)}  ({val_a})")
    print(f"   b = {to_bin32(val_b)}  ({val_b})")

    # Lê as instruções do arquivo (ignora comentários e linhas em branco)
    instrucoes = []
    with open(arquivo_entrada, encoding="utf-8") as f:
        for linha in f:
            linha = linha.strip()
            if not linha.startswith("#"):
                instrucoes.append(linha)   # inclui linhas vazias para EOP

    payload = {"instrucoes": instrucoes, "a": val_a, "b": val_b}

    try:
        resp = requests.post(
            f"{BASE_URL}/api/ula/mic1/tarefa1/executar",
            json=payload,
            timeout=TIMEOUT,
        )

        if resp.status_code != 200:
            print(f"❌ Erro HTTP {resp.status_code}: {resp.text}")
            return

        data = resp.json()

    except Exception as e:
        tratar_erro_conexao(e)
        return

    # A API já retorna o log textual formatado no campo "logTexto"
    log_texto: str = data.get("logTexto", "")
    total: int = data.get("totalCiclos", 0)

    if not criar_diretorio_logs():
        return

    saida = caminho_saida(arquivo_entrada, "t1")
    if salvar_log(saida, log_texto):
        print(f"✅ Log gerado: {saida}")
        print(f"   Total de ciclos processados: {total}")


# ---------------------------------------------------------------------------
# Modo Etapa 2 Tarefa 2 — 21 bits (Banco de Registradores + Barramentos)
# ---------------------------------------------------------------------------

def etapa2_tarefa2(arquivo_programa: str, arquivo_registradores: str) -> None:
    """
    Envia dois arquivos para /api/ula/mic1/tarefa2/executar-arquivo e gera log.

    Formato do programa:    uma instrução binária de 21 bits por linha.
    Formato dos registradores: 'nome = <valor binário>' por linha.

    Exemplo:
        python3 gerar_log.py --etapa2-t2 \\
            programa_etapa2_tarefa2.txt \\
            registradores_etapa2_tarefa2.txt
    """
    if not verificar_arquivo(arquivo_programa):
        return
    if not verificar_arquivo(arquivo_registradores):
        return

    print(f"📤 [Etapa 2 – Tarefa 2] Enviando:")
    print(f"   programa      → {arquivo_programa}")
    print(f"   registradores → {arquivo_registradores}")

    try:
        with open(arquivo_programa, "rb") as fp, \
             open(arquivo_registradores, "rb") as fr:

            files = {
                "programa":      (os.path.basename(arquivo_programa),      fp, "text/plain"),
                "registradores": (os.path.basename(arquivo_registradores), fr, "text/plain"),
            }
            resp = requests.post(
                f"{BASE_URL}/api/ula/mic1/tarefa2/executar-arquivo",
                files=files,
                timeout=TIMEOUT,
            )

        if resp.status_code != 200:
            print(f"❌ Erro HTTP {resp.status_code}: {resp.text}")
            return

        data = resp.json()

    except Exception as e:
        tratar_erro_conexao(e)
        return

    # A API já retorna o log textual completo no campo "logTexto"
    log_texto: str = data.get("logTexto", "")
    total: int = data.get("totalCiclos", 0)

    if not criar_diretorio_logs():
        return

    saida = caminho_saida(arquivo_programa, "t2")
    if salvar_log(saida, log_texto):
        print(f"✅ Log gerado: {saida}")
        print(f"   Total de ciclos processados: {total}")


# ---------------------------------------------------------------------------
# CLI — parsing de argumentos
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        prog="gerar_log.py",
        description=(
            "Gerador de logs de execução para o emulador Mic-1.\n"
            "Requer o servidor Spring Boot rodando em http://localhost:8080.\n"
            "Inicie com: cd ijvm && ./mvnw spring-boot:run"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
exemplos:
  # Etapa 1 (6 bits, A/B fixos):
  python3 gerar_log.py programa_etapa1.txt

  # Etapa 2 Tarefa 1 (8 bits + deslocador), valores padrão a=1 b=0x80000000:
  python3 gerar_log.py --etapa2-t1 programa_etapa2_tarefa1.txt

  # Etapa 2 Tarefa 1 com valores customizados:
  python3 gerar_log.py --etapa2-t1 programa_etapa2_tarefa1.txt --a 1 --b -2147483648

  # Etapa 2 Tarefa 2 (21 bits + banco de registradores):
  python3 gerar_log.py --etapa2-t2 programa_etapa2_tarefa2.txt registradores_etapa2_tarefa2.txt
        """,
    )

    # Modo de operação (mutuamente exclusivos)
    grupo = parser.add_mutually_exclusive_group()
    grupo.add_argument(
        "--etapa2-t1",
        action="store_true",
        help="Etapa 2 Tarefa 1 — instruções de 8 bits com deslocador (ULA expandida)",
    )
    grupo.add_argument(
        "--etapa2-t2",
        action="store_true",
        help="Etapa 2 Tarefa 2 — instruções de 21 bits com banco de registradores",
    )

    # Argumentos posicionais
    parser.add_argument(
        "arquivo",
        help=(
            "Arquivo de programa (.txt).\n"
            "  Etapa 1 / T1: instruções binárias (6 ou 8 bits) por linha.\n"
            "  Tarefa 2: instruções binárias de 21 bits por linha."
        ),
    )
    parser.add_argument(
        "registradores",
        nargs="?",          # opcional — obrigatório apenas para --etapa2-t2
        default=None,
        help="[--etapa2-t2 apenas] Arquivo de registradores iniciais (.txt).",
    )

    # Valores iniciais A e B (Tarefa 1)
    parser.add_argument(
        "--a",
        type=int,
        default=1,
        metavar="VALOR",
        help="Valor inicial do registrador A para --etapa2-t1 (padrão: 1)",
    )
    parser.add_argument(
        "--b",
        type=int,
        default=-2147483648,   # 0x80000000
        metavar="VALOR",
        help="Valor inicial do registrador B para --etapa2-t1 (padrão: -2147483648)",
    )

    args = parser.parse_args()

    # Roteamento para o modo correto
    if args.etapa2_t2:
        if args.registradores is None:
            parser.error(
                "--etapa2-t2 requer dois arquivos: "
                "python3 gerar_log.py --etapa2-t2 <programa.txt> <registradores.txt>"
            )
        etapa2_tarefa2(args.arquivo, args.registradores)

    elif args.etapa2_t1:
        etapa2_tarefa1(args.arquivo, args.a, args.b)

    else:
        # Modo padrão: Etapa 1
        if args.registradores is not None:
            parser.error(
                "O segundo arquivo de registradores só é usado com --etapa2-t2."
            )
        etapa1(args.arquivo)


if __name__ == "__main__":
    main()
