/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import sailpoint.object.SignOffHistory;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.tools.GeneralException;

import java.util.Date;

/**
 * DTO class to represent a certification signoff
 */
public class CertificationSignoffDTO {
    
    private IdentitySummaryDTO signer;
    private Date date;
    private String application;
    private String account;
    private String esigMeaning;

    /**
     * Constructor
     * @param signOffHistory SignoffHistory object.
     */
    public CertificationSignoffDTO(SignOffHistory signOffHistory) throws GeneralException {
        if (signOffHistory == null) {
            throw new GeneralException("signoffHistory is required");
        }
        
        this.signer = new IdentitySummaryDTO(
                signOffHistory.getSignerId(), 
                signOffHistory.getSignerName(), 
                signOffHistory.getSignerDisplayName(), 
                false);
        
        this.date = signOffHistory.getDate();
        this.application = signOffHistory.getApplication();
        this.account = signOffHistory.getAccount();
        this.esigMeaning = signOffHistory.getText();
    }

    /**
     * @return IdentitySummaryDTO representing identity doing the signing
     */
    public IdentitySummaryDTO getSigner() {
        return signer;
    }

    /**
     * @return Date the signing occurred
     */
    public Date getDate() {
        return date;
    }

    /**
     * @return Application name from the signing 
     */
    public String getApplication() {
        return application;
    }

    /**
     * @return Account name from the signing 
     */
    public String getAccount() {
        return account;
    }

    /**
     * @return Text of the electronic signature meaning, if esigned.
     */
    public String getEsigMeaning() {
        return esigMeaning;
    }
}
