import java.util.Arrays;

public class Stream_Arrays {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		String arr[] = {"Srikanth","Sachin","Kohli","Sravya","Test","Tendulkar"};
		
		Arrays.stream(arr).filter(s->s.startsWith("T")).forEach(x->System.out.println(x));
		
		
		// Map function on Arrays
		
		Integer intArr[] = {1,2,3,4,5};
		Arrays.stream(intArr).map(x->x*x).forEach(x->System.out.print(x+" "));

	}

}
