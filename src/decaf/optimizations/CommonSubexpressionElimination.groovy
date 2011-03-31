package decaf.optimizations
import decaf.*
import decaf.graph.*
import static decaf.graph.Traverser.eachNodeOf

class CommonSubexpressionElimination extends Analizer{

  def memoryExprs = new LinkedHashSet()
  def allExprs = new LinkedHashSet()
  def exprsContainingTmp = [:]

  def lazy = { map, key -> if (!map.containsKey(key)) map[key] = new LinkedHashSet(); map[key] }

  def run(MethodDescriptor methodDesc) {
    def startNode = methodDesc.lowir
    store(startNode, new LinkedHashSet())
    eachNodeOf(startNode) {
      def expr
      expr = new AvailableExpr(it)
      switch (it) {
      case LowIrLoad:
        memoryExprs << expr
      case LowIrBinOp:
        it.getUses().each { use ->
          lazy(exprsContainingTmp, use) << expr
        }
        allExprs << expr
        break
      }
    }
    analize(startNode)
    eachNodeOf(startNode) {
      switch (it) {
      case LowIrLoad:
      case LowIrBinOp:
        def expr = new AvailableExpr(it)
        if (it.predecessors.every{pred -> load(pred).contains(expr)}) {
          def tmpVar = methodDesc.tempFactory.createLocalTemp()
          def worklist = new LinkedHashSet(it.predecessors)
          def visited = new HashSet(worklist)
          while (worklist.size() > 0) {
            def candidate = worklist.iterator().next()
            worklist.remove(candidate)
            visited << candidate
            //we're BFS backwards from the node to find the defsites
            if ((candidate instanceof LowIrBinOp || candidate instanceof LowIrLoad)
                 && new AvailableExpr(candidate) == expr) {
              //now, we insert a move to the temporary
              //since candidate isn't a condjump, it has one successor
              def mov = new LowIrMov(src: candidate.getDef(), dst: tmpVar)
              assert candidate.successors.size() == 1
              new LowIrBridge(mov).insertBetween(candidate, candidate.successors[0])
            } else {
              worklist += candidate.predecessors - visited
            }
          }
          //now, insert mov so that from tmpvar to redundant expr's destination
          assert it.successors.size() == 1
          new LowIrBridge(new LowIrMov(src: tmpVar, dst: it.getDef())).insertBetween(
            it, it.successors[0])
          it.excise()
        }
      }
    }
  }

  // map from nodes to (map from availableExpr to first def site)
  def availExprMap = [:]
  final void lazyInit(node) {
    if (availExprMap[node] == null)
      availExprMap[node] = [:]
  }

  void store(GraphNode node, Set data) {
    def newMap = [:]
    data.each { newMap[it] = it.node.getDef() }
    availExprMap[node] = newMap
  }

  Set load(GraphNode node) {
    lazyInit(node)
    return new LinkedHashSet(availExprMap[node].keySet())
  }

  Set transfer(GraphNode node, Set input) {
    return (input - kill(node)) + gen(node)
  }

  Set join(GraphNode node) {
    if (node.predecessors)
      return node.predecessors.inject(allExprs) { set, succ -> set.intersect(load(succ)) }
    else
      return new LinkedHashSet()
  }

  def gen(node) {
    def set
    switch (node) {
    case LowIrBinOp:
    case LowIrLoad:
      set = Collections.singleton(new AvailableExpr(node))
      set -= kill(node)
      break
    default:
      set = Collections.emptySet()
      break
    }
    return set
  }

  def kill(node) {
    def set = node.getDef() != null ? lazy(exprsContainingTmp, node.getDef()) : Collections.emptySet()
    //todo: this is so not optimal it hurts
    if (node instanceof LowIrCallOut || node instanceof LowIrMethodCall || node instanceof LowIrStore) {
      set = memoryExprs
    }
    return set
  }
}

class AvailableExpr {
  def node

  AvailableExpr(node) {
    this.node = node
  }

  int hashCode() {
    int i = 0
    assert node instanceof LowIrBinOp || node instanceof LowIrLoad
    switch (node) {
    case LowIrBinOp:
      i = node.op.hashCode() * 43 + node.leftTmpVar.hashCode() * 97
      if (node.rightTmpVar) i += node.rightTmpVar.hashCode() * 97
      break
    case LowIrLoad:
      i = node.desc.hashCode() * 4257
      if (node.index != null) i += node.index.hashCode() * 101
      break
    }
    return i
  }

  boolean equals(Object other) {
    return other != null && other.getClass() == AvailableExpr.class && other.hashCode() == this.hashCode()
  }

  String toString() {
    return "Available($node)"
  }
}