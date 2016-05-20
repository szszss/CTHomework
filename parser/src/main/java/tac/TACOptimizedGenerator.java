package tac;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import parser.Node;
import parser.SimpleNode;
import tac.TACProgram.Operator;

public class TACOptimizedGenerator extends TACGenerator {

	private Map<Node, Integer> nodeHash = new IdentityHashMap<>();
	private Map<Node, Node> constFoldingTable = new IdentityHashMap<>();
	
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
			if(node.getId() == JJTASSIGNMENTSTATEMENT) {
				travelNode(node.jjtGetChild(0));
				Node valueNode = node.jjtGetChild(1);
				NodeInfo childInfo = travelNode(valueNode);
				if(childInfo.isConst)
					constFoldingTable.put(node, calConst(valueNode));
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
	
	private Node calConst(Node node) {
		int value = calConstValue(node);
		SimpleNode constNode = new SimpleNode(JJTNUMBER);
		constNode.jjtSetValue(value);
		return constNode;
	}
	
	private int calConstValue(Node node) {
		switch (node.getId()) {
		case JJTADDITIVEEXPRESSION:
		case JJTMULTIPLICATIVEEXPRESSION:
			int lhs = calConstValue(node.jjtGetChild(0)), rhs = calConstValue(node.jjtGetChild(1));
			Operator operator = (Operator)((SimpleNode)node).jjtGetValue();
			switch (operator) {
			case ADD:
				return lhs + rhs;
			case SUB:
				return lhs - rhs;
			case MUL:
				return lhs * rhs;
			case DIV:
				return lhs / rhs;
			default:
				throw new RuntimeException("Unexpected operator:" + operator);
			}
		case JJTNUMBER:
			return (int)((SimpleNode)node).jjtGetValue();
		default:
			throw new RuntimeException("Unexpected node:" + node + 
					" JJTADDITIVEEXPRESSION, JJTMULTIPLICATIVEEXPRESSION or JJTNUMBER" + 
					" Actual:" + jjtNodeName[node.getId()]);
		}
	}
	
	private static class NodeInfo {
		int hash;
		boolean isConst = true;
	}
}
