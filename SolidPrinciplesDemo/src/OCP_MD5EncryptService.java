
public class OCP_MD5EncryptService implements OCP_EncryptService {

	@Override
	public Object encryptPassword(String pswd) {
		// TODO Auto-generated method stub
		return pswd+"MD5";
	}

}
