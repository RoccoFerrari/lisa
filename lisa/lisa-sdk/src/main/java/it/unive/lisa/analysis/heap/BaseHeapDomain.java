package it.unive.lisa.analysis.heap;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.lattices.ExpressionSet;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.ExpressionVisitor;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.heap.AccessChild;
import it.unive.lisa.symbolic.heap.HeapDereference;
import it.unive.lisa.symbolic.heap.HeapExpression;
import it.unive.lisa.symbolic.heap.HeapReference;
import it.unive.lisa.symbolic.heap.MemoryAllocation;
import it.unive.lisa.symbolic.heap.NullConstant;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.HeapLocation;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.MemoryPointer;
import it.unive.lisa.symbolic.value.PushAny;
import it.unive.lisa.symbolic.value.PushInv;
import it.unive.lisa.symbolic.value.Skip;
import it.unive.lisa.symbolic.value.TernaryExpression;
import it.unive.lisa.symbolic.value.UnaryExpression;
import it.unive.lisa.symbolic.value.ValueExpression;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * A base implementation of the {@link HeapDomain} interface, handling base
 * cases of
 * {@link #smallStepSemantics(HeapLattice, SymbolicExpression, ProgramPoint, SemanticOracle)}
 * and providing a base expression rewriting strategy as an
 * {@link ExpressionVisitor}. All implementers of {@link HeapDomain} should
 * inherit from this class for ensuring a consistent behavior on the base cases,
 * unless explicitly needed. Rewriting substitutes {@link SymbolicExpression}s
 * to {@link ValueExpression}s. The visiting of {@link HeapExpression}s is left
 * unimplemented for concrete instances to provide their logic.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 * 
 * @param <L> the type of {@link HeapLattice} produced by this domain
 */
public interface BaseHeapDomain<L extends HeapLattice<L>>
		extends
		HeapDomain<L>,
		ExpressionVisitor<ExpressionSet> {

	/**
	 * Message used when no rewriting rule is defined for a given expression
	 * type.
	 */
	public static final String NO_REWRITE_DEFINED = "No rewriting rule defined for expressions of type ";

	@Override
	default Pair<L, List<HeapReplacement>> smallStepSemantics(
			L state,
			SymbolicExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		if (expression instanceof HeapExpression)
			return semanticsOf(state, (HeapExpression) expression, pp, oracle);

		if (expression instanceof UnaryExpression) {
			UnaryExpression unary = (UnaryExpression) expression;
			return smallStepSemantics(state, unary.getExpression(), pp, oracle);
		}

		if (expression instanceof BinaryExpression) {
			BinaryExpression binary = (BinaryExpression) expression;
			Pair<L, List<HeapReplacement>> sem1 = smallStepSemantics(state, binary.getLeft(), pp, oracle);
			if (sem1.getLeft().isBottom())
				return sem1;
			Pair<L, List<HeapReplacement>> sem2 = smallStepSemantics(sem1.getLeft(), binary.getRight(), pp, oracle);
			return Pair.of(sem2.getLeft(), ListUtils.union(sem1.getRight(), sem2.getRight()));
		}

		if (expression instanceof TernaryExpression) {
			TernaryExpression ternary = (TernaryExpression) expression;
			Pair<L, List<HeapReplacement>> sem1 = smallStepSemantics(state, ternary.getLeft(), pp, oracle);
			if (sem1.getLeft().isBottom())
				return sem1;
			Pair<L, List<HeapReplacement>> sem2 = smallStepSemantics(sem1.getLeft(), ternary.getMiddle(), pp, oracle);
			if (sem2.getLeft().isBottom())
				return Pair.of(sem2.getLeft(), ListUtils.union(sem1.getRight(), sem2.getRight()));
			Pair<L, List<HeapReplacement>> sem3 = smallStepSemantics(sem2.getLeft(), ternary.getRight(), pp, oracle);
			return Pair.of(
					sem3.getLeft(),
					ListUtils.union(sem1.getRight(), ListUtils.union(sem2.getRight(), sem3.getRight())));
		}

		if (expression instanceof ValueExpression)
			return Pair.of(state, List.of());

		return Pair.of(state.top(), List.of());
	}

	/**
	 * Yields a new instance of this domain, built by evaluating the semantics
	 * of the given heap expression.
	 * 
	 * @param state      the current state of this domain
	 * @param expression the expression to evaluate
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return a new instance of this domain
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	public abstract Pair<L, List<HeapReplacement>> semanticsOf(
			L state,
			HeapExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException;

	@Override
	default ExpressionSet rewrite(
			L state,
			SymbolicExpression expression,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return expression.accept(this, state, pp, oracle);
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			UnaryExpression expression,
			ExpressionSet arg,
			Object... params)
			throws SemanticException {
		return rewriteUnaryExpression(expression, arg, (L) params[0], (ProgramPoint) params[1],
				(SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link UnaryExpression} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. The argument of the expression is automatically rewritten first and
	 * is provided in the {@code arg} parameter.
	 * 
	 * @param expression the expression to rewrite
	 * @param arg        the result of rewriting the inner expression of this
	 *                       unary expression
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewriteUnaryExpression(
			UnaryExpression expression,
			ExpressionSet arg,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		Set<SymbolicExpression> result = new HashSet<>();
		for (SymbolicExpression expr : arg) {
			UnaryExpression e = new UnaryExpression(
					expression.getStaticType(),
					expr,
					expression.getOperator(),
					expression.getCodeLocation());
			result.add(e);
		}
		return new ExpressionSet(result);
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			BinaryExpression expression,
			ExpressionSet left,
			ExpressionSet right,
			Object... params)
			throws SemanticException {
		return rewriteBinaryExpression(expression, left, right, (L) params[0], (ProgramPoint) params[1],
				(SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link BinaryExpression} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. The arguments of the expression are automatically rewritten first and
	 * are provided in the {@code left} and {@code right} parameters.
	 * 
	 * @param expression the expression to rewrite
	 * @param left       the result of rewriting the left argument of this
	 *                       expression
	 * @param right      the result of rewriting the right argument of this
	 *                       expression
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewriteBinaryExpression(
			BinaryExpression expression,
			ExpressionSet left,
			ExpressionSet right,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		Set<SymbolicExpression> result = new HashSet<>();
		for (SymbolicExpression l : left)
			for (SymbolicExpression r : right) {
				BinaryExpression e = new BinaryExpression(
						expression.getStaticType(),
						l,
						r,
						expression.getOperator(),
						expression.getCodeLocation());
				result.add(e);
			}
		return new ExpressionSet(result);
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			TernaryExpression expression,
			ExpressionSet left,
			ExpressionSet middle,
			ExpressionSet right,
			Object... params)
			throws SemanticException {
		return rewriteTernaryExpression(expression, left, middle, right, (L) params[0], (ProgramPoint) params[1],
				(SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link TernaryExpression} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. The arguments of the expression are automatically rewritten first and
	 * are provided in the {@code left}, {@code middle}, and {@code right}
	 * parameters.
	 * 
	 * @param expression the expression to rewrite
	 * @param left       the result of rewriting the left argument of this
	 *                       expression
	 * @param middle     the result of rewriting the middle argument of this
	 *                       expression
	 * @param right      the result of rewriting the right argument of this
	 *                       expression
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewriteTernaryExpression(
			TernaryExpression expression,
			ExpressionSet left,
			ExpressionSet middle,
			ExpressionSet right,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		Set<SymbolicExpression> result = new HashSet<>();
		for (SymbolicExpression l : left)
			for (SymbolicExpression m : middle)
				for (SymbolicExpression r : right) {
					TernaryExpression e = new TernaryExpression(
							expression.getStaticType(),
							l,
							m,
							r,
							expression.getOperator(),
							expression.getCodeLocation());
					result.add(e);
				}
		return new ExpressionSet(result);
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			Skip expression,
			Object... params)
			throws SemanticException {
		return rewriteSkip(expression, (L) params[0], (ProgramPoint) params[1], (SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link Skip} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. By default, this method returns a singleton set containing the
	 * expression itself. This method can be overridden by concrete
	 * implementations to provide a more precise rewriting.
	 * 
	 * @param expression the expression to rewrite
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewriteSkip(
			Skip expression,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return new ExpressionSet(expression);
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			PushInv expression,
			Object... params)
			throws SemanticException {
		return rewritePushInv(expression, (L) params[0], (ProgramPoint) params[1], (SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link PushInv} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. By default, this method returns a singleton set containing the
	 * expression itself. This method can be overridden by concrete
	 * implementations to provide a more precise rewriting.
	 * 
	 * @param expression the expression to rewrite
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewritePushInv(
			PushInv expression,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return new ExpressionSet(expression);
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			Constant expression,
			Object... params)
			throws SemanticException {
		return rewriteConstant(expression, (L) params[0], (ProgramPoint) params[1], (SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link Constant} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. By default, this method returns a singleton set containing the
	 * expression itself. This method can be overridden by concrete
	 * implementations to provide a more precise rewriting.
	 * 
	 * @param expression the expression to rewrite
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewriteConstant(
			Constant expression,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return new ExpressionSet(expression);
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			Identifier expression,
			Object... params)
			throws SemanticException {
		return rewriteIdentifier(expression, (L) params[0], (ProgramPoint) params[1], (SemanticOracle) params[2]);
	}

	/**
	 * Rewrites an {@link Identifier} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. By default, this method returns a singleton set containing the
	 * expression itself. This method can be overridden by concrete
	 * implementations to provide a more precise rewriting.
	 * 
	 * @param expression the expression to rewrite
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewriteIdentifier(
			Identifier expression,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return new ExpressionSet(expression);
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			ValueExpression expression,
			ExpressionSet[] subExpressions,
			Object... params)
			throws SemanticException {
		return rewriteValueExpression(expression, subExpressions, (L) params[0], (ProgramPoint) params[1],
				(SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link ValueExpression} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. The arguments of the expression are automatically rewritten first and
	 * are provided in the {@code subExpressions} parameter. The default
	 * implementation of this method throws a {@link SemanticException}, since
	 * it is meant for frontend-specific expressions that this domain cannot
	 * know of.
	 * 
	 * @param expression     the expression to rewrite
	 * @param subExpressions the result of rewriting the sub-expressions of this
	 *                           expression, in the same order as they appear in
	 *                           the original expression
	 * @param state          the current state of this domain
	 * @param pp             the program point that where this expression is
	 *                           being evaluated
	 * @param oracle         the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewriteValueExpression(
			ValueExpression expression,
			ExpressionSet[] subExpressions,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		throw new SemanticException(NO_REWRITE_DEFINED + expression.getClass().getName());
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			HeapExpression expression,
			ExpressionSet[] subExpressions,
			Object... params)
			throws SemanticException {
		return rewriteHeapExpression(expression, subExpressions, (L) params[0], (ProgramPoint) params[1],
				(SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link HeapExpression} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. The arguments of the expression are automatically rewritten first and
	 * are provided in the {@code subExpressions} parameter. The default
	 * implementation of this method throws a {@link SemanticException}, since
	 * it is meant for frontend-specific expressions that this domain cannot
	 * know of.
	 * 
	 * @param expression     the expression to rewrite
	 * @param subExpressions the result of rewriting the sub-expressions of this
	 *                           expression, in the same order as they appear in
	 *                           the original expression
	 * @param state          the current state of this domain
	 * @param pp             the program point that where this expression is
	 *                           being evaluated
	 * @param oracle         the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewriteHeapExpression(
			HeapExpression expression,
			ExpressionSet[] subExpressions,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		throw new SemanticException(NO_REWRITE_DEFINED + expression.getClass().getName());
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			PushAny expression,
			Object... params)
			throws SemanticException {
		return rewritePushAny(expression, (L) params[0], (ProgramPoint) params[1], (SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link PushAny} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. By default, this method returns a singleton set containing the
	 * expression itself. This method can be overridden by concrete
	 * implementations to provide a more precise rewriting.
	 * 
	 * @param expression the expression to rewrite
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	default ExpressionSet rewritePushAny(
			PushAny expression,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException {
		return new ExpressionSet(expression);
	}

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			AccessChild expression,
			ExpressionSet receiver,
			ExpressionSet child,
			Object... params)
			throws SemanticException {
		return rewriteAccessChild(expression, receiver, child, (L) params[0], (ProgramPoint) params[1],
				(SemanticOracle) params[2]);
	}

	/**
	 * Rewrites an {@link AccessChild} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. The arguments of the expression, that is, the receiver and the child,
	 * are automatically rewritten first and are provided in the
	 * {@code receiver} and {@code child} parameters.
	 * 
	 * @param expression the expression to rewrite
	 * @param receiver   the result of rewriting the receiver of this access
	 * @param child      the result of rewriting the child of this access
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	ExpressionSet rewriteAccessChild(
			AccessChild expression,
			ExpressionSet receiver,
			ExpressionSet child,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException;

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			MemoryAllocation expression,
			Object... params)
			throws SemanticException {
		return rewriteMemoryAllocation(expression, (L) params[0], (ProgramPoint) params[1], (SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link MemoryAllocation} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to.
	 * 
	 * @param expression the expression to rewrite
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	ExpressionSet rewriteMemoryAllocation(
			MemoryAllocation expression,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException;

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			HeapReference expression,
			ExpressionSet arg,
			Object... params)
			throws SemanticException {
		return rewriteHeapReference(expression, arg, (L) params[0], (ProgramPoint) params[1],
				(SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link HeapReference} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. The argument of the expression, that is, the referenced expression,
	 * is automatically rewritten first and is provided in the {@code arg}
	 * parameter.
	 *
	 * @param expression the expression to rewrite
	 * @param arg        the result of rewriting the inner expression of this
	 *                       reference
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 *
	 * @return the result of rewriting {@code expression}
	 *
	 * @throws SemanticException if an error occurs during the computation
	 */
	ExpressionSet rewriteHeapReference(
			HeapReference expression,
			ExpressionSet arg,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException;

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			HeapDereference expression,
			ExpressionSet arg,
			Object... params)
			throws SemanticException {
		return rewriteHeapDereference(expression, arg, (L) params[0], (ProgramPoint) params[1],
				(SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link HeapDereference} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to. The argument of the expression, that is, the expression being
	 * dereferenced, is automatically rewritten first and is provided in the
	 * {@code arg} parameter.
	 * 
	 * @param expression the expression to rewrite
	 * @param arg        the result of rewriting the inner expression of this
	 *                       dereference
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	ExpressionSet rewriteHeapDereference(
			HeapDereference expression,
			ExpressionSet arg,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException;

	@Override
	@SuppressWarnings("unchecked")
	default ExpressionSet visit(
			NullConstant expression,
			Object... params)
			throws SemanticException {
		return rewriteNullConstant(expression, (L) params[0], (ProgramPoint) params[1], (SemanticOracle) params[2]);
	}

	/**
	 * Rewrites a {@link NullConstant} to the {@link HeapLocation}s,
	 * {@link MemoryPointer}s, or other {@link ValueExpression}s it can resolve
	 * to.
	 * 
	 * @param expression the expression to rewrite
	 * @param state      the current state of this domain
	 * @param pp         the program point that where this expression is being
	 *                       evaluated
	 * @param oracle     the oracle for inter-domain communication
	 * 
	 * @return the result of rewriting {@code expression}
	 * 
	 * @throws SemanticException if an error occurs during the computation
	 */
	ExpressionSet rewriteNullConstant(
			NullConstant expression,
			L state,
			ProgramPoint pp,
			SemanticOracle oracle)
			throws SemanticException;

}
