package tac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import parser.Node;
import parser.SimpleNode;
import tac.TACProgram.Cond;
import tac.TACProgram.Condition;
import tac.TACProgram.Operator;
import tac.TACProgram.RValue;
import tac.TACProgram.Variable;

public class TACOptimizedGenerator extends TACGenerator {

	private Map<Node, Integer> nodeHash = new IdentityHashMap<>();
	private Map<Node, String> constFoldingTable = new IdentityHashMap<>();
	
	public TACOptimizedGenerator(Node astRoot) throws IllegalArgumentException {
		super(astRoot);
		travelAst(astRoot);
	}
	
	private void travelAst(Node node) {
		travelNode(node);
	}
	
	private NodeInfo travelNode(Node node) {
		final int prime = 31;
		int result = 1;
		NodeInfo info = new NodeInfo();
		int length = node.jjtGetNumChildren();
		if(length > 0) {
			//常量折叠
			if(node.getId() == JJTASSIGNMENTSTATEMENT) {
				travelNode(node.jjtGetChild(0));
				Node valueNode = node.jjtGetChild(1);
				NodeInfo childInfo = travelNode(valueNode);
				if(childInfo.isConst)
					constFoldingTable.put(valueNode, calConst(valueNode));
				info.isConst = false;
			} else if(node.getId() == JJTCONDITION) {
				Node leftNode = node.jjtGetChild(0), rightNode = node.jjtGetChild(1);
				NodeInfo leftInfo = travelNode(leftNode), rightInfo = travelNode(rightNode);
				if(leftInfo.isConst)
					constFoldingTable.put(leftNode, calConst(leftNode));
				if(rightInfo.isConst)
					constFoldingTable.put(rightNode, calConst(rightNode));
				info.isConst = false;
			} else {
				for(int i = 0; i < length; i++) {
					NodeInfo childInfo = travelNode(node.jjtGetChild(i));
					result = prime * result + childInfo.hash;
					info.isConst &= childInfo.isConst;
				}
			}
		} else {
			switch (node.getId()) {
			case JJTIDENTIFIER:
				info.isConst = false;
				//THOUGH
			case JJTNUMBER:	
				result = prime * result + ((SimpleNode)node).jjtGetValue().hashCode();
				break;
			default:
				throw new RuntimeException("Unexpected node:" + node + 
						" Expect:JJTIDENTIFIER or JJTNUMBER" + 
						" Actual:" + jjtNodeName[node.getId()]);
			}
		}
		nodeHash.put(node, result);
		info.hash = result;
		return info;
	}
	
	private String calConst(Node node) {
		int value = calConstValue(node);
		return String.valueOf(value);
	}
	
	private int calConstValue(Node node) {
		switch (node.getId()) {
		case JJTADDITIVEEXPRESSION:
		case JJTMULTIPLICATIVEEXPRESSION:
			ArrayList<Operator> operators = (ArrayList<Operator>)((SimpleNode)node).jjtGetValue();
			int lhs = calConstValue(node.jjtGetChild(0));
			for(int i = 0; i < operators.size(); i++) {
				int rhs = calConstValue(node.jjtGetChild(i+1));
				switch (operators.get(i)) {
				case ADD:
					lhs += rhs;
					break;
				case SUB:
					lhs -= rhs;
					break;
				case MUL:
					lhs *= rhs;
					break;
				case DIV:
					lhs /= rhs;
					break;
				default:
					throw new RuntimeException("Unexpected operator");
				}
			}
			return lhs;
		case JJTNUMBER:
			return getNumberInt((String)((SimpleNode)node).jjtGetValue());
		default:
			throw new RuntimeException("Unexpected node:" + node + 
					" JJTADDITIVEEXPRESSION, JJTMULTIPLICATIVEEXPRESSION or JJTNUMBER" + 
					" Actual:" + jjtNodeName[node.getId()]);
		}
	}
	
	private int getNumberInt(String str) {
		str = str.toLowerCase();
		if(str.startsWith("0"))
			if(str.length()==1)
				return 0;
			else
				return Integer.valueOf(str.substring(1), 8);
		else if(str.startsWith("0x"))
			return Integer.valueOf(str.substring(1), 16);
		else if(str.startsWith("0b"))
			return Integer.valueOf(str.substring(1), 2);
		else if(str.indexOf("e") > -1)
		{
			String[] strings = str.split("e");
			float l1 = Float.valueOf(strings[0]);
			int l2 = Integer.valueOf(strings[1]);
			return (int)(Math.pow(10, l2) * l1);
		}
		return Integer.valueOf(str);
	}
	
	private static class NodeInfo {
		int hash;
		boolean isConst = true;
	}

	@Override
	protected void visitAssignment (TACProgram program, Node statement) {
		assertChildrenNum(statement, 2);
		String theConst = null;
		if((theConst = constFoldingTable.get(statement.jjtGetChild(1))) != null) {
			Variable variable = visitIdentifier(program, statement.jjtGetChild(0), false);
			RValue rvalue = program.constant(theConst);
			program.assign(variable, Operator.ASSIGN, rvalue, null);
			releaseTemp(rvalue);
		} else {
			super.visitAssignment(program, statement);
		}
	}

	@Override
	protected Condition visitCondition (TACProgram program, Node condition) {
		assertType(condition, JJTCONDITION);
		assertChildrenNum(condition, 2);
		String theConst = null;
		RValue left = null, right = null;
		if((theConst = constFoldingTable.get(condition.jjtGetChild(0))) != null) {
			left = program.constant(theConst);
		} else {
			left = visitExpression(program, condition.jjtGetChild(0)); //生成LHS的计算
		}
		if((theConst = constFoldingTable.get(condition.jjtGetChild(1))) != null) {
			right = program.constant(theConst);
		} else {
			right = visitExpression(program, condition.jjtGetChild(1)); //生成RHS的计算
		}
		Condition cond = program.new Condition(left, right, (Cond)((SimpleNode)condition).jjtGetValue()); //这里并不会生成代码 8-)
		releaseTemp(left);  //LHS或RHS的计算结果很可能是个临时变量,所以尝试释放它们
		releaseTemp(right);
		return cond;
	}
}
