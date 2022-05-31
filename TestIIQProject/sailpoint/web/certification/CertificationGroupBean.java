/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.certification;

import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.data.general.DefaultPieDataset;

import sailpoint.api.TaskManager;
import sailpoint.api.certification.CertificationStatCounter;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.UIConfig;
import sailpoint.task.ActivateCertificationGroupTask;
import sailpoint.task.CancelCertificationGroupTask;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.LazyLoad.ILazyLoader;
import sailpoint.tools.Message;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
public class CertificationGroupBean extends BaseObjectBean<CertificationGroup> implements NavigationHistory.Page {

    private static Log log = LogFactory.getLog(CertificationGroupBean.class);

    private Certification.Type certificationType;
    private Certification.CertificationStatistics statistics;
    private String selectedAccessReview;
    private LazyLoad<String> entityType;
    private LazyLoad<String> exclusionsId;

    public CertificationGroupBean() {
        super();
        setScope(CertificationGroup.class);
        setStoredOnSession(false);

        if (getRequestParam().containsKey("certGroupId")) {
            setObjectId(getRequestParameter("certGroupId"));
            //IIQETN-5068 :- Setting "certGroupId" to session scope
            getSessionScope().put("certGroupId", getObjectId());
        } else if (getSessionScope().containsKey("certGroupId")){
            String id = (String)getSessionScope().get("certGroupId");
            setObjectId(id);
        }

        try {
            init();
        } catch (GeneralException e) {
            log.error(e);
        }
        
        entityType = new LazyLoad<String>(new ILazyLoader<String>() {

            public String load() throws GeneralException {

                return calculateEntityType();
            }
        });
        exclusionsId = new LazyLoad<String>(new ILazyLoader<String>() {

            public String load() throws GeneralException {

                return calculateExclusionsId();
            }
        });

    }

    public String getId(){
        return this.getObjectId();
    }

    public void setId(String id){
        setObjectId(id);
    }

    /**
     * This is a string value that is understood.
     * by the Js backing code ExclusionsGrid.js.
     * "Identity", "AccountGroup", "DataOwner" etc.
     * 
     * This code has been moved from Js to Java because we were
     * having a lot of conditionals there.
     */
    public String getEntityType() throws GeneralException {
        
        return entityType.getValue();
    }
    
    private String calculateEntityType() {
        
        String type = "Role";
        if (isAccountGroupCertification()) {
            type = "AccountGroup";
        } else if (isIdentityCertification()) {
            type = "Identity";
        } else if (isDataOwnerCertification()) {
            type = "DataOwner";
        }
        return type;
    }
    
    /**
     * This is a string value that is understood.
     * by the Js backing code ExclusionsGrid.js.
     * "identityExclusions", "roleExclusions" etc.
     * See the js code for more details.
     * 
     * This code has been moved from Js to Java because we were
     * having a lot of conditionals there.
     */
    public String getExclusionsId()  throws GeneralException {
        
        return exclusionsId.getValue();
    }
    
    private String calculateExclusionsId() {
        
        String id = "roleExclusions";
        if (isAccountGroupCertification()) {
            id = "accountGroupExclusions";
        } else if (isIdentityCertification()) {
            id = "identityExclusions";
        } else if (isDataOwnerCertification()) {
            id = "dataOwnerExclusions";
        }
        return id;
    }
    
    public String getName() throws GeneralException {
        CertificationGroup grp = this.getObject();
        if (grp != null)
            return grp.getName();
        return null;
    }

    public String getOwnerDisplayName()throws GeneralException{
        CertificationGroup grp = this.getObject();
        if (grp != null && grp.getOwner() != null){
            return grp.getOwner().getDisplayableName();    
        }
        return null;
    }

    public Date getCreated() throws GeneralException{
        CertificationGroup grp = this.getObject();
        if (grp != null && grp.getOwner() != null){
            return grp.getCreated();    
        }
        return null;
    }

    public Integer getCompletedCertifications() throws GeneralException {
        if (this.getObject() != null){
            return this.getObject().getCompletedCertifications();
        }
        return new Integer(0);
    }


    public Integer getTotalCertifications() throws GeneralException{
        if (this.getObject() != null){
            return this.getObject().getTotalCertifications();
        }
        return new Integer(0);
    }

    public Integer getCertificationPercentComplete() throws GeneralException{
        int completed = getCompletedCertifications();
        int total =  getTotalCertifications();
        return total > 0 ? CertificationStatCounter.calculatePercentComplete(completed, total) : 0;
    }

    public Integer getExcludedEntities() throws GeneralException{
        if (this.getObject() != null){
            return statistics.getExcludedEntities();
        }
        return new Integer(0);
    }

    public Integer getExcludedItems() throws GeneralException{
        if (this.getObject() != null){
            return statistics.getExcludedItems();
        }
        return new Integer(0);
    }

