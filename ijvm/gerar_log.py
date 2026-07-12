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
    python3 gerar_log.py --mic <programa.txt> <registradores.txt>
    python3 gerar_log.py --mic23 <microinstrucoes.txt> <registradores.txt> <dados.txt>
    python3 gerar_log.py --ijvm <instrucoes.txt> <registradores.txt> <dados.txt>

Bit-width auto-detection:
    If the first valid instruction line is 8 characters long -> 8-bit mode.
    If it is 6 characters long -> 6-bit mode.

In --ijvm mode the memory is printed once per high-level instruction, while the
registers are still shown before and after every microinstruction.

Author: Miguel Mochizuki Silva, Mateus C. Dias, Joaquim Germano Felix,
        Gabriel Bringel Goncalves
Version: 2.3
"""

import sys
import os
import argparse
import json
import socket
import urllib.error
import urllib.request
import uuid
from datetime import datetime
from typing import Optional, Dict, Any

try:
    import requests
except ImportError:
    requests = None


class _SimpleResponse:
    def __init__(self, status_code: int, body: bytes):
        self.status_code = status_code
        self.text = body.decode('utf-8', errors='replace')

    def json(self):
        return json.loads(self.text)


class _RequestsFallback:
    class exceptions:
        ConnectionError = urllib.error.URLError
        Timeout = socket.timeout

    @staticmethod
    def post(endpoint: str, files: Dict, data: Dict = None, timeout: int = 30):
        boundary = "----ijvm-" + uuid.uuid4().hex
        body = bytearray()

        for name, value in (data or {}).items():
            body.extend(f"--{boundary}\r\n".encode())
            body.extend(
                f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
            body.extend(str(value).encode())
            body.extend(b"\r\n")

        for name, spec in files.items():
            filename, fileobj, content_type = spec
            body.extend(f"--{boundary}\r\n".encode())
            body.extend(
                f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode())
            body.extend(f"Content-Type: {content_type}\r\n\r\n".encode())
            body.extend(fileobj.read())
            body.extend(b"\r\n")

        body.extend(f"--{boundary}--\r\n".encode())

        request = urllib.request.Request(
            endpoint,
            data=bytes(body),
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
            method="POST",
        )

        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return _SimpleResponse(response.getcode(), response.read())
        except urllib.error.HTTPError as e:
            return _SimpleResponse(e.code, e.read())


if requests is None:
    requests = _RequestsFallback()


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
        f.write(f"b = {to_bin_32bits(estado['a'])}\n")
        f.write(f"a = {to_bin_32bits(estado['b'])}\n")
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

    f.write(f"b = {to_bin_32bits(initial_a)}\n")
    f.write(f"a = {to_bin_32bits(initial_b)}\n")
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
            f.write(f"b = {to_bin_32bits(estado['a'])}\n")
            f.write(f"a = {to_bin_32bits(estado['b'])}\n")
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


# ===========================================================================
# 21-bit MIC-1 log support
# ===========================================================================

_REG_ORDER = ['MAR', 'MDR', 'PC', 'MBR', 'SP', 'LV', 'CPP', 'TOS', 'OPC', 'H']
_SEP = "=" * 53


def upload_mic_program(arquivo_programa: str, arquivo_regs: str) -> Optional[Dict[str, Any]]:
    """Upload program + register file to /api/mic/executar-arquivo."""
    for f in [arquivo_programa, arquivo_regs]:
        if not os.path.exists(f):
            print(f"Arquivo nao encontrado: {f}")
            return None

    endpoint = "http://localhost:8080/api/mic/executar-arquivo"
    print(f"Enviando programa:      {arquivo_programa}")
    print(f"Enviando registradores: {arquivo_regs}")

    try:
        with open(arquivo_programa, 'rb') as fp, open(arquivo_regs, 'rb') as fr:
            files = {
                'programa':      (os.path.basename(arquivo_programa), fp, 'text/plain'),
                'registradores': (os.path.basename(arquivo_regs),     fr, 'text/plain'),
            }
            response = requests.post(endpoint, files=files, timeout=30)

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
        print("Nao foi possivel conectar ao servidor Spring Boot (http://localhost:8080)")
        return None
    except Exception as e:
        print(f"Erro inesperado: {e}")
        return None


def _fmt_reg_mic(name: str, val: int) -> str:
    """Format one register as 'name = <binary>' (MBR = 8 bits, others = 32 bits)."""
    if name.upper() == 'MBR':
        return f"{name.lower()} = {format(val & 0xFF, '08b')}"
    return f"{name.lower()} = {format(val & 0xFFFFFFFF, '032b')}"


def _fmt_regs_block_mic(regs: Dict[str, int]) -> list:
    return [_fmt_reg_mic(name, regs[name]) for name in _REG_ORDER]


def write_log_mic(f, data: Dict[str, Any]) -> None:
    """Write a 21-bit MIC-1 execution log in the exact expected format."""
    instructions = [c['ir'] for c in data['log']]

    for instr in instructions:
        f.write(instr + "\n")

    f.write(_SEP + "\n")
    f.write("> Initial register states\n")
    if data['log']:
        for line in _fmt_regs_block_mic(data['log'][0]['inicio']):
            f.write(line + "\n")

    f.write(_SEP + "\n")
    f.write("Start of program\n")
    f.write(_SEP + "\n")

    last_ciclo = 0
    for ciclo in data['log']:
        last_ciclo = ciclo['ciclo']
        ir = ciclo['ir']
        ir_fmt = f"{ir[0:8]} {ir[8:17]} {ir[17:21]}"

        f.write(f"Cycle {ciclo['ciclo']}\n")
        f.write(f"ir = {ir_fmt}\n")
        f.write(f"b_bus = {ciclo['busBRegistrador']}\n")
        bus_c = ciclo['busCRegistradores']
        f.write(f"c_bus = {', '.join(bus_c) if bus_c else '(none)'}\n")
        f.write("> Registers before instruction\n")
        for line in _fmt_regs_block_mic(ciclo['inicio']):
            f.write(line + "\n")
        f.write("> Registers after instruction\n")
        for line in _fmt_regs_block_mic(ciclo['fim']):
            f.write(line + "\n")
        f.write(_SEP + "\n")

    f.write(f"Cycle {last_ciclo}\n")
    f.write("No more lines, EOP.\n\n")


def gerar_log_mic(arquivo_programa: str, arquivo_regs: str) -> None:
    """Full pipeline for 21-bit MIC-1 log generation."""
    data = upload_mic_program(arquivo_programa, arquivo_regs)
    if data is None:
        return

    logs_dir = "logs"
    os.makedirs(logs_dir, exist_ok=True)

    nome_base  = os.path.splitext(os.path.basename(arquivo_programa))[0]
    timestamp  = datetime.now().strftime("%Y%m%d_%H%M%S")
    nome_saida = os.path.join(logs_dir, f"{nome_base}_{timestamp}.log")

    try:
        with open(nome_saida, 'w', encoding='utf-8', newline='\r\n') as f:
            write_log_mic(f, data)
    except (IOError, KeyError) as e:
        print(f"Erro ao escrever log: {e}")
        return

    print(f"Log gerado: {nome_saida}")
    print(f"   Total de ciclos: {data['totalInstrucoes']}")


# ===========================================================================
# 23-bit MIC-1 log support
# ===========================================================================

def upload_mic23_program(
        arquivo_programa: str, arquivo_regs: str, arquivo_memoria: str) -> Optional[Dict[str, Any]]:
    """Upload program + register + memory files to /api/mic/executar-arquivo23."""
    for f in [arquivo_programa, arquivo_regs, arquivo_memoria]:
        if not os.path.exists(f):
            print(f"Arquivo nao encontrado: {f}")
            return None

    endpoint = "http://localhost:8080/api/mic/executar-arquivo23"
    print(f"Enviando programa:      {arquivo_programa}")
    print(f"Enviando registradores: {arquivo_regs}")
    print(f"Enviando memoria:       {arquivo_memoria}")

    try:
        with open(arquivo_programa, 'rb') as fp, \
             open(arquivo_regs, 'rb') as fr, \
             open(arquivo_memoria, 'rb') as fm:
            files = {
                'programa':      (os.path.basename(arquivo_programa), fp, 'text/plain'),
                'registradores': (os.path.basename(arquivo_regs),     fr, 'text/plain'),
                'memoria':       (os.path.basename(arquivo_memoria),  fm, 'text/plain'),
            }
            response = requests.post(endpoint, files=files, timeout=30)

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
        print("Nao foi possivel conectar ao servidor Spring Boot (http://localhost:8080)")
        return None
    except Exception as e:
        print(f"Erro inesperado: {e}")
        return None


def _write_star_block(f, title: str, lines: list) -> None:
    f.write(title + "\n")
    f.write("*" * 31 + "\n")
    for line in lines:
        f.write(line + "\n")


def write_log_mic23(f, data: Dict[str, Any]) -> None:
    """Write a 23-bit MIC-1 execution log compatible with the stage 3 example."""
    f.write("=" * 60 + "\n")
    _write_star_block(f, "Initial memory state", data['memoriaInicial'])
    _write_star_block(f, "Initial register state",
                      _fmt_regs_block_mic(data['registradoresIniciais']))
    f.write("=" * 60 + "\n")
    f.write("Start of Program\n")
    f.write("=" * 60 + "\n")

    last_ciclo = 0
    for ciclo in data['log']:
        last_ciclo = ciclo['ciclo']
        ir = ciclo['ir']
        ir_fmt = f"{ir[0:8]} {ir[8:17]} {ir[17:19]} {ir[19:23]}"

        f.write(f"Cycle {ciclo['ciclo']}\n")
        f.write(f"ir = {ir_fmt}\n")
        f.write(f"b = {ciclo['busBRegistrador']}\n")
        bus_c = ciclo['busCRegistradores']
        f.write(f"c = {', '.join(bus_c) if bus_c else '(none)'}\n")
        f.write("\n")
        _write_star_block(f, "> Registers before instruction",
                          _fmt_regs_block_mic(ciclo['inicio']))
        f.write("\n")
        _write_star_block(f, "> Registers after instruction",
                          _fmt_regs_block_mic(ciclo['fim']))
        f.write("\n")
        _write_star_block(f, "> Memory after instruction", ciclo['memoriaFim'])
        f.write("=" * 60 + "\n")

    f.write(f"Cycle {last_ciclo + 1}\n")
    f.write("No more lines, EOP.\n\n")


def gerar_log_mic23(
        arquivo_programa: str, arquivo_regs: str, arquivo_memoria: str) -> None:
    """Full pipeline for 23-bit MIC-1 log generation."""
    data = upload_mic23_program(arquivo_programa, arquivo_regs, arquivo_memoria)
    if data is None:
        return

    logs_dir = "logs"
    os.makedirs(logs_dir, exist_ok=True)

    nome_base = os.path.splitext(os.path.basename(arquivo_programa))[0]
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    nome_saida = os.path.join(logs_dir, f"{nome_base}_mic23_{timestamp}.log")

    try:
        with open(nome_saida, 'w', encoding='utf-8', newline='\r\n') as f:
            write_log_mic23(f, data)
    except (IOError, KeyError) as e:
        print(f"Erro ao escrever log: {e}")
        return

    print(f"Log gerado: {nome_saida}")
    print(f"   Total de ciclos: {data['totalInstrucoes']}")


# ===========================================================================
# IJVM log support (high-level instructions)
# ===========================================================================

def upload_ijvm_program(
        arquivo_instrucoes: str, arquivo_regs: str, arquivo_memoria: str) -> Optional[Dict[str, Any]]:
    """Upload instruction + register + memory files to /api/mic/executar-ijvm."""
    for f in [arquivo_instrucoes, arquivo_regs, arquivo_memoria]:
        if not os.path.exists(f):
            print(f"Arquivo nao encontrado: {f}")
            return None

    endpoint = "http://localhost:8080/api/mic/executar-ijvm"
    print(f"Enviando instrucoes:    {arquivo_instrucoes}")
    print(f"Enviando registradores: {arquivo_regs}")
    print(f"Enviando memoria:       {arquivo_memoria}")

    try:
        with open(arquivo_instrucoes, 'rb') as fi, \
             open(arquivo_regs, 'rb') as fr, \
             open(arquivo_memoria, 'rb') as fm:
            files = {
                'instrucoes':    (os.path.basename(arquivo_instrucoes), fi, 'text/plain'),
                'registradores': (os.path.basename(arquivo_regs),       fr, 'text/plain'),
                'memoria':       (os.path.basename(arquivo_memoria),    fm, 'text/plain'),
            }
            response = requests.post(endpoint, files=files, timeout=30)

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
        print("Nao foi possivel conectar ao servidor Spring Boot (http://localhost:8080)")
        return None
    except Exception as e:
        print(f"Erro inesperado: {e}")
        return None


def _write_cycle_ijvm(f, ciclo: Dict[str, Any]) -> None:
    """
    Write one microinstruction cycle: the 23-bit IR split into its four fields,
    the bus B source, the bus C destinations and the registers before and after.

    A fetch cycle (memory bits = 11) bypasses the ULA and writes nothing through
    bus C, so both buses are reported as '-' and the cycle is marked as a fetch.
    """
    ir = ciclo['ir']
    ir_fmt = f"{ir[0:8]} {ir[8:17]} {ir[17:19]} {ir[19:23]}"

    f.write(f"Cycle {ciclo['ciclo']}\n")
    f.write(f"ir = {ir_fmt}\n")

    if ciclo.get('operacaoMemoria') == 'fetch':
        f.write("b = -\n")
        f.write("c = - (fetch)\n")
    else:
        f.write(f"b = {ciclo['busBRegistrador']}\n")
        bus_c = ciclo['busCRegistradores']
        f.write(f"c = {', '.join(bus_c) if bus_c else '(none)'}\n")

    f.write("\n")
    _write_star_block(f, "> Registers before instruction",
                      _fmt_regs_block_mic(ciclo['inicio']))
    f.write("\n")
    _write_star_block(f, "> Registers after instruction",
                      _fmt_regs_block_mic(ciclo['fim']))


def write_log_ijvm(f, data: Dict[str, Any]) -> None:
    """
    Write an IJVM execution log.

    Registers are shown before and after every microinstruction, while the data
    memory is printed once per high-level instruction — that is the difference
    from the 23-bit log of task 1, which prints memory on every cycle. Cycle
    numbering is continuous across the whole program.
    """
    f.write("=" * 60 + "\n")
    _write_star_block(f, "Initial memory state", data['memoriaInicial'])
    f.write("*" * 31 + "\n")
    _write_star_block(f, "Initial register state",
                      _fmt_regs_block_mic(data['registradoresIniciais']))
    f.write("=" * 60 + "\n")
    f.write("Start of Program\n")
    f.write("=" * 60 + "\n")

    for bloco in data['blocos']:
        instrucao = bloco['instrucao']

        f.write(f"Instruction: {instrucao}\n")
        f.write("=" * 60 + "\n")

        for i, ciclo in enumerate(bloco['ciclos']):
            if i:
                f.write("-" * 60 + "\n")
            _write_cycle_ijvm(f, ciclo)

        f.write("=" * 60 + "\n")
        _write_star_block(f, f"> Memory after instruction {instrucao}",
                          bloco['memoriaApos'])
        f.write("=" * 60 + "\n")

    f.write(_SEP + "\n")
    f.write("End of Program, EOP.\n")


def gerar_log_ijvm(
        arquivo_instrucoes: str, arquivo_regs: str, arquivo_memoria: str) -> None:
    """Full pipeline for IJVM log generation."""
    data = upload_ijvm_program(arquivo_instrucoes, arquivo_regs, arquivo_memoria)
    if data is None:
        return

    logs_dir = "logs"
    os.makedirs(logs_dir, exist_ok=True)

    nome_base = os.path.splitext(os.path.basename(arquivo_instrucoes))[0]
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    nome_saida = os.path.join(logs_dir, f"{nome_base}_ijvm_{timestamp}.log")

    try:
        with open(nome_saida, 'w', encoding='utf-8', newline='\r\n') as f:
            write_log_ijvm(f, data)
    except (IOError, KeyError) as e:
        print(f"Erro ao escrever log: {e}")
        return

    print(f"Log gerado: {nome_saida}")
    print(f"   Instrucoes IJVM: {data['totalInstrucoes']}"
          f"  |  Total de ciclos: {data['totalCiclos']}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="MIC-1 ALU log generator",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Modes:
  6-bit / 8-bit ULA (single file):
    python3 gerar_log.py programa.txt
    python3 gerar_log.py programa.txt --bits 8
    python3 gerar_log.py programa.txt --bits 8 --a -2147483648 --b 1

  21-bit MIC-1 (program + register file):
    python3 gerar_log.py --mic programa.txt registradores.txt

  23-bit MIC-1 (program + register + memory file):
    python3 gerar_log.py --mic23 microinstrucoes.txt registradores.txt dados.txt

  IJVM (high-level instructions + register + memory file):
    python3 gerar_log.py --ijvm instrucoes.txt registradores.txt dados.txt
        """
    )

    parser.add_argument("arquivo",
        help="Program file. For --mic/--mic23/--ijvm mode this is the instruction file.")
    parser.add_argument("registradores", nargs="?", default=None,
        help="Register file (.txt). Required for --mic, --mic23 and --ijvm modes.")
    parser.add_argument("memoria", nargs="?", default=None,
        help="Memory file (.txt). Required for --mic23 and --ijvm modes.")
    parser.add_argument("--mic", action="store_true",
        help="21-bit MIC-1 mode (requires a register file as second argument)")
    parser.add_argument("--mic23", action="store_true",
        help="23-bit MIC-1 mode (requires register and memory files)")
    parser.add_argument("--ijvm", action="store_true",
        help="IJVM mode: ILOAD/DUP/BIPUSH (requires register and memory files)")
    parser.add_argument("--bits", type=int, choices=[6, 8], default=None,
        help="ULA instruction width (default: auto-detect). Ignored in --mic mode.")
    parser.add_argument("--a", type=int, default=None,
        help="Initial A register value (ULA modes only).")
    parser.add_argument("--b", type=int, default=None,
        help="Initial B register value (ULA modes only).")

    args = parser.parse_args()

    if sum([args.mic, args.mic23, args.ijvm]) > 1:
        print("Erro: use apenas um modo por vez (--mic, --mic23 ou --ijvm).")
        sys.exit(1)

    if args.ijvm:
        if args.registradores is None or args.memoria is None:
            print("Erro: --ijvm requer arquivos de registradores e memoria.")
            print("  Uso: python3 gerar_log.py --ijvm instrucoes.txt registradores.txt dados.txt")
            sys.exit(1)
        for f in [args.arquivo, args.registradores, args.memoria]:
            if not os.path.exists(f):
                print(f"Arquivo nao encontrado: {f}")
                sys.exit(1)
        gerar_log_ijvm(args.arquivo, args.registradores, args.memoria)
        sys.exit(0)

    if args.mic23:
        if args.registradores is None or args.memoria is None:
            print("Erro: --mic23 requer arquivos de registradores e memoria.")
            print("  Uso: python3 gerar_log.py --mic23 programa.txt registradores.txt dados.txt")
            sys.exit(1)
        for f in [args.arquivo, args.registradores, args.memoria]:
            if not os.path.exists(f):
                print(f"Arquivo nao encontrado: {f}")
                sys.exit(1)
        gerar_log_mic23(args.arquivo, args.registradores, args.memoria)
        sys.exit(0)

    if args.mic:
        if args.registradores is None:
            print("Erro: --mic requer um arquivo de registradores como segundo argumento.")
            print("  Uso: python3 gerar_log.py --mic programa.txt registradores.txt")
            sys.exit(1)
        if not os.path.exists(args.arquivo):
            print(f"Arquivo nao encontrado: {args.arquivo}")
            sys.exit(1)
        if not os.path.exists(args.registradores):
            print(f"Arquivo nao encontrado: {args.registradores}")
            sys.exit(1)
        gerar_log_mic(args.arquivo, args.registradores)
        sys.exit(0)

    if args.bits is None:
        if not os.path.exists(args.arquivo):
            print(f"Arquivo nao encontrado: {args.arquivo}")
            sys.exit(1)
        args.bits = detect_bit_width(args.arquivo)
        print(f"Modo detectado automaticamente: {args.bits}-bit")

    if args.a is None:
        args.a = to_java_int(0x80000000) if args.bits == 8 else to_java_int(0xFFFFFFFF)
    if args.b is None:
        args.b = 0x00000001

    gerar_log(args.arquivo, args.bits, args.a, args.b)
    sys.exit(0)
