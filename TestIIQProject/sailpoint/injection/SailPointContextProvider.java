package sailpoint.injection;

import javax.inject.Provider;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;

/**
 * Provides an instance of SailPointContext.
 * 
 * Note: This does not create an instance. 
 * We will need to migration SailPointFactory into Injection framework so that we can instantiate properly.
 * 
 * @author tapash.majumder
 *
 */
public class SailPointContextProvider implements Provider<SailPointContext> {

    @Override
    public SailPointContext get() {
        try {
            return SailPointFactory.getCurrentContext();
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }
}
