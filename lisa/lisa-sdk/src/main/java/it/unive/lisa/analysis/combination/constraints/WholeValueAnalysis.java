package it.unive.lisa.analysis.combination.constraints;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.analysis.value.ValueLattice;
import it.unive.lisa.lattices.Satisfiability;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;

/**
 * The constraint-based whole-value analysis among an arbitrary number of client
 * abstractions as defined in <a href=
 * "https://www.frontiersin.org/journals/computer-science/articles/10.3389/fcomp.2025.1655377/full">"Whole-value
 * analysis by abstract interpretation" by Luca Negrini</a>. All client
 * abstractions must be subtypes of {@link WholeValueParticipant}, that is, they
 * must be value abstractions that know how to generate constraints and
 * interpret them. This analysis forwards each expression to be evaluated to all
 * the domains that can handle it, according to
 * {@link WholeValueParticipant#canAbstract(ValueExpression, ProgramPoint, SemanticOracle)}.
 * Also, the class will insert itself into the {@link SemanticOracle} so that
 * client analyses can ask it to generate constraints for any expression and to
 * evaluate them.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 */
public class WholeValueAnalysis
		implements
		ValueDomain<WholeValue> {

	private final WholeValueParticipant<?>[] participants;

	/**
	 * Builds a value with the given participants.
	 * 
	 * @param participants the participants of this value
	 */
	public WholeValueAnalysis(
			WholeValueParticipant<?>... participants) {
		this.participants = participants;
	}

	/**
	 * Returns the participant of this value.
	 * 
	 * @return the participant of this value
	 */
	public WholeValueParticipant<?>[] getParticipants() {
		return participants;
	}

	/**
	 * Returns the participant at the given index.
	 *
	 * @param i the index of the participant to return
	 * 
	 * @return the participant at the given index
	 */
	public WholeValueParticipant<?> get(
			int i) {
		return participants[i];
	}

	/**
	 * Returns the participant at the given index, cast to the given type. If
	 * the participant at the given index is not of the given type, an exception
	 * is thrown.
	 *
	 * @param i     the index of the participant to return
	 * @param clazz the class of the participant to return
	 * 
	 * @return the participant at the given index, cast to the given type
	 * 
	 * @throws SemanticException if the participant at the given index is not of
	 *                               the given type
	 */
	@SuppressWarnings("unchecked")
	public <L extends ValueLattice<L>, T extends WholeValueParticipant<L>> T get(
			int i,
			Class<T> clazz)
			throws SemanticException {
		try {
			return (T) participants[i];
		} catch (ClassCastException e) {
			throw new SemanticException("Participant at index " + i + " is not of type " + clazz.getName());
		}
	}

	/**
	 * Returns the first participant of the given type. If multiple participant
	 * of the same type are present, only the first one is returned. If no
	 * participant of the given type is present, an exception is thrown.
	 *
	 * @param clazz the class of the participant to return
	 * 
	 * @return the first participant of the given type
	 * 
	 * @throws SemanticException if no participant of the given type is present
	 */
	public <L extends ValueLattice<L>, T extends WholeValueParticipant<L>> T get(
			Class<T> clazz)
			throws SemanticException {
		for (WholeValueParticipant<?> p : participants)
			if (clazz.isInstance(p))
				return clazz.cast(p);
		throw new SemanticException("No participant of type " + clazz.getName() + " found");
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public WholeValue assign(
			WholeValue state,
			Identifier id,
			ValueExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		ValueLattice<?>[] lattices = new ValueLattice<?>[participants.length];
		for (int i = 0; i < participants.length; i++)
			if (participants[i].canAbstract(expression, pp, oracle))
				lattices[i] = (ValueLattice<?>) ((WholeValueParticipant) participants[i]).assign(state.get(i),
						id, expression, pp, oracle);
			else
				lattices[i] = state.get(i);
		return new WholeValue(lattices);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public WholeValue smallStepSemantics(
			WholeValue state,
			ValueExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		ValueLattice<?>[] lattices = new ValueLattice<?>[participants.length];
		for (int i = 0; i < participants.length; i++)
			if (participants[i].canAbstract(expression, pp, oracle))
				lattices[i] = (ValueLattice<?>) ((WholeValueParticipant) participants[i])
						.smallStepSemantics(state.get(i), expression, pp, oracle);
			else
				lattices[i] = state.get(i);
		return new WholeValue(lattices);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Satisfiability satisfies(
			WholeValue state,
			ValueExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		Satisfiability result = null;
		for (int i = 0; i < participants.length; i++)
			if (participants[i].canAbstract(expression, pp, oracle)) {
				Satisfiability res = ((WholeValueParticipant) participants[i]).satisfies(state.get(i), expression, pp,
						oracle);
				if (res == Satisfiability.BOTTOM)
					return Satisfiability.BOTTOM;
				else
					result = result == null ? res : result.lub(res);
			}
		return result == null ? Satisfiability.UNKNOWN : result;
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public WholeValue assume(
			WholeValue state,
			ValueExpression expression,
			ProgramPoint src,
			ProgramPoint dest,
			SemanticOracle oracle)
			throws SemanticException {
		ValueLattice<?>[] lattices = new ValueLattice<?>[participants.length];
		for (int i = 0; i < participants.length; i++)
			if (participants[i].canAbstract(expression, src, oracle))
				lattices[i] = (ValueLattice<?>) ((WholeValueParticipant) participants[i]).assume(state.get(i),
						expression, src, dest, oracle);
			else
				lattices[i] = state.get(i);
		return new WholeValue(lattices);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public WholeValue onCallReturn(
			WholeValue entryState,
			WholeValue callres,
			ProgramPoint call)
			throws SemanticException {
		ValueLattice<?>[] lattices = new ValueLattice<?>[participants.length];
		for (int i = 0; i < participants.length; i++)
			lattices[i] = (ValueLattice<?>) ((WholeValueParticipant) participants[i]).onCallReturn(entryState.get(i),
					callres.get(i), call);
		return new WholeValue(lattices);
	}

	@Override
	public WholeValue makeLattice() {
		ValueLattice<?>[] lattices = new ValueLattice<?>[participants.length];
		for (int i = 0; i < participants.length; i++)
			lattices[i] = participants[i].makeLattice();
		return new WholeValue(lattices);
	}

}
