/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Certificationer;
import sailpoint.api.Localizer;
import sailpoint.api.certification.CertificationActionDescriber;
import sailpoint.api.certification.CertificationDelegationDescriber;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.integration.ListResult;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.view.ViewBuilder;
import sailpoint.web.view.ViewEvaluationContext;
import sailpoint.web.view.certification.CertificationItemColumn;
import sailpoint.web.view.certification.ContinuousStateColumn;

/**
 * @author jonathan.bryant@sailpoint.com
 */
@Path("certEntity")
public class CertificationEntityResource extends BaseListResource {

    private static final Log log = LogFactory
            .getLog(CertificationEntityResource.class);

    @QueryParam("workItemId")
    protected String workItemId;

    private CertificationDefinition definition;

    public CertificationEntityResource() {
        super();
    }

    @GET
    @Path("{entity}/violations")
    public ListResult getViolations(@PathParam("entity") String entityId)
            throws GeneralException {

        // Build the base filter, this will be used in the total count,
        // and in the paged query
        Filter filters = Filter.and(Filter.eq("parent.id", entityId),
                Filter.eq("type", CertificationItem.Type.PolicyViolation));

        return getResult(CertificationItem.Type.PolicyViolation, filters,
                entityId, UIConfig.CERT_DETAIL_VIOLATIONS);
    }

    @GET
    @Path("{entity}/roles")
    public ListResult getRoles(@PathParam("entity") String entityId)
            throws GeneralException {

        // Build the base filter, this will be used in the total count,
        // and in the paged query
        Filter filters = Filter.and(Filter.eq("parent.id", entityId),
                Filter.eq("type", CertificationItem.Type.Bundle));

        return getResult(CertificationItem.Type.Bundle, filters, entityId,
                UIConfig.CERT_DETAIL_MGR_ROLES);
    }

    @GET
    @Path("{entity}/entitlements")
    public ListResult getEntitlements(@PathParam("entity") String entityId)
            throws GeneralException {

        Filter typeFilter = Filter.or(
                Filter.eq("type", CertificationItem.Type.Exception),
                Filter.eq("type", CertificationItem.Type.Account));

        Filter filters = Filter.and(Filter.eq("parent.id", entityId),
                typeFilter);

        String grid = UIConfig.CERT_DETAIL_ENTITLEMENTS;

        // Application granularity certs need their own column config b/c they
        // have one line item
        // for multiple accounts
        CertificationDefinition def = getDefinitionByEntityId(entityId);
        if (Certification.EntitlementGranularity.Application.equals(def
                .getEntitlementGranularity())) {
            grid = UIConfig.CERT_DETAIL_ENTITLEMENTS_APP_GRANULARITY;
        }

        return getResult(CertificationItem.Type.Exception, filters, entityId,
                grid);
    }

    @GET
    @Path("{entity}/dataowner")
    public ListResult getDataOwnerEntitlements(
            @PathParam("entity") String entityId) throws GeneralException {

        Filter filters = Filter.and(Filter.eq("parent.id", entityId),
                Filter.eq("type", CertificationItem.Type.DataOwner));

        return getResult(CertificationItem.Type.Exception, filters, entityId,
                UIConfig.CERT_DETAIL_DATA_OWNER);
    }

    @GET
    @Path("{entity}/memberships")
    public ListResult getAccountGroupMemberships(
            @PathParam("entity") String entityId) throws GeneralException {

        Filter filters = Filter.and(Filter.eq("parent.id", entityId), Filter
                .eq("type", CertificationItem.Type.AccountGroupMembership));

        return getResult(CertificationItem.Type.Exception, filters, entityId,
                UIConfig.CERT_DETAIL_ACCT_GRP_MEMBERSHIPS);
    }

    @GET
    @Path("{entity}/permissions")
    public ListResult getAccountGroupPermissions(
            @PathParam("entity") String entityId) throws GeneralException {

        Filter filters = Filter.and(Filter.eq("parent.id", entityId),
                Filter.eq("type", CertificationItem.Type.Exception));

        String grid = UIConfig.CERT_DETAIL_ACCT_GRP_PERMISSIONS;

        // Application granularity certs need their own column config b/c they
        // have one line itemfor multiple accounts
        if (Certification.EntitlementGranularity.Application
                .equals(getDefinitionByEntityId(entityId)
                        .getEntitlementGranularity()))
            grid = UIConfig.CERT_DETAIL_ENTITLEMENTS_APP_GRANULARITY;

        return getResult(CertificationItem.Type.Exception, filters, entityId,
                grid);
    }

