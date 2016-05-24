package tac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import parser.Node;
import parser.SimpleNode;
import tac.TACProgram.Cond;
import tac.TACProgram.Condition;
import tac.TACProgram.Operator;
import tac.TACProgram.RValue;
import tac.TACProgram.Variable;

public class TACOptimizedGenerator extends TACGenerator {

	private Map<Node, String> constFoldingTable = new IdentityHashMap<>();
	private List<NodeInfo> expressions = new ArrayList<>();
	private Map<NodeInfo, Map<String, Integer>> varScopeInfo = new HashMap<>();
	private Map<String, Integer> currentScopeInfo = new HashMap<>();
	private Map<Node, String> commonSubexpressionEliminationTable = new IdentityHashMap<>();
	
	public TACOptimizedGenerator(Node astRoot) throws IllegalArgumentException {
		super(astRoot);
		travelAst(astRoot);
	}
	
	private void travelAst(Node node) {
		travelNode(node);
		cse();
	}
	
	private NodeInfo travelNode(Node node) {
		final int prime = 31;
		int result = 1;
		NodeInfo info = new NodeInfo();
		int length = node.jjtGetNumChildren();
		if(length > 0) {
			int id = node.getId();
			//常量折叠
			if(id == JJTASSIGNMENTSTATEMENT) {
				travelNode(node.jjtGetChild(0));
				String varName = (String)((SimpleNode)node.jjtGetChild(0)).jjtGetValue();
				Node valueNode = node.jjtGetChild(1);
				NodeInfo childInfo = travelNode(valueNode);
				if(childInfo.isConst)
					constFoldingTable.put(valueNode, calConst(valueNode));
				else {
					childInfo.assignName = varName;
					expressions.add(childInfo);
					varScopeInfo.put(childInfo, new HashMap<>(currentScopeInfo));
				}
				info.isConst = false;
				//更新变量作用域信息
				
				Integer scopeId = currentScopeInfo.get(varName);
				if(scopeId == null) {
					currentScopeInfo.put(varName, 1);
				} else {
					currentScopeInfo.put(varName, scopeId + 1);
				}
			} else if(id == JJTCONDITION) {
				Node leftNode = node.jjtGetChild(0), rightNode = node.jjtGetChild(1);
				NodeInfo leftInfo = travelNode(leftNode), rightInfo = travelNode(rightNode);
				if(leftInfo.isConst)
					constFoldingTable.put(leftNode, calConst(leftNode));
				else {
					expressions.add(leftInfo);
					varScopeInfo.put(leftInfo, new HashMap<>(currentScopeInfo));
				}
				if(rightInfo.isConst)
					constFoldingTable.put(rightNode, calConst(rightNode));
				else {
					expressions.add(rightInfo);
					varScopeInfo.put(rightInfo, new HashMap<>(currentScopeInfo));
				}
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
		//nodeHash.put(node, result);
		info.hash = result;
		info.node = node;
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
			return NumberUtil.getNumberInt((String)((SimpleNode)node).jjtGetValue());
		default:
			throw new RuntimeException("Unexpected node:" + node + 
					" JJTADDITIVEEXPRESSION, JJTMULTIPLICATIVEEXPRESSION or JJTNUMBER" + 
					" Actual:" + jjtNodeName[node.getId()]);
		}
	}
	
	private void cse() {
		int length = expressions.size();
		for(int i = 0; i < length; i++) {
			NodeInfo source = expressions.get(i);
			for(int j = i+1; j < length; j++) {
				NodeInfo target = expressions.get(j);
				if(source.hash == target.hash) {
					if(isNodeEquals(source.node, target.node)) {
						if(isInSameScope(source.assignName, source, target)) {
							SimpleNode ss = (SimpleNode)source.node;
							commonSubexpressionEliminationTable.put(target.node, source.assignName);
							//System.out.println("CSE!");
						}
					}
				}
			}
		}
	}
	
	private boolean isNodeEquals(Node n1, Node n2) {
		if(n1.getId() != n2.getId())
			return false;
		if(n1.jjtGetNumChildren() != n2.jjtGetNumChildren())
			return false;
		SimpleNode sn1 = (SimpleNode)n1, sn2 = (SimpleNode)n2;
		if(sn1.jjtGetValue() == null && sn2.jjtGetValue() != null)
			return false;
		if(sn1.jjtGetValue() == null || !sn1.jjtGetValue().equals(sn2.jjtGetValue()))
			return false;
		boolean result = true;
		for(int i = 0; i < n1.jjtGetNumChildren(); i++) {
			result &= isNodeEquals(n1.jjtGetChild(i), n2.jjtGetChild(i));
		}
		return result;
	}
	
	private boolean isInSameScope(String assignName, NodeInfo ni1, NodeInfo ni2) {
		Map<String, Integer> vs1 = varScopeInfo.get(ni1), vs2 = varScopeInfo.get(ni2);
		SimpleNode sn1 = (SimpleNode)ni1.node;
		return (assignName == null || isInSameScopeDo(assignName, vs1, vs2)) && isInSameScopeDo(sn1, vs1, vs2);
	}
	
	private boolean isInSameScopeDo(String assignName, Map<String, Integer> scope1, Map<String, Integer> scope2) {
		String name = assignName;
		Integer s1 = scope1.get(name);
		Integer s2 = scope2.get(name);
		if(s1 == null && s2 == 1)
			return true;
		if(s1 != null && s2 != s1 + 1)
			return true;
		return false;
	}
	
	private boolean isInSameScopeDo(SimpleNode node, Map<String, Integer> scope1, Map<String, Integer> scope2) {
		if(node.getId() == JJTIDENTIFIER) {
			String name = (String)node.jjtGetValue();
			Integer s1 = scope1.get(name);
			Integer s2 = scope2.get(name);
			if(s1 == null && s2 == null)
				return true;
			if(s1 != null && s1.equals(s2))
				return true;
			return false;
		}
		boolean result = true;
		for(int i = 0; i < node.jjtGetNumChildren(); i++) {
			result &= isInSameScopeDo((SimpleNode)node.jjtGetChild(i), scope1, scope2);
		}
		return result;
	}
	
	private static class NodeInfo {
		int hash;
		boolean isConst = true;
		Node node;
		String assignName = null;
	}

	@Override
	protected void visitAssignment (TACProgram program, Node statement) {
		assertChildrenNum(statement, 2);
		String theConst = null, theCSE = null;
		Variable variable = visitIdentifier(program, statement.jjtGetChild(0), false);
		RValue rvalue;
		if((theConst = constFoldingTable.get(statement.jjtGetChild(1))) != null) {
			rvalue = getConstant(program, theConst);
		} else if((theCSE = commonSubexpressionEliminationTable.get(statement.jjtGetChild(1))) != null) {
			rvalue = getVariable(program, theCSE, true);
		} else {
			rvalue = visitExpression(program, statement.jjtGetChild(1));
		}
		program.assign(variable, Operator.ASSIGN, rvalue, null);
		releaseTemp(rvalue);
	}

	@Override
	protected Condition visitCondition (TACProgram program, Node condition) {
		assertType(condition, JJTCONDITION);
		assertChildrenNum(condition, 2);
		String theConst = null;
		RValue left = null, right = null;
		if((theConst = constFoldingTable.get(condition.jjtGetChild(0))) != null) {
			left = getConstant(program, theConst);
		} else {
			left = visitExpression(program, condition.jjtGetChild(0)); //生成LHS的计算
		}
		if((theConst = constFoldingTable.get(condition.jjtGetChild(1))) != null) {
			right = getConstant(program, theConst);
		} else {
			right = visitExpression(program, condition.jjtGetChild(1)); //生成RHS的计算
		}
		Condition cond = program.new Condition(left, right, (Cond)((SimpleNode)condition).jjtGetValue()); //这里并不会生成代码 8-)
		releaseTemp(left);  //LHS或RHS的计算结果很可能是个临时变量,所以尝试释放它们
		releaseTemp(right);
		return cond;
	}
}
