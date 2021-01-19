package soot.jimple.infoflow.problems.rules;

import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.problems.rules.backwardsRules.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Backward implementation of the {@link IPropagationRuleManagerFactory} class
 * 
 * @author Tim Lange
 *
 */
public class BackwardPropagationRuleManagerFactory implements IPropagationRuleManagerFactory {

	@Override
	public PropagationRuleManager createRuleManager(InfoflowManager manager, Abstraction zeroValue,
                                                    TaintPropagationResults results) {
		List<ITaintPropagationRule> ruleList = new ArrayList<>();

		ruleList.add(new BackwardsSinkPropagationRule(manager, zeroValue, results));
		ruleList.add(new BackwardsSourcePropagationRule(manager, zeroValue, results));
		ruleList.add(new SkipSystemClassRule(manager, zeroValue, results));
		ruleList.add(new BackwardsClinitRule(manager, zeroValue, results));
		ruleList.add(new BackwardsStrongUpdatePropagationRule(manager, zeroValue, results));

		if (manager.getConfig().getEnableExceptionTracking())
			ruleList.add(new BackwardsExceptionPropagationRule(manager, zeroValue, results));
		if (manager.getConfig().getStopAfterFirstKFlows() > 0)
			ruleList.add(new StopAfterFirstKFlowsPropagationRule(manager, zeroValue, results));
		if (manager.getConfig().getEnableArrayTracking())
			ruleList.add(new BackwardsArrayPropagationRule(manager, zeroValue, results));
		if (manager.getTaintWrapper() != null)
			ruleList.add(new BackwardsWrapperRule(manager, zeroValue, results));
//		if (manager.getConfig().getImplicitFlowMode().trackControlFlowDependencies())
//			ruleList.add(new ImplicitPropagtionRule(manager, zeroValue, results));

		return new PropagationRuleManager(manager, zeroValue, results,
				ruleList.toArray(new ITaintPropagationRule[0]));
	}

}
