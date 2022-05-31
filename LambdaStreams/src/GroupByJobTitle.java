import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;


public class GroupByJobTitle {
	
	class Emp
	{
	String name;
	int id;

	Emp(String name, int id )
	{
	this.id=id;
	this.name=name;

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

	}
	
	public static void main(String args[])
	{
		
		List<Emp> emp = new ArrayList<Emp>();
		emp.add(new Emp("Software Engineer",1));
		emp.add(new Emp("Senior Software Engineer",2));
		emp.add(new Emp("Engineer",3));
		emp.add(new Emp("Software Engineer",4));
		
		// Group the Emps by job title
		
		System.out.println(emp.stream().collect(Collectors.groupingBy(Emp::getName()));
		
	}

	

}
