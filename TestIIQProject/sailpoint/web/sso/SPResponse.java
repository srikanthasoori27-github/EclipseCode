/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.sso;

import org.opensaml.saml.saml2.core.Response;

/**
 * @author chris.annino
 *
 */
public class SPResponse extends SPSamlObject {
    
    private final Response response;
    
    public SPResponse(Response response) {
        super(response);
        this.response = response;
    }
}
