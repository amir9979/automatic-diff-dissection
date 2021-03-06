package add.features.codefeatures.codeanalyze;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import add.features.codefeatures.Cntx;
import add.features.codefeatures.CodeElementInfo;
import add.features.codefeatures.CodeFeatures;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtDo;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtWhile;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.CtScanner;

public class BinaryOperatorAnalyzer extends AbstractCodeAnalyzer {
	
    List<BinaryOperatorKind> logicalOperator = Arrays.asList(BinaryOperatorKind.OR, BinaryOperatorKind.AND);
	
	List<BinaryOperatorKind> bitOperator = Arrays.asList(BinaryOperatorKind.BITOR, BinaryOperatorKind.BITXOR,
			BinaryOperatorKind.BITAND);
	
	List<BinaryOperatorKind> compareOperator = Arrays.asList(BinaryOperatorKind.EQ, BinaryOperatorKind.NE,
			BinaryOperatorKind.LT, BinaryOperatorKind.GT, BinaryOperatorKind.LE, BinaryOperatorKind.GE);
	
	List<BinaryOperatorKind> shiftOperator = Arrays.asList(BinaryOperatorKind.SL, BinaryOperatorKind.SR,
			BinaryOperatorKind.USR);
	
	List<BinaryOperatorKind> mathOperator = Arrays.asList(BinaryOperatorKind.PLUS, BinaryOperatorKind.MINUS,
			BinaryOperatorKind.MUL, BinaryOperatorKind.DIV, BinaryOperatorKind.MOD);
	
	List<CodeFeatures> binoperatortype = Arrays.asList(CodeFeatures.O1_IS_LOGICAL, CodeFeatures.O1_IS_BIT,
			CodeFeatures.O1_IS_COMPARE, CodeFeatures.O1_IS_SHIFT, CodeFeatures.O1_IS_MATH, CodeFeatures.O1_IS_OTHERS);
	
	public BinaryOperatorAnalyzer (CodeElementInfo inputinfo) {
		super(inputinfo);
	}
	
	@Override
	public void analyze() {

		analyzeBinary_BinarySides(elementinfo.element, elementinfo.elementToStudy, elementinfo.context, 
				elementinfo.binoperators);
	}

	private void analyzeBinary_BinarySides(CtElement wholeoriginal, CtElement elementtostudy, Cntx<Object> context, 
			List<CtBinaryOperator> allbinaryoperators) {

		List<CtBinaryOperator> binaryOperatorsFromFaultyLine = allbinaryoperators;
		
		for(int index=0; index<binaryOperatorsFromFaultyLine.size(); index++) {
			
			CtBinaryOperator specificBinOperator = binaryOperatorsFromFaultyLine.get(index);
			
			analyzeBinaryOperatorKind(specificBinOperator, index, context);
			
			analyzeBinaryLogicalOperator(specificBinOperator, index, context);
			
			analyzeBinaryOperatorInvolveNull(specificBinOperator, index, context);
			
			analyzeBinaryOperatorInvolve01(specificBinOperator, index, context);
			
			analyzeBinaryOperatorCompareInCondition(wholeoriginal, specificBinOperator, index, context); 
			
			analyzeBinaryWhetehrMathRoot(specificBinOperator, index, context);
		}
	}
	
   private void analyzeBinaryWhetehrMathRoot (CtBinaryOperator operatorunderstudy, int operatorindex, Cntx<Object> context) {
		
		boolean whethermathroot = false;
		
		BinaryOperatorKind operatorkind = operatorunderstudy.getKind();

		if(mathOperator.contains(operatorkind)) {
			
			whethermathroot = true;
			
			CtElement parent = operatorunderstudy.getParent(CtBinaryOperator.class);
			
			if(parent!=null && mathOperator.contains(((CtBinaryOperator)parent).getKind()))
				whethermathroot =false;
		}
		
		writeGroupedInfo(context, "binaryoperator_"+Integer.toString(operatorindex), CodeFeatures.O5_IS_MATH_ROOT, 
				whethermathroot, "FEATURES_BINARYOPERATOR");	
	}
	
	private void analyzeBinaryLogicalOperator(CtBinaryOperator operatorunderstudy, int operatorindex, Cntx<Object> context) {
		
		boolean whethercontainnotoperator = false;
		
		BinaryOperatorKind operatorkind = operatorunderstudy.getKind();

		if(logicalOperator.contains(operatorkind)) {
			
			CtExpression leftexpression = operatorunderstudy.getLeftHandOperand();
			CtExpression rightexpression = operatorunderstudy.getRightHandOperand();
					
			List<CtBinaryOperator> logicalOperatorLeft = leftexpression.getElements(
			  e -> e instanceof CtBinaryOperator && logicalOperator.contains(((CtBinaryOperator) e).getKind()));
			
			List<CtBinaryOperator> logicalOperatorRight = rightexpression.getElements(
					  e -> e instanceof CtBinaryOperator && logicalOperator.contains(((CtBinaryOperator) e).getKind()));
			
			if(logicalOperatorLeft.size() == 0) {	
				if(scannotoperator(leftexpression))
					whethercontainnotoperator=true;
			}
				
			if(!whethercontainnotoperator && logicalOperatorRight.size() == 0)	{
				if(scannotoperator(rightexpression))
					whethercontainnotoperator=true;
			}
		}
		
		writeGroupedInfo(context, "binaryoperator_"+Integer.toString(operatorindex), CodeFeatures.O2_LOGICAL_CONTAIN_NOT, 
				whethercontainnotoperator, "FEATURES_BINARYOPERATOR");
		
	}
	
