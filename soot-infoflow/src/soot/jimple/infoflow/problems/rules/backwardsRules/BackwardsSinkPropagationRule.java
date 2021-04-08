package soot.jimple.infoflow.problems.rules.backwardsRules;

import soot.SootMethod;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.problems.rules.AbstractTaintPropagationRule;
import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.definitions.MethodSourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.manager.IReversibleSourceSinkManager;
import soot.jimple.infoflow.sourcesSinks.manager.SourceInfo;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Rule to introduce unconditional taints at sinks
 * Caution: As in backwards analysis sinks and sources are
 * swapped internally this works with sources
 *
 * @author Steven Arzt
 * @author Tim Lange
 */
public class BackwardsSinkPropagationRule extends AbstractTaintPropagationRule {

	public BackwardsSinkPropagationRule(InfoflowManager manager, Abstraction zeroValue, TaintPropagationResults results) {
		super(manager, zeroValue, results);
	}

	private Collection<Abstraction> propagate(Abstraction d1, Abstraction source, Stmt stmt,
			ByReferenceBoolean killSource, ByReferenceBoolean killAll) {
		if (source == getZeroValue()) {
			// Check whether this can be a source at all
			if (!(manager.getSourceSinkManager() instanceof IReversibleSourceSinkManager))
				return null;
			final IReversibleSourceSinkManager ssm = (IReversibleSourceSinkManager) manager.getSourceSinkManager();
			final SourceInfo sinkInfo = ssm.getInverseSinkInfo(stmt, getManager());
			// We never propagate zero facts onwards
			killSource.value = true;

			// Is this a source?
			if (sinkInfo != null && !sinkInfo.getAccessPaths().isEmpty()) {
				Set<Abstraction> res = new HashSet<>();
				for (AccessPath ap : sinkInfo.getAccessPaths()) {
					// Create the new taint abstraction
					Abstraction abs = new Abstraction(sinkInfo.getDefinition(), ap, stmt, sinkInfo.getUserData(),
							false, false);
					abs.setTurnUnit(stmt);
					res.add(abs);

					// Set the corresponding call site
					if (stmt.containsInvokeExpr())
						abs.setCorrespondingCallSite(stmt);
				}
				return res;
			}
			if (killAll != null)
				killAll.value = true;
		}
		return null;
	}

	@Override
	public Collection<Abstraction> propagateNormalFlow(Abstraction d1, Abstraction source, Stmt stmt, Stmt destStmt,
			ByReferenceBoolean killSource, ByReferenceBoolean killAll) {
		return propagate(d1, source, stmt, killSource, killAll);
	}

	@Override
	public Collection<Abstraction> propagateCallToReturnFlow(Abstraction d1, Abstraction source, Stmt stmt,
			ByReferenceBoolean killSource, ByReferenceBoolean killAll) {
		return propagate(d1, source, stmt, killSource, null);
	}

	@Override
	public Collection<Abstraction> propagateReturnFlow(Collection<Abstraction> callerD1s, Abstraction source, Stmt stmt,
			Stmt retSite, Stmt callSite, ByReferenceBoolean killAll) {
		return null;
	}

	@Override
	public Collection<Abstraction> propagateCallFlow(Abstraction d1, Abstraction source, Stmt stmt, SootMethod dest,
			ByReferenceBoolean killAll) {
		if (!(manager.getSourceSinkManager() instanceof IReversibleSourceSinkManager))
			return null;
		final IReversibleSourceSinkManager ssm = (IReversibleSourceSinkManager) manager.getSourceSinkManager();

		// Normally, we don't inspect source methods
		if (!getManager().getConfig().getInspectSources() && getManager().getSourceSinkManager() != null) {
			final SourceInfo sinkInfo = ssm.getInverseSinkInfo(stmt, getManager());
			if (sinkInfo != null)
				killAll.value = true;
		}

		// By default, we don't inspect sinks either
		if (!getManager().getConfig().getInspectSinks() && getManager().getSourceSinkManager() != null) {
			final boolean isSource = ssm.getInverseSourceInfo(stmt, getManager(), source.getAccessPath()) != null;
			if (isSource)
				killAll.value = true;
		}

		return null;
	}
}
