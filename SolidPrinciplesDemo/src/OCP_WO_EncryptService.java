
public class OCP_WO_EncryptService {
	
	public Object encryptPassword(String encryptionAlgorithm, String pswd)
	{
		//Encrypt the Passowrd and return according to encryptionAlgorithm
		if(encryptionAlgorithm.equals("MD5"))
		{
		return pswd+"md5";
		}
		if(encryptionAlgorithm.equals("SHA-128"))
		{
		return pswd+"SHA-128";
		}
		if(encryptionAlgorithm.equals("SHA-256"))
		{
		return pswd+"SHA-256";
		}
		return pswd;
	}

}