    @GET
    @Path("{entity}/scopes")
    public ListResult getScopesAndCapabilities(
            @PathParam("entity") String entityId) throws GeneralException {

        Filter typeFilter = Filter.or(Filter.eq("type",
                CertificationItem.Type.BusinessRoleGrantedScope), Filter.eq(
                        "type", CertificationItem.Type.BusinessRoleGrantedCapability));

        Filter filters = Filter.and(Filter.eq("parent.id", entityId),
                typeFilter);

        return getResult(CertificationItem.Type.Exception, filters, entityId,
                UIConfig.CERT_DETAIL_SCOPE_AND_CAPS);
    }

    @GET
    @Path("{entity}/profiles")
    public ListResult getProfiles(@PathParam("entity") String entityId)
            throws GeneralException {

        Filter filters = Filter.and(Filter.eq("parent.id", entityId),
                Filter.eq("type", CertificationItem.Type.BusinessRoleProfile));

        return getResult(CertificationItem.Type.Exception, filters, entityId,
                UIConfig.CERT_DETAIL_PROFILES);
    }

    @GET
    @Path("{entity}/relatedroles")
    public ListResult getRelatedRoles(@PathParam("entity") String entityId)
            throws GeneralException {

        Filter typeFilter = Filter.or(Filter.eq("type",
                CertificationItem.Type.BusinessRolePermit), Filter.eq("type",
                        CertificationItem.Type.BusinessRoleRequirement), Filter.eq(
                                "type", CertificationItem.Type.BusinessRoleHierarchy));

        Filter filters = Filter.and(Filter.eq("parent.id", entityId),
                typeFilter);

        return getResult(CertificationItem.Type.Exception, filters, entityId,
                UIConfig.CERT_DETAIL_RELATED_ROLES);
    }

    @GET
    @Path("{entity}/summary")
    public RequestResult getSummary(@PathParam("entity") String entityId)
            throws GeneralException {

        Map data = new HashMap();

        if (getSession() != null) {
            try {
                CertificationEntity entity = getContext().getObjectById(
                        CertificationEntity.class, entityId);
                authCertification(entity.getCertification(), workItemId);

                List<String> entityPaging = (List<String>) getSession()
                        .getAttribute("certificationEntityPaging");
                int curIndex = entityPaging.indexOf(entityId);
                data.put(
                        "nextEntity",
                        curIndex + 1 < entityPaging.size() ? entityPaging
                                .get(curIndex + 1) : "");
                data.put("prevEntity",
                        curIndex > 0 ? entityPaging.get(curIndex - 1) : "");
                data.put("index", curIndex + 1);
                data.put("id", entityId);
                data.put("name",
                        entity.calculateDisplayName(getContext(), getLocale()));

                CertificationActionDescriber describer = new CertificationActionDescriber(
                        CertificationAction.Status.Delegated, getContext());
                data.put("delegationDesc",
                        describer.getDefaultDelegationDescription(entity));
                data.put("remediationDesc", describer
                        .getDefaultRemediationDescription(null, entity));

                CertificationDelegationDescriber delegationDescriber = new CertificationDelegationDescriber(
                        getContext(), entity, getLocale(), getUserTimeZone());
                data.put("delegationStatus", delegationDescriber.getStatus());

                data.put("currentDelegationOwner",
                        entity.getDelegation() != null
                        && entity.getDelegation().isActive() ? entity
                                .getDelegation().getOwnerName() : "");

                data.put("activeDelegations", getActiveDelegations(entityId));
                data.put("savedDecisionCount", getSavedDecisionCount(entityId));

                data.put("custom1", entity.getCustom1());
                data.put("custom2", entity.getCustom2());
                data.put("customMap", entity.getCustomMap());

                boolean hasInstances = false;
                boolean showApp = true;

                // determine if we have any instances in this cert, if not we
                // can hide the column
                QueryOptions instanceOps = new QueryOptions(Filter.eq(
                        "parent.id", entityId));
                instanceOps.add(Filter
                        .notnull("exceptionEntitlements.instance"));
                hasInstances = getContext().countObjects(
                        CertificationItem.class, instanceOps) > 0;

                        // Dont show the app column for app owner certs
                        CertificationDefinition definition = getDefinitionByEntityId(entityId);
                        showApp = !Certification.Type.ApplicationOwner
                                .equals(definition.getType());

                        data.put("hasInstances", hasInstances);
                        data.put("showApp", showApp);

                        RequestResult result = new ObjectResult(data);
                        result.setStatus(RequestResult.STATUS_SUCCESS);

                        return result;
            } catch (UnauthorizedAccessException ex) {
                throw ex;
            } catch (Exception e) {
                log.error(e);
                return new RequestResult(
                        RequestResult.STATUS_FAILURE,
                        null,
                        null,
                        Arrays.asList("Error loading certification entity paging."));
            }
        } else {
            log.error("Could not find certification entity paging information on session.");
            return new RequestResult(
                    RequestResult.STATUS_FAILURE,
                    null,
                    null,
                    Arrays.asList("Could not retrieve certification entity paging data."));
        }
    }

