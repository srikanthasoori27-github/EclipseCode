
// This class will perform Encryption, Decryption and Saving the password.
// Violates the SRP - each class should have single purpose to modify 
// if there is any change in encrypt or decrypt or save then the total class should be re tested

public class SRP_WO_EncryptDecryptService {
	
	public Object encryptPassword(String pswd)
	{
		//Encrypt the Passowrd and return that
		return pswd;
	}
	
	public Object decryptPassword(String pswd)
	{
		//Decrypt the password and return that
		return pswd;
	}
	public void savePassword(String pswd)
	{
		//Save password into DB
	}

}
