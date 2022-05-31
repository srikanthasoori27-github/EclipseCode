/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.CertificationActionDescriber;
import sailpoint.api.certification.CertificationDelegationDescriber;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.workitem.WorkItemNavigationUtil;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationEntityViewBean extends BaseCertificationViewBean {
    private static Log log = LogFactory.getLog(CertificationEntityViewBean.class);

    private String entityId;
    private CertificationEntity.Type type;
    private String entityName;
    private int entityIndex;
    private int entityCount;
    private String nextEntity;
    private String prevEntity;
    private CertificationDelegationDescriber delegationDescriber;
    private ViewMode mode;
    private String modeString;
    private CertificationDelegation delegation;
    private boolean isComplete;
    private String defaultDelegationDescription;
    private String defaultRemediationDescription;
    boolean workItemEditable;
    private String custom1;
    private String custom2;
    private Map<String, Object> customMap;
    private boolean hasInstances;
    private boolean showApp;

    private CertificationItem certificationItem;

    private static final String SESSION_ENTITY_LIST = "entityList";

    public enum ViewMode{
        // When user has come to the detail from the entity list view
        EntityList,
        // When user has come to the detail from the worksheet view
        Worksheet,
        // When the view preference for this cert (or the identity) is to default to the detail page
        Detail,
        // When viewing a delegated entity from the workitem page
        WorkItem
    }

    public CertificationEntityViewBean() {
        super();

        if (super.getRequestOrSessionParameter("entityId") != null )
            entityId = super.getRequestOrSessionParameter("entityId");

        if (getRequestParameter("m") != null){
            modeString = getRequestParameter("m");
            evalMode();
        }

        if (getRequestParameter("certificationId") != null){
            setCertificationId(getRequestParameter("certificationId"));
            mode = ViewMode.Detail;
        }

        try{
            // If mode is null, they click the back button, in which case we dont want
            // to init the bean
            if (mode != null) {
                init();
            }
        } catch(GeneralException e){
            throw new RuntimeException("Could not initialize CertificationEntityViewBean", e);
        }
        
    }

    public CertificationEntityViewBean(String entityId, String workItemId, boolean workItemEditable) {
        super();

        this.entityId = entityId;
        this.setWorkItemId(workItemId);
        this.workItemEditable = workItemEditable;
        mode = ViewMode.WorkItem;


        try{
            init();
        } catch(GeneralException e){
            throw new RuntimeException("Could not initialize CertificationEntityViewBean");
        }
    }

    public CertificationEntityViewBean(CertificationItem item, String workItemId, boolean workItemEditable) {
        super();

        this.certificationItem = item;

        this.entityId = item.getParent().getId();
        this.setWorkItemId(workItemId);
        this.workItemEditable = workItemEditable;
        mode = ViewMode.WorkItem;

        try{
            init();
        } catch(GeneralException e){
            throw new RuntimeException("Could not initialize CertificationEntityViewBean");
        }
    }

    /**
     * Determines if there are any open items. This is used to determine whether or not
     * to redirect users to the detail view when they have selected that as their default
     * view. If they have selected that option, we will only direct them to the detail view
     * if there is at least one entity with open items.
     */
    public static boolean hasOpenItems(SailPointContext context, String certId) throws GeneralException{
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("certification.id", certId));
        ops.add(Filter.eq("summaryStatus", AbstractCertificationItem.Status.Open));
        ops.setResultLimit(1);

        return context.countObjects(CertificationEntity.class, ops) > 0;
    }

    private void init() throws GeneralException{

        if (getCertificationId() == null){
            Iterator<Object[]> iter = getContext().search(CertificationEntity.class,
                    new QueryOptions(Filter.eq("id", entityId)), Arrays.asList("certification.id"));
            if (!iter.hasNext())
                throw new RuntimeException("Could not find certification for entity where id=" + entityId);
            setCertificationId((String)iter.next()[0]);
        }

        super.initCertification();

        // Calculate paging. If we're arriving here because the user has
        // their preference set to detail view, this will figure out the current
        // entity id by picking the first open entity
        calculatePaging();

        CertificationEntity entity = getContext().getObjectById(CertificationEntity.class, entityId);
        type = entity.getType();
        entityName = entity.calculateDisplayName(getContext(), getLocale());
        isComplete = entity.isComplete();
        delegation = entity.getDelegation();

        custom1 = entity.getCustom1();
        custom2 = entity.getCustom2();
        customMap = entity.getCustomMap();

        delegationDescriber = new CertificationDelegationDescriber(getContext(), entity, getLocale(), getUserTimeZone());

        CertificationActionDescriber describer =
                new CertificationActionDescriber(CertificationAction.Status.Delegated, getContext());
        this.defaultDelegationDescription = describer.getDefaultDelegationDescription(entity);
        this.defaultRemediationDescription = describer.getDefaultRemediationDescription(null, entity);
        
        QueryOptions instanceOps = new QueryOptions(Filter.eq("parent.id", entityId));
        instanceOps.add(Filter.notnull("exceptionEntitlements.instance"));
        hasInstances = getContext().countObjects(CertificationItem.class, instanceOps) > 0;

        // Dont show the app column for app owner certs
        showApp = !Certification.Type.ApplicationOwner.equals(getDefinition().getType());
        checkLocked();
    }
    
    public boolean getHasInstances() {
    	return hasInstances;
    }
    
    public boolean getShowApp() {
    	return showApp;
    }

    public CertificationDelegationDescriber getDelegationDescriber() {
        return delegationDescriber;
    }

    public void setDelegationDescriber(
            CertificationDelegationDescriber delegationDescriber) {
        this.delegationDescriber = delegationDescriber;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public ViewMode getMode() {
        return mode;
    }

    public void setSrc(ViewMode mode) {
        this.mode = mode;
    }

    public CertificationEntity.Type getType() {
        return type;
    }

    public void setType(CertificationEntity.Type type) {
        this.type = type;
    }


    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getNextEntity() {
        return nextEntity;
    }

    public void setNextEntity(String nextEntity) {
        this.nextEntity = nextEntity;
    }

    public String getPrevEntity() {
        return prevEntity;
    }

    public void setPrevEntity(String prevEntity) {
        this.prevEntity = prevEntity;
    }

    public int getEntityIndex() {
        return entityIndex;
    }

    public void setEntityIndex(int entityIndex) {
        this.entityIndex = entityIndex;
    }

    public int getEntityCount(){
        return entityCount;
    }

    public String getDefaultDelegationDescription() {
        return defaultDelegationDescription;
    }

    public String getDefaultRemediationDescription() {
        return defaultRemediationDescription;
    }

    public String getNextButtonText() throws GeneralException{
        return this.getMessage(new Message(MessageKeys.NAV_NEXT_ENTITY, getEntityTypeName()));
    }

    public String getPrevButtonText() throws GeneralException{
        return this.getMessage(new Message(MessageKeys.NAV_PREV_ENTITY, getEntityTypeName()));
    }

    public boolean isAllowCertificationEntityBulkApprove()  throws GeneralException {
        return getDefinition().isAllowEntityBulkApprove(getContext());
    }

    public boolean isAllowCertificationEntityBulkRevocation()  throws GeneralException {
        return getDefinition().isAllowEntityBulkRevocation(getContext());
    }
    
    public boolean isAllowCertificationEntityBulkAccountRevocation()  throws GeneralException {
        boolean isAllowedByType = (getCertType() != null && getCertType().isIdentity()) &&
                !Certification.Type.BusinessRoleMembership.equals(getCertType());
        return isAllowedByType && getDefinition().isAllowAccountRevocation(getContext()) &&
                getDefinition().isAllowEntityBulkAccountRevocation(getContext());
    }

    public boolean isEntityDelegationEnabled()  throws GeneralException {
        return getWorkItemId() == null && getDefinition().isAllowEntityDelegation(getContext());
    }

    public boolean isDelegationForwardingDisabled()  throws GeneralException {
        return getDefinition().isDelegationForwardingDisabled();
    }

    public boolean isBulkUndoEnabled()  throws GeneralException {
        return getDefinition().isAllowEntityBulkClearDecisions(getContext());
    }

    public boolean isDelegation(){
        return getWorkItemId() != null;
    }

    public boolean isItemDelegation(){
        return isDelegation() && (null != this.certificationItem);
    }

    public boolean isBulkActionsEditable() throws GeneralException{
        boolean isEntityDelegated = this.delegation != null && this.delegation.isActive();
        boolean isDelegationCertifier = false;
        boolean baseRequirement = !isSignedOff();
        if(isEntityDelegated || this.isDelegation()) {
            isDelegationCertifier = CertificationAuthorizer.isCertifier(getLoggedInUser(), Util.stringToList(getCurrentDelegationOwner()));
        } else {
            //Not delegated in any way, base requirement met?
            return baseRequirement;
        }
        
        //delegated in some way, all requirements met?
        return baseRequirement && ((isEntityDelegated && isDelegationCertifier) || (this.isDelegation() && this.isEditable()));
    }

    /**
     * Returns true if roles may be created from the certification
     * items in the current certification. This excludes non-identity
     * certifications. Business role membership certifications are
     * also excluded because while they deal with identities they
     * do not include additional entitlements.
     *
     * @return True if roles may be created from certification items.
     * @throws GeneralException
     */
    public boolean isAllowCreatingRolesFromCertification() throws GeneralException{
        Certification.Type type = getCertType();
        return type != null && type.isIdentity() && !isBusinessRoleMembershipCertification()
                &&!isDataOwnerCertification();
    }

    @Override
    protected GridState loadGridState() {
        String name = "certificationGrid-";
        if(gridState==null) {
            IdentityService iSvc = new IdentityService(getContext());
            try {
                if(this.getType()!=null) {
                    name = name+this.getType().name().toLowerCase();
                    gridState = iSvc.getGridState(getLoggedInUser(), name);
                }
            } catch(GeneralException ge) {
                log.info("GeneralException encountered while loading gridstates: "+ge.getMessage());
            }
        }
        
        if(gridState==null) {
            gridState = new GridState();
            gridState.setName(name);
        }
        return gridState;
    }

    private String getEntityTypeName() throws GeneralException {

        String key;

        if (isAccountGroupCertification()) {
            key = MessageKeys.ACCOUNT_GROUP;
        } else if (isBusinessRoleCertification()) {
            key = MessageKeys.BIZ_ROLE;
        } else if (isDataOwnerCertification()) {
            key = MessageKeys.CERT_ITEM_TBL_HEADER_DATA_ITEM;
        } else {
            key = MessageKeys.IDENTITY;
        }

        return getMessage(key);
    }

    public String getCertificationItemId(){
        return certificationItem!=null ? certificationItem.getId() : "";
    }

    public String getCertificationItemType(){
        return certificationItem!=null ? certificationItem.getType().name() : "";
    }

    @Override
    public boolean isEditable() throws GeneralException {
        return this.workItemEditable || super.isEditable();
    }

    public int getActiveDelegations() throws GeneralException{

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent.id", getEntityId()));
        ops.add(getItemActiveDelegationFilter());

        ops.setDistinct(true);

        return getContext().countObjects(CertificationItem.class, ops);
    }
    
    private Filter getItemActiveDelegationFilter() {
        
        Filter filter = Filter.and(Filter.notnull("delegation"), (Filter.isnull("delegation.completionState")));

        // If we're in a delegation workitem, we need to limit the count
        // to line-item delegations included in the workitem.
        if (getWorkItemId() != null){
            Filter.and(filter, Filter.eq("parent.delegation.workItem", getWorkItemId()));
        }
        return filter;
    }
    
    public int getSavedDecisionCount() throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent.id", getEntityId()));
        ops.add(Filter.or(Filter.notnull("action"), getItemActiveDelegationFilter()));
        ops.setDistinct(true);

        return getContext().countObjects(CertificationItem.class, ops);
    }

    public String getCurrentDelegationOwner(){
        return delegation != null && delegation.isActive() ? delegation.getOwnerName() : "";
    }

    public String getCustom1() {
        return custom1;
    }

    public String getCustom2() {
        return custom2;
    }

    public String getCustomMapJson() {
        if (customMap != null){
             return JsonHelper.toJson(customMap);
        }

        return "null";
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Paging stuff
    //
    ////////////////////////////////////////////////////////////////////////////

    private void calculatePaging() throws GeneralException{


        Class scope = !ViewMode.Worksheet.equals(mode) ? CertificationEntity.class : CertificationItem.class;
        String idField = !ViewMode.Worksheet.equals(mode) ? "id" : "parent.id";

        Iterator<Object[]> entities = getContext().search(scope, getEntityQuery(),
                Arrays.asList(idField));
        List<String> entityList = new ArrayList<String>();
        String lastEntity = null;
        while (entities != null && entities.hasNext()){
            // Iterate through the entities looking for the current entity.
            // If we've already found the next entity, we can ignore all this
            // and continue to get the total count
            String currentEntity = (String)entities.next()[0];
            if (nextEntity == null){
                // if entityId is null we just want to select the first entity
                // in the list
                if (entityId== null || currentEntity.equals(entityId)){
                    this.entityIndex = entityCount;
                    this.prevEntity = lastEntity;
                    if (entityId == null)
                        entityId = currentEntity;
                }

                if (this.entityId.equals(lastEntity)){
                    this.nextEntity = currentEntity;
                }
                lastEntity = currentEntity;
            }

            entityList.add(currentEntity);

            // keep a total so we can get the total count
            this.entityCount++;
        }

        getSessionScope().put("certificationEntityPaging", entityList);
    }

    private QueryOptions getEntityQuery() throws GeneralException{

        QueryOptions ops = new QueryOptions();
        if (!ViewMode.Worksheet.equals(mode))
            ops.add(Filter.eq("certification.id", getCertificationId()));
        else
            ops.add(Filter.eq("parent.certification.id", getCertificationId()));
        ops.setScopeResults(false);

        String sessionFilterKey = null;
        CertificationFilterContext certFilterCtx = null;
        switch (mode){
            case Worksheet:
                sessionFilterKey = CertificationItemsListBean.FILTER_SESSION_ATTRIBUTE;
                certFilterCtx = new CertificationItemsListBean();
                break;
            case EntityList:
                sessionFilterKey = CertificationEntityListBean.FILTER_SESSION_ATTRIBUTE;
                certFilterCtx = new CertificationEntityListBean();
        }

        CertificationFilterBean sessionFilter = null;
        if (sessionFilterKey != null){
            sessionFilter = (CertificationFilterBean) super.getSessionScope().get(sessionFilterKey);
            sessionFilter.attachContext(certFilterCtx, this);
            if (null != sessionFilter && null != sessionFilter.getFilter())
                ops.add(sessionFilter.getFilter());
        } else {
            ops.add(Filter.eq("summaryStatus", AbstractCertificationItem.Status.Open));
            ops.addOrdering("identity", true);
        }

        if (ops.getOrderings() == null || ops.getOrderings().isEmpty()){
            switch (mode){
                case Worksheet:
                    ops.addOrdering("parent.identity", true);
                    break;
                case EntityList:
                    boolean foundSort = false;
                    String sortField = this.getRequestParameter("s");
                    String sortOrder = this.getRequestParameter("so");
                    if (!Util.isNullOrEmpty(sortField)){
                        List<ColumnConfig> columns = CertificationEntityListBean.calculateColumnConfig(getCertType());
                        if (columns != null){
                           for(ColumnConfig col : columns){
                               if (sortField.equals(col.getJsonProperty()) && col.getSortProperty() != null){
                                   ops.addOrdering(col.getSortProperty(), "ASC".equals(sortOrder));
                                   if (col.getProperty() != null && col.getProperty().startsWith("Identity.")) {
                                       // Bug 10583 -- Need to join the Identity table when sorting on an Identity attribute
                                       ops.addFilter(Filter.join("identity", "Identity.name"));
                                   }
                                   foundSort = true;
                                   break;
                               }
                           }
                        }
                    }

                    if (!foundSort)
                        ops.addOrdering(getDefaultEntitySort(), true);
                    break;
            }
        }

        ops.setDistinct(true);

        return ops;
    }

    private String getDefaultEntitySort() throws GeneralException{

        switch (getCertType()){
            case BusinessRoleComposition:
                return "targetName";
            case AccountGroupPermissions: case AccountGroupMembership:
                return "accountGroup";
            default:
                return "identity";
        }
    }


    public String getModeString() {
        if(modeString==null && mode!=null) {
            modeString = mode.toString();
        }
        return modeString;
    }

    public void setModeString(String modeString) {
        this.modeString = modeString;
    }

    public ViewMode evalMode(){

        if (mode != null)
            return mode;

        if (modeString != null)
            mode = ViewMode.valueOf(modeString);

        return mode;
    }

    private void checkLocked() throws GeneralException {
        
        if (getCertificationId() != null && Certification.isLockedAndActionable(getContext(), getCertificationId())) {
            addErrorMessage("", Message.warn(MessageKeys.CERT_LOCKED_WARN), null);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Action Methods
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Action to go back to the previous page in the navigation history.
     */
    public String back() throws GeneralException {
        return NavigationHistory.getInstance().back();
    }
    public String viewWorkItem() throws GeneralException {
        NavigationHistory.getInstance().saveHistory(this);
        WorkItemNavigationUtil navigationUtil = new WorkItemNavigationUtil(getContext());
        return navigationUtil.navigate(getWorkItemId(), true /* check archive */, super.getSessionScope());
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Page History Methods
    //
    ////////////////////////////////////////////////////////////////////////////


    public String getPageName() {
        return "CertificationDetail";
    }

    public String getNavigationString() {
        return "viewCertDetail?m="+this.getModeString()+"&entityId=" + getEntityId();
    }

    public Object calculatePageState() {
        Object[] state = (Object[])super.calculatePageState();
        state[0] = getEntityId();
        state[1] = this.getModeString();
        return state;
    }

    public void restorePageState(Object state) {
        if (null == getEntityId()) {
            Object[] myState = (Object[]) state;
            setEntityId(((String) myState[0]));
            if (myState[1] != null)
                this.mode = ViewMode.valueOf((String)myState[1]);
        }
    }
}
