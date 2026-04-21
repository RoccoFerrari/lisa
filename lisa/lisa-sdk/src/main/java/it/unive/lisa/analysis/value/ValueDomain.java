package it.unive.lisa.analysis.value;

import java.util.Collections;
import java.util.Set;

import it.unive.lisa.analysis.SemanticComponent;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.combination.constraints.WholeValueAnalysis;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonGe;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonLe;
import it.unive.lisa.type.Type;

/**
 * A semantic domain that can evaluate the semantic of expressions that operate
 * on values, and not on memory locations. A value domain can handle instances
 * of {@link ValueExpression}s, and are associated to {@link ValueLattice}
 * instances.<br/>
 * <br/>
 * A {@link ValueDomain} can be employed in a {@link WholeValueAnalysis} to
 * cooperate with domains tracking different value kinds. The cooperation
 * happens through <i>constraints</i> (i.e., {@link BinaryExpression}s having a
 * constant on the left-hand side and an expression on the right-hand side). To
 * enable this cooperation the methods
 * {@link #canSummarize(ValueExpression, ProgramPoint, SemanticOracle)} and
 * {@link #constraints(ValueLattice, ValueExpression, ProgramPoint, SemanticOracle)}
 * must be overridden with the actual logic for the generation of constraints.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 * 
 * @param <L> the type of {@link ValueLattice} that this domain works with
 */
public interface ValueDomain<L extends ValueLattice<L>>
		extends
		SemanticComponent<L, L, ValueExpression, Identifier> {

	/**
	 * Yields {@code true} if the domain can process {@code expression},
	 * {@code false} otherwise. Being able to process an expression means being
	 * able to abstract its value, meaning that the type of values produced by
	 * the expression is the type of value that this domain abstracts.
	 * 
	 * @param expression the expression
	 * @param pp         the program point where this method is queried
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return {@code true} if the domain can process {@code expression},
	 *             {@code false} otherwise.
	 */
	boolean canProcess(
			ValueExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle);

	/**
	 * Yields {@code true} if this domain can summarize the value of {@code e},
	 * such that the {@link WholeValueAnalysis} can use this domain to generate
	 * constraints regarding {@code e}. Since "to summarize" in this context is
	 * limited to the generation of constraints, domains can return {@code true}
	 * even if they can just produce facts about the value (e.g., its relation
	 * to other program variables), and not the value itself.
	 *
	 * @param e      the expression whose value is being abstracted
	 * @param pp     the program point at which the abstraction is being
	 *                   generated
	 * @param oracle the oracle for inter-domain communication
	 * 
	 * @return {@code true} if this domain can abstract the value of {@code e},
	 *             {@code false} otherwise
	 */
	boolean canSummarize(
			ValueExpression e,
			ProgramPoint pp,
			SemanticOracle oracle);

	/**
	 * Generates a set of constraints that model the concrete values of
	 * {@code e} in the state {@code state}. The constraints must be definite,
	 * as in with each constraint the set of concrete values shrinks. An empty
	 * set of constraints thus represents any possible concrete value. A
	 * {@code null} set of constraints represents a bottom value.<br/>
	 * <br/>
	 * Each constraint is given as a {@link BinaryExpression}, where the left
	 * operand is a constant and the right operand is the expression whose value
	 * is being constrained, corresponding to the parameter {@code e}.
	 * 
	 * @param state  the abstract state from which the constraints are generated
	 * @param e      the expression whose value is being constrained
	 * @param pp     the program point at which the constraints are being
	 *                   generated
	 * @param oracle the oracle for inter-domain communication
	 * 
	 * @return a set of constraints modeling the possible values of {@code e} in
	 *             the state {@code state}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	Set<BinaryExpression> constraints(
			L state,
			ValueExpression e,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException;

	public static Set<BinaryExpression> makeConstraint(
			Type constantType,
			Object constant,
			BinaryOperator operator,
			SymbolicExpression expr,
			ProgramPoint pp) {
		return Collections.singleton(
				new BinaryExpression(
						pp.getProgram().getTypes().getBooleanType(),
						new Constant(constantType,
								constant,
								expr.getCodeLocation()),
						expr,
						operator,
						pp.getLocation()));
	}

	public static Set<BinaryExpression> makeEqConstraint(
			Type constantType,
			Object constant,
			SymbolicExpression expr,
			ProgramPoint pp) {
		return makeConstraint(constantType, constant, ComparisonEq.INSTANCE, expr, pp);
	}

	public static Set<BinaryExpression> makeRangeConstraints(
			Type constantType,
			Object low,
			Object high,
			SymbolicExpression expr,
			ProgramPoint pp) {
		BinaryExpression lb = new BinaryExpression(
				pp.getProgram().getTypes().getBooleanType(),
				new Constant(constantType,
						low,
						expr.getCodeLocation()),
				expr,
				ComparisonGe.INSTANCE,
				pp.getLocation());
		BinaryExpression ub = new BinaryExpression(
				pp.getProgram().getTypes().getBooleanType(),
				new Constant(constantType,
						high,
						expr.getCodeLocation()),
				expr,
				ComparisonLe.INSTANCE,
				pp.getLocation());

		if (low == null)
			return Collections.singleton(ub);
		if (high == null)
			return Collections.singleton(lb);
		return Set.of(lb, ub);
	}

}
