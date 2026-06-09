# IJVM-machine

An IJVM microarchitecture emulator created as a project for the course **Computer Architecture and Organization II** at UFPB (Profª. Sarah Pontes Madruga).

This project implements a modified version of the Mic-1 virtual machine, starting with the ALU simulation and progressively building up to a full IJVM instruction interpreter.

---

## Current Status: Week 1 ✅

**Stage 1 – ALU Simulation** is **complete and tested**.

The ALU correctly processes 6-bit MIC-1 control words with full support for:
- AND, OR, NOT B, and ADD operations
- Bus enables (ENA, ENB)
- A bus inversion (INVA)
- Carry-in and carry-out (INC, vai-um)

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Build Tool | Maven |
| Testing | JUnit 5 |
| API Style | REST (JSON) |

---

## Project Structure

```
ijvm/
├── src/main/java/com/bisha/ijvm/
│   ├── controller/
│   │   └── UlaController.java      # REST endpoints (JSON + file upload)
│   ├── model/
│   │   └── EstadoULA.java          # ALU state DTO with hex/binary formatting
│   ├── service/
│   │   └── UlaService.java         # ALU logic implementation
│   └── UlaApplication.java         # Spring Boot main class
├── src/main/resources/
│   └── exemplos_projeto/           # Professor's test files
│       ├── programa_etapa1.txt
│       ├── programa_etapa2_tarefa1.txt
│       ├── programa_etapa2_tarefa2.txt
│       ├── registradores_etapa2_tarefa2.txt
│       ├── registradores_etapa3_tarefa1.txt
│       ├── dados_etapa3_tarefa1.txt
│       ├── microinstruções_etapa3_tarefa1.txt
│       ├── instruções.txt
│       ├── saída_etapa1.txt
│       ├── saída_etapa2_tarefa1.txt
│       ├── saída_etapa2_tarefa2.txt
│       └── saída_etapa3_tarefa1.txt
├── src/test/java/com/bisha/ijvm/
│   ├── EstadoULATest.java          # Model formatting tests
│   ├── UlaServiceTest.java         # ALU operation tests (38 test cases)
│   ├── UlaControllerTest.java      # REST endpoint tests
│   └── IjvmApplicationTests.java   # Context load test
├── logs/                           # Generated execution logs
├── HELP.md                         # Detailed documentation
└── pom.xml                         # Maven dependencies
```

---

## Stage 1 – ALU Specification (✅ Complete)

### Instruction Format (6 bits)

| Bit | Name | Function |
|-----|------|----------|
| 5 (X0) | F0 | ALU function selector (MSB) |
| 4 (X1) | F1 | ALU function selector (LSB) |
| 3 (X2) | ENA | Enable A bus (0 = force A=0) |
| 2 (X3) | ENB | Enable B bus (0 = force B=0) |
| 1 (X4) | INVA | Invert A bus (bitwise complement) |
| 0 (X5) | INC | Carry-in to full adder (vem-um) |

### ALU Operations

| F0 | F1 | Operation | Description |
|----|----|-----------|-------------|
| 0 | 0 | A AND B | Bitwise logical AND |
| 0 | 1 | A OR B | Bitwise logical OR |
| 1 | 0 | NOT B | Bitwise complement of effective B |
| 1 | 1 | A + B | Arithmetic addition with carry-in |

### Processing Pipeline

```
1. Bus Enable:  effA = ENA ? a : 0;   effB = ENB ? b : 0
2. Invert A:    effA = INVA ? ~effA : effA
3. Logic Unit:  out = AND/OR/NOT/ADD(effA, effB)
4. Full Adder:  S = out + INC
5. Output:      32-bit result S + carry-out (vai-um)
```

---

## Log Format (Stage 1)

The program generates logs in the following format:

```
b = 00000000000000000000000000000001
a = 11111111111111111111111111111111

Start of Program
============================================================
Cycle 1

PC = 1
IR = 111100
b = 00000000000000000000000000000001
a = 11111111111111111111111111111111
s = 00000000000000000000000000000000
co = 1
============================================================
Cycle 2

PC = 2
IR = 110101
b = 00000000000000000000000000000001
a = 00000000000000000000000000000000
s = 00000000000000000000000000000010
co = 0
============================================================
Cycle 3

PC = 3
IR = 110100
b = 00000000000000000000000000000001
a = 00000000000000000000000000000000
s = 00000000000000000000000000000001
co = 0
============================================================
Cycle 4

PC = 4
IR = 011100
b = 00000000000000000000000000000001
a = 11111111111111111111111111111111
s = 11111111111111111111111111111111
co = 0
============================================================
Cycle 5

> Line is empty, EOP.
```

### Log Fields

| Field | Description |
|-------|-------------|
| `a` | Effective A value after ENA and INVA (32-bit binary) |
| `b` | Effective B value after ENB (32-bit binary) |
| `PC` | Program counter (1-indexed instruction number) |
| `IR` | Instruction register (6-bit binary) |
| `s` | ALU result S (32-bit binary) |
| `co` | Carry-out (vai-um) from full adder |

---

## API Endpoints

### POST /api/ula/executar
Execute program from JSON payload.

**Request:**
```bash
curl -X POST http://localhost:8080/api/ula/executar \
  -H "Content-Type: application/json" \
  -d '{"instrucoes": ["111100", "011100", "101100"]}'
```

### POST /api/ula/executar-arquivo
Upload and execute a `.txt` file (produces log in the format above).

```bash
curl -F "arquivo=@programa_etapa1.txt" http://localhost:8080/api/ula/executar-arquivo
```

---

## Running the Application

### Build
```bash
cd ijvm
./mvnw clean compile
```

### Run
```bash
./mvnw spring-boot:run
```

### Run Tests
```bash
./mvnw test
```

Test coverage includes:
- `UlaServiceTest`: 38 test cases covering all ALU operations, bus enables, inversion, carry handling, and program execution
- `EstadoULATest`: Hexadecimal and binary formatting validation
- `UlaControllerTest`: REST endpoint integration tests

---

## Upcoming Deliverables

### Week 2 – Stage 2 Task 1 (Extended ALU)
- Add 8-bit control word
- Implement `SLL8` (logical left shift 8 bits)
- Implement `SRA1` (arithmetic right shift 1 bit)
- Add status flags: `N` (negative) and `Z` (zero)

### Week 3 – Stage 2 Task 2 (Register File)
- Implement 9 registers (H, OPC, TOS, CPP, LV, SP, PC, MDR, MAR) + MBR
- 4-to-9 decoder for B-bus selection
- 9-bit selector for C-bus writes
- Support MBR/MBRU with sign/zero extension
- Execute 21-bit microinstructions

### Week 4 – Stage 3 (Memory)
- Add 2-bit memory control to instructions (23-bit format)
- Implement READ from `dados.txt`
- Implement WRITE to `dados.txt`
- Memory operations after ALU result is written

### Week 5 – Final Deliverable (IJVM Interpreter)
- Translate `ILOAD x`, `DUP`, `BIPUSH byte` into microinstruction sequences
- Special case: BIPUSH sets READ+WRITE=11, loads byte directly into H via MBR
- Generate complete execution log with register and memory state

---

## Test Files (provided by professor)

| File | Purpose |
|------|---------|
| `programa_etapa1.txt` | Stage 1 ALU test |
| `saída_etapa1.txt` | Expected output for Stage 1 |
| `programa_etapa2_tarefa1.txt` | Stage 2.1 ALU with shifts test |
| `saída_etapa2_tarefa1.txt` | Expected output for Stage 2.1 |
| `programa_etapa2_tarefa2.txt` | Stage 2.2 register file test |
| `registradores_etapa2_tarefa2.txt` | Initial register values |
| `saída_etapa2_tarefa2.txt` | Expected output for Stage 2.2 |
| `microinstruções_etapa3_tarefa1.txt` | Stage 3.1 memory test |
| `dados_etapa3_tarefa1.txt` | Initial memory contents |
| `registradores_etapa3_tarefa1.txt` | Initial register values |
| `saída_etapa3_tarefa1.txt` | Expected output for Stage 3.1 |
| `instruções.txt` | Final interpreter test (BIPUSH, DUP, ILOAD) |

---

## Team

- **Miguel Mochizuki Silva**
- **Mateus C. Dias**
- **Joaquim Germano Félix**
- **Gabriel Bringel Gonçalves**

---

## License

Academic use only – UFPB Computer Architecture and Organization II Course

**Professor:** Sarah Pontes Madruga
**Date:** June 8, 2026
