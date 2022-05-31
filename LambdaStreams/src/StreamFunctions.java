import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StreamFunctions {

	public static void main(String[] args) {
		
		List<Integer> intList = Arrays.asList(1,2,3,4,5,6);
		
		// allMatch is used to check if all the elements in collection matches the Predicate
		System.out.println(intList.stream().allMatch(s->s%2==0));
		// anyMatch is used to check if any of the element in collection matches the Predicate
		System.out.println(intList.stream().anyMatch(s->s%2==0));
		// To count the number of elements in List
		System.out.println(intList.stream().count());
		// To count the number of elements satisfying the Predicate in List
		System.out.println(intList.stream().filter(i->i%2==0).count());
		
		// for each on the stream
		intList.stream().forEach(x->System.out.print(x+" "));
		System.out.println();
		
		// sorted method to sort and findFirst method to find the first element in the sorted list
		intList.stream().sorted().findFirst().ifPresent(x->System.out.println(x));
		
		// map method is to perform some operation on all the collections
		// collect method is to collect the processed information back to another map
		List<Integer> processedList = intList.stream().filter(i -> i>=2).collect(Collectors.toList());
		processedList.forEach(x->System.out.print(x+" "));
		System.out.println();
		
		// skip function to skip the elements
		IntStream.range(1, 10).skip(5).forEach(System.out::print);
		System.out.println();
		
		// Reduce function
		
		int total = IntStream.range(1, 5).reduce(6, (a,b)->a+b);
		System.out.println(total);
		
		// for( int i= 
		
		// Summary statistics function.
		
		IntSummaryStatistics iss= IntStream.range(1, 15).summaryStatistics();
		System.out.println(iss);
		
		// joining method
		
		String listToString = Arrays.asList("sri","kanth","kohli","virat").stream().map(x->x+"a")
				  .collect(Collectors.joining(", ", "[", "]"));
		System.out.println(listToString);
	}

}
