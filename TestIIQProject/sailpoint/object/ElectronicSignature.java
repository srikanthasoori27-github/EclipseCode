/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

/**
 * Author: peter.holcomb
 */
public class ElectronicSignature {

    public static final String INPUT_SIGNATURE_ACCOUNT = "signatureAccountId";
    public static final String INPUT_SIGNATURE_PASSWORD = "signaturePassword";

    private String accountId;
    private String password;

    public ElectronicSignature(String accountId, String password) {
        this.accountId = accountId;
        this.password = password;
    }

    public String getAccountId() {
        return this.accountId;
    }

    public String getPassword() {
        return this.password;
    }
}
