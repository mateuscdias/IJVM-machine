# IJVM — MIC-1 ALU & Datapath Simulator

A Spring Boot REST API that simulates the MIC-1 microarchitecture from Tanenbaum's *Structured Computer Organization*. The project is organized in three stages, each building on the previous.

---

## Authors

- Miguel Mochizuki Silva
- Mateus C. Dias
- Joaquim Germano Félix
- Gabriel Bringel Gonçalves

---

## Project Structure

```
src/main/java/com/bisha/ijvm/
├── UlaApplication.java
├── controller/
│   ├── UlaController.java      # ALU endpoints (6-bit and 8-bit)
│   └── MicController.java      # Endpoints MIC-1 de 21/23 bits e IJVM
├── model/
│   ├── BlocoInstrucao.java      # Ciclos agrupados por instrução IJVM
│   ├── EstadoULA.java          # ALU cycle state (single instruction)
│   ├── EstadoCiclo.java        # MIC-1 cycle state (single instruction)
│   ├── Memoria.java            # Memória de dados em palavras de 32 bits
│   └── Registradores.java      # MIC-1 register file
└── service/
    ├── UlaService.java         # ALU execution logic
    ├── MicService.java         # MIC-1 datapath and memory execution
    └── TradutorService.java    # Tradução de ILOAD, DUP e BIPUSH

gerar_log.py                    # Log generation utility (all modes)
```

---

## Running the Application

```bash
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## Stage 1 — 6-bit ALU

### Instruction Format

```
X0  X1  X2  X3  X4  X5
F0  F1  ENA ENB INVA INC
```

| Bits | Signal | Description |
|------|--------|-------------|
| X0 | F0 | ALU function selector (MSB) |
| X1 | F1 | ALU function selector (LSB) |
| X2 | ENA | Enable A bus (0 forces A = 0) |
| X3 | ENB | Enable B bus (0 forces B = 0) |
| X4 | INVA | Invert A bus (bitwise complement) |
| X5 | INC | Carry-in to full adder |

### ALU Operations

| F0 | F1 | Operation |
|----|----|-----------|
| 0 | 0 | A AND B |
| 0 | 1 | A OR B |
| 1 | 0 | NOT B |
| 1 | 1 | A + B |

Initial register values: **A = `0xFFFFFFFF`**, **B = `0x00000001`**.

### Endpoints

#### `POST /api/ula/executar`
Execute a program from a JSON body.

```bash
curl -X POST http://localhost:8080/api/ula/executar \
  -H "Content-Type: application/json" \
  -d '{"instrucoes": ["111100", "110101", "110100", "011100"]}'
```

#### `POST /api/ula/executar-arquivo`
Execute a program from an uploaded `.txt` file (one 6-bit instruction per line).

```bash
curl -F "arquivo=@programa_etapa1.txt" \
  http://localhost:8080/api/ula/executar-arquivo
```

### Response Fields

| Field | Description |
|-------|-------------|
| `pc` | Program counter (1-indexed) |
| `irBin` | Instruction as 6-bit binary string |
| `a`, `aHex`, `aBin` | Effective A value |
| `b`, `bHex`, `bBin` | Effective B value |
| `s`, `sHex`, `sBin` | ALU result S |
| `vaiUm` | Carry-out (0 or 1) |

---

## Stage 2 — Task 1: 8-bit ALU with Shifter and Flags

Extends the 6-bit ALU with two shift control signals and two output flags.

### Instruction Format

```
X0   X1   X2  X3  X4  X5  X6   X7
SLL8 SRA1 F0  F1  ENA ENB INVA INC
```

| Signal | Description |
|--------|-------------|
| SLL8 | Shift output S left 8 bits (logical) |
| SRA1 | Shift output S right 1 bit (arithmetic, sign-preserving) |
| F0, F1 | ALU function (same table as Stage 1) |
| ENA, ENB, INVA, INC | Same as Stage 1 |

> **Constraint:** SLL8 and SRA1 must never both be 1. If they are, the instruction is flagged as invalid and no registers are written.

### Additional Outputs

| Output | Description |
|--------|-------------|
| `Sd` | Shifted result (S after applying SLL8 or SRA1) |
| `N` | 1 when Sd is negative (MSB = 1) |
| `Z` | 1 when Sd is zero |

The shifter is applied **after** the ALU: `Sd = shift(S)`. Flags N and Z are computed on `Sd`, not on `S`.

Initial register values: **A = `0x80000000`**, **B = `0x00000001`** (configurable).

### Endpoints

#### `POST /api/ula/executar8`
Execute from JSON. Optionally supply initial register values.

```bash
curl -X POST http://localhost:8080/api/ula/executar8 \
  -H "Content-Type: application/json" \
  -d '{
    "instrucoes": ["10111100", "01111100", "11111100"],
    "a": -2147483648,
    "b": 1
  }'
