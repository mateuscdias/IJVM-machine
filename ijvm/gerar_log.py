#!/usr/bin/env python3
"""
MIC-1 ALU Execution Log Generator

This script automates the process of executing MIC-1 ALU programs and generating
detailed execution logs by interacting with the Spring Boot REST API.

Overview:
    Uploads a text file containing 6-bit binary instructions to the IJVM ALU
    service, retrieves the execution results, and formats them into a readable
    .log file with cycle-by-cycle state information.

Features:
    - Automatic file validation before upload
    - Timestamp-based log file naming to prevent overwrites
    - Organized storage in dedicated 'logs/' directory
    - Full state capture (PC, IR, registers A/B, result S, carry-out)
    - Windows-compatible line endings (CRLF)
    - Detailed error handling and user feedback

Workflow:
    1. Validates input file existence
    2. Uploads file to http://localhost:8080/api/ula/executar-arquivo
    3. Parses JSON response with execution log
    4. Creates logs/ directory if it doesn't exist
    5. Generates formatted .log file with timestamp
    6. Displays summary and file location

Dependencies:
    - requests library (HTTP client)
    - Python 3.6+ for f-string support

Usage Example:
    $ python3 gerar_log.py etapa1.txt
    📤 Enviando arquivo: etapa1.txt
    ✅ Log gerado: logs/etapa1_20231201_143025.log
       Total de ciclos: 4

Author: Miguel Mochizuki Silva, Mateus C. Dias, Joaquim Germano Félix, Gabriel Bringel Gonçalves
Version: 1.0
Date: 2024
License: Proprietary
"""

import requests
import sys
import os
from datetime import datetime
from typing import Optional, Dict, Any


