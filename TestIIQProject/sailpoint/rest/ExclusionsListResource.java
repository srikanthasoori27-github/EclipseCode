/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EntitlementDescriber;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.ArchivedCertificationItem;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.web.util.Sorter;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
@Path("exclusions")
public class ExclusionsListResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(ExclusionsListResource.class);

    @QueryParam("certId") protected String certificationId;
    @QueryParam("certGroupId") protected String certificationGroupId;
    @QueryParam("showDesc") protected Boolean showEntitlementDescriptions;
   
    public ExclusionsListResource() {
        super();
    }

    @Override
    protected QueryOptions getQueryOptions(String columnsKey) throws GeneralException {
        QueryOptions ops = super.getQueryOptions(columnsKey);

        if (this.query != null) {
            if (calculateIsDataOwnerFromColKey()) {
                ops.add(Filter.or(
                        Filter.ignoreCase(Filter.like("exceptionApplication", this.query, Filter.MatchMode.START)),
                        Filter.ignoreCase(Filter.like("targetName", this.query, Filter.MatchMode.START))));
            } else {
                ops.add(Filter.or(
                        Filter.ignoreCase(Filter.like("parent.identity", this.query, Filter.MatchMode.START)),
                        Filter.ignoreCase(Filter.like("parent.accountGroup", this.query, Filter.MatchMode.START)),
                        Filter.ignoreCase(Filter.like("parent.targetName", this.query, Filter.MatchMode.START))
                ));
            }
        }
               
        if (certificationGroupId != null)
            ops.add(Filter.eq("parent.certification.certificationGroups.id", certificationGroupId));
        else if (certificationId != null)
            ops.add(Filter.eq("parent.certification.id", certificationId));


        return ops;
    }

    @Override
    protected List<String> getProjectionColumns(String columnsKey) throws GeneralException {
        List<String> cols = super.getProjectionColumns(columnsKey);

        // items needed to calculate description
        if (!cols.contains("bundle"))
            cols.add("bundle");
        if (!cols.contains("violationSummary"))
            cols.add("violationSummary");
        if (!cols.contains("entitlements"))
            cols.add("entitlements");
        if (!cols.contains("type"))
            cols.add("type");
        if (!cols.contains("parent.targetName"))
            cols.add("parent.targetName");

        return cols;
    }

    @Override
    protected void calculateColumn(ColumnConfig config, Map<String,Object> rawQueryResults, Map<String, Object> map) throws GeneralException {
        if ("SPT_description".equals(config.getDataIndex())){
             map.put("SPT_description", this.calculateDescription(rawQueryResults) );
             
             // the JSON serializer will barf on this if left in the map
             map.remove("entitlements");
        }
    }

    @Override
    protected void calculateColumns(Map<String, Object> rawQueryResults, String columnsKey, Map<String, Object> map) throws GeneralException {
        super.calculateColumns(rawQueryResults, columnsKey, map);

        CertificationItem.Type type = (CertificationItem.Type)rawQueryResults.get("type");
        if (type != null){
            String msg = Internationalizer.getMessage(type.getMessageKey(), getLocale());
            if (msg != null)
                map.put("type", msg);
        }
    }

    private String calculateDescription(Map<String, Object> map) throws GeneralException {

        CertificationItem.Type type = (CertificationItem.Type)map.get("type");
        String description = null;
        switch (type) {
            case Bundle:
                description = (String)map.get("bundle");
                break;
            case AccountGroupMembership:
            case Exception:
            case DataOwner:
                List<EntitlementSnapshot> entitlements = (List<EntitlementSnapshot>)map.get("entitlements");
                if (entitlements != null && !entitlements.isEmpty()) {
                    EntitlementSnapshot snap = entitlements.get(0);
                    Message msg = showEntitlementDescriptions != null && showEntitlementDescriptions.booleanValue() ?
                            EntitlementDescriber.summarizeWithDescriptions(snap, getLocale())
                            : EntitlementDescriber.summarize(snap);
                    description = msg != null ? msg.getLocalizedMessage(getLocale(), null) : null;
                }

                break;
            case PolicyViolation:
                description = (String)map.get("violationSummary");
                break;
            default:
                description = (String)map.get("parent.targetName");
        }

        return description;
    }

    /**
     * Return a list of exclusions.
     * @return
     */
    @GET
    public ListResult list() throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));

        String colKey = getColumnKey();

        ListResult result = null;

        try {
            result = getListResult(colKey, ArchivedCertificationItem.class, getQueryOptions(colKey));
        } catch (GeneralException e) {
            log.error(e);
            result = new ListResult(Collections.EMPTY_LIST, 0);
            result.setStatus("error");
            result.addError(e.getLocalizedMessage());
        }

        return result;
    }

    @Override
    protected void handleOrdering(QueryOptions qo, String columnsKey)
            throws GeneralException{
        if ("SPT_description".equals(this.sortBy)){
            boolean ascending = Sorter.isAscending(this.sortDirection);
            qo.addOrdering("type", ascending);
            qo.addOrdering("bundle", ascending);
            qo.addOrdering("exceptionApplication", ascending);
            qo.addOrdering("exceptionAttributeName", ascending);
            qo.addOrdering("exceptionAttributeValue", ascending);
            qo.addOrdering("exceptionPermissionTarget", ascending);
            qo.addOrdering("exceptionPermissionRight", ascending);
            qo.addOrdering("violationSummary", ascending);
        } else {
            super.handleOrdering(qo, columnsKey);
        }
    }
    
    /**
     * What we get from rest request is 'colKey' from it try to 
     * determine if the request type is of 'DataOwner' type.
     * 
     */
    private boolean calculateIsDataOwnerFromColKey() {

        if (UIConfig.DATA_OWNER_EXCLUSIONS_COLUMNS.equals(colKey)) {
            return true;
        } else {
            return false;
        }
    }
}