#!/usr/bin/env python3
"""
MIC-1 ALU Execution Log Generator (v2 – 8-bit extension)

Supports both the original 6-bit instruction set and the extended 8-bit
instruction set with SLL8 / SRA1 shifter outputs and N / Z flags.

Usage:
    python3 gerar_log.py <arquivo.txt>               # auto-detect bit width
    python3 gerar_log.py <arquivo.txt> --bits 6      # force 6-bit mode
    python3 gerar_log.py <arquivo.txt> --bits 8      # force 8-bit mode
    python3 gerar_log.py <arquivo.txt> --bits 8 --a -2147483648 --b 1

Bit-width auto-detection:
    If the first valid instruction line is 8 characters long -> 8-bit mode.
    If it is 6 characters long -> 6-bit mode.

Author: Miguel Mochizuki Silva, Mateus C. Dias, Joaquim Germano Felix,
        Gabriel Bringel Goncalves
Version: 2.2
"""

import requests
import sys
import os
import argparse
from datetime import datetime
from typing import Optional, Dict, Any


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def to_bin_32bits(valor: int) -> str:
    """Return a zero-padded 32-bit binary string for any Python int."""
    return format(valor & 0xFFFFFFFF, '032b')


def to_java_int(v: int) -> int:
    """
    Reinterpret a Python int as a Java signed 32-bit integer.

    Java's Integer range is [-2147483648, 2147483647].  Python's 0x80000000
    equals 2147483648, which overflows Java Integer — so we mask to 32 bits
    and apply two's-complement sign extension, exactly as Java would.

    Examples:
        0x80000000  ->  -2147483648
        0xFFFFFFFF  ->  -1
        0x00000001  ->   1
    """
    v = v & 0xFFFFFFFF
    return v if v < 0x80000000 else v - 0x100000000


def detect_bit_width(arquivo: str) -> int:
    """
    Peek at the first valid instruction line in *arquivo* and return 6 or 8.
    Returns 8 if the first line has 8 characters, 6 otherwise.
    """
    with open(arquivo, encoding='utf-8') as f:
        for raw in f:
            line = raw.strip()
            if line and not line.startswith('#'):
                return 8 if len(line) >= 8 else 6
    return 6


def upload_program(arquivo: str, endpoint: str, extra_params: Dict = None) -> Optional[Dict[str, Any]]:
    """
    Upload *arquivo* to *endpoint* via multipart POST.

    Register values (a, b) are sent as multipart form DATA fields so that
    Spring Boot resolves them alongside MultipartFile without binding errors.
    Values are converted to Java signed 32-bit range before serialisation to
    avoid MethodArgumentTypeMismatchException (e.g. 0x80000000 -> -2147483648).
    """
    if not os.path.exists(arquivo):
        print(f"Arquivo nao encontrado: {arquivo}")
        return None

    print(f"Enviando arquivo: {arquivo}  ->  {endpoint}")

    try:
        with open(arquivo, 'rb') as f:
            files = {'arquivo': (os.path.basename(arquivo), f, 'text/plain')}

            # Convert each value to Java-signed range BEFORE turning into a string.
            # Without this, 0x80000000 becomes "2147483648" which overflows Integer.
            data_fields = {
                k: str(to_java_int(v))
                for k, v in (extra_params or {}).items()
            }

            response = requests.post(
                endpoint,
                files=files,
                data=data_fields,
                timeout=30,
            )

        if response.status_code != 200:
            print(f"Erro HTTP: {response.status_code}")
            try:
                body = response.json()
                msg = body.get('message') or body.get('erro') or response.text
            except Exception:
                msg = response.text
            print(f"   Detalhes: {msg}")
            return None

        return response.json()

    except requests.exceptions.ConnectionError:
        print("Nao foi possivel conectar ao servidor Spring Boot.")
        print("   Certifique-se que a aplicacao esta rodando em http://localhost:8080")
        return None
    except requests.exceptions.Timeout:
        print("Timeout ao aguardar resposta do servidor.")
        return None
    except ValueError as e:
        print(f"Erro ao parsear JSON: {e}")
        return None
    except Exception as e:
        print(f"Erro inesperado: {e}")
        return None


# ---------------------------------------------------------------------------
# Log writers
# ---------------------------------------------------------------------------

def write_log_6bit(f, data: Dict[str, Any]) -> None:
    """Write a 6-bit execution log in the original format."""
    f.write(f"b = {to_bin_32bits(0x00000001)}\n")
    f.write(f"a = {to_bin_32bits(0xFFFFFFFF)}\n")
    f.write("\n")
    f.write("Start of Program\n")
    f.write("=" * 60 + "\n")

    for estado in data['log']:
        f.write(f"Cycle {estado['pc']}\n")
        f.write("\n")
        f.write(f"PC = {estado['pc']}\n")
        f.write(f"IR = {estado['irBin']}\n")
        f.write(f"b = {to_bin_32bits(estado['b'])}\n")
        f.write(f"a = {to_bin_32bits(estado['a'])}\n")
        f.write(f"s = {to_bin_32bits(estado['s'])}\n")
        f.write(f"co = {estado['vaiUm']}\n")
        f.write("=" * 60 + "\n")

    ultimo_ciclo = data['totalInstrucoes'] + 1
    f.write(f"Cycle {ultimo_ciclo}\n\n")
    f.write(f"PC = {ultimo_ciclo}\n")
    f.write("> Line is empty, EOP.\n\n")


def write_log_8bit(f, data: Dict[str, Any]) -> None:
    """
    Write an 8-bit execution log in the extended format.

    Normal cycle:
        Cycle N
        (blank line)
        PC = N
        IR = <8-bit bin>
        b = <32-bit bin>
        a = <32-bit bin>
        s = <32-bit bin>
        sd = <32-bit bin>
        n = <0|1>
        z = <0|1>
        co = <0|1>

    Invalid-signals cycle (SLL8=SRA1=1):
        Cycle N
        PC = N
        IR = <8-bit bin>
        > Error, invalid control signals.
    """
    initial_a = data.get('initialA', 0x80000000)
    initial_b = data.get('initialB', 0x00000001)

    f.write(f"b = {to_bin_32bits(initial_b)}\n")
    f.write(f"a = {to_bin_32bits(initial_a)}\n")
    f.write("\n")
    f.write("Start of Program\n")
    f.write("=" * 60 + "\n")

    for estado in data['log']:
        f.write(f"Cycle {estado['pc']}\n")

        if estado.get('invalidSignals', False):
            f.write(f"PC = {estado['pc']}\n")
            f.write(f"IR = {estado['irBin']}\n")
            f.write("> Error, invalid control signals.\n")
        else:
            f.write("\n")
            f.write(f"PC = {estado['pc']}\n")
            f.write(f"IR = {estado['irBin']}\n")
            f.write(f"b = {to_bin_32bits(estado['b'])}\n")
            f.write(f"a = {to_bin_32bits(estado['a'])}\n")
            f.write(f"s = {to_bin_32bits(estado['s'])}\n")
            f.write(f"sd = {to_bin_32bits(estado['sd'])}\n")
            f.write(f"n = {estado['n']}\n")
            f.write(f"z = {estado['z']}\n")
            f.write(f"co = {estado['vaiUm']}\n")

        f.write("=" * 60 + "\n")

    ultimo_ciclo = data['totalInstrucoes'] + 1
    f.write(f"Cycle {ultimo_ciclo}\n\n")
    f.write(f"PC = {ultimo_ciclo}\n")
    f.write("> Line is empty, EOP.\n\n")


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------

def gerar_log(arquivo_entrada: str, bits: int, initial_a: int, initial_b: int) -> None:
    """Full pipeline: upload -> parse -> write log file."""
    base_url = "http://localhost:8080/api/ula"

    if bits == 8:
        endpoint = f"{base_url}/executar-arquivo8"
        extra    = {"a": initial_a, "b": initial_b}
    else:
        endpoint = f"{base_url}/executar-arquivo"
        extra    = {}

    data = upload_program(arquivo_entrada, endpoint, extra)
    if data is None:
        return

    logs_dir = "logs"
    os.makedirs(logs_dir, exist_ok=True)

    nome_base  = os.path.splitext(os.path.basename(arquivo_entrada))[0]
    timestamp  = datetime.now().strftime("%Y%m%d_%H%M%S")
    nome_saida = os.path.join(logs_dir, f"{nome_base}_{timestamp}.log")

    try:
        with open(nome_saida, 'w', encoding='utf-8', newline='\r\n') as f:
            if bits == 8:
                write_log_8bit(f, data)
            else:
                write_log_6bit(f, data)

    except IOError as e:
        print(f"Erro ao escrever arquivo de log: {e}")
        return
    except KeyError as e:
        print(f"Estrutura JSON inesperada - campo '{e}' nao encontrado")
        return

    print(f"Log gerado: {nome_saida}")
    print(f"   Modo: {bits}-bit  |  Total de ciclos: {data['totalInstrucoes']}")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="MIC-1 ALU log generator (6-bit and 8-bit instruction sets)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 gerar_log.py programa_etapa1.txt
  python3 gerar_log.py programa_etapa2_tarefa1.txt --bits 8
  python3 gerar_log.py programa.txt --bits 8 --a -2147483648 --b 1
        """
    )
    parser.add_argument("arquivo", help="Text file with binary instructions (one per line)")
    parser.add_argument("--bits", type=int, choices=[6, 8], default=None,
                        help="Instruction word width (default: auto-detect)")
    parser.add_argument("--a", type=int, default=None,
                        help="Initial A register value (default: 0xFFFFFFFF for 6-bit, 0x80000000 for 8-bit)")
    parser.add_argument("--b", type=int, default=None,
                        help="Initial B register value (default: 0x00000001)")

    args = parser.parse_args()

    if args.bits is None:
        if not os.path.exists(args.arquivo):
            print(f"Arquivo nao encontrado: {args.arquivo}")
            sys.exit(1)
        args.bits = detect_bit_width(args.arquivo)
        print(f"Modo detectado automaticamente: {args.bits}-bit")

    if args.a is None:
        # Use to_java_int so 0x80000000 becomes -2147483648, not 2147483648
        args.a = to_java_int(0x80000000) if args.bits == 8 else to_java_int(0xFFFFFFFF)
    if args.b is None:
        args.b = 0x00000001

    gerar_log(args.arquivo, args.bits, args.a, args.b)
    sys.exit(0)