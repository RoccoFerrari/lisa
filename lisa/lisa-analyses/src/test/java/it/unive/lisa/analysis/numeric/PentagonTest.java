package it.unive.lisa.analysis.numeric;

import it.unive.lisa.TestParameterProvider;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.lattices.numeric.PentagonLattice;
import it.unive.lisa.lattices.symbolic.DefiniteIdSet;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.program.type.Int32Type;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.Variable;
import it.unive.lisa.util.numeric.IntInterval;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class PentagonTest {

	private final ProgramPoint pp = TestParameterProvider.provideParam(null, ProgramPoint.class);;

	@Test
	public void testBottomIntervalClosure() throws SemanticException {

		Identifier varX = new Variable(Int32Type.INSTANCE, "x", pp.getLocation());
		Identifier varY = new Variable(Int32Type.INSTANCE, "y", pp.getLocation());

		Map<Identifier, IntInterval> intervalFunction = new HashMap<>();
		intervalFunction.put(varX, IntInterval.ONE);
		intervalFunction.put(varY, IntInterval.BOTTOM);
		ValueEnvironment<IntInterval> interval = new ValueEnvironment<IntInterval>(IntInterval.TOP, intervalFunction);

		Map<Identifier, DefiniteIdSet> boundsFunction = new HashMap<>();

		boundsFunction.put(varX, new DefiniteIdSet(new HashSet<>()));
		boundsFunction.put(varY, new DefiniteIdSet(Set.of(varX)));

		ValueEnvironment<DefiniteIdSet> bounds = new ValueEnvironment<DefiniteIdSet>(
				new DefiniteIdSet(new HashSet<Identifier>()), boundsFunction);

		PentagonLattice pentagonLattice = new PentagonLattice(interval, bounds);
		pentagonLattice.closure();
	}
}
