package tac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import parser.AstParserTreeConstants;
import parser.Node;
import parser.SimpleNode;
import tac.TACProgram.Cond;
import tac.TACProgram.Condition;
import tac.TACProgram.Constant;
import tac.TACProgram.JIT;
import tac.TACProgram.Jump;
import tac.TACProgram.Label;
import tac.TACProgram.Operator;
import tac.TACProgram.RValue;
import tac.TACProgram.Temp;
import tac.TACProgram.Variable;

public class TACGenerator implements AstParserTreeConstants{

	private final Node root;
	private Map<String, Variable> variables;
	private Map<String, Constant> constants;
	private LinkedList<Temp> temps;
	
	/**
	 * 创建一个三地址码生成器
	 * @param astRoot AST的根节点,类型应为Start
	 * @throws IllegalArgumentException 如果传入节点不是根节点,或者AST不正确.
	 */
	public TACGenerator(Node astRoot) throws IllegalArgumentException {
		root = astRoot;
		if(root.getId() != JJTSTART)
			throw new IllegalArgumentException(astRoot + " is not the root of an AST");
		if(root.jjtGetNumChildren() != 1 && root.jjtGetChild(0).getId() != JJTSTATEMENTBLOCK)
			throw new IllegalArgumentException("Can't find the statement block of " + astRoot + "...");
	}
	
	/**
	 * 声明一个新变量或获取一个已有的变量,程序会保证针对同一个变量的引用总是源自唯一一个Variable实例.
	 * @param program
	 * @param name 变量名
	 * @param isRV 当前引用是否是作为右值,这会用于判断变量是参数还是实际变量
	 * @return 唯一的Variable实例
	 */
	protected Variable getVariable(TACProgram program, String name, boolean isRV) {
		Variable variable = variables.get(name);
		if(variable == null)
		{
			variable = program.variable(name, isRV);
			variables.put(name, variable);
		}
		return variable;
	}
	
	protected Constant getConstant(TACProgram program, String value) {
		Constant constant = constants.get(value);
		if(constant == null)
		{
			constant = program.new Constant(value);
			constants.put(value, constant);
		}
		return constant;
	}
	
	protected Temp requestTemp(TACProgram program) {
		if(temps.isEmpty())
			return program.temp();
		return temps.pop();
	}
	
	protected void releaseTemp(Temp temp) {
		temps.push(temp);
	}
	
	protected void releaseTemp(Object object) {
		if(object instanceof Temp)
			releaseTemp((Temp)object);
	}
	
	public synchronized TACProgram generate() {
		TACProgram program = new TACProgram();
		begin();
		try {
			visitStatementBlock(program, root.jjtGetChild(0));
			inject(program);
		} finally {
			end();
		}
		return program;
	}
	
	protected void begin() {
		variables = new HashMap<>();
		constants = new HashMap<>();
		temps = new LinkedList<>();
	}
	
	protected void inject(TACProgram program) {
		
	}
	
	protected void end() {
		variables = null;
		constants = null;
		temps = null;
	}
	
	protected void visitStatementBlock(TACProgram program, Node statementBlock) {
		assertType(statementBlock, JJTSTATEMENTBLOCK);
		for(int i = 0, length = statementBlock.jjtGetNumChildren(); i < length; i++)
		{
			Node statement = statementBlock.jjtGetChild(i);
			visitStatement(program, statement);
		}
	}
	
	protected void visitStatement(TACProgram program, Node unknownStatement) {
		
		switch (unknownStatement.getId()) {
		case JJTASSIGNMENTSTATEMENT : // xx = xx;
			visitAssignment(program, unknownStatement);
			break;
		case JJTIFSTATEMENT : //if then
			visitIfStatement(program, unknownStatement);
			break;
		case JJTIFELSESTATEMENT : //if then else
			//TODO :)
			break;	
		case JJTWHILESTATEMENT : //While
			visitWhileStatement(program, unknownStatement);
			break;
		default:
			throw new RuntimeException("Unexpected node:" + unknownStatement);
		}
	}
	
	protected void visitAssignment(TACProgram program, Node statement) {
		assertChildrenNum(statement, 2);
		Variable variable = visitIdentifier(program, statement.jjtGetChild(0), false);
		RValue rvalue = visitExpression(program, statement.jjtGetChild(1));
		program.assign(variable, Operator.ASSIGN, rvalue, null);
		releaseTemp(rvalue);
	}
	
	protected Variable visitIdentifier(TACProgram program, Node ident, boolean isRV) {
		assertType(ident, JJTIDENTIFIER);
		return getVariable(program, (String)((SimpleNode)ident).jjtGetValue(), isRV);
	}
	
	protected Constant visitNumber(TACProgram program, Node num) {
		assertType(num, JJTNUMBER);
		return getConstant(program, (String)((SimpleNode)num).jjtGetValue());
	}
	
