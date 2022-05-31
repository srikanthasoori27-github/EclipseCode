package sailpoint.object;

import java.util.Date;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * This is the object used to store an SMS Password Reset Token.
 * 
 * @author ryan.pickens
 * 
 */
@SuppressWarnings("serial")
@XMLClass
public class VerificationToken extends AbstractXmlObject {

    /**
     * Date the token was created. Might not need or care about this date. Keep it
     * around for auditing purposes
     */
    private Date createDate;

    // Date the Token will expire
    private Date expireDate;

    // Encoded TextCode sent via SMS
    private String textCode;

    //initialize the number of failed attempts to 0
    private int failedAttempts = 0;
    
    public VerificationToken(Date create, Date expire, String token) {

        this.createDate = create;
        this.expireDate = expire;
        this.textCode = token;
    }

    public VerificationToken() {
    }

    public VerificationToken(Date expire, String token) {
        this.expireDate = expire;
        this.textCode = token;
    }

    @XMLProperty
    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    @XMLProperty
    public Date getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(Date expireDate) {
        this.expireDate = expireDate;
    }

    @XMLProperty
    public String getTextCode() {
        return textCode;
    }

    /**
     * 
     * @param textCode
     *            Code sent to the user. Should be stored encoded
     */
    public void setTextCode(String textCode) {
        this.textCode = textCode;
    }

    @XMLProperty
    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

}
