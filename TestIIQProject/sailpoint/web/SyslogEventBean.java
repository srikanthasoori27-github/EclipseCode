/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import sailpoint.api.logging.SyslogAppender;
import sailpoint.object.Configuration;
import sailpoint.object.SyslogEvent;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 * This is a simple bean that does nothing more than indicate 
 * whether or not syslogging is enabled and give access to the 
 * stacktrace of a SyslogEvent object. 
 * 
 * @author derry.cannon
 *
 */
public class SyslogEventBean extends BaseEditBean<SyslogEvent> {
    
    private String NO_STACKTRACE_AVAILABLE = "syslog_no_stacktrace_available";
   
    public String getStacktrace() throws GeneralException {
        if (getObject().getStacktrace() == null)
            return (new Message(NO_STACKTRACE_AVAILABLE)).getLocalizedMessage();
        else
            return getObject().getStacktrace();
    }

    // makes hibernate happy
    public void setStacktrace(String stacktrace) {}

    public boolean isEnabled() throws GeneralException {
        Configuration config = getContext().getConfiguration();
        return config.getBoolean(SyslogAppender.ENABLE_SYSLOG);
    }

    // makes hibernate happy
    public void setEnabled(boolean isEnabled) {}

    @Override
    protected Class<SyslogEvent> getScope() {
        return SyslogEvent.class;
    }

    @Override
    public boolean isStoredOnSession() {
        return false;
    }    
}
