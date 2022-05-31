
public class DIP_WO_EncryptService {
	
	private EnryptionAlg encryptionAlg = new EnryptionAlg();
	
	public void encrypt(String pswd)
	{
		encryptionAlg.encrypt(pswd);
	}

}
