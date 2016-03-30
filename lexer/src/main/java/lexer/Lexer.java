package lexer;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Lexer implements PseudoLexerConstants{
	
	private List<Token> tokens;
	private Set<String> symbolTable = new HashSet<>();
	private Set<String> constantTable = new HashSet<>();
	private int pointer = 0;

	public Lexer(String string) {
		try {
			tokens = PseudoLexer.lex(new StringReader(string));
			for(Token token : tokens)
			{
				int kind = token.kind;
				switch(kind)
				{
				case IDENTIFIER:
					symbolTable.add(token.image);
					break;
				case INTEGER_DEC:
				case INTEGER_HEX:
				case INTEGER_OCT:
				case REAL_DEC:
				case REAL_HEX:
				case REAL_OCT:
					constantTable.add(token.image);
				default:
					break;
				}
			}
		} catch (Exception e) {
			System.out.println("Failed to lex - " + e.getMessage());
		}
	}
	
	public int scan() {
		if(pointer < tokens.size()) {
			return tokens.get(pointer++).kind;
		}
		return -1;
	}
	
	public int size() {
		return tokens.size();
	}
	
	public void rewind() {
		pointer = 0;
	}
	
	public Set<String> getSymbolTable() {
		return Collections.unmodifiableSet(symbolTable);
	}
	
	public Set<String> getConstantTable() {
		return Collections.unmodifiableSet(constantTable);
	}
	
	public String print() {
		rewind();
		StringBuilder sb  = new StringBuilder("Tokens:\r\n");
		for(Token token : tokens) {
			sb.append(tokenImage[token.kind]).append("    ").append(token.image).append("\r\n");
		}
		sb.append("\r\nSymbols:\r\n");
		for(String symbol : symbolTable) {
			sb.append(symbol).append("\r\n");
		}
		sb.append("\r\nConstants:\r\n");
		for(String constant : constantTable) {
			sb.append(constant).append("\r\n");
		}
		return sb.toString();
	}
	
	public static void main(String[] args) {
		String str;
		Scanner scanner = new Scanner(System.in);
		while(!(str = scanner.nextLine()).equals("!exit")) {
			try {
				Lexer lexer = new Lexer(str);
				System.out.println(lexer.print());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
