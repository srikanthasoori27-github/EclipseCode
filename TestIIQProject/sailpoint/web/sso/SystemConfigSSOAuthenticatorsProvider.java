package sailpoint.web.sso;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;

/**
 * This will initialize a list from system configuration property
 * 
 * @author tapash.majumder
 *
 */
public class SystemConfigSSOAuthenticatorsProvider implements Provider<List<SSOAuthenticator>> {

    private static final Log log = LogFactory.getLog(SystemConfigSSOAuthenticatorsProvider.class);

    private SailPointContext context;
    
    @Inject
    public SystemConfigSSOAuthenticatorsProvider(SailPointContext context) {
        this.context = context;
    }
    
    private List<SSOAuthenticator> authenticators = null;
    
    @Override
    public List<SSOAuthenticator> get() {
        if (authenticators == null) {
            authenticators = loadAuthenticators();
        }
        return authenticators;
    }

    private List<SSOAuthenticator> loadAuthenticators() {
        List<SSOAuthenticator> vals = new ArrayList<SSOAuthenticator>();
   
        try {
            Configuration config = context.getConfiguration();
            String authenticatorsStr = config.getString(Configuration.SSO_AUTHENTICATORS);
            if (authenticatorsStr == null) {
                vals.add(new SAMLSSOAuthenticator());
                vals.add(new DefaultSSOAuthenticator());
            } else {
                List<String> authenticatorsListStr = Util.csvToList(authenticatorsStr);
                for (String authenticatorStr : authenticatorsListStr) {
                    vals.add((SSOAuthenticator) Reflection.newInstance(authenticatorStr));
                }
            }
            
        } catch(Throwable t) {
            if (log.isWarnEnabled()) {
                log.warn("Exception initializing authenticators: "  + Util.stackToString(t));
            }
        }
        
        if (vals.size() == 0) {
            // If nothing is present initialize default.
            vals.add(new DefaultSSOAuthenticator());
        }
        
        return vals;
    }
    
}
