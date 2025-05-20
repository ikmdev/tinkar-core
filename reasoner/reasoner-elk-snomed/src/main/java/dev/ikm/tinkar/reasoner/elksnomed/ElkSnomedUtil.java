package dev.ikm.tinkar.reasoner.elksnomed;

import org.eclipse.collections.api.factory.Lists;

import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;

public class ElkSnomedUtil {

	private static int getStatedSemanticNid(int conceptNid) {
		int[] statedSemanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(conceptNid,
				TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid());
		if (statedSemanticNids.length == 0)
			throw new IllegalStateException("No stated form for concept: " + PrimitiveData.text(conceptNid));
		if (statedSemanticNids.length > 1)
			throw new IllegalStateException("More than one stated form for concept: " + PrimitiveData.text(conceptNid));
		return statedSemanticNids[0];
	}

	public static SemanticEntityVersion getStatedSemantic(ViewCalculator viewCalculator, int conceptNid) {
		int statedSemanticNid = getStatedSemanticNid(conceptNid);
		Latest<SemanticEntityVersion> latestStatedSemantic = viewCalculator.latest(statedSemanticNid);
		return latestStatedSemantic.get();
	}

	public static Concept buildConcept(SemanticEntityVersion semanticEntityVersion) {
		ElkSnomedDataBuilder builder = new ElkSnomedDataBuilder(null, null, new ElkSnomedData());
		return builder.buildConcept(semanticEntityVersion);
	}

	public static Concept getConcept(ViewCalculator viewCalculator, int conceptNid) {
		SemanticEntityVersion sev = ElkSnomedUtil.getStatedSemantic(viewCalculator, conceptNid);
		return ElkSnomedUtil.buildConcept(sev);
	}

	public static void updateStatedSemantic(ViewCalculator viewCalculator, int conceptNid,
			LogicalExpression newStatedExpression) {
		int statedSemanticNid = getStatedSemanticNid(conceptNid);
		Transaction updateStatedTransaction = Transaction.make();
		StampEntity<?> updateStamp = updateStatedTransaction.getStamp(State.ACTIVE,
				viewCalculator.viewCoordinateRecord().getAuthorNidForChanges(),
				viewCalculator.viewCoordinateRecord().getDefaultModuleNid(),
				viewCalculator.viewCoordinateRecord().getDefaultPathNid());
		updateStatedTransaction.addComponent(statedSemanticNid);
		Entity<? extends EntityVersion> statedSemanticRecord = viewCalculator.updateFields(statedSemanticNid,
				Lists.immutable.of(newStatedExpression.sourceGraph()), updateStamp.nid());
		EntityService.get().putEntity(statedSemanticRecord);
		updateStatedTransaction.commit();
	}

}