    @POST
    @Path("{entityId}/classification")
    public RequestResult setClassificationData(
            @PathParam("entityId") String id, @FormParam("data") String data) throws GeneralException {

        CertificationEntity entity = getContext().getObjectById(
                CertificationEntity.class, id);
        Certification cert = entity.getCertification();

        if (cert != null) {
            authCertification(cert, workItemId);
        }

        Map<String, Object> customData = JsonHelper.mapFromJson(String.class, Object.class, data);

        if (customData.containsKey("custom1")) {
            Object val = customData.get("custom1");
            entity.setCustom1((String) val);
        }

        if (customData.containsKey("custom2")) {
            Object val = customData.get("custom2");
            entity.setCustom2((String) val);
        }

        if (customData.containsKey("customMap")
                && customData.get("customMap") != null) {
            Map<String, String> customMap = (Map) customData
                    .get("customMap");
            if (entity.getCustomMap() == null)
                entity.setCustomMap(new HashMap<String, Object>());
            for (Map.Entry<String, String> entry : customMap.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();

                // Turn empty strings into null
                val = Util.getString((String) val);

                entity.getCustomMap().put(key, val);
            }
        }

        getContext().saveObject(entity);
        getContext().commitTransaction();

        if (cert != null) {
            Certificationer certificationer = new Certificationer(
                    getContext());
            certificationer.refresh(cert);
        }

        return new RequestResult(RequestResult.STATUS_SUCCESS, null,
                null, null);
    }

    // -------------------------------------------------------------
    //
    // Private Methods
    //
    // -------------------------------------------------------------

    private CertificationDefinition getDefinition(String definitionId)
            throws GeneralException {

        if (definition == null) {
            definition = getContext().getObjectById(CertificationDefinition.class,
                    definitionId);
            if (definition == null) {
                // if we can't find the def, bootstrap one using the sysconf
                // defaults
                definition = new CertificationDefinition();
                definition.initialize(getContext());
            }
        }

        return definition;
    }

    private CertificationDefinition getDefinitionByEntityId(String entityId)
            throws GeneralException {

        if (definition == null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("id", entityId));

            List<String> fields = Arrays.asList("certification.id",
                    "certification.certificationDefinitionId");
            Iterator<Object[]> idSearch = getContext().search(
                    CertificationEntity.class,
                    new QueryOptions(Filter.eq("id", entityId)), fields);
            if (idSearch != null && idSearch.hasNext()) {
                Object[] results = idSearch.next();
                String certId = (String) results[0];
                String defId = (String) results[1];
                if (defId != null)
                    definition = getContext().getObjectById(
                            CertificationDefinition.class, defId);
            }

            if (definition == null) {
                // if we can't find the def, bootstrap one using the sysconf
                // defaults
                definition = new CertificationDefinition();
                definition.initialize(getContext());
            }
        }

