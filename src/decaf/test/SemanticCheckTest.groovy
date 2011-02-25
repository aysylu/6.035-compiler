package decaf.test
import decaf.*
import static decaf.BinOpType.*
import static decaf.Type.*

class SemanticCheckTest extends GroovyTestCase {
  void testIfThenElseCondition() {
    def good = new IfThenElse(condition: new BooleanLiteral(value:true), thenBlock: new Block())
    def bad = new IfThenElse(condition: new IntLiteral(value:1), thenBlock: new Block())
    def errors = []
    def semCheck = new SemanticChecker(errors: errors)
    good.inOrderWalk(semCheck.ifThenElseConditionCheck)
    bad.inOrderWalk(semCheck.ifThenElseConditionCheck)
    assertEquals(1, errors.size())
  }

  void testBinOpOperands() {
    def goodErrors = [];
    def badErrors = [];
    def goodSemanticChecker = new SemanticChecker(errors: goodErrors)
    def badSemanticChecker  = new SemanticChecker(errors: badErrors)
    
    def goodConditions = [];
    def badConditions = [];

    def typicalBlnLiteral = new BooleanLiteral(value: true);
    def typicalIntLiteral = new IntLiteral(value: 3);

    // test the good conditions
    goodConditions.addAll([LT, GT, LTE, GTE].collect { 
      new BinOp(op: it, left: typicalIntLiteral, right: typicalIntLiteral)
    });

    goodConditions.addAll([EQ, NEQ].collect { 
      new BinOp(op: it, left: typicalIntLiteral, right: typicalIntLiteral)
    });

    goodConditions.addAll([EQ, NEQ, AND, OR, NOT].collect { 
      new BinOp(op: it, left: typicalBlnLiteral, right: typicalBlnLiteral)
    });

    def badTypeCombos1 = [[typicalIntLiteral, typicalBlnLiteral], 
                         [typicalBlnLiteral, typicalIntLiteral], 
                         [typicalBlnLiteral, typicalBlnLiteral]];

    // 36
    badTypeCombos1.each { combo -> 
      [ADD, SUB, MUL, DIV, MOD, LT, GT, LTE, GTE].each { arithOp -> 
          badConditions.add(new BinOp(op: arithOp, left: combo[0], right: combo[1]))
        }
      }

    def badTypeCombos2 = [[typicalIntLiteral, typicalBlnLiteral], 
                         [typicalBlnLiteral, typicalIntLiteral], 
                         [typicalIntLiteral, typicalIntLiteral]];

    // 12
    badTypeCombos2.each { combo -> 
      [AND, OR, NOT].each { op -> 
        badConditions.add(new BinOp(op: op, left: combo[0], right: combo[1]));
      }
    }

    // 4
    [EQ, NEQ].each { op -> 
      badConditions.add(new BinOp(op: op, left: typicalIntLiteral, right: typicalBlnLiteral));
      badConditions.add(new BinOp(op: op, left: typicalBlnLiteral, right: typicalIntLiteral));
    }
    
    def numExpectedBadConds = 52;
    
    goodConditions.each { 
      it.inOrderWalk(goodSemanticChecker.binOpOperands)
    }
    
    badConditions.each { 
      it.inOrderWalk(badSemanticChecker.binOpOperands)
    }

    assertEquals(numExpectedBadConds, badErrors.size());
    assertEquals(0, goodErrors.size());
  }

}