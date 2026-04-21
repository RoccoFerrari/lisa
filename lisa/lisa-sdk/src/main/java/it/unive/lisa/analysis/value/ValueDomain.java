package it.unive.lisa.analysis.value;

import java.util.Collections;
import java.util.Set;

import it.unive.lisa.analysis.SemanticComponent;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.combination.constraints.WholeValueAnalysis;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;

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
	 * Generates a set of constraints that model the concrete values of
	 * {@code e} in the state {@code state}. The constraints must be definite,
	 * as in with each constraint the set of concrete values shrinks. An empty
	 * set of constraints thus represents any possible concrete value. A
	 * {@code null} set of constraints represents a bottom value.<br/>
	 * <br/>
	 * Each constraint is given as a {@link BinaryExpression}, where the left
	 * operand is a constant and the right operand is the expression whose value
	 * is being constrained, corresponding to the parameter {@code e}. Instead
	 * of {@code e}, the right operand can also represent properties of
	 * {@code e} (e.g., its length, if {@code e} is an array or a string), as
	 * long as the right operand is an expression that can be evaluated by this
	 * domain and that is related to {@code e}.
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
	default Set<BinaryExpression> constraints(
			L state,
			ValueExpression e,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return Collections.emptySet();
	}

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
	default boolean canSummarize(
			ValueExpression e,
			ProgramPoint pp,
			SemanticOracle oracle) {
		return false;
	}

}
