package com.solidprinciples.ocp;

public class NetBanking implements Payment{

	@Override
	public void processPayment() {
		// TODO Auto-generated method stub
		System.out.println("Payment from Netbanking");
	}

}
