package com.solidprinciples.srp;

public class AccountManagement {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		AccountInfo accntInfo = new AccountInfo();
		
		accntInfo.openAccount();
		
		AccountSave accntSave = new AccountSave();
		
		accntSave.saveAccountInfo();
		
		Notification notify = new Notification();
		
		notify.sendNotification();

	}

}
