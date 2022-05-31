package com.solidprinciples.lsp;

public class TestLSP {

	static Object info = null;
	public static void main(String[] args) {
		
		ParentClass pc = new ParentClass();
		info= pc.getInfo();
		savePostalCode(info);
		ChildClass cc = new ChildClass();
		info = cc.getInfo();
		// this breaks as the return type is not int. 
		savePostalCode(info);
	}
	
	public static void savePostalCode(Object info)
	{
		//postal code must be integer.. validate info to be of int
		// If the client uses the parent class which returns the info as int it is fine. 
		// If the client replaces the parent class with subclass 
		// it breaks the operation as in this context the info is of type String
		System.out.println(info);
	}

}