    public String getEntitiesDescription(){
        String key = MessageKeys.CERT_GRP_ENTITY_IDENTITIES_COMPLETED;
        if (isAccountGroupCertification()){
            key = MessageKeys.CERT_GRP_ENTITY_ACCT_GRP_COMPLETED;
        } else if (isRoleCompositionCertification()){
            key = MessageKeys.CERT_GRP_ENTITY_ROLES_COMPLETED;
        } else if (isDataOwnerCertification()){
            key = MessageKeys.CERT_GRP_ENTITY_ENTITLEMENTS_COMPLETED;
        }
        return Internationalizer.getMessage(key, getLocale());
    }

    public boolean isIdentityCertification(){
        return certificationType != null && certificationType.isIdentity();
    }

    public boolean isAccountGroupCertification(){
        return certificationType != null &&
                (certificationType.equals(Certification.Type.AccountGroupMembership) ||
                      certificationType.equals(Certification.Type.AccountGroupPermissions));
    }

    public boolean isRoleCompositionCertification(){
        return certificationType != null &&
                (certificationType.equals(Certification.Type.BusinessRoleComposition));
    }

    public boolean isRoleMembershipCertification(){
        return certificationType != null &&
                (certificationType.equals(Certification.Type.BusinessRoleMembership));
    }

    public boolean isAccountGroupMembershipCertification(){
        return certificationType != null &&
                (certificationType.equals(Certification.Type.AccountGroupMembership));
    }

    public boolean isAccountGroupPermissionCertification(){
        return certificationType != null &&
                (certificationType.equals(Certification.Type.AccountGroupPermissions));      
    }

    public boolean isDataOwnerCertification(){
        return certificationType != null &&
                (certificationType.equals(Certification.Type.DataOwner));
    }


    public Certification.CertificationStatistics getStatistics(){
        return statistics;
    }

    public String getSelectedAccessReview() {
        return selectedAccessReview;
    }

    public void setSelectedAccessReview(String selectedAccessReview) {
        this.selectedAccessReview = selectedAccessReview;
    }

    public List<Message> getResultMessages() throws GeneralException{
        if (getObject() != null)
            return getObject().getMessages();
        return null;
    }
    
    public boolean isComplete() throws GeneralException {
    	return getObject() != null && CertificationGroup.Status.Complete.equals(getObject().getStatus());
    }

    public boolean isPending() throws GeneralException{
        return getObject() != null && CertificationGroup.Status.Pending.equals(getObject().getStatus());
    }

    public boolean isHasAccounts() throws GeneralException{
        return getObject() != null && statistics.getTotalAccounts() > 0;
    }
    
    public boolean isStaged() throws GeneralException {
        return getObject() != null && CertificationGroup.Status.Staged.equals(getObject().getStatus());
    }
    
    /**
     * Cancels the certification group.
     * This can only be called for staged certification groups.
     */
    public String cancel()
        throws GeneralException
    {
        if (!isStaged()) {
            throw new GeneralException("Unable to cancel a non-staged certification");
        }
        
        Attributes<String, Object> args = new Attributes<String, Object>();
        args.put(CancelCertificationGroupTask.ARG_CERTIFICATION_GROUP_ID, getId());
        
        TaskManager taskManager = new TaskManager(getContext());
        taskManager.run("Cancel Certification Group", args);
        
        addMessageToSession(new Message(Message.Type.Info, MessageKeys.CERT_GRP_MSG_CANCEL_STARTED, getObject().getName()));
        
        return "viewCertificationGroupList";
    }
    