```

#### `POST /api/ula/executar-arquivo8`
Execute from an uploaded file. Register values can be passed as form parameters.

```bash
curl -F "arquivo=@programa_etapa2_tarefa1.txt" \
     -F "a=-2147483648" \
     -F "b=1" \
     http://localhost:8080/api/ula/executar-arquivo8
```

### Additional Response Fields

| Field | Description |
|-------|-------------|
| `sd`, `sdHex`, `sdBin` | Shifted output Sd |
| `n` | Negative flag (0 or 1) |
| `z` | Zero flag (0 or 1) |
| `invalidSignals` | `true` if SLL8 = SRA1 = 1 |

---

## Stage 2 — Task 2: MIC-1 Register File and Datapath

Adds the full MIC-1 register file and connects it to the ALU via bus B (decoder) and bus C (selector).

### Registers

| Register | Width | Description |
|----------|-------|-------------|
| H | 32-bit | Always drives ALU input A |
| OPC | 32-bit | Opcode register |
| TOS | 32-bit | Top of stack |
| CPP | 32-bit | Constant pool pointer |
| LV | 32-bit | Local variable pointer |
| SP | 32-bit | Stack pointer |
| PC | 32-bit | Program counter |
| MDR | 32-bit | Memory data register |
| MAR | 32-bit | Memory address register |
| MBR | 8-bit | Memory byte register |

### 21-bit Instruction Format

```
[  0 : 7  ]  [  8 : 16  ]  [ 17 : 20 ]
  8-bit ULA   9-bit Bus C   4-bit Bus B
```

#### Bus B decoder (4-bit value → register driving ALU input B)

| Value | Register | Extension |
|-------|----------|-----------|
| 0 | MDR | — |
| 1 | PC | — |
| 2 | MBR | Sign-extended to 32 bits |
| 3 | MBRU | Zero-extended to 32 bits |
| 4 | SP | — |
| 5 | LV | — |
| 6 | CPP | — |
| 7 | OPC | — |
| 8 | TOS | — |

#### Bus C selector (9-bit string, read left to right)

```
position: 0    1    2    3    4    5    6    7    8
register: H    OPC  TOS  CPP  LV   SP   PC   MDR  MAR
```

A `1` at a given position means the shifted ALU output `Sd` is written to that register at the end of the cycle.

#### Execution pipeline per cycle

1. Snapshot registers (state **before**)
2. Read A = H
3. Decode bus B → read B
4. Execute 8-bit ALU on A, B → compute Sd
5. Write Sd to all bus C selected registers
6. Snapshot registers (state **after**)

### Register File Format

One register per line. Values can be 32-bit binary, 8-bit binary (MBR), hex (`0x`), or decimal.

```
mar = 00000000000000000000000000000000
mdr = 00000000000000000000000000000000
pc  = 00000000000000000000000000000000
mbr = 10000001
sp  = 00000000000000000000000000000000
lv  = 00000000000000000000000000000000
cpp = 00000000000000000000000000000000
tos = 00000000000000000000000000000010
opc = 00000000000000000000000000000000
h   = 00000000000000000000000000000001
```

Lines starting with `#` are treated as comments and ignored.

### Endpoints

#### `POST /api/mic/executar`
Execute from a JSON body.

```bash
curl -X POST http://localhost:8080/api/mic/executar \
  -H "Content-Type: application/json" \
  -d '{
    "instrucoes": ["001101001010000000000", "001111000000000100100"],
    "registradores": {
      "H": 1, "OPC": 0, "TOS": 2, "CPP": 0,
      "LV": 0, "SP": 0, "PC": 0, "MDR": 0, "MAR": 0, "MBR": 129
    }
  }'
```

#### `POST /api/mic/executar-arquivo`
Execute from two uploaded files: the program and the register file.

```bash
curl -F "programa=@programa_etapa2_tarefa2.txt" \
     -F "registradores=@registradores_etapa2_tarefa2.txt" \
     http://localhost:8080/api/mic/executar-arquivo
```

### Response Fields (per cycle)

| Field | Description |
|-------|-------------|
| `ciclo` | Cycle number (1-indexed) |
| `ir` | 21-bit instruction string |
| `busBRegistrador` | Name of the register driving bus B |
| `busCRegistradores` | List of register names written by bus C |
| `sd`, `sdHex` | Shifted ALU output written to bus C |
| `inicio` | Register snapshot before execution |
| `fim` | Register snapshot after execution |

---

## Stage 3 - Data Memory (23-bit)

Stage 3 adds a two-bit memory-control field between buses C and B:

```
[  0 : 7  ]  [  8 : 16  ]  [17:18]  [ 19 : 22 ]
  8-bit ALU   9-bit Bus C    Memory    4-bit Bus B
```

| Memory bits | Operation |
|-------------|-----------|
| `00` | No memory operation |
| `01` | `READ`: `MDR = memory[MAR]` |
| `10` | `WRITE`: `memory[MAR] = MDR` |
| `11` | Special `fetch` used by `BIPUSH` |

Bus C is written before the memory operation. Therefore, `READ` and `WRITE`
observe the new values of MAR and MDR when those registers are selected in the
same microinstruction. Data memory is loaded from a text file containing one
32-bit binary word per line.

The `fetch` case bypasses the ALU and data memory. Its first eight bits are
copied to MBR and zero-extended into H.

### Endpoint

```bash
curl -F "programa=@microinstruções_etapa3_tarefa1.txt" \
     -F "registradores=@registradores_etapa3_tarefa1.txt" \
     -F "memoria=@dados_etapa3_tarefa1.txt" \
     http://localhost:8080/api/mic/executar-arquivo23
```

The response includes the initial state and, for every microinstruction, the
register snapshots before and after execution, buses B and C, memory operation,
and resulting memory snapshot.

---

## Final Deliverable - IJVM Translation

The final endpoint reads and executes these high-level IJVM instructions:

| Instruction | Translation |
|-------------|-------------|
| `ILOAD x` | `H=LV`; repeat `H=H+1` x times; `MAR=H;rd`; `MAR=SP=SP+1;wr`; `TOS=MDR` |
| `DUP` | `MAR=SP=SP+1`; `MDR=TOS;wr` |
| `BIPUSH byte` | `MAR=SP=SP+1`; dynamic `fetch`; `MDR=TOS=H;wr` |

`ILOAD` accepts a non-negative decimal index. `BIPUSH` accepts exactly eight
binary digits. The execution result is grouped by IJVM instruction, while cycle
numbers remain continuous across the entire program. Each group contains the
microinstruction cycles and the memory state after the IJVM instruction.

### Endpoint

```bash
curl -F "instrucoes=@instruções.txt" \
     -F "registradores=@registradores_etapa3_atualizado.txt" \
     -F "memoria=@dados_etapa3_atualizado.txt" \
     http://localhost:8080/api/mic/executar-ijvm
```

The supplied memory must contain every address reached by MAR. For the complete
example beginning with `SP = 7`, the included `dados_etapa3_atualizado.txt` has
16 words so the stack can grow to addresses 8, 9 and 10.

---

## Log Generator (`gerar_log.py`)

A Python utility that calls the REST API and writes a formatted `.log` file.

### Requirements

```bash
pip install requests
```

### Usage

#### 6-bit ALU (auto-detect or explicit)
```bash
python3 gerar_log.py programa_etapa1.txt
python3 gerar_log.py programa_etapa1.txt --bits 6
```

#### 8-bit ALU with shifter
```bash
python3 gerar_log.py programa_etapa2_tarefa1.txt --bits 8
python3 gerar_log.py programa_etapa2_tarefa1.txt --bits 8 --a -2147483648 --b 1
```

#### MIC-1 datapath (21-bit)
```bash
python3 gerar_log.py --mic programa_etapa2_tarefa2.txt registradores_etapa2_tarefa2.txt
```

#### MIC-1 with memory (23-bit)

```bash
python3 gerar_log.py --mic23 microinstruções_etapa3_tarefa1.txt \
  registradores_etapa3_tarefa1.txt dados_etapa3_tarefa1.txt
```

#### Final IJVM program

```bash
python3 gerar_log.py --ijvm instruções.txt \
  registradores_etapa3_atualizado.txt dados_etapa3_atualizado.txt
```

### Output

Logs are saved to the `logs/` directory with a timestamp in the filename:

```
logs/programa_etapa2_tarefa2_20240101_120000.log
```

### Log Format — 6-bit

```
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

PC = 2
> Line is empty, EOP.
```

### Log Format — 8-bit

```
b = 10000000000000000000000000000000
a = 00000000000000000000000000000001

Start of Program
============================================================
Cycle 1

PC = 1
IR = 10111100
b = 10000000000000000000000000000000
a = 00000000000000000000000000000001
s = 10000000000000000000000000000001
sd = 00000000000000000000000100000000
n = 0
z = 0
co = 0
============================================================
Cycle 2
PC = 2
IR = 11111100
> Error, invalid control signals.
============================================================
Cycle 2
> Line is empty, EOP.
```

### Log Format — 21-bit MIC-1

```
001101000000110000010
001101000000010000011
=====================================================
> Initial register states
mar = 00000000000000000000000000000000
mdr = 00000000000000000000000000000000
pc  = 00000000000000000000000000000000
mbr = 10000001
sp  = 00000000000000000000000000000000
lv  = 00000000000000000000000000000000
cpp = 00000000000000000000000000000000
tos = 00000000000000000000000000000010
opc = 00000000000000000000000000000000
h   = 00000000000000000000000000000001
=====================================================
Start of program
=====================================================
Cycle 1
ir = 00110100 000011000 0010
b_bus = mbr
c_bus = lv, sp
> Registers before instruction
mar = 00000000000000000000000000000000
...
> Registers after instruction
mar = 00000000000000000000000000000000
...
=====================================================
Cycle 1
No more lines, EOP.
```

---

## Example Files

| File | Description |
|------|-------------|
| `programa_etapa1.txt` | 6-bit instruction program |
| `programa_etapa2_tarefa1.txt` | 8-bit instruction program |
| `programa_etapa2_tarefa2.txt` | 21-bit MIC-1 program |
| `registradores_etapa2_tarefa2.txt` | Initial register state (binary format) |
| `microinstruções_etapa3_tarefa1.txt` | 23-bit MIC-1 program with memory operations |
| `registradores_etapa3_tarefa1.txt` | Initial registers for the Stage 3 example |
| `dados_etapa3_tarefa1.txt` | Initial data memory for the Stage 3 example |
| `instruções.txt` | Final IJVM example with `BIPUSH`, `DUP` and `ILOAD` |
| `registradores_etapa3_atualizado.txt` | Initial registers for the complete IJVM example |
| `dados_etapa3_atualizado.txt` | 16-word memory for the complete IJVM example |
| `saida_etapa1.txt` | Expected output for Stage 1 |
| `saida_etapa2_tarefa1.txt` | Expected output for Stage 2 Task 1 |
| `saida_etapa2_tarefa2.txt` | Expected output for Stage 2 Task 2 |

All example files are located under `src/main/resources/exemplos_projeto/`.