def gerar_log_com_upload(arquivo_entrada: str) -> None:
    """
    Generate a detailed execution log file by uploading instructions to the ALU service.

    This function serves as the main entry point for log generation. It orchestrates
    the complete workflow:
        1. File validation
        2. HTTP upload to Spring Boot service
        3. Response processing
        4. Log file formatting and writing

    The generated log file follows the MIC-1 specification format with:
        - Initial register values (A=0xFFFFFFFF, B=0x00000001)
        - Cycle-by-cycle state dumps
        - Program counter progression
        - Final End-of-Program (EOP) marker

    Output Format Example:
        b = 00000000000000000000000000000001
        a = 11111111111111111111111111111111

        Start of Program
        ============================================================
        Cycle 1

        PC = 1
        IR = 111100
        b = 00000000000000000000000000000001
        a = 11111111111111111111111111111111
        s = 11111111111111111111111111111110
        co = 0
        ============================================================
        Cycle 2
        ...
        Cycle 5

        PC = 5
        > Line is empty, EOP.

    File Naming Convention:
        Format: {base_name}_{YYYYMMDD_HHMMSS}.log
        Example: etapa1_20231201_143025.log
        Location: logs/ directory (created automatically)

    Error Handling:
        - Missing input file: Displays error and exits gracefully
        - Connection refused: Guides user to start Spring Boot application
        - HTTP error codes: Displays status and response body
        - JSON parsing errors: Propagates with context

    Args:
        arquivo_entrada: Path to the input text file containing 6-bit binary
                        instructions (one per line). Lines starting with '#'
                        are treated as comments and ignored.

    Returns:
        None. Creates a .log file in the logs/ directory and prints status
        messages to stdout. On error, prints error message without creating file.

    Raises:
        FileNotFoundError: Propagated if arquivo_entrada doesn't exist
                           (handled internally with user message)
        requests.exceptions.ConnectionError: If Spring Boot server is not running
        KeyError: If unexpected JSON structure from API response
        OSError: If unable to create logs/ directory or write log file

    Example:
        >>> gerar_log_com_upload("programa.txt")
        📤 Enviando arquivo: programa.txt
        ✅ Log gerado: logs/programa_20231201_143025.log
           Total de ciclos: 42

    See Also:
        - ULA controller endpoint: POST /api/ula/executar-arquivo
        - Service expects: Multipart file with field name 'arquivo'
        - API response format: {"log": [...], "totalInstrucoes": N}
    """

    # Validate input file existence
    if not os.path.exists(arquivo_entrada):
        print(f"❌ Arquivo não encontrado: {arquivo_entrada}")
        return

    print(f"📤 Enviando arquivo: {arquivo_entrada}")

    try:
        # Perform HTTP multipart file upload to Spring Boot service
        # The endpoint expects a file field named 'arquivo'
        with open(arquivo_entrada, 'rb') as f:
            files = {'arquivo': (os.path.basename(arquivo_entrada), f, 'text/plain')}
            response = requests.post(
                'http://localhost:8080/api/ula/executar-arquivo',
                files=files,
                timeout=30  # 30-second timeout for large programs
            )

        # Check for HTTP errors before processing
        if response.status_code != 200:
            print(f"❌ Erro HTTP: {response.status_code}")
            print(response.text)
            return

        # Parse JSON response containing execution log
        data: Dict[str, Any] = response.json()

    except requests.exceptions.ConnectionError:
        print("❌ Erro: Não foi possível conectar ao servidor Spring Boot")
        print("   Certifique-se que a aplicação está rodando em http://localhost:8080")
        return
    except requests.exceptions.Timeout:
        print("❌ Erro: Timeout ao aguardar resposta do servidor")
        print("   O programa pode ser muito longo ou o servidor está sobrecarregado")
        return
    except ValueError as e:
        print(f"❌ Erro ao parsear resposta JSON: {e}")
        print("   Resposta recebida:", response.text[:200])
        return
    except Exception as e:
        print(f"❌ Erro inesperado: {e}")
        return

    # Create logs directory if it doesn't exist
    # Uses exist_ok=True to avoid race conditions in concurrent execution
    logs_dir = "logs"
    if not os.path.exists(logs_dir):
        try:
            os.makedirs(logs_dir)
        except OSError as e:
            print(f"❌ Erro ao criar diretório '{logs_dir}': {e}")
            return

    # Generate unique log filename with timestamp to prevent overwrites
    nome_base = os.path.basename(arquivo_entrada).replace('.txt', '')
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    nome_saida = os.path.join(logs_dir, f"{nome_base}_{timestamp}.log")

    def to_bin_32bits(valor: int) -> str:
        """
        Convert a 32-bit integer to its binary string representation.

        This helper function ensures consistent 32-bit formatting by masking
        the value to unsigned 32-bit range before conversion. This handles
        Python's arbitrary-precision integers correctly.

        Args:
            valor: Signed or unsigned integer (will be masked to 32 bits)

        Returns:
            Zero-padded 32-character binary string (e.g., "00000000000000000000000000000001")

        Examples:
            >>> to_bin_32bits(1)
            '00000000000000000000000000000001'
            >>> to_bin_32bits(-1)
            '11111111111111111111111111111111'
            >>> to_bin_32bits(0xFFFFFFFF)
            '11111111111111111111111111111111'
        """
        return format(valor & 0xFFFFFFFF, '032b')

    # Write formatted log file with Windows line endings (CRLF) for compatibility
    try:
        with open(nome_saida, 'w', encoding='utf-8', newline='\r\n') as f:
            # Write initial register state header
            f.write(f"b = {to_bin_32bits(0x00000001)}\n")
            f.write(f"a = {to_bin_32bits(0xFFFFFFFF)}\n")
            f.write("\n")
            f.write("Start of Program\n")
            f.write("=" * 60 + "\n")

            # Write execution cycle details for each instruction
            # Each cycle includes: PC, IR, register values, ALU result, carry-out
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

            # Write end-of-program marker
            # PC increments one beyond the last instruction
            ultimo_ciclo = data['totalInstrucoes'] + 1
            f.write(f"Cycle {ultimo_ciclo}\n")
            f.write("\n")
            f.write(f"PC = {ultimo_ciclo}\n")
            f.write("> Line is empty, EOP.\n")
            f.write("\n")

    except IOError as e:
        print(f"❌ Erro ao escrever arquivo de log: {e}")
        return
    except KeyError as e:
        print(f"❌ Erro: Estrutura JSON inesperada - campo '{e}' não encontrado")
        print("   Verifique se a API retornou o formato esperado")
        return

    # Display success message with file location and statistics
    print(f"✅ Log gerado: {nome_saida}")
    print(f"   Total de ciclos: {data['totalInstrucoes']}")


if __name__ == "__main__":
    """
    Command-line interface for the log generator script.

    Usage:
        python3 gerar_log.py <arquivo.txt>

    Arguments:
        arquivo.txt: Path to the text file containing ALU instructions
                    (one 6-bit binary instruction per line)

    Exit Codes:
        0: Success (log generated)
        1: Error (invalid arguments, file not found, server unavailable, etc.)

    Environment Requirements:
        - Python 3.6 or higher
        - requests library installed (pip install requests)
        - Spring Boot application running on http://localhost:8080

    Examples:
        $ python3 gerar_log.py etapa1.txt
        📤 Enviando arquivo: etapa1.txt
        ✅ Log gerado: logs/etapa1_20231201_143025.log
           Total de ciclos: 4

        $ python3 gerar_log.py
        Uso: python3 gerar_log.py <arquivo.txt>

        $ python3 gerar_log.py inexistente.txt
        ❌ Arquivo não encontrado: inexistente.txt
    """

    # Validate command line arguments
    if len(sys.argv) < 2:
        print("Uso: python3 gerar_log.py <arquivo.txt>")
        print("\nDescrição:")
        print("  Gera arquivo .log detalhado a partir de um arquivo de instruções")
        print("  para a ULA IJVM, utilizando o serviço REST Spring Boot.")
        print("\nArgumentos:")
        print("  arquivo.txt  - Arquivo com instruções binárias de 6 bits")
        print("\nExemplo:")
        print("  python3 gerar_log.py etapa1.txt")
        sys.exit(1)

    # Execute main log generation function
    gerar_log_com_upload(sys.argv[1])
    sys.exit(0)
