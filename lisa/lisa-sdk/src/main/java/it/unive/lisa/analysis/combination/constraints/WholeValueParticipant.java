package it.unive.lisa.analysis.combination.constraints;

import java.util.Set;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.analysis.value.ValueLattice;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.ValueExpression;

/**
 * A {@link ValueDomain} that can be employed in a {@link WholeValueAnalysis} to
 * cooperate with domains tracking different value kinds. The cooperation
 * happens through <i>constraints</i> (i.e., {@link BinaryExpression}s having a
 * constant on the left-hand side and an expression on the right-hand side).
 * <br/>
 * <br/>
 * The main difference between the LiSA implementation of this analysis and the
 * one defined in the <a href=
 * "https://www.frontiersin.org/journals/computer-science/articles/10.3389/fcomp.2025.1655377/full">paper</a>
 * is that the generator function {@code G} is absent from the implementation:
 * instead, the constraints are interpreted directly in the abstract
 * transformers that ask for them.
 *
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 * 
 * @param <L> the type of {@link ValueLattice} that this domain works with
 */
public interface WholeValueParticipant<L extends ValueLattice<L>>
		extends
		ValueDomain<L> {

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
	Set<BinaryExpression> constraints(
			L state,
			ValueExpression e,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException;

	/**
	 * Yields {@code true} if this domain can summarize the value of {@code e},
	 * such that the {@link WholeValueAnalysis} can use this domain to generate
	 * constraints regarding {@code e}. Since "to summarize" in this context is
	 * limited to the generation of constraints, domains can return {@code true}
	 * even if they can just produce facts about the value (e.g., its relation to
	 * other program variables), and not the value itself.
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

}
