/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONWriter;
import org.json.JSONException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.Writer;
import java.io.StringWriter;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class UncorrelatedLinksListBean extends BaseListBean<Link>{

    private static final Log log = LogFactory.getLog(UncorrelatedLinksListBean.class);


    public UncorrelatedLinksListBean() {

        super();
        setScope(Link.class);

    }

   /* private void getLinkAttributes(){

        try {
            ObjectConfig linkConfig = getLinkConfig();
            List<ObjectAttribute> attributes = linkConfig.getExtendedAttributeList();
            int x = 1;
        } catch (GeneralException e) {
            // todo
            e.printStackTrace();
        }

    } */


    public List<String> getProjectionColumns() throws GeneralException {
        return Arrays.asList("id", "nativeIdentity", "application.name");
    }


    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.getQueryOptions();
        return ops;
    }


    public Map<String, String> getSortColumnMap() throws GeneralException {
        Map<String, String> mapping = new HashMap<String, String>();
        for(String col : getProjectionColumns()){
            mapping.put(col.replace(".","_"), col);
        }
        return mapping;    //To change body of overridden methods use File | Settings | File Templates.
    }
    

    public String getUncorrelatedLinks(){

        String nativeIdentityQuery = this.getRequestParameter("nativeIdentity");
        String applicationId = this.getRequestParameter("applicationId");
        
        Iterator<Object[]> iter = null;
        int totalRows = 0;

        try {
            QueryOptions ops = getQueryOptions();

            if (nativeIdentityQuery != null && nativeIdentityQuery.trim().length() > 0){
                ops.add(Filter.like("nativeIdentity", nativeIdentityQuery, Filter.MatchMode.START));    
            }

            if (applicationId != null && applicationId.trim().length() > 0){
                ops.add(Filter.eq("application.id", applicationId));
            }

            iter = getContext().search(Link.class, ops, getProjectionColumns());

            ops.setResultLimit(0);
            ops.setFirstRow(0);
            totalRows = getContext().countObjects(Link.class, ops);

        } catch (GeneralException e) {
            log.error(e);
            return "error " + e.getMessage();
        }



        Writer jsonString = new StringWriter();
        JSONWriter writer = new JSONWriter(jsonString);

        try {

            writer.object();
            writer.key("objects");
            writer.array();
            while (iter.hasNext()) {
                writer.object();
                Object[] columns =  iter.next();
                writer.key("id");
                writer.value(columns[0] != null ? columns[0] : "");
                writer.key("nativeIdentity");
                writer.value(columns[1] != null ? columns[1] : "");
                writer.key("application_name");
                writer.value(columns[2] != null ? columns[2] : "");
                //writer.key("lastLogin");
                //writer.value("");
                writer.key("accountType");
                writer.value("");
                //writer.key("links");
                //writer.value("5");
                writer.endObject();
            } 
            writer.endArray();

            writer.key("count");
            writer.value(totalRows);

            writer.endObject();
        } catch (JSONException e) {
            log.error(e);
        }

        return jsonString.toString();


    }
}
