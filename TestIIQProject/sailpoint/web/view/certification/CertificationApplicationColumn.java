/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.view.certification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.util.*;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationApplicationColumn extends CertificationItemColumn{

    private static final Log LOG = LogFactory.getLog(CertificationApplicationColumn.class);

    private static final String BUILDER_ATTR_APP_DESCS = "applicationDescriptions";

    private static final String COL_EXCEPTION_APP = "exceptionApplication";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        cols.add(COL_EXCEPTION_APP);
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        return calculateApplicationDescription(row);
    }

    /**
     * Grabs the application definition from the application using the app name
     * @param map
     * @return
     */
    private String calculateApplicationDescription(Map<String,Object> map) {
        String description = null;
        String applicationName = (String)map.get(COL_EXCEPTION_APP);
        if(applicationName!=null) {
            Attributes<String, Object> attrs = getContext().getBuilderAttributes();
            Map<String, String> apps = new HashMap<String, String>();
            if (attrs.containsKey(BUILDER_ATTR_APP_DESCS)){
                apps = (Map<String, String>)attrs.get(BUILDER_ATTR_APP_DESCS);
            } else {
                apps = new HashMap<String, String>();
                attrs.put(BUILDER_ATTR_APP_DESCS, apps);
            }

            String desc = null;
            if (apps.containsKey(applicationName)){
                desc = apps.get(applicationName);
            } else {
                desc = lookupAppDescription(applicationName);
                apps.put(applicationName, desc);
            }
        }
        return description;
    }


    private String lookupAppDescription(String applicationName){
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("name", applicationName));
        String description = "";
        try {
            Iterator<Object[]> apps =
                    getContext().getSailPointContext().search(Application.class, qo, Arrays.asList("description"));
            if(apps.hasNext()) {
                Object[] app = apps.next();
                description = (String)app[0];
                if(description==null)
                    description = "";
            }
        } catch(GeneralException ge) {
            LOG.warn("Unable to query application for name: " + applicationName);
        }
        return description;
    }

}