	private boolean scannotoperator (CtExpression expressiontostudy) {
		
		List<String> unaryOps = new ArrayList();
		
		CtScanner scanner = new CtScanner() {

			@Override
			public <T> void visitCtUnaryOperator(CtUnaryOperator<T> operator) {

				super.visitCtUnaryOperator(operator);
				unaryOps.add(operator.getKind().toString());
			}
		};
		
		scanner.scan(expressiontostudy);
		
		return unaryOps.contains(UnaryOperatorKind.NOT.toString());
	}
	
	private void analyzeBinaryOperatorKind(CtBinaryOperator operatorunderstudy, int operatorindex, Cntx<Object> context) {
		
		BinaryOperatorKind operatorkind = operatorunderstudy.getKind();
		
		String operatorstring="";
		
		if(logicalOperator.contains(operatorkind)) {
			operatorstring="logical";
		} else if (bitOperator.contains(operatorkind)) {
			operatorstring="bit";
		} else if (compareOperator.contains(operatorkind)) {
			operatorstring="compare";
		} else if (shiftOperator.contains(operatorkind)) {
			operatorstring="shift";
		} else if (mathOperator.contains(operatorkind)) {
			operatorstring="math";
		} else operatorstring="others";
		
		for(int index=0; index<binoperatortype.size(); index++) {
			CodeFeatures cerainfeature = binoperatortype.get(index);
			
			if(cerainfeature.toString().endsWith(operatorstring.toUpperCase()))
				writeGroupedInfo(context, "binaryoperator_"+Integer.toString(operatorindex), cerainfeature, 
							true, "FEATURES_BINARYOPERATOR");
			else writeGroupedInfo(context, "binaryoperator_"+Integer.toString(operatorindex), cerainfeature, 
					false, "FEATURES_BINARYOPERATOR");
		}	
	}
	
	private void analyzeBinaryOperatorInvolveNull(CtBinaryOperator operatorunderstudy, int operatorindex, Cntx<Object> context) {
			
			boolean whethercontainnull = false; 
					
			CtExpression leftexpression = operatorunderstudy.getLeftHandOperand();
			CtExpression rightexpression = operatorunderstudy.getRightHandOperand();
			
			if(leftexpression.toString().trim().equals("null") || rightexpression.toString().trim().equals("null"))
				whethercontainnull = true;
			
			writeGroupedInfo(context, "binaryoperator_"+Integer.toString(operatorindex), CodeFeatures.O3_CONTAIN_NULL, 
					whethercontainnull, "FEATURES_BINARYOPERATOR");
			
		}
	   
	   private void analyzeBinaryOperatorInvolve01 (CtBinaryOperator operatorunderstudy, int operatorindex, Cntx<Object> context) {
			
			boolean whethercontain01 = false; 
					
			CtExpression leftexpression = operatorunderstudy.getLeftHandOperand();
			CtExpression rightexpression = operatorunderstudy.getRightHandOperand();
			
			if(leftexpression.toString().trim().equals("0") || leftexpression.toString().trim().equals("0.0") ||
					leftexpression.toString().trim().equals("1.0") || leftexpression.toString().trim().equals("1")
					|| rightexpression.toString().trim().equals("0") || rightexpression.toString().trim().equals("0.0") ||
					rightexpression.toString().trim().equals("1.0") || rightexpression.toString().trim().equals("1")
					|| leftexpression.toString().trim().endsWith("1") || rightexpression.toString().trim().endsWith("1"))
				whethercontain01 = true;
			
			writeGroupedInfo(context, "binaryoperator_"+Integer.toString(operatorindex), CodeFeatures.O3_CONTAIN_01, 
					whethercontain01, "FEATURES_BINARYOPERATOR");
		}
	   
	   private void analyzeBinaryOperatorCompareInCondition (CtElement wholeoriginal, CtBinaryOperator operatorunderstudy, int operatorindex, Cntx<Object> context) {
			
			boolean whethercompareincondition = false; 
			
	        if(wholeoriginal instanceof CtIf || wholeoriginal instanceof CtWhile || wholeoriginal instanceof CtFor 
	        	|| wholeoriginal instanceof CtDo || wholeoriginal instanceof CtForEach || wholeoriginal instanceof CtSwitch) {
	        	
	    		BinaryOperatorKind operatorkind = operatorunderstudy.getKind();

	    		if (compareOperator.contains(operatorkind))
	    			whethercompareincondition = true;
	        }
			
	        writeGroupedInfo(context, "binaryoperator_"+Integer.toString(operatorindex), CodeFeatures.O4_COMPARE_IN_CONDITION, 
					whethercompareincondition, "FEATURES_BINARYOPERATOR");
		}	
}
