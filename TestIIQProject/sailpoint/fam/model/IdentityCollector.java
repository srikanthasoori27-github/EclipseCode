/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

public class IdentityCollector extends FAMObject {

    boolean _isAuthenticationStore;

    public boolean isAuthenticationStore() {
        return _isAuthenticationStore;
    }

    public void setAuthenticationStore(boolean authenticationStore) {
        _isAuthenticationStore = authenticationStore;
    }
}
