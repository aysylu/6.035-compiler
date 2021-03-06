package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.BinOpType.*

//This class figures out what expression corresponds to a node
//It memoizes the results, but it doesn't do a one-pass run, so it's pretty much lazily evaluated
class ValueNumberer {

  def visitedPhis //prevents us from looping around phi functions
  //entry point into the numberer
  def getExpr(LowIrNode node) {
    visitedPhis = new HashSet()
    return getExprOfNode(node)
  }

  //maintains uniqueness for values
  def uniqueMap = new LazyMap({new Expression(unique: true)})
  def uniqueToTmp = [:]

  def getExprOfTmp(TempVar tmp) {
    //if param, unique
    //if undef, 0
    //if known, node
    if (tmp.type == TempVarType.PARAM) {
      uniqueToTmp[uniqueMap[tmp]] = tmp
      return uniqueMap[tmp]
    } else if (tmp.defSite == null) {
      return new Expression(constVal: 0)
    } else {
      return getExprOfNode(tmp.defSite)
    }
  }

  def memo = [:] // (memoize)
  def getExprOfNode(LowIrNode node) {
    if (memo.containsKey(node)) return memo[node]

    def result
    switch (node) {
    case LowIrMov:
      result = getExprOfTmp(node.src)
      break
    case LowIrCallOut:
    case LowIrMethodCall:
      result = uniqueMap[node]
      uniqueToTmp[result] = node.tmpVar
      break
    case LowIrIntLiteral:
      result = new Expression(constVal: node.value)
      break
    case LowIrBinOp:
      def lhs = getExprOfTmp(node.leftTmpVar)
      def rhs = node.rightTmpVar != null ? getExprOfTmp(node.rightTmpVar) : null
      result = new Expression(left: lhs, right: rhs, op: node.op)
      break
    case LowIrStore:
    case LowIrLoad:
      result = new Expression(varDesc: node.desc)
      if (node.index != null) {
        result.index = getExprOfTmp(node.index)
      }
      break
    case LowIrBoundsCheck:
      result = new Expression(
        boundLow: node.lowerBound,
        boundHigh: node.upperBound,
        boundTest: getExprOfTmp(node.testVar)
      )
      break
    case LowIrPhi:
      if (visitedPhis.contains(node)) {
        result = uniqueMap[node]
        uniqueToTmp[result] = node.tmpVar
        break
      }
      visitedPhis << node
      def exprs = node.args.collect{getExprOfTmp(it)}.findAll{it != uniqueMap[node]}
      if (exprs.every{it == exprs[0]}) {
        result = exprs[0]
        break
      } else {
        result = uniqueMap[node]
        uniqueToTmp[result] = node.tmpVar
        break
      }
    case LowIrCondJump:
    case LowIrReturn:
    case LowIrStringLiteral:
    default:
      //no expr/unique expr are the same
      result = uniqueMap[node]
      break
    }

    memo[node] = result
    return result
  }
}

//This class represents an expression
//There are 4 kinds: constant expressions (a number), binops, variables (globals), and uniques
//uniques are things whose value can't be determined at all, like the return value of a callout
//  or a method parameter
class Expression {
  //For constants
  def constVal
  //For uniques
  def unique = false
  //For binops
  def left, right, op
  //For variables
  def varDesc, index
  //For bounds checks
  def boundTest, boundLow, boundHigh

  //This checks that you set it up correctly
  def check() {
    if (constVal != null) {
      assert !unique && left == null && right == null && op == null && varDesc == null &&
        index == null && boundTest == null && boundLow == null && boundHigh == null
    } else if (unique) {
      assert constVal == null && left == null && right == null && op == null &&
        varDesc == null && index == null && boundTest == null && boundLow == null && boundHigh == null
    } else if (left) {
      assert constVal == null && !unique && varDesc == null && index == null &&
        boundTest == null && boundLow == null && boundHigh == null
    } else if (varDesc) {
      assert !unique && constVal == null && left == null && right == null &&
        op == null && boundTest == null && boundLow == null && boundHigh == null
    } else if (boundTest) {
      assert boundLow != null && boundHigh != null
      assert !unique && constVal == null && left == null && right == null &&
        op == null && varDesc == null && index == null
    } else {
      assert false
    }
  }

  int hashCode() {
    check()
    if (unique) return super.hashCode() //hash of pointer
    if (constVal != null) return 103*constVal
    if (op != null) {
      def tmp = left.hashCode() * 17 + op.hashCode() * 41
      if (right != null) {
        if (op in [ADD, MUL, EQ, NEQ, AND, OR]) tmp += right.hashCode() * 17
        else tmp += right.hashCode() * 91
      }
      return tmp
    }
    if (varDesc != null) {
      def tmp = varDesc.hashCode()
      if (index != null) {
        tmp += index.hashCode() * 13
      }
      return tmp
    }
    if (boundTest != null) {
      return boundTest.hashCode() * 19 + boundLow * 1047 + boundHigh * 3
    }
  }

  boolean equals(Object o) { o instanceof Expression && o.hashCode() == this.hashCode() }

  String toString() {
    check()
    if (unique) return "unique${this.hashCode() % 100}"
    if (constVal != null) return "const($constVal)"
    if (op != null) return "($left $op $right)"
    if (varDesc != null && index != null) return "desc($varDesc.name[$index])"
    if (varDesc != null) return "desc($varDesc.name)"
    if (boundTest != null) return "check($boundLow <= $boundTest < $boundHigh"
    assert false
  }
}
