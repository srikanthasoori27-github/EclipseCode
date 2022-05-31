/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.trigger;

import sailpoint.api.CertificationTriggerHandler;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.web.messages.MessageKeys;


/**
 * Bean for listing identity triggers (non-certification event).
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class IdentityTriggersListBean extends BaseIdentityTriggersListBean {

    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseIdentityTriggersListBean methods
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Override
    protected void addHandlerFilter(QueryOptions qo) {
        // Return everything but certification triggers.
        qo.add(Filter.ne("handler", CertificationTriggerHandler.class.getName()));
    }

    @Override
    protected String getDeletedMessageKey() {
        return MessageKeys.IDENTITY_TRIGGER_DELETED;
    }
}
