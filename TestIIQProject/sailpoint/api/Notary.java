/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class to aid in the creation of electronic signatures.
 *
 * Author: Jeff
 * 
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.Configuration;
import sailpoint.object.ESignatureType;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.object.SignOffHistory;
import sailpoint.object.WorkItem;
import sailpoint.server.Authenticator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class Notary  {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Notary.class);

    /**
     * Special configuration attribute for the unit tests to bypass
     * authentication.
     */
    public static final String CONFIG_SIMULATE_AUTH = "simulateAuthentication";

    /**
     * Everyone loves context.
     */
    SailPointContext _context;

    /**
     * Cached signature configuration.
     */
    Configuration _config;
    
    /**
     * Locale from current session
     */
    private Locale _locale;

    //////////////////////////////////////////////////////////////////////
    //
    // Interface
    //
    //////////////////////////////////////////////////////////////////////

    public Notary(SailPointContext con, Locale locale) {
        _context = con;
        _locale = locale;
    }

    /**
     * Determine whether we need to show a signature "meaning" before the
     * object can be saved.
     */
    public String getSignatureMeaning(SailPointObject obj) 
        throws GeneralException {
        
        if (obj instanceof WorkItem) {
            WorkItem item = (WorkItem)obj;
            return getSignatureMeaning(WorkItem.class, item.getAttributes());
        }
        else if (obj instanceof Certification) {
            Certification cert = (Certification)obj;
            /* Since we don't use any system defaults for esignature, we
             * can just get direct from the attributes map for consistency instead 
             * of using Certification.getAttribute. */ 
            return getSignatureMeaning(Certification.class, cert.getAttributes());
        }
        
        return null;
    }

    /**
     * Get the signature meaning from attributes map of given class type
     * @param objectType Class of object
     * @param attributes Attributes of object
     * @return String meaning, or null
     * @throws GeneralException
     */
    public String getSignatureMeaning(Class objectType, Attributes attributes) 
        throws GeneralException {
        
        String text = null;
        
        if (null != attributes) {
            String type = null;

            if (WorkItem.class.equals(objectType)) {
                type = attributes.getString(WorkItem.ATT_ELECTRONIC_SIGNATURE);
            } else if (Certification.class.equals(objectType)) {
                type = attributes.getString(Certification.ATT_SIGNATURE_TYPE);
            }

            if (type != null) {
                text = getLocalizedMeaning(type);
            }
        }

        return text;
    }
    
    /**
     * This method will resolve the meaning type to a localized message.
     * 
     * @param type
     * @return
     * @throws GeneralException
     */
    public String getLocalizedMeaning(String type) throws GeneralException {
        String localized = null;
        
        if (type != null) {
                Map<String,String> meanings = null;

                List<ESignatureType> sigs = this.getESignatureTypes();
                for (ESignatureType sig : sigs) {
                    if (type.equals(sig.getName())) {
                        meanings = sig.getMeanings();
                        break;
                    }
                }

                localized = filterMeaningByLocale(meanings);
        }
        return localized;
    }

	/**
     * Given the signature credentials gathered on the page,
     * validate them and sign the object.
     *
     * The page backing bean is expected to call this unconditionally 
     * in the save method, the values will be null if getSignatureText
     * returned null.  We check again whether a signature is required
     * so the backing bean doesn't have to worry about it.
     */
    public void sign(SailPointObject obj, String accountId, String password)
            throws GeneralException, AuthenticationFailureException, ExpiredPasswordException {

        String text = getSignatureMeaning(obj);
        if (text != null) {
            Identity ident = authenticate(accountId, password);

            // assuming we're still here the object has been signed
            //TODO: Need to add application once we get that
            SignOffHistory esig = new SignOffHistory(ident.getId(), ident.getName(), ident.getDisplayableName(), new Date(), ident.getAuthApplication(), accountId, text);
            obj.addSignOff(esig);
        }
    }

    /**
     * Authenticate the account and password. This does not sign anything.
     * @param accountId Account ID
     * @param password Password
     * @return Identity that was authenticated
     */
    public Identity authenticate(String accountId, String password)
            throws GeneralException, AuthenticationFailureException, ExpiredPasswordException {

        // must have credentials
        if (accountId == null)
            throw new GeneralException("Missing account id");

        if (password == null)
            throw new GeneralException("Missing password");

        // TODO: need a new interface so Authenticator can pass
        // back the Application it used.    
        // for the unit tests allow this to be configured out
        Authenticator auth = new Authenticator(_context);
        return auth.authenticate(accountId, password); // throws AuthenticationFailureException
    }

    /**
     * Set the locale in case this needs to change after instantiation.
     */
    public void setLocale(Locale locale) {
        _locale = locale;
    }
    
	@SuppressWarnings("unchecked")
	public List<ESignatureType> getESignatureTypes() throws GeneralException {
		List<ESignatureType> sigs = new ArrayList<ESignatureType>();
		
		Configuration config = _context.getObjectByName(Configuration.class, Configuration.ELECTRONIC_SIGNATURE);
		if (config != null) {
			Object o = config.get(ESignatureType.CONFIG_ATT_TYPES);
			if (o instanceof List) {
				sigs = (List<ESignatureType>) o;
			}
		}
		
		return sigs;
	}

	private String filterMeaningByLocale(Map<String, String> meanings) throws GeneralException {
		String localized = null; 
		
		if (meanings != null) {
		    localized = meanings.get(_locale.toString());                    
		    // TODO: Is this appropriate behavior? What if there is
		    // no localized meaning and the signer doesn't understand English?
		    // Would it be better to throw an error?                    
		    if ( Util.getString(localized) == null ) {
		        String iiqDefault = _context.getConfiguration().getString(Configuration.DEFAULT_LANGUAGE);
		        if ( iiqDefault != null  && !iiqDefault.equals( _locale.toString()) ) {
		            localized = meanings.get(iiqDefault);
		        } 
		        // djs: think about this case! what happens when its null? Should we just use a default meaning?                        
		        if ( localized == null )  {
		            if (!_locale.toString().equals(Locale.getDefault().toString())) {
		                localized = meanings.get(Locale.getDefault().toString());
		            }
		        }
		    }
		}
		return localized;
	}
}

