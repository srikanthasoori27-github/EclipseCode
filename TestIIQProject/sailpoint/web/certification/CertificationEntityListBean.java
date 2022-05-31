/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.certification.DataOwnerCertifiableEntity;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.object.RoleSnapshot;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;


/**
 * JSF UI bean that can list certification identities for a given certification.
 * The filter state is stored on the session so that it is maintained between
 * requests.
 *
 * The following parameters are expected on the request:
 * <ul>
 *   <li>certificationId - The ID of the certification for which the identities are
 *       being listed.</li>
 *   <li>currentIdentityId - The ID of the certification identity that is
 *       currently being edited.</li>
 * </ul>
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationEntityListBean
    extends AbstractCertificationContentsListBean<CertificationEntity>
{
    private static final Log log = LogFactory.getLog(CertificationEntityListBean.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    public static final String FILTER_SESSION_ATTRIBUTE = "certEntityListFilter";
    public static final String IDENTITY_GRID_STATE = "certEntityListGridState";
    public static final String DATA_OWNER_GRID_STATE = "certDataOwnerGridState";
    public static final String BUSINESS_ROLE_COMPOSITION_GRID_STATE = "certBusinessRoleCompositionListGridState";
    public static final String BUSINESS_ROLE_MEMBERSHIP_GRID_STATE = "certBusinessRoleMembershipListGridState";
    public static final String ACCOUNT_GROUP_GRID_STATE = "certAccountGroupListGridState";

    private String currentIdentityId;
    private List<String> extraDisplayableColumns;

    private static final String COL_ACCT_GRP_DESC =  "IIQ_accountGroupDesc";
    private static final String COL_DATA_OWNER_ENTITY = "identity";
    private static final String COL_DATA_OWNER_ENTITY_DESC = "IIQ_dataOwnerEntityDesc";
    private static final String COL_ROLE_TYPE=  "IIQ_roleType";

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public CertificationEntityListBean()
    {
        super();
        super.setScope(CertificationEntity.class);
        this.currentIdentityId = super.getRequestParameter("currentIdentityId");
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getCurrentIdentityId()
    {
        return currentIdentityId;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // OVERRIDDEN METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    @SuppressWarnings("unchecked")
	public QueryOptions getQueryOptions() throws GeneralException{
		QueryOptions qo = super.getQueryOptions();
		/** Add a default sort by identity id to make the sorting of values
			that are the same or empty consistent 
			Need to ensure that the ordering doesn't already exist for MSSQL**/
		boolean containsOrdering = false;
		if(qo.getOrderings()!=null) {
			for(Ordering ordering : qo.getOrderings()) {
				if(ordering.getColumn().equals("identity"))
					containsOrdering = true;
			}
		}
		if(!containsOrdering)
			qo.addOrdering(qo.getOrderings().size(), "identity", true);
        /** Setting this to distinct as some filter options from the ui cover the certification
         * items and causes the query to return the same certification entity for every
         * certification item that applies to the filter.
         */
		qo.setDistinct(true);
		return qo;
	}
    
    /**
     * Clear the filter from the session.
     *
     * @param  context  The FacesContext to use.
     */
    static void resetFilter(FacesContext context) {
        context.getExternalContext().getSessionMap().remove(FILTER_SESSION_ATTRIBUTE);
    }

    public CertificationFilterBean getFilter() {
        validateFilter(getFacesContext(), getCertificationId());
        return super.getFilter();
    }

    static void validateFilter(FacesContext context, String certificationId) {

         CertificationFilterBean filter =
                 (CertificationFilterBean)context.getExternalContext().getSessionMap().get(FILTER_SESSION_ATTRIBUTE);

         if (filter != null && certificationId != null && !certificationId.equals(filter.getCertifictionId())){
            resetFilter(context);
         }
    }    

    String getFilterSessionAttribute() {
        return FILTER_SESSION_ATTRIBUTE;
    }

    void loadColumnConfig() throws GeneralException {

        Certification cert = getCertification();
        if (cert != null)
            columns = calculateColumnConfig(cert.getType());
    }

    protected static List<ColumnConfig> calculateColumnConfig(Certification.Type certType) throws GeneralException {
        UIConfig uiConf = UIConfig.getUIConfig();

        if (certType != null){
            switch(certType){
                case AccountGroupMembership: case AccountGroupPermissions:
                    return uiConf.getCertificationAccountGroupTableColumns();
                case DataOwner:
                    return uiConf.getCertificationEntityDataOwnerTableColumns();
                // jfb - we don't use the role membership columns apparently?
                //case BusinessRoleMembership:
                //    return continuous ? uiConf.getContinuousCertBusinessRoleMembershipTableColumns() :
                //            uiConf.getCertificationBusinessRoleMembershipTableColumns();
                case BusinessRoleComposition:
                    return uiConf.getCertificationBusinessRoleCompositionTableColumns();
                default:
                    return uiConf.getCertificationEntityTableColumns();
            }
        }

        return null;
    }

    public boolean isDisplayingItems() {
        boolean isDisplayingItems;
        try {
            // The only entity-containing cert that displays items is  Business Role Composition
            isDisplayingItems = getCertification().getType() == Certification.Type.BusinessRoleComposition;
        } catch (GeneralException e) {
            isDisplayingItems = false;
        }
        
        return isDisplayingItems;
    }

    public String getEntityPropertyPrefix() {
        return null;
    }

    public int getNonSortColumnCount() {
        return 4;
    }

    void addCustomSortColumns(int startIdx, Map<String,String> sortMap) {

        int curIdx = startIdx;
        sortMap.put("s" + curIdx, "summaryStatus");
        sortMap.put("s" + ++curIdx, "hasDifferences");
        replaceEntryWithValue(sortMap, "IIQ_changes_detected", "newUser, hasDifferences");
    }

    void addCustomSortColumnConfigs(int startIdx, Map<String,ColumnConfig> sortMap) {
    
        int curIdx = startIdx;
        sortMap.put("s" + curIdx, new ColumnConfig("summaryStatus", "summaryStatus"));
        sortMap.put("s" + ++curIdx, new ColumnConfig("hasDifferences", "hasDifferences"));

        replaceSortProperty(sortMap, "IIQ_changes_detected", "newUser, hasDifferences");
    }

    void replaceEntryWithValue(Map<String,String> map, String existingValue,
            String newValue) {

        for (Map.Entry<String,String> entry : map.entrySet()) {
            if (existingValue.equals(entry.getValue())) {
                map.put(entry.getKey(), newValue);
                break;
            }
        }
    }

    void replaceSortProperty(Map<String,ColumnConfig> map, String propertyName, String newValue) {

        for (Map.Entry<String,ColumnConfig> entry : map.entrySet()) {
            if (entry.getKey().equals(propertyName)) {
                entry.getValue().setSortProperty(newValue);
                break;
            }
        }
    }
    
    void addDefaultProjectionAttributes(List<String> projectionAttributes) {
        projectionAttributes.add("id");
        projectionAttributes.add("completed");
        projectionAttributes.add("delegation");
        projectionAttributes.add("summaryStatus");
        projectionAttributes.add("hasDifferences");
        projectionAttributes.add("actionRequired");
        projectionAttributes.add("newUser");
        projectionAttributes.add("continuousState");
        projectionAttributes.add("nextContinuousStateChange");
        projectionAttributes.add("referenceAttribute");
        projectionAttributes.add("application");
        projectionAttributes.add("nativeIdentity");
        projectionAttributes.add("type");
        projectionAttributes.add("targetId");
        projectionAttributes.add("targetName");
    }

    void addExtraDisplayableColumns(List<String> displayableColumns) {
        displayableColumns.addAll(extraDisplayableColumns);
    }

    /**
	 * Add some calculated properties to the Map that is returned.
	 */
	@Override
	public Map<String,Object> convertRow(Object[] row, List<String> cols)
	throws GeneralException {

        Map<String,Object> map = super.convertRow(row, cols);

        if (CertificationEntity.Type.AccountGroup.equals(map.get("type")))
            map.put(COL_ACCT_GRP_DESC, getAcctGrpDescription(map));

        if (CertificationEntity.Type.BusinessRole.equals(map.get("type")))
            map.put(COL_ROLE_TYPE, getRoleType((String)map.get("id")));

        if (CertificationEntity.Type.DataOwner.equals(map.get("type"))) {
            convertRowForDataOwner(map);
        }

        if((Boolean)map.get("newUser")) {
            Message msg = new Message(MessageKeys.CERT_NEW_USER);
            map.put("IIQ_changes_detected", msg.getLocalizedMessage(getLocale(),getUserTimeZone()));
        } else {
            if((Boolean)map.get("hasDifferences")) {
                Message msg = new Message(MessageKeys.YES);
                map.put("IIQ_changes_detected", msg.getLocalizedMessage(getLocale(),getUserTimeZone()));
            } else {
                Message msg = new Message(MessageKeys.NO);
                map.put("IIQ_changes_detected", msg.getLocalizedMessage(getLocale(),getUserTimeZone()));
            }
        }
        
        map.put("IIQ_hasDelegations", hasDelegation(row, cols));

        return map;
    }

    /**
     * Returns true if the given row covers a CertificationEntity with
     * a delegation of some sort.
     */
    private boolean hasDelegation(Object[] row, List<String> cols){

        int summaryStatusIdx = -1;
        for(int i=0;i<cols.size();i++){
            if ("summaryStatus".equals(cols.get(i)) ){
                summaryStatusIdx = i;
                break;
            }
        }

        if (summaryStatusIdx > -1){
            AbstractCertificationItem.Status status = (AbstractCertificationItem.Status)row[summaryStatusIdx];
            return AbstractCertificationItem.Status.Delegated.equals(status);
        } else {
            throw new RuntimeException("Could not find summary status.");
        }
    }

    /**
     * Check for an account group description for the current row.
     *
     * @param row
     * @throws GeneralException
     */
    private String getAcctGrpDescription(Map<String, Object> row) throws GeneralException{

        if(!getListType().equals(LIST_TYPE_ACCOUNT)) {
            return null;
        }

        String attributeName = (String)row.get("referenceAttribute");
        String appName = (String)row.get("application");
        String attributeValue = (String)row.get("nativeIdentity");
        
        if (attributeName == null || attributeValue == null || appName == null) {
            return null;
        }

        Application app = getContext().getObjectByName(Application.class, appName);

        if (app != null) {
            return Explanator.getDescription(app, attributeName, attributeValue, getLocale());
        }

        return null;
    }

    private void convertRowForDataOwner(Map<String, Object> map) throws GeneralException {
        String entityId = (String) map.get("id");
        CertificationEntity entity = getContext().getObjectById(CertificationEntity.class, entityId);
        DataOwnerCertifiableEntity dataOwnerCertifiableEntity = DataOwnerCertifiableEntity.createFromCertificationEntity(entity);

        map.put(COL_DATA_OWNER_ENTITY, dataOwnerCertifiableEntity.getDisplayName(getContext(), getLocale()));
        map.put(COL_DATA_OWNER_ENTITY_DESC, dataOwnerCertifiableEntity.getDisplayDescription(getContext(), getLocale()));
    }

    /**
     * Overload this in the subclass in order to provide different values
     * for each column depending on how the subclass wants to display
     */
    @Override
    public Object getColumnValue(Map<String, Object> row, String column) {
        if(column.equals("newUser")){
            return null;
        }
        if(column.equals("hasDifferences")) {
            boolean newUser = (Boolean)row.get("newUser");
            if(newUser){
                Message msg = new Message(MessageKeys.CERT_NEW_USER);
                return msg.getLocalizedMessage(getLocale(),getUserTimeZone());
            }else {
                boolean hasDifferences = (Boolean)row.get("hasDifferences");
                if(hasDifferences){
                    Message msg = new Message(MessageKeys.YES);
                    return msg.getLocalizedMessage(getLocale(),getUserTimeZone());
                }else{
                    Message msg = new Message(MessageKeys.NO);
                    return msg.getLocalizedMessage(getLocale(),getUserTimeZone());
                }
            }
        } else if (COL_ACCT_GRP_DESC.equals(column)){
            try {
                return getAcctGrpDescription(row);
            } catch (GeneralException e) {
                log.error(e);
            }
        } else if (COL_ROLE_TYPE.equals(column)){
            return getRoleType((String)row.get("targetId"));
        }
        Object value = row.get(column);
        return value;
    }
    
    public String getGridResponseJson() throws GeneralException {

        authorize(new CertificationAuthorizer(getCertification()));
        
        return super.getGridResponseJson();
    }    

    /**
     * Get the role type display name for the current entity. Note this is only
     * needed in role composition certifications.
     * @param entityId
     * @return
     */
    private String getRoleType(String entityId){
        try {

            Iterator<Object[]> results = getContext().search(CertificationEntity.class,
                    new QueryOptions(Filter.eq("id", entityId)), Arrays.asList("attributes"));
            int notnull=0;
            if(results!=null && results.hasNext()){
                Attributes attrs = (Attributes)results.next()[0];
                String type = null;
                if (attrs != null && attrs.containsKey(CertificationEntity.ATTR_SNAPSHOT)){
                   RoleSnapshot snap = (RoleSnapshot)attrs.get(CertificationEntity.ATTR_SNAPSHOT);
                   type = snap.getTypeDisplayName();
                   if (type == null)
                       type = snap.getType();
                   String msg = Internationalizer.getMessage(type, getLocale());
                   return msg != null ? msg : type;
                }
            }
        } catch (GeneralException e) {
            log.error(e);
        }

        return "";
    }
    

    /**
     * Sets up the implied filter for the detailed view automatically.
     * This shows up in the Identity View Grid Filter UI.
     * This is used in setting up and retaining the Detailed View. 
     * @see CertificationBean#setupImpliedFilterForDetailedView()
     * @throws GeneralException
     */
	public void initializeImpliedFilterForDetailedView() throws GeneralException
    {
        getFilter().setStatus(AbstractCertificationItem.Status.Open);
        //save it in the session
	    save();    		
    }
}
