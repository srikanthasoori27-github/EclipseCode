/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.WorkItem;
import sailpoint.service.suggest.SuggestService;
import sailpoint.service.suggest.SuggestServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.Sorter;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * A bean that can provide suggestions for values of attributes that we have
 * aggregated.  This can also provide a list of possible permission rights.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class LinkAttributeValuesSuggestBean extends BaseBean implements SuggestServiceContext {

    private static final String REQ_ATTR_APP = "application";
    private static final String REQ_ATTR_ATTR_NAME = "attrName";
    private static final String REQ_ATTR_VALUE_PREFIX = "valuePrefix";
    private static final String REQ_ATTR_IS_PERMISSION = "isPermission";
    private static final String REQ_CERTIFICATION_ITEM = "certificationItem";
    protected int start;
    protected int limit;
    protected String prefix;

    /**
     * Default constructor.
     */
    public LinkAttributeValuesSuggestBean() throws GeneralException {
        super();
        authorizeCertificationItem();
    }

    /**
     * Authorize access to this bean using the certificationItem referer. This suggest is only used and allowed
     * from the classic certification UI.
     * @throws GeneralException
     */
    private void authorizeCertificationItem() throws GeneralException {
        boolean isAuthorized = false;

        String certificationItem = super.getRequestParameter(REQ_CERTIFICATION_ITEM);
        if (Util.isNothing(certificationItem)) {
            throw new GeneralException("Invalid certification item");
        }

        CertificationItem item = getContext().getObjectById(CertificationItem.class, certificationItem);
        String workItemId = null;
        if (item.isDelegated()) {
            workItemId = item.getDelegation().getWorkItem();
        } else if (item.getParent().isEntityDelegated()) {
            workItemId = item.getParent().getDelegation().getWorkItem();
        } else if (item.isChallengeActive()) {
            workItemId = item.getChallenge().getWorkItem();
        }

        if (workItemId != null) {
            WorkItem workItem = getContext().getObjectById(WorkItem.class, workItemId);
            if (workItem != null && WorkItemAuthorizer.isAuthorized(workItem, this, true)) {
                isAuthorized = true;
            }
        }

        if (!isAuthorized) {
            // TODO: This can be removed once we remove continuous certs, only work items will still have old ui
            Certification certification = item.getCertification();
            if (certification != null && CertificationAuthorizer.isAuthorized(certification, (WorkItem) null, this)) {
                isAuthorized = true;
            }
        }

        if (!isAuthorized) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_CERT_ITEM_UNAUTHORIZED_ACCESS));
        }
    }

    /**
     * Return the values starting with the given prefix.
     */
    @SuppressWarnings("unchecked")
    private ListResult getListResult(String prefix, int start, int limit)
        throws GeneralException {

        String app = super.getRequestParameter(REQ_ATTR_APP);
        String attrName = super.getRequestParameter(REQ_ATTR_ATTR_NAME);
        boolean isPermission =
            Util.otob(super.getRequestParameter(REQ_ATTR_IS_PERMISSION));

        this.start = start;
        this.limit = limit;
        this.prefix = prefix;
        SuggestService suggestService = new SuggestService(this);
        return (suggestService.getApplicationAttributeValues(app, attrName, isPermission));
    }
    
    /**
     * Return the values as JSON, pulling the Ext start, limit, and query
     * parameters.
     */
    @SuppressWarnings("unchecked")
    public String getValuesJSON() throws GeneralException {
        
        int start = 0;
        int limit = getResultLimit();
        String startStr = super.getRequestParameter("start");

        if (null != startStr) {
            start = Integer.parseInt(startStr);
        }
        String prefix = super.getRequestParameter("query");

        ListResult listResult = getListResult(prefix, start, limit);

        StringWriter jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {
            jsonWriter.object();

            jsonWriter.key("values");
            jsonWriter.array();

            if ( listResult.getObjects() != null ) {
                for (Map<String, Object> value : (List<Map<String, Object>>)listResult.getObjects()) {
                    jsonWriter.object();
                    jsonWriter.key("value");
                    jsonWriter.value(value.get("name"));
                    jsonWriter.endObject();
                }
            }

            jsonWriter.endArray();

            jsonWriter.key("numValues");
            jsonWriter.value(listResult.getCount());
            
            jsonWriter.endObject();
        }
        catch (JSONException e) {
            throw new GeneralException(e);
        }

        return jsonString.toString();
    }

    @Override
    public int getStart() {
        return this.start;
    }

    @Override
    public int getLimit() {
        return this.limit;
    }

    @Override
    public String getQuery() {
        return this.prefix;
    }

    @Override
    public List<Sorter> getSorters(List<ColumnConfig> columnConfigs) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getSuggestClass() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getFilterString() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getGroupBy() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Filter> getFilters() {
        // TODO Auto-generated method stub
        return null;
    }
}
