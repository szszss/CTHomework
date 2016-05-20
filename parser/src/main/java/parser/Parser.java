package parser;

import java.io.StringReader;
import java.util.Scanner;

import tac.TACGenerator;
import tac.TACOptimizedGenerator;

public class Parser implements AstParserTreeConstants {
	
	public static void main(String[] args) {
		String str;
		Scanner scanner = new Scanner(System.in);
		while(!(str = scanner.nextLine()).equals("!exit")) {
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
				
				TACGenerator tacg = new TACGenerator(node);
				System.out.println(tacg.generate().dump());
				
				TACOptimizedGenerator tacog = new TACOptimizedGenerator(node);
				System.out.println(tacog.generate().dump());
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
