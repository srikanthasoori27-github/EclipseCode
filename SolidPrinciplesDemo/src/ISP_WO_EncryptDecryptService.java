//Every Impl class of this interface should implement all the three methods even though they are not totally dependent
// This violates the ISP principle
// So segregate the interface into different modular interfaces with only required dependent functions for implementation classes.
public interface ISP_WO_EncryptDecryptService {
	
	public Object encrypt(String pswd);
	public Object decrypt(String pswd);
	public void savePassword(String pswd);

}
