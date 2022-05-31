import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Streams_Files {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try (Stream <String> p=Files.lines(Paths.get("testFile.txt"));) {
			System.out.println(p.count());
			p.filter(s->s.length()>5).sorted().forEach(System.out::println);
			p.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
		

	}

}