    /**
     * Makes the certification group active.
     * This can only be called for staged certification groups.
     */
    public String activate()
        throws GeneralException
    {
        if (!isStaged()) {
            throw new GeneralException("Unable to activate a non-staged certification");
        }
        
        Attributes<String, Object> args = new Attributes<String, Object>();
        args.put(ActivateCertificationGroupTask.ARG_CERTIFICATION_GROUP_ID, getId());
        
        TaskManager taskManager = new TaskManager(getContext());
        taskManager.run("Activate Certification Group", args);
        
        addMessageToSession(new Message(Message.Type.Info, MessageKeys.CERT_GRP_MSG_ACTIVATE_STARTED, getObject().getName()));
        
        return "viewCertificationGroupList";
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Decision Stat Charts
    //
    //////////////////////////////////////////////////////////////////////

    public DefaultPieDataset getViolationsDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenViolations());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_ALLOWED,  getLocale()),
                statistics.getViolationsAllowed());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getViolationsRemediated());
        if( statistics.getViolationsAcknowledged() > 0)
            pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_ACKNOWLEDGED,  getLocale()),
                    statistics.getViolationsAcknowledged());

        return pieDataset;
    }

    public DefaultPieDataset getAccountsDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenAccounts());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getAccountsApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getAccountsRemediated());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_ALLOWED,  getLocale()),
                statistics.getAccountsAllowed());

        return pieDataset;
    }


    public DefaultPieDataset getRolesDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenRoles());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getRolesApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getRolesRemediated());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_ALLOWED, getLocale()),
                statistics.getRolesAllowed());

        return pieDataset;
    }

    public DefaultPieDataset getExceptionsDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenExceptions());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getExceptionsApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getExceptionsRemediated());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_ALLOWED,  getLocale()),
                statistics.getExceptionsAllowed());

        return pieDataset;
    }

    public DefaultPieDataset getAccountGroupMembershipDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenAccountGroupMemberships());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getAccountGroupMembershipsApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getAccountGroupMembershipsRemediated());

        return pieDataset;
    }

    public DefaultPieDataset getAccountGroupPermissionsDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenAccountGroupPermissions());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getAccountGroupPermissionsApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getAccountGroupPermissionsRemediated());

        return pieDataset;
    }

    public DefaultPieDataset getRoleHierarchiesDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenRoleHierarchies());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getRoleHierarchiesApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getRoleHierarchiesRemediated());

        return pieDataset;
    }

    public DefaultPieDataset getPermitsDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenPermits());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getPermitsApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getPermitsRemediated());

        return pieDataset;
    }

    public DefaultPieDataset getRequirementsDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenRequirements());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getRequirementsApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getRequirementsRemediated());

        return pieDataset;
    }

    public DefaultPieDataset getScopesDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenScopes());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getScopesApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getScopesRemediated());

        return pieDataset;
    }

    public DefaultPieDataset getCapabilitiesDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenCapabilities());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getCapabilitiesApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getCapabilitiesRemediated());

        return pieDataset;
    }

    public DefaultPieDataset getProfilesDataSource() throws GeneralException
    {
        DefaultPieDataset pieDataset = new DefaultPieDataset();

        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_OPEN,  getLocale()),
                statistics.getOpenProfiles());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_APPROVED,  getLocale()),
                statistics.getProfilesApproved());
        pieDataset.setValue(Internationalizer.getMessage(MessageKeys.CERT_GRP_CHART_REMEDIATED,  getLocale()),
                statistics.getProfilesRemediated());

        return pieDataset;
    }

    public String getExclusionsGridConfig(){
        if (this.isIdentityCertification()){
            return UIConfig.IDENTITY_EXCLUSIONS_COLUMNS;
        } else if (this.isAccountGroupMembershipCertification()){
            return UIConfig.ACCOUNT_GRP_MEMBERSHIP_EXCLUSIONS_COLUMNS;
        } else if (this.isAccountGroupPermissionCertification()){
            return UIConfig.ACCOUNT_GRP_PERMISSIONS_EXCLUSIONS_COLUMNS;
        } else if (this.isDataOwnerCertification()) {
            return UIConfig.DATA_OWNER_EXCLUSIONS_COLUMNS;
        }
        else {
            return UIConfig.ROLE_EXCLUSIONS_COLUMNS;
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    //////////////////////////////////////////////////////////////////////

    public String goBack(){
        return NavigationHistory.getInstance().back();
    }

    public String viewAccessReview() throws GeneralException{
       CertificationBean.viewCertification(FacesContext.getCurrentInstance(), selectedAccessReview);
       NavigationHistory.getInstance().saveHistory(this);
       CertificationPreferencesBean certPrefBean  = new CertificationPreferencesBean(selectedAccessReview);
       return certPrefBean.getDefaultView();
    }

    public String editSchedule() {
        NavigationHistory.getInstance().saveHistory(this);
        return "editCertificationSchedule?certGroup=" + getId() + "&forceLoad=true";
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Private methods
    //
    //////////////////////////////////////////////////////////////////////

    private void init() throws GeneralException{

        if (this.getObject() != null){

            // Get the certification type. In this context, we know that
            // all certifications in the group will be of the same type.
            // That could change in the future...
            QueryOptions typeOps = new QueryOptions();
            typeOps.add(Filter.eq("certificationGroups.id", getObjectId()));
            typeOps.setDistinct(true);
            Iterator<Object[]> typeIter = getContext().search(Certification.class, typeOps, Arrays.asList("type"));
            if (typeIter != null && typeIter.hasNext()){
                certificationType = (Certification.Type)typeIter.next()[0];
            }

            statistics = this.getObject().calculateStatistics(getContext());
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory methods
    //
    //////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Certification Group Detail";
    }

    public String getNavigationString() {
        return "viewCertificationGroup?id=" + this.getId();
    }

    public Object calculatePageState() {
        return null;
    }

    public void restorePageState(Object state) {
    }
}