/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.reporting.datasource.AccountGroupMembershipDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;


/**
 * This report is designed to report all of the account groups for a given
 * application and the groups members.
 * 
 * Since there isn't a good way to query for the "no member" groups
 * for now let the datasource filter out groups with members.
 * 
 */
public class AccountGroupReport extends JasperExecutor {
	
    public TopLevelDataSource getDataSource()
    	throws GeneralException {

    	Attributes<String,Object> args = getInputs();
    	List<Filter> filters = buildFilters(args);
    	return new AccountGroupMembershipDataSource(filters, getLocale(), getTimeZone(), args);
    }

    protected List<Filter> buildFilters(Attributes<String,Object> inputs) 
        throws GeneralException {    
        List<Filter> filters = new ArrayList<Filter>();
        String appName = inputs.getString("application");
        if ( appName == null ) {
            Message msg = new Message(Message.Type.Error,
                MessageKeys.REPT_APP_ACCOUNT_GRP_MEMB_ERROR_APPNAME_MISSING);
            throw new GeneralException(msg);
        }

        Application app = getContext().getObjectByName(Application.class, appName);
        if ( app == null )  {
            Message msg = new Message(Message.Type.Error,
                MessageKeys.REPT_APP_ACCOUNT_GRP_MEMB_ERROR_APPNAME_NOT_FOUND, appName);
            throw new GeneralException(msg);
        }
        filters.add(Filter.eq("application", app));
        return filters;
    }
   
}
