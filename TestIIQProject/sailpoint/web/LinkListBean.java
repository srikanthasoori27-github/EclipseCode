/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;

import sailpoint.authorization.LCMIdentityActionAuthorizer;
import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.extjs.ExtColumn;
import sailpoint.web.extjs.ExtGridResponse;
import sailpoint.web.extjs.GenericJSONObject;
import sailpoint.web.messages.MessageKeys;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class LinkListBean extends BaseListBean<Link> {

    private static Log log = LogFactory.getLog(ManualCorrelationIdentityBean.class);


    public LinkListBean() {
        super();
        setScope(Link.class);
    }

    public Map<String, String> getSortColumnMap() throws GeneralException {
        Map<String, String> mapping = new HashMap<String, String>();
        for (String col : getProjectionColumns()) {
            mapping.put(Util.getJsonSafeKey(col), col);
        }
        return mapping;
    }

    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = new QueryOptions();

        List<Filter> filters = new ArrayList<Filter>();
        for (Object param : this.getRequestParam().keySet()) {
            if (param != null && param.toString().startsWith("q_")) {
                String p = param.toString();
                String val = this.getRequestParameter(p);

                if (val != null && val.trim().length() > 0) {
                    String propertyName = Util.getKeyFromJsonSafeKey(p.substring(2, p.length()));
                    filters.add(Filter.eq(propertyName, val));
                }
            }
        }

        if (!filters.isEmpty()) {
            qo.add(Filter.and(filters));
        }


        getSortOrdering(qo);
        return qo;
    }

    public String getObjectsJson() {

        String out = null;
        int totalCount = 0;
        List<GenericJSONObject> rows = new ArrayList<GenericJSONObject>();
        Iterator<Object[]> links = null;

        try {
            //IIQETN-4952 :- We need to protect this endpoint and make sure
            //that only the authorized persons are using it.
            isAuthorized();
            QueryOptions ops = getQueryOptions();
            ops.setResultLimit(10);
            getContext().setScopeResults(true);
            links = this.getContext().search(Link.class, getQueryOptions(), getProjectionColumns());
            totalCount = this.getCount();
        } catch (GeneralException e) {
            log.error(e);
            return JsonHelper.failure();
        }

        if (links != null) {

            while (links != null && links.hasNext()) {
                GenericJSONObject row = new GenericJSONObject();
                Object[] link = links.next();

                String displayName = link[0] != null ? (String)link[0] : null;
                if (displayName == null)
                    displayName = (String)link[3];
                                    
                row.set("displayName", displayName);
                row.set(Util.getJsonSafeKey("application.name"), link[1]);
                Date refresh = link[2] != null ? (Date) link[2] : null;
                row.set("lastRefresh", refresh != null ? Internationalizer.getLocalizedDate(refresh,
                        DateFormat.SHORT, null, getLocale(), getUserTimeZone()) : "");

                GenericJSONObject attributes = new GenericJSONObject();
                row.set("attributes", attributes);
                rows.add(row);
            }
        }

        List<ExtColumn> cols = Arrays.asList(
                new ExtColumn("displayName", getMessage(MessageKeys.LINK_ACCOUNT_ID), 120, true),
                new ExtColumn(Util.getJsonSafeKey("application.name"), getMessage(MessageKeys.LINK_APPLICATION), 200, true),
                new ExtColumn("lastRefresh", getMessage(MessageKeys.LINK_LAST_REFRESH), 110, true)
        );
        ExtGridResponse.GridMetadata meta = new ExtGridResponse.GridMetadata(cols);
        meta.setSortField(getRequestParameter("sort"));
        meta.setSortDir(getRequestParameter("dir"));
        ExtGridResponse resp = new ExtGridResponse(rows, totalCount, true, meta);

        try {
            out = resp.getJson();
        } catch (JSONException e) {
            log.error(e);
            return JsonHelper.failure();
        }

        return out;
    }

    /**
     * This method is used to verify who has access to the endpoint
     * manage/correlation/appAccounts.json
     *
     * This endpoint is only used in the component IdentityDetailPopup,
     * but that component is used in different areas such as Identity
     * correlation, work items with manual action and some areas of
     * the LCM.
     *
     * There is the need to identify who exactly is calling the method
     * getObjectsJson() to identify the correct authorization.
     */
    private void isAuthorized() throws GeneralException {
        //The action parameter is used to identify which component is
        //calling the endpoint and decide the correct authorization.
        //see bug IIQETN-4952
        final String ACTION = getRequestParameter("action");

        if (Util.nullSafeEq(ACTION, "WORK_ITEM")) {
            String workItemId = getRequestParameter("workItemId");
            if (!Util.isNullOrEmpty(workItemId)) {
                   WorkItem workItem = getContext().getObjectById(WorkItem.class, workItemId);
                   //We need to validate workItem.targetId with the parameter q_identity
                   //otherwise the LoggedInUser can have access to other identities.
                   if (workItem != null &&
                           Util.nullSafeEq(workItem.getTargetId(), getRequestParameter("q_identity-id"))) {
                       //Make sure that workItem is not null otherwise
                       //the authorization will be successful
                       authorize(new WorkItemAuthorizer(workItem));
                   } else {
                       throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
                   }
            } else {
               throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
            }
        } else if (Util.nullSafeEq(ACTION, "CORRELATION")) {
            boolean isAuthorized = isAuthorized(new RightAuthorizer(
                    SPRight.FullAccessIdentityCorrelation));
            if (!isAuthorized) {
               throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
            }
        } else if (!Util.isNullOrEmpty(ACTION)){
            authorize(new LcmActionAuthorizer(ACTION));
            authorize(new LCMIdentityActionAuthorizer(
                    getRequestParameter("q_identity-id"), new String[] { ACTION } ));
        } else {
            //if we are calling the endpoint without the action parameter
            //an exception is going to be thrown
            throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
        }
    }

    /* public String getApplicationAccounts() {

     String id = getRequestParameter("id");
     String start = getRequestParameter("start");

     GenericJSONObject obj = new GenericJSONObject();
     String out = "{}";

     try {

         QueryOptions ops = new QueryOptions(Filter.eq("identity.id", id));


         String sort = getRequestParameter("sort");
         if (sort == null || "".equals(sort))
             sort="displayName";
         else
             sort = sort.replace("-", ".");

         boolean dir = getRequestParameter("dir") != null ? "ASC".equals(getRequestParameter("dir")) : true;
         ops.addOrdering(sort, dir);
         ops.setResultLimit(20);
         ops.setFirstRow(start != null ? Integer.parseInt(start) : 0);
         ops.setScopeResults(true);


         List<Link> links = getContext().getObjects(Link.class, ops);
         if (links != null) {
             List<GenericJSONObject> rows = new ArrayList<GenericJSONObject>();
             for (Link link : links) {
                 GenericJSONObject row = new GenericJSONObject();
                 row.set("account", link.getDisplayableName());
                 row.set("application-name", link.getApplicationName());
                 Date refresh = link.getLastRefresh();
                 row.set("lastRefresh", refresh != null ? Internationalizer.getLocalizedDate(refresh,
                         DateFormat.SHORT, null, getLocale(), getUserTimeZone()) : "");

                 GenericJSONObject attributes = new GenericJSONObject();
                 if (link.getAttributes() != null) {
                     for (String key : link.getAttributes().keySet()) {
                         attributes.set(key, link.getAttributes().get(key));
                     }
                 }

                 row.set("attributes", attributes);

                 rows.add(row);
             }

             int totalCount = getContext().countObjects(Link.class, new QueryOptions(Filter.eq("identity.id", id)));

             List<ExtColumn> cols = Arrays.asList(
                     new ExtColumn("account", "Account", 120, true),
                     new ExtColumn("application-name", "Application", 200, true),
                     new ExtColumn("lastRefresh", "Last Refresh", 110, true),
                     new ExtColumn("attributes", null, 0, false)
             );
             ExtGridResponse.GridMetadata meta = new ExtGridResponse.GridMetadata(cols);
             meta.setSortField(getRequestParameter("sort"));
             meta.setSortDir(getRequestParameter("dir"));
             ExtGridResponse resp = new ExtGridResponse(rows, totalCount, true, meta);

             out = resp.getJson();
         }
     } catch (Exception e) {
         log.error(e);
     }

     return out;

 }   */

}
