/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.reporting.datasource.UserForwardingDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;

public class UserForwardingReport extends UserReport {

    @Override   
    public TopLevelDataSource getDataSource()
    throws GeneralException {

        Attributes<String,Object> args = getInputs();
        SailPointContext ctx = getContext();
        List<Filter> filters = buildFilters(args);
        
        /** Need to handle extended attributes and create filters for them **/
        ObjectConfig conf = ctx.getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        List<ObjectAttribute> attributes = conf.getExtendedAttributeList();
        if(attributes!=null) {
            for(ObjectAttribute attr : attributes) {
                addLikeFilter(filters, args, attr.getName(), attr.getName(), null);
            }
        }
        return new UserForwardingDataSource(filters, getLocale(), getTimeZone(), args);
    }

   
}
