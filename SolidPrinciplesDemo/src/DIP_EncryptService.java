
public class DIP_EncryptService {
	
	private EnryptionAlg encryptionAlg;
	
	DIP_EncryptService(EnryptionAlg encryptionAlg)
	{
		this.encryptionAlg=encryptionAlg;
	}
	
	public void encrypt(String pswd)
	{
		encryptionAlg.encrypt(pswd);
	}


}
