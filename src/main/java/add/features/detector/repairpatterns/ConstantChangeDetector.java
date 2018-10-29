package add.features.detector.repairpatterns;

import java.util.List;

import add.entities.RepairPatterns;
import add.features.detector.spoon.RepairPatternUtils;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;

/**
 * Created by tdurieux
 */
public class ConstantChangeDetector extends AbstractPatternDetector {

	public ConstantChangeDetector(List<Operation> operations) {
		super(operations);
	}

	@Override
	public void detect(RepairPatterns repairPatterns) {
		for (int i = 0; i < operations.size(); i++) {
			Operation operation = operations.get(i);
			if ((operation instanceof UpdateOperation)) {
				CtElement srcNode = operation.getSrcNode();
				if (operation.getSrcNode().getParent().getMetadata("new") != null
						|| operation.getSrcNode().getParent().getMetadata("isMoved") != null) {
					continue;
				}
				if (srcNode instanceof CtLiteral) {
					repairPatterns.incrementFeatureCounter("constChange", operation);
				}
				if (srcNode instanceof CtVariableAccess
						&& RepairPatternUtils.isConstantVariableAccess((CtVariableAccess) srcNode)) {
					repairPatterns.incrementFeatureCounter("constChange", operation);
				}
				if (srcNode instanceof CtTypeAccess
						&& RepairPatternUtils.isConstantTypeAccess((CtTypeAccess) srcNode)) {
					repairPatterns.incrementFeatureCounter("constChange", operation);
				}
			} else {
				if (operation instanceof DeleteOperation && operation.getSrcNode() instanceof CtLiteral) {
					CtLiteral ctLiteral = (CtLiteral) operation.getSrcNode();
					// try to search a replacement for the literal
					for (int j = 0; j < operations.size(); j++) {
						Operation operation2 = operations.get(j);
						if (operation2 instanceof InsertOperation) {
							CtElement ctElement = operation2.getSrcNode();
							boolean isConstantVariable = false;
							if ((ctElement instanceof CtVariableAccess
									&& RepairPatternUtils.isConstantVariableAccess((CtVariableAccess) ctElement))
									|| (ctElement instanceof CtTypeAccess
											&& RepairPatternUtils.isConstantTypeAccess((CtTypeAccess) ctElement))) {
								isConstantVariable = true;
							}
							if (((InsertOperation) operation2).getParent() == ctLiteral.getParent()
									&& isConstantVariable) {
								repairPatterns.incrementFeatureCounter("constChange", operation);
							}
						}
					}
				}
			}
		}
	}

}
