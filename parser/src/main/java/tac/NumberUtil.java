package tac;

final class NumberUtil {
	
	private NumberUtil() {}
	
	static int getNumberInt(String str) {
		str = str.toLowerCase();
		if(str.startsWith("0x"))
			return Integer.valueOf(str.substring(2), 16);
		else if(str.startsWith("0"))
			if(str.length()==1)
				return 0;
			else
				return Integer.valueOf(str.substring(1), 8);
		else if(str.startsWith("0b"))
			return Integer.valueOf(str.substring(2), 2);
		else if(str.indexOf("e") > -1)
		{
			String[] strings = str.split("e");
			float l1 = Float.valueOf(strings[0]);
			int l2 = Integer.valueOf(strings[1]);
			return (int)(Math.pow(10, l2) * l1);
		}
		return Integer.valueOf(str);
	}
}
