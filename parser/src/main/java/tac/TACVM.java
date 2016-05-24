package tac;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import tac.TACProgram.Assign;
import tac.TACProgram.JIT;
import tac.TACProgram.Jump;
import tac.TACProgram.Label;
import tac.TACProgram.TACCode;
import tac.TACProgram.Variable;

public class TACVM {

	private final TACProgram program;
	
	public TACVM(TACProgram program) {
		this.program = program;
	}
	
	public void run() {
		program.link();
		List<Variable> variables = program.getVariables();
		List<TACCode> codes = program.getCodes();
		Map<String, Integer> runtimeVars = new HashMap<>();
		Scanner scanner = new Scanner(System.in);
		for(Variable variable : variables) {
			if(variable.isParam()) {
				System.out.println("Input init value of param " + variable.getName() + ":");
				boolean readed = false;
				while(!readed) {
					try {
						int num = scanner.nextInt();
						runtimeVars.put(variable.name, num);
						readed = true;
					} catch (Exception e) {
						System.out.println("Not a number, you sucker :)");
					}
				}
			}
		}
		
		int pc = 0;
		int maxPc = codes.size();
		while(pc < maxPc) {
			TACCode code = codes.get(pc);
			int nextPc = code.eval(pc, runtimeVars);
			pc = nextPc;
		}
		
		System.out.println("\nProgram ended. Printing result...");
		for(Variable variable : variables) {
			String name = variable.name;
			Integer value = runtimeVars.get(name);
			if(value == null) {
				System.out.println(name + ": Unused?");
			} else {
				System.out.println(name + ": " + value);
			}
		}
	}
}
