import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
class Employee
{
String name;
int id;

Employee(String name,int id)
{
this.name=name;
this.id=id;
}
public String getName() {
	return name;
}
public void setName(String name) {
	this.name = name;
}
public int getId() {
	return id;
}
public void setId(int id) {
	this.id = id;
}
public String toString()
{
	return id+":"+name;
	
}


}
public class Lambda_CollectionsSorting {

	public static void main(String[] args) {
		
		List<Employee> empList = new ArrayList<Employee>();
		
		empList.add(new Employee("Srikanth",1));
		empList.add(new Employee("Messi",3));
		empList.add(new Employee("Ronaldo",2));
		empList.add(new Employee("Sachin",5));
		empList.add(new Employee("Virat",4));
		empList.add(new Employee("Virat",5));
		
		
		System.out.println(empList);
		Collections.sort(empList,(e1,e2)->e1.id>e2.id?-1:e1.id<e2.id?1:0);
		System.out.println(empList);
		Collections.sort(empList,(e1,e2)->e1.name.compareTo(e2.name));
		empList.forEach(System.out::println);
		System.out.println(empList.stream().collect(Collectors.groupingBy(Employee::getId)));
		
	}

}
