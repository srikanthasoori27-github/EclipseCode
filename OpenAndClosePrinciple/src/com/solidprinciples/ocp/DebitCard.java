package com.solidprinciples.ocp;

public class DebitCard implements Payment {

	@Override
	public void processPayment() {
		// TODO Auto-generated method stub
		System.out.println("Payment method from Debit card");
	}

}
