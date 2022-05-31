package com.solidprinciples.ocp;

public class Payment_WO_OCP {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		Payment_WO_OCP payment = new Payment_WO_OCP();
		payment.processPayment("Debitcard");
		payment.processPayment("Netbanking");

	}
	
	public void processPayment(String mode)
	{
		
		if(mode.equalsIgnoreCase("Debitcard"))
			System.out.println("payment from debit card");
		if(mode.equalsIgnoreCase("Netbanking"))
			System.out.println("payment from Netbanking");
		
	}

}
