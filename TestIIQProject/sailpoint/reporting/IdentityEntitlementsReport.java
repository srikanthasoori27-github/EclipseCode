/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.reporting.datasource.IdentityEntitlementDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;

public class IdentityEntitlementsReport extends UserReport {

    @Override	
    public TopLevelDataSource getDataSource()
    throws GeneralException {

        Attributes<String,Object> args = getInputs();
        SailPointContext ctx = getContext();
        List<Filter> filters = buildFilters(args);
        getApplicationNames(args);
        /** Need to handle extended attributes and create filters for them **/
        ObjectConfig conf = ctx.getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        List<ObjectAttribute> attributes = conf.getExtendedAttributeList();
        if(attributes!=null) {
            for(ObjectAttribute attr : attributes) {
                addLikeFilter(filters, args, attr.getName(), attr.getName(), null);
            }
        }
        return new IdentityEntitlementDataSource(filters, getLocale(), getTimeZone(), args);
    }

    /** If the user has chosen to filter the list of identities based on applications, we will
     * also need to filter the entitlements by application, so we build a list of application
     * names and pass it down to the datasource so it can filter out the entitlements
     * @param args
     * @throws GeneralException
     */
    private void getApplicationNames(Attributes<String, Object> args) throws GeneralException{
        List<Filter> filters = new ArrayList<Filter>();
        addEQFilter(filters, args, "applications", "id", null);
        
        if(!filters.isEmpty()) {
            List<String> applications = new ArrayList<String>();
            QueryOptions qo = new QueryOptions();
            for(Filter filter : filters) {
                qo.add(filter);
            }
            List<String> props = Arrays.asList("name");
            Iterator<Object[]> rows = getContext().search(Application.class, qo, props);
            while(rows.hasNext()) {
                applications.add((String)(rows.next())[0]);
            }
            
            args.put(IdentityEntitlementDataSource.FILTERED_APPLICATIONS, applications);
        }
    }
}
