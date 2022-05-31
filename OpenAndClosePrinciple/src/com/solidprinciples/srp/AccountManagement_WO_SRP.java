package com.solidprinciples.srp;

public class AccountManagement_WO_SRP {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		AccountManagement_WO_SRP accntManagement = new AccountManagement_WO_SRP();
		accntManagement.openAccount();
		accntManagement.saveAccountInfo();
		accntManagement.sendNotification();
	}
	
	public void openAccount()
	{
		System.out.println("Fill the account information");
	}
	
	public void saveAccountInfo()
	{
		System.out.println("Save the account info to DB");
	}
	public void sendNotification()
	{
		System.out.println("Send Notification");
	}
}
