package soot.jimple.infoflow.problems.rules.backwardsRules;

import soot.SootMethod;
import soot.Value;
import soot.jimple.CastExpr;
import soot.jimple.DefinitionStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.problems.rules.AbstractTaintPropagationRule;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.Collection;

/**
 * Rule that implements type checs
 * 
 * @author Steven Arzt
 *
 */
public class BackwardsTypingPropagationRule extends AbstractTaintPropagationRule {

	public BackwardsTypingPropagationRule(InfoflowManager manager, Abstraction zeroValue, TaintPropagationResults results) {
		super(manager, zeroValue, results);
	}

	@Override
	public Collection<Abstraction> propagateNormalFlow(Abstraction d1, Abstraction source, Stmt stmt, Stmt destStmt,
			ByReferenceBoolean killSource, ByReferenceBoolean killAll) {
		// Check for a typecast on the right side of an assignment
		if (!source.getAccessPath().isStaticFieldRef() && stmt instanceof DefinitionStmt) {
			DefinitionStmt defStmt = (DefinitionStmt) stmt;
			if (defStmt.getRightOp() instanceof CastExpr) {
				CastExpr ce = (CastExpr) defStmt.getRightOp();
				Value left = defStmt.getLeftOp();
				if (left == source.getAccessPath().getPlainValue()) {
					// If the typecast is not compatible with the current type, we
					// have to kill the taint
					if (!getManager().getTypeUtils().checkCast(source.getAccessPath(), ce.getCastType())) {
						killAll.value = true;
					}
				}
			}
		}

		return null;
	}

	@Override
	public Collection<Abstraction> propagateCallFlow(Abstraction d1, Abstraction source, Stmt stmt, SootMethod dest,
			ByReferenceBoolean killAll) {
		return null;
	}

	@Override
	public Collection<Abstraction> propagateCallToReturnFlow(Abstraction d1, Abstraction source, Stmt stmt,
			ByReferenceBoolean killSource, ByReferenceBoolean killAll) {
		return null;
	}

	@Override
	public Collection<Abstraction> propagateReturnFlow(Collection<Abstraction> callerD1s, Abstraction source, Stmt stmt,
			Stmt retSite, Stmt callSite, ByReferenceBoolean killAll) {
		return null;
	}

}