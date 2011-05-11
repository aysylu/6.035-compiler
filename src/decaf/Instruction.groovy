package decaf

class Instruction {
  InstrType type
  Operand op1
  Operand op2

  Instruction(InstrType type) {
    this.type = type
  }

  Instruction(InstrType type, Operand op1) {
    this.type = type
    this.op1 = op1
  }

  Instruction(InstrType type, Operand op1, Operand op2) {
    this.type = type
    this.op1 = op1
    this.op2 = op2
  }

  String getOpCode() {
    switch (type.numOperands) {
    case 2:
      assert op2 != null
    case 1:
      assert op1 != null
      break
    case 0:
      assert op1 == null
      break
    default:
      assert false
    }

    switch (type.numOperands) {
    case 0:
      return "$type.name"
    case 1:
      return "$type.name $op1"
    case 2:
      return "$type.name $op1, $op2"
    }
  }

  String toString() { getOpCode() }
}

enum InstrType {
//copying values
  MOV('movq', 2),
  MOVD('mov', 2),
  MOVSXL('movsxl', 2),
  CMOVE('cmove',2),
  CMOVNE('cmovne',2),
  CMOVG('cmovg',2),
  CMOVL('cmovl',2),
  CMOVGE('cmovge',2),
  CMOVLE('cmovle',2),

//Stack Management
  ENTER('enter',2),
  LEAVE('leave',0),
  PUSH('push',1),
  POP('pop',1),

//Control Flow
  CALL('call', 1),
  RET('ret',0),
  JMP('jmp',1),
  JE('je',1),
  JNE('jne',1),
  JGE('jge',1),
  JG('jg', 1),
  JLE('jle',1),
  JL('jl',1),

//ARITHMETIC AND LOGIC
  ADD('add',2),
  SUB('sub',2),
  IMUL('imul',2),
  IDIV('idiv',1),
  SHR('shr',2),
  SHL('shl',2),
  ROR('ror',2),
  CMP('cmp',2),
  XOR('xor',2),
  CQO('cqo',0), //sign extend rax to rdx:rax
  CDQ('cdq',0), //sign extend rax to rdx:rax
  NEG('neg',1),
  SBB('sbb',2),
  SAR('sar',2),
  AND('and',2),

//MMX
  MOVDQA('movdqa',2),

//DEBUG
  INT3('int3',0);

  final String name
  final int numOperands

  InstrType(String name, int numOperands) {
    this.name = name
    this.numOperands = numOperands
  }
}

class Operand {
  OperType type
  def val
  def offset
  def stride

  Operand(int val) {
    this.type = OperType.IMM
    this.val = val
  }

  Operand(long val) {
    this((int) val)
  }

  Operand(Reg val) {
    this.type = OperType.REG
    this.val = val
  }

  Operand(String val) {
    this.type = OperType.ADDR
    this.val = val
  }

  Operand(String offset, Reg val) {
    this.type = OperType.MEM
    this.val = val
    this.offset = offset
  }

  Operand(int offset, Reg val) {
    this.type = OperType.MEM
    this.val = val
    this.offset = offset
  }

  String toString() {
    assert val != null
    switch (type) {
    case OperType.IMM:
      return "\$${val}"
    case OperType.REG:
      assert val instanceof Reg
      return "%${val.name}"
    case OperType.ADDR:
      return "${val}"
    case OperType.MEM:
      assert offset != null
      assert val instanceof Reg
      if (stride == null) {
        return "${offset}(%${val.name})"
      } else {
        return "${offset}(,%${val.name},${stride})"
      }
    }
  }
}

enum OperType {
  ADDR,
  IMM,
  REG,
  MEM
}

enum Reg {
//64 bit regs
  RAX('rax'),
  RBX('rbx'),
  RCX('rcx'),
  RDX('rdx'),
  RSP('rsp'),
  RBP('rbp'),
  RSI('rsi'),
  RDI('rdi'),
  R8(  'r8'),
  R9(  'r9'),
  R10('r10'),
  R11('r11'),
  R12('r12'),
  R13('r13'),
  R14('r14'),
  R15('r15'),

//32 bit regs
  EAX('eax'),
  EBX('ebx'),
  ECX('ecx'),
  EDX('edx'),
  ESP('esp'),
  EBP('ebp'),
  ESI('esi'),
  EDI('edi'),
  R8D( 'r8d'),
  R9D( 'r9d'),
  R10D('r10d'),
  R11D('r11d'),
  R12D('r12d'),
  R13D('r13d'),
  R14D('r14d'),
  R15D('r15d'),

//special regs
  XMM0('xmm0'),
  RIP('rip'); //used by parallelizer

  final String name
  final RegisterTempVar rtv;

  Reg(String name) {
    this.name = name
    this.rtv = new RegisterTempVar(name);
  }

  RegisterTempVar GetRegisterTempVar() {
    assert this.rtv;
    return this.rtv;
  }

  String toString() {
    return this.name;
  }

  static Reg get32BitReg(Reg reg64) {
    switch (reg64) {
    case RAX: return EAX
    case RBX: return EBX
    case RCX: return ECX
    case RDX: return EDX
    case RSI: return ESI
    case RDI: return EDI
    case R8: return R8D
    case R9: return R9D
    case R10: return R10D
    case R11: return R11D
    case R12: return R12D
    case R13: return R13D
    case R14: return R14D
    case R15: return R15D
    default: assert false
    }
  }

  static Operand get32BitReg(Operand oper64) {
    assert oper64.type == OperType.REG
    return new Operand(get32BitReg(oper64.val))
  }

  static Reg getReg(String regName) {
    for(r in Reg.values()) {
      if(regName == r.toString())
        return r;
    }

    println "getReg failed, regName = $regName, Reg.values() = ${Reg.values().collect {"$it"}}"
    Reg.values().each { println it.toString() }
    assert false;
  }

  static def eachReg = { c -> 
    assert c; 
    return [Reg.RAX, Reg.RBX, Reg.RCX, Reg.RDX, Reg.RSI, Reg.RDI, Reg.RSP, Reg.RBP, Reg.R8, Reg.R9, Reg.R10, Reg.R11, Reg.R12, Reg.R13, Reg.R14, Reg.R15].collect { c(it) }
  }

  static Reg getRegOfParamArgNum(int argNum) {
    assert (argNum > 0) && (argNum <= 6);
    return GetParameterRegisters()[argNum - 1] 
  }

  static List<Reg> GetCallerSaveRegisters() {
    return [Reg.RCX, Reg.RDX, Reg.RSI, Reg.RDI, Reg.R8, Reg.R9, Reg.R10, Reg.R11]
  }

  static List<Reg> GetCalleeSaveRegisters() {
    return [Reg.RBX, Reg.R12, Reg.R13, Reg.R14, Reg.R15]
  }

  static List<Reg> GetParameterRegisters() {
    return [Reg.RDI, Reg.RSI, Reg.RDX, Reg.RCX, Reg.R8, Reg.R9];
  }
}
