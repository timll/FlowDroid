package soot.jimple.infoflow.problems.rules.backwardsRules;

import soot.RefType;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.problems.rules.AbstractTaintPropagationRule;
import soot.jimple.infoflow.sourcesSinks.manager.SourceInfo;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.taintWrappers.IReversibleTaintWrapper;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.jimple.infoflow.util.TypeUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class BackwardsWrapperRule extends AbstractTaintPropagationRule {
    public BackwardsWrapperRule(InfoflowManager manager, Abstraction zeroValue, TaintPropagationResults results) {
        super(manager, zeroValue, results);
    }

    @Override
    public Collection<Abstraction> propagateNormalFlow(Abstraction d1, Abstraction source, Stmt stmt, Stmt destStmt, ByReferenceBoolean killSource, ByReferenceBoolean killAll) {
        return null;
    }

    @Override
    public Collection<Abstraction> propagateCallFlow(Abstraction d1, Abstraction source, Stmt stmt, SootMethod dest, ByReferenceBoolean killAll) {
        final ITaintPropagationWrapper wrapper = manager.getTaintWrapper();

        // Can we use the taintWrapper results?
        // If yes, this is done in CallToReturnFlowFunction
        if (wrapper != null && wrapper.isExclusive(stmt, source))
            killAll.value = true;
        return null;
    }

    @Override
    public Collection<Abstraction> propagateCallToReturnFlow(Abstraction d1, Abstraction source, Stmt stmt, ByReferenceBoolean killSource, ByReferenceBoolean killAll) {
        if (source == zeroValue)
            return null;

        if (manager.getTaintWrapper() == null || !(manager.getTaintWrapper() instanceof IReversibleTaintWrapper))
            return null;
        final IReversibleTaintWrapper wrapper = (IReversibleTaintWrapper) manager.getTaintWrapper();

        final Aliasing aliasing = getAliasing();
        if (aliasing == null)
            return null;

        final AccessPath ap = source.getAccessPath();
        boolean isTainted = false;
        if (!ap.isStaticFieldRef() && !ap.isEmpty()) {
            InvokeExpr invokeExpr = stmt.getInvokeExpr();

            // is the base object tainted
            if (invokeExpr instanceof InstanceInvokeExpr)
                isTainted = aliasing.mayAlias(((InstanceInvokeExpr) invokeExpr).getBase(), ap.getPlainValue());

            // is the return value tainted
            if (!isTainted && stmt instanceof AssignStmt)
                isTainted = aliasing.mayAlias(((AssignStmt) stmt).getLeftOp(), ap.getPlainValue());


            // is at least one parameter tainted?
            // we need this because of one special case in EasyTaintWrapper:
            // String.getChars(int srcBegin, int srcEnd, char[] dest, int destBegin)
            // if String is tainted, the third parameter contains the exploded string
            if (!isTainted) {
                if (wrapper instanceof EasyTaintWrapper && invokeExpr.getArgCount() >= 3)
                    isTainted = aliasing.mayAlias(invokeExpr.getArg(2), ap.getPlainValue());
                else
                    isTainted = invokeExpr.getArgs().stream().anyMatch(arg -> aliasing.mayAlias(arg, ap.getPlainValue()));
            }
        }

        if (!isTainted)
            return null;

        // This is the same as in (Forward)WrapperPropagationRule as sources/sinks are swapped in SourceSinkManager
        if (!getManager().getConfig().getInspectSources()) {
            final SourceInfo sourceInfo = manager.getSourceSinkManager() != null
                    ? manager.getSourceSinkManager().getSourceInfo(stmt, manager)
                    : null;

            if (sourceInfo != null)
                return null;
        }

        Set<Abstraction> res = wrapper.getInverseTaintsForMethod(stmt, d1, source);
        if (res != null) {
            Set<Abstraction> resWAliases = new HashSet<>(res);
            for (Abstraction abs : res) {
                AccessPath absAp = abs.getAccessPath();
                boolean isBasicString = TypeUtils.isStringType(absAp.getBaseType()) && !absAp.getCanHaveImmutableAliases()
                        && !getAliasing().isStringConstructorCall(stmt);
                boolean taintsObjectValue = absAp.getBaseType() instanceof RefType && !isBasicString;
                boolean taintsStaticField = getManager().getConfig()
                        .getStaticFieldTrackingMode() != InfoflowConfiguration.StaticFieldTrackingMode.None && abs.getAccessPath().isStaticFieldRef();

                if (taintsObjectValue || taintsStaticField
                        || aliasing.canHaveAliasesRightSide(stmt, abs.getAccessPath().getPlainValue(), abs)) {
                        aliasing.computeAliases(d1, stmt, absAp.getPlainValue(), resWAliases,
                            getManager().getICFG().getMethodOf(stmt), abs);
                }

                if (!killSource.value /*&& !absAp.equals(ap)*/)
                    killSource.value = abs != source;
            }
            res = resWAliases;
        }

        return res;
    }

    @Override
    public Collection<Abstraction> propagateReturnFlow(Collection<Abstraction> callerD1s, Abstraction source, Stmt stmt, Stmt retSite, Stmt callSite, ByReferenceBoolean killAll) {
        return null;
    }
}
