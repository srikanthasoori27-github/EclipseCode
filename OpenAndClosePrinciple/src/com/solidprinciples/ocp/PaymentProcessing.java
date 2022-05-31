package com.solidprinciples.ocp;

public class PaymentProcessing {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		Payment payment = new DebitCard();
		payment.processPayment();
		
		Payment netPayment = new NetBanking();
		netPayment.processPayment();

	}

}
