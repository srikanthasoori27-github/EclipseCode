/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.sso;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.ServletException;

import sailpoint.integration.Util;
import sailpoint.web.sso.SSOAuthenticator.SSOAuthenticationInput;
import sailpoint.web.sso.SSOAuthenticator.SSOAuthenticationResult;

/**
 * This class will run a list of {@link SSOAuthenticator} objects in sequence.
 * The first one to succeed wins otherwise it will move to the next authenticator
 * in the list.
 * 
 * @author tapash.majumder
 *
 */
public class SSOAuthenticationRunner {

    private List<SSOAuthenticator> authenticators;
    
    @Inject
    public SSOAuthenticationRunner(List<SSOAuthenticator> authenticators) {
        this.authenticators = authenticators;
    }
    
    public SSOAuthenticationResult run(SSOAuthenticationInput input) throws ServletException, IOException {
        SSOAuthenticationResult result = new SSOAuthenticationResult();
        result.setSuccess(false);
        
        for (SSOAuthenticator authenticator : authenticators) {
            SSOAuthenticationResult oneResult = authenticator.authenticate(input);
            if (oneResult.isSuccess()) {
                result = oneResult;
                // Set this to the authenticator that succeeded, for auditing purposes
                result.setAuthenticator(authenticator.getClass().getSimpleName());
                break;
            }
            //If we have set some params to filter, pass these through
            if(!Util.isEmpty(oneResult.getParamsToFilter())) {
                result.setParamsToFilter(oneResult.getParamsToFilter());
            }
        }
        
        return result;
    }
}
