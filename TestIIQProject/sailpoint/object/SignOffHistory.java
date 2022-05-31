/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.Date;


/**
 * This is the object to store SailPointObject signoffs. This was previously used
 * strictly for Certification signoffs, but has now been extended to include all
 * SailPointObject signoffs.
 * 
 * If a signoff includes text/application/account, this signoff is considered to be 
 * an "electronic signature" signoff. This means that a user had to successfully provide 
 * login credentials in order to signoff.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 * @author <a href="mailto:ryan.pickens@sailpoint.com">Ryan Pickens</a>
 */
@XMLClass(xmlname="SignOffHistoryItem")
public class SignOffHistory extends SailPointObject {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private String signerId;
    private String signerName;
    private String signerDisplayName;
    private Date date;
    /**
     * The name of the application used for authentication.
     */
    private String _application;

    /**
     * The name of the application account that was authenticated.
     */
    private String _account;
    
    /**
     * The "meaning" text presented to the user.
     * 
     * @ignore
     * Need to store the locale?
     */
    private String _text;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor. Required by persistence.
     */
    public SignOffHistory() {}

    /**
     * Constructor. This uses the current time as the date.
     */
    public SignOffHistory(Identity signer) {
        if (signer != null){
            this.signerId = signer.getId();
            this.signerName = signer.getName();
            this.signerDisplayName = signer.getDisplayName();
        }
        this.date = new Date();
    }

    public SignOffHistory(String signerId, String signerName, String signerDisplayName, Date date) {
        this.signerId = signerId;
        this.signerName = signerName;
        this.signerDisplayName = signerDisplayName;
        this.date = date;
    }

    /**
     * Constructor used for electronicSignatures.
     */
    public SignOffHistory(String signerId, String signerName, String signerDisplayname, Date date, String application, String account, String text) {
        this.signerId = signerId;
        this.signerName = signerName;
        this.signerDisplayName = signerDisplayname;
        this.date = date;
        this._application = application;
        this._account = account;
        this._text = text;
    }
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public Identity getSigner(Resolver resolver) throws GeneralException {
        boolean useId = null != this.signerId;
        return useId ? resolver.getObjectById(Identity.class, this.signerId) : resolver.getObjectByName(Identity.class, this.signerName);
    }

    @XMLProperty
    public String getSignerId() {
        return signerId;
    }

    public void setSignerId(String signerId) {
        this.signerId = signerId;
    }

    @XMLProperty
    public String getSignerName() {
        return signerName;
    }

    public void setSignerName(String signerName) {
        this.signerName = signerName;
    }

    @XMLProperty
    public String getSignerDisplayName() {
        return signerDisplayName;
    }

    public void setSignerDisplayName(String signerDisplayName) {
        this.signerDisplayName = signerDisplayName;
    }

    public String getSignerDisplayableName() {
        return ((this.signerDisplayName != null) ? 
                this.signerDisplayName : this.signerName);
    }

    @XMLProperty
    public Date getDate() {
        return this.date;
    }   
    
    public void setApplication(String s) {
        _application = s;
    }

    @XMLProperty
    public String getApplication() {
        return _application;
    }
    
    public void setAccount(String s) {
        _account = s;
    }

    @XMLProperty
    public String getAccount() {
        return _account;
    }

    
    public void setText(String s) {
        _text = s;
    }

    @XMLProperty
    public String getText() {
        return _text;
    }

    /**
     * Derived property indicating whether or not this
     * signoff was electronically signed.
     */
    @XMLProperty
    public boolean isElectronicSign() {
        return !Util.isNullOrEmpty(_text);
    }

    /**
     * @deprecated  This property is derived and might not be set.
     */
    @Deprecated
    public void setElectronicSign(boolean eSigned) {

    }

    /**
     * @exclude
     * Date is immutable - this is required for persistence.
     * 
     * @deprecated use constructor to set.
     */
    public void setDate(Date date) {
        this.date = date;
    }

    @Override
    public boolean hasName() {
        return false;
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // OBJECT OVERRIDES - equals() and hashCode() are implemented to provide
    // dirty checking in hibernate.
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SignOffHistory that = (SignOffHistory) o;

        if (date != null ? !date.equals(that.date) : that.date != null) return false;
        if (signerId != null ? !signerId.equals(that.signerId) : that.signerId != null) return false;
        if (signerName != null ? !signerName.equals(that.signerName) : that.signerName != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (signerId != null ? signerId.hashCode() : 0);
        result = 31 * result + (signerName != null ? signerName.hashCode() : 0);
        result = 31 * result + (date != null ? date.hashCode() : 0);
        return result;
    }
    
}