        return definition;
    }

    private Certification getCertification(String entityId)
            throws GeneralException {
        QueryOptions ops = new QueryOptions(Filter.eq("id", entityId));
        Iterator<Object[]> result = getContext().search(
                CertificationEntity.class, ops, Arrays.asList("certification"));
        return result.hasNext() ? (Certification) result.next()[0] : null;
    }

    private ListResult getResult(CertificationItem.Type type, Filter filters,
            String entityId, String columnConfig) throws GeneralException {

        if (this.workItemId != null) {
            if (this.isEntityDelegation()) {
                filters = Filter.and(filters,
                        Filter.eq("parent.delegation.workItem", workItemId));
            } else {
                filters = Filter.and(filters,
                        Filter.eq("delegation.workItem", workItemId));
            }
        }

        Certification certification = this.getCertification(entityId);
        authCertification(certification, workItemId);

        CertificationDefinition definition = this.getDefinition(certification
                .getCertificationDefinitionId());

        // Build query with paging
        QueryOptions qo = getQueryOptions();
        qo.add(filters);
        boolean attributeOrValueGranularity = (Certification.EntitlementGranularity.Attribute
                .equals(certification.getEntitlementGranularity()) || Certification.EntitlementGranularity.Value
                .equals(certification.getEntitlementGranularity()));
        boolean valueGranularity = Certification.EntitlementGranularity.Value
                .equals(certification.getEntitlementGranularity());
        if (CertificationItem.Type.Exception.equals(type)) {
            qo.addOrdering("exceptionEntitlements.application", true);
            qo.addOrdering("exceptionEntitlements.nativeIdentity", true);

            if (attributeOrValueGranularity) {
                // Add the permission ordering first so that the attributes will
                // be listed first (permission values are null in that case)
                qo.addOrdering("exceptionPermissionTarget", true);
                if (valueGranularity) {
                    qo.addOrdering("exceptionPermissionRight", true);
                }
                qo.addOrdering("exceptionAttributeName", true);
                if (valueGranularity) {
                    qo.addOrdering("exceptionAttributeValue", true);
                }
            }
        }

        qo.addOrdering("id", true); // no ambiguity, please

        List<ColumnConfig> columns = getColumnConfig(definition, type,
                entityId, columnConfig);

        ViewEvaluationContext viewContext = new ViewEvaluationContext(this,
                columns);
        viewContext.getBuilderAttributes().put(
                CertificationItemColumn.BUILDER_ATTR_WORKITEM_ID, workItemId);

        ViewBuilder viewBuilder = new ViewBuilder(viewContext,
                CertificationItem.class, columns);

        ListResult result = viewBuilder.getResult(qo);

        /** Do special localiztion of the descriptions if this is a role type **/
        if (CertificationItem.Type.Bundle.equals(type)) {
            Localizer localizer = new Localizer(getContext());
            for (Map row : (List<Map>) (result.getObjects())) {
                Map roleDetails = (Map) row.get("IIQ_roleDetails");
                String roleId = (String) roleDetails.get("roleId");
                row.put("IIQ_role-description", localizer.getLocalizedValue(
                        roleId, Localizer.ATTR_DESCRIPTION, getLocale()));
            }
        }

        GridResponseMetaData metadata = viewBuilder.calculateGridMetaData();
        for (GridColumn col : metadata.getColumns()) {
            String localizedHeader = Internationalizer.getMessage(
                    col.getHeader(), getLocale());
            col.setHeader(localizedHeader != null ? localizedHeader : col
                    .getHeader());
        }

        result.setMetaData(metadata.asMap());
        return result;
    }

    private void authCertification(Certification certification,
            String workItemId) throws GeneralException {
        authorize(new CertificationAuthorizer(certification, workItemId));
    }

    private List<ColumnConfig> getColumnConfig(
            CertificationDefinition definition, CertificationItem.Type type,
            String entityId, String grid) throws GeneralException {

        boolean isContinuous = definition.isContinuous();
        boolean hasInstances = false;
        boolean showApp = true;
        if (CertificationItem.Type.Exception.equals(type)) {

            // determine if we have any instances in this cert, if not we can
            // hide the column
            QueryOptions instanceOps = new QueryOptions(Filter.eq("parent.id",
                    entityId));
            instanceOps.add(Filter.notnull("exceptionEntitlements.instance"));
            hasInstances = getContext().countObjects(CertificationItem.class,
                    instanceOps) > 0;

                    // Dont show the app column for app owner certs
                    showApp = !Certification.Type.ApplicationOwner.equals(definition
                            .getType());
        }

        List<ColumnConfig> columns = getColumns(grid);

        // clone the columns since otherwise our changes will be written to the
        // cache.
        List<ColumnConfig> clonedCols = new ArrayList<ColumnConfig>();
        if (columns != null) {
            for (ColumnConfig col : columns) {
                clonedCols.add((ColumnConfig) col.deepCopy(getContext()));
            }
        }

        for (ColumnConfig col : clonedCols) {
            if (!isContinuous
                    && ContinuousStateColumn.class.getName().equals(
                            col.getEvaluator())) {
                col.setFieldOnly(true);
            }
            if (!hasInstances
                    && "exceptionEntitlements.instance".equals(col
                            .getProperty())) {
                col.setFieldOnly(true);
            }
            if (!showApp
                    && "exceptionEntitlements.application".equals(col
                            .getProperty())) {
                col.setFieldOnly(true);
            }
        }
        return clonedCols;
    }

    private boolean isEntityDelegation() throws GeneralException {
        QueryOptions ops = new QueryOptions(Filter.eq("id", workItemId));
        Iterator<Object[]> result = getContext().search(WorkItem.class, ops,
                Arrays.asList("certificationEntity"));
        if (result.hasNext())
            return result.next()[0] != null;

        return false;
    }

    private int getActiveDelegations(String entityId) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent.id", entityId));
        ops.add(Filter.and(Filter.notnull("delegation"),
                (Filter.isnull("delegation.completionState"))));
        ops.setDistinct(true);

        return getContext().countObjects(CertificationItem.class, ops);
    }

    private int getSavedDecisionCount(String entityId) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent.id", entityId));
        ops.add(Filter.or(
                Filter.notnull("action"),
                Filter.and(Filter.notnull("delegation"),
                        (Filter.isnull("delegation.completionState")))));
        ops.setDistinct(true);

        return getContext().countObjects(CertificationItem.class, ops);
    }
}
