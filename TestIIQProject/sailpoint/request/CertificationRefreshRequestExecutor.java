/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.request;

import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractChangeEvent;
import sailpoint.object.Attributes;
import sailpoint.object.Request;


/**
 * This request executor simply calls on of the event handling methods on
 * CertificationRefresher.  The purpose is to allow this processing to be
 * backgrounded when an identity is changed interactively (through the UI
 * for example).  To create a request to be executed by this executor, you
 * just need call setExecuteInBackground(true) on the CertificationRefresher
 * before calling one of the event handling methods.
 *
 * @deprecated Certification refresh is not support. Continuous certifications are not supported
 * and certifications are no longer reactive to changes on inactive status of identities
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Deprecated
public class CertificationRefreshRequestExecutor
    extends AbstractRequestExecutor {

    /**
     * The name of the RequestDefinition object in the repository.
     */
    public static final String DEF_NAME = "Certification Refresh Request";
    
    /**
     * The argument on the request that hold the AbstractChangeEvent.
     */
    public static final String ARG_EVENT = "event";
    
    
    /**
     * Default constructor.
     */
    public CertificationRefreshRequestExecutor() {
        super();
    }

    /* (non-Javadoc)
     * @see sailpoint.object.RequestExecutor#execute(sailpoint.api.SailPointContext, sailpoint.object.Request, sailpoint.object.Attributes)
     */
    public void execute(SailPointContext context, Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        AbstractChangeEvent evt = (AbstractChangeEvent) args.get(ARG_EVENT);
        if (null == evt) {
            throw new RequestPermanentException("Change event was not found in the request.");
        }

        throw new RequestPermanentException("Certification refresh is deprecated!");

    }
}
