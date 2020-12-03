package soot.jimple.infoflow.problems.rules;

import soot.SootMethod;
import soot.Value;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AbstractionAtSink;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;
import soot.jimple.infoflow.sourcesSinks.manager.SinkInfo;
import soot.jimple.infoflow.util.BaseSelector;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.Collection;

/**
 * Rule for recording abstractions that arrive at sources
 * The sources are swapped for the sinks, thats why all the other methods call for sink
 * even though they work on the sources
 *
 * @author Steven Arzt
 * @author Tim Lange
 */
public class BackwardSourcePropagationRule extends AbstractTaintPropagationRule {

	private boolean killState = false;

	public BackwardSourcePropagationRule(InfoflowManager manager, Abstraction zeroValue, TaintPropagationResults results) {
		super(manager, zeroValue, results);
	}

	@Override
	public Collection<Abstraction> propagateNormalFlow(Abstraction d1, Abstraction source, Stmt stmt, Stmt destStmt,
			ByReferenceBoolean killSource, ByReferenceBoolean killAll) {
		if (stmt instanceof ReturnStmt) {
			final ReturnStmt returnStmt = (ReturnStmt) stmt;
			checkForSource(d1, source, stmt, returnStmt.getOp());
		} else if (stmt instanceof IfStmt) {
			final IfStmt ifStmt = (IfStmt) stmt;
			checkForSource(d1, source, stmt, ifStmt.getCondition());
		} else if (stmt instanceof LookupSwitchStmt) {
			final LookupSwitchStmt switchStmt = (LookupSwitchStmt) stmt;
			checkForSource(d1, source, stmt, switchStmt.getKey());
		} else if (stmt instanceof TableSwitchStmt) {
			final TableSwitchStmt switchStmt = (TableSwitchStmt) stmt;
			checkForSource(d1, source, stmt, switchStmt.getKey());
		} else if (stmt instanceof AssignStmt) {
			final AssignStmt assignStmt = (AssignStmt) stmt;
			checkForSource(d1, source, stmt, assignStmt.getRightOp());
		}

		return null;
	}

	/**
	 * Checks whether the given taint abstraction at the given satement triggers a
	 * sink. If so, a new result is recorded
	 * 
	 * @param d1     The context abstraction
	 * @param source The abstraction that has reached the given statement
	 * @param stmt   The statement that was reached
	 * @param retVal The value to check
	 */
	private void checkForSource(Abstraction d1, Abstraction source, Stmt stmt, final Value retVal) {
		// The incoming value may be a complex expression. We have to look at
		// every simple value contained within it.
		final AccessPath ap = source.getAccessPath();
		final ISourceSinkManager sourceSinkManager = getManager().getSourceSinkManager();

		if (ap != null && sourceSinkManager != null && source.isAbstractionActive()) {
			for (Value val : BaseSelector.selectBaseList(retVal, false)) {
				if (val == ap.getPlainValue()) {
					SinkInfo sinkInfo = sourceSinkManager.getSinkInfo(stmt, getManager(), source.getAccessPath());
					if (sinkInfo != null
							&& !getResults().addResult(new AbstractionAtSink(sinkInfo.getDefinition(), source, stmt)))
						killState = true;
				}
			}
		}
	}

	@Override
	public Collection<Abstraction> propagateCallFlow(Abstraction d1, Abstraction source, Stmt stmt, SootMethod dest,
			ByReferenceBoolean killAll) {
		// If we are in the kill state, we stop the analysis
		if (killAll != null)
			killAll.value |= killState;

		return null;
	}

	/**
	 * Checks whether the given taint is visible inside the method called at the
	 * given call site
	 * 
	 * @param stmt   A call site where a sink method is called
	 * @param source The taint that has arrived at the given statement
	 * @return True if the callee has access to the tainted value, false otherwise
	 */
	protected boolean isTaintVisibleInCallee(Stmt stmt, Abstraction source) {
		InvokeExpr iexpr = stmt.getInvokeExpr();

		// Is an argument tainted?
		final Value apBaseValue = source.getAccessPath().getPlainValue();
		if (apBaseValue != null) {
			for (int i = 0; i < iexpr.getArgCount(); i++) {
				if (iexpr.getArg(i) == apBaseValue) {
					if (source.getAccessPath().getTaintSubFields() || source.getAccessPath().isLocal())
						return true;
				}
			}
		}

		// Is the base object tainted?
		if (iexpr instanceof InstanceInvokeExpr) {
			if (((InstanceInvokeExpr) iexpr).getBase() == source.getAccessPath().getPlainValue())
				return true;
		}

		// Is return tainted?
		if (stmt instanceof AssignStmt && apBaseValue == ((AssignStmt) stmt).getLeftOp())
			return true;

		return false;
	}

	@Override
	public Collection<Abstraction> propagateCallToReturnFlow(Abstraction d1, Abstraction source, Stmt stmt,
			ByReferenceBoolean killSource, ByReferenceBoolean killAll) {
		// We only report leaks for active taints, not for alias queries
		if (source.isAbstractionActive() && !source.getAccessPath().isStaticFieldRef()) {
			// Is the taint even visible inside the callee?
			if (!stmt.containsInvokeExpr() || isTaintVisibleInCallee(stmt, source)) {
				// Is this a sink?
				if (getManager().getSourceSinkManager() != null) {
					// Get the sink descriptor
					SinkInfo sinkInfo = getManager().getSourceSinkManager().getSinkInfo(stmt, getManager(),
							source.getAccessPath());

					// If we have already seen the same taint at the same sink, there is no need to
					// propagate this taint any further.
					if (sinkInfo != null
							&& !getResults().addResult(new AbstractionAtSink(sinkInfo.getDefinition(), source, stmt))) {
						killState = true;
					}
				}
			}
		}

		// If we are in the kill state, we stop the analysis
		if (killAll != null)
			killAll.value |= killState;

		return null;
	}

	@Override
	public Collection<Abstraction> propagateReturnFlow(Collection<Abstraction> callerD1s, Abstraction source, Stmt stmt,
			Stmt retSite, Stmt callSite, ByReferenceBoolean killAll) {
		// If we are in the kill state, we stop the analysis
		if (killAll != null)
			killAll.value |= killState;

		return null;
	}

}