	protected RValue visitExpression(TACProgram program, Node expr) {
		switch (expr.getId()) {
		case JJTADDITIVEEXPRESSION:
		case JJTMULTIPLICATIVEEXPRESSION:
			return visitMath(program, expr);
		case JJTIDENTIFIER:
			return visitIdentifier(program, expr, true);
		case JJTNUMBER:
			return visitNumber(program, expr);
		default:
			throw new RuntimeException("Unexpected node:" + expr);
		}
	}
	
	@SuppressWarnings("unchecked")
	protected RValue visitMath(TACProgram program, Node additive) {
		ArrayList<Operator> operators = (ArrayList<Operator>)((SimpleNode)additive).jjtGetValue();
		int length = additive.jjtGetNumChildren();
		RValue left = null, right = visitExpression(program, additive.jjtGetChild(0));
		for(int i = 1; i < length; i++)
		{
			left = right;
			right = visitExpression(program, additive.jjtGetChild(i));
			Operator operator = operators.get(i-1);
			Temp temp = requestTemp(program);
			program.assign(temp, operator, left, right);
			releaseTemp(left);
			releaseTemp(right);
			right = temp;
		}
		return right;
	}
	
	/**
	 * 访问一个IfStatement语句,首先会访问它的Condition节点,生成条件判断中的表达式求值,然后生成JIT(JumpIfTrue)指令,
	 * 然后生成一个Goto,再生成Label:IfTrue,If语句中的代码块,和Label:IfFalse.最后将JIT的转跳目标指向IfTrue,
	 * Goto的转跳目标指向IfFalse.
	 * @param program
	 * @param ifStatement IfStatement节点
	 */
	protected void visitIfStatement(TACProgram program, Node ifStatement) {
		assertType(ifStatement, JJTIFSTATEMENT);
		assertChildrenNum(ifStatement, 2);
		Condition condition = visitCondition(program, ifStatement.jjtGetChild(0));
		JIT jit = program.jit(condition);
		Jump jumpWhenFalse = program.jump();
		Label ifTrue = program.label();
		Node statementBlock = ifStatement.jjtGetChild(1);
		if(statementBlock.getId() == JJTSTATEMENTBLOCK)
			visitStatementBlock(program, statementBlock);
		else
			visitStatement(program, statementBlock);
		Label ifFalse = program.label();
		jit.target = ifTrue;
		jumpWhenFalse.target = ifFalse;
	}
	
	protected Condition visitCondition(TACProgram program, Node condition) {
		assertType(condition, JJTCONDITION);
		assertChildrenNum(condition, 2);
		RValue left = visitExpression(program, condition.jjtGetChild(0));
		RValue right = visitExpression(program, condition.jjtGetChild(1));
		Condition cond = program.new Condition(left, right, (Cond)((SimpleNode)condition).jjtGetValue());
		releaseTemp(left);
		releaseTemp(right);
		return cond;
	}
	
	protected void visitWhileStatement(TACProgram program, Node whileStatement) {
		assertType(whileStatement, JJTWHILESTATEMENT);
		assertChildrenNum(whileStatement, 2);
		Label before = program.label();
		Condition condition = visitCondition(program, whileStatement.jjtGetChild(0));
		JIT jit = program.jit(condition);
		Jump jumpWhenFalse = program.jump();
		Label ifTrue = program.label();
		Node statementBlock = whileStatement.jjtGetChild(1);
		if(statementBlock.getId() == JJTSTATEMENTBLOCK)
			visitStatementBlock(program, statementBlock);
		else
			visitStatement(program, statementBlock);
		program.jump().target = before;
		Label ifFalse = program.label();
		jit.target = ifTrue;
		jumpWhenFalse.target = ifFalse;
	}
	
	/**
	 * 断言节点类型
	 * @param node 被断言的节点
	 * @param exceptedType 预期的节点类型,见{@link AstParserTreeConstants}
	 * @throws RuntimeException 如果实际的节点类型与预期不符
	 */
	private void assertType(Node node, int exceptedType) throws RuntimeException {
		if(node.getId() != exceptedType)
			throw new RuntimeException("Unexpected node:" + node + 
					" Expect:" + jjtNodeName[exceptedType] + 
					" Actual:" + jjtNodeName[node.getId()]);
	}
	
	/**
	 * 断言子节点数量
	 * @param node 被断言的节点
	 * @param exceptedNum 预期的子节点数量
	 * @throws RuntimeException 如果实际节点数量与预期不符
	 */
	private void assertChildrenNum(Node node, int exceptedNum) throws RuntimeException {
		if(node.jjtGetNumChildren() != exceptedNum)
			throw new RuntimeException("Unexpected number of children:" + node + 
					" Expect:" + exceptedNum + 
					" Actual:" + node.jjtGetNumChildren());
	}
}
