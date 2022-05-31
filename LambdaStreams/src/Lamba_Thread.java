
public class Lamba_Thread {

	public static void main(String[] args) {
		
		System.out.println("start of main thread");
		
		/*
		 * Thread t = new Thread(new Runnable(){
		 * 
		 * public void run() { System.out.println("start of child thread"); } });
		 */
		
		Thread t = new Thread(()->System.out.println("Inside the child thread"));
		t.start();
		
		Thread t1 = new Thread(()->System.out.println("Inside the child thread"));
		t1.start();
		
		Thread t2 = new Thread(()->System.out.println("Inside the child thread"));
		t2.start();
		
		System.out.println("end of main thread");

	}

}
