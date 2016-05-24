package parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.util.Scanner;

import tac.TACGenerator;
import tac.TACOptimizedGenerator;
import tac.TACProgram;
import tac.TACVM;

public class Parser implements AstParserTreeConstants {
	
	public static void main(String[] args) {
		TACProgram lastProgram = null;
		if(args.length == 1) {
			File file = null;
			BufferedReader reader = null;
			try {
				file = new File(args[0]);
				reader = new BufferedReader(new FileReader(file));
				StringBuilder sb = new StringBuilder();
				String str;
				while((str = reader.readLine()) != null)
					sb.append(str);
				StringReader strReader = new StringReader(sb.toString());
				try {
					AstParser parser = new AstParser(strReader);
					SimpleNode node = parser.Start();
					node.dump("");
					System.out.println("");
					
					TACGenerator tacg = new TACGenerator(node);
					System.out.println("Unopted");
					System.out.println(tacg.generate().dump());
					
					TACOptimizedGenerator tacog = new TACOptimizedGenerator(node);
					System.out.println("Opted");
					lastProgram = tacog.generate();
					System.out.println(lastProgram.dump());
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					if(reader != null)
						reader.close();
				} catch (Exception e2) {}
			}
		}
		String str;
		Scanner scanner = new Scanner(System.in);
		while(true) {
			str = scanner.nextLine();
			if(str.equals("!exit")) {
				break;
			} else if(str.equals("!run")) {
				if(lastProgram == null) {
					System.out.println("No program yet.");
					continue;
				}
				TACVM vm = new TACVM(lastProgram);
				vm.run();
				continue;
			}
			StringBuilder sb = new StringBuilder(str).append("\n");
			while(!(str = scanner.nextLine()).equals("#"))
			{
				sb.append(str).append("\n");
			}
			StringReader reader = new StringReader(sb.toString());
			try {
				AstParser parser = new AstParser(reader);
				SimpleNode node = parser.Start();
				node.dump("");
				System.out.println("");
				
				TACGenerator tacg = new TACGenerator(node);
				System.out.println("Unopted");
				System.out.println(tacg.generate().dump());
				
				TACOptimizedGenerator tacog = new TACOptimizedGenerator(node);
				System.out.println("Opted");
				lastProgram = tacog.generate();
				System.out.println(lastProgram.dump());
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
