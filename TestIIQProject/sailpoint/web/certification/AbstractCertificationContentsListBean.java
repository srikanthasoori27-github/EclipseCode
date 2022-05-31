/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;

import javax.faces.el.ValueBinding;

import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItemSelector;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.AbstractCertificationItem.ContinuousState;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseListBean;
import sailpoint.web.view.certification.CertificationItemColumn;


/**
 * Base JSF UI bean that can list the contents of a certification - either 
 * entities or items.  The filter state is stored on the session so that it is
 * maintained between requests.
 * 
 * The following parameters are expected on the request:
 * <ul>
 *   <li>certificationId - The ID of the certification for which the contents
 *       are being listed.</li>
 * </ul>
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class AbstractCertificationContentsListBean<E extends SailPointObject>
    extends BaseListBean<E>
    implements CertificationFilterContext {

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private static final String SESSION_ATTR_RESET_FILTERS =
        "resetCertFiltersAfterReassignment ... Cichowski is dastardly";

    static final String LIST_TYPE_IDENTITY = "identity";
    static final String LIST_TYPE_ACCOUNT = "account";
    static final String LIST_TYPE_DATA_OWNER = "dataOwner";
    static final String LIST_TYPE_BIZ_ROLE = "businessRole";

    static final String CALCULATED_COLUMN_PREFIX = "IIQ_";

    public static final String CONTINUOUS_STATE_NAME =
        CALCULATED_COLUMN_PREFIX + "continuousStateName";

    
    private static final String EXPLICIT_FILTER_STATE = "explicitFilterState";

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private String certificationId;
    private Certification certification;

    private CertificationFilterBean filterBean;

    List<ColumnConfig> columns;
    private List<String> projectionAttributes;

    private String defaultSortColumn;
    private String defaultSortOrder;
    
    /** The UI references two different lists of certification entities.  One contains
     * a list of entities that applies to the account groups, the other to identities. This
     * flag will help us differentiate between them */
    private String listType;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor. 
     */
    public AbstractCertificationContentsListBean()
    {
        super();

        // This will come in on the request for the Live Grid AJAX request.
        this.certificationId = super.getRequestParameter("certificationId");

        // If we couldn't find the ID from the AJAX request, we're being
        // referenced from within the certification page to calculate the
        // total.  Get the certification bean from JSF to figure out the ID.
        if (null == this.certificationId) {
            CertificationBean certBean = getCertificationBean();
            if (null != certBean) {
                this.certificationId = certBean.getObjectId();
            }
        }

        restore();
    }

    /**
     * Return the CertificationBean.
     */
    private CertificationBean getCertificationBean() {
    
        ValueBinding vb =
            getFacesContext().getApplication().createValueBinding("#{certification}");
        return (CertificationBean) vb.getValue(getFacesContext());
    }

    /**
     * Save the filter in the session.
     */
    @SuppressWarnings("unchecked")
    void save()
    {
        super.getSessionScope().put(getFilterSessionAttribute(), this.filterBean);
    }

    /**
     * Restore the filter from the session.
     */
    void restore()
    {
        CertificationFilterBean sessionFilter =
            (CertificationFilterBean) super.getSessionScope().get(getFilterSessionAttribute());
        if (null != sessionFilter) {
            this.filterBean = sessionFilter;
            this.filterBean.attachContext(this, this);
        }
        else {
            this.filterBean = new CertificationFilterBean(this.certificationId, this, this);
            save();
        }
    }

    /**
     * Get the name of the attribute in which the CertificationFilterBean should
     * be stored on the session.
     * 
     * @return The name of the attribute in which the CertificationFilterBean
     *         should be stored on the session.
     */
    abstract String getFilterSessionAttribute();

    /**
     * Load the column and display column information into the
     * <code>columns</code> field.
     */
    abstract void loadColumnConfig() throws GeneralException;

    /**
     * Return the number of non-sortable columns in the table before the
     * <code>columns</code> are displayed.
     * 
     * @return The number of non-sortable columns in the table before the
     *         <code>columns</code> are displayed.
     */
    public abstract int getNonSortColumnCount();

    /**
     * Add any custom sort columns to the sortMap.
     * 
     * @param  startIdx  The next column index in the sort map.
     * @param  sortMap   The sortMap to modify.
     */
    abstract void addCustomSortColumns(int startIdx, Map<String,String> sortMap);

    /**
     * Add any custom sort columns to the sortMap.
     * 
     * @param  startIdx  The next column index in the sort map.
     * @param  sortMap   The sortMap to modify.
     */
    abstract void addCustomSortColumnConfigs(int startIdx, Map<String,ColumnConfig> sortMap);
    
    /**
     * Add any projection attributes to retrieve in the query that are not
     * included in the <code>columns</code>.  This should include any hidden
     * fields or attributes that are required to calculate other fields.
     * 
     * @param  projectionAttrs  The list of projection attributes to modify.
     */
    abstract void addDefaultProjectionAttributes(List<String> projectionAttrs);
    
    /**
     * Add any displayable columns that we may want to show in the CSV export.
     * 
     * @param  columnHeaders  The headers of the columns to add.
     */
    abstract void addExtraDisplayableColumns(List<String> columnHeaders);


    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getCertificationId()
    {
        return certificationId;
    }

    public CertificationFilterBean getFilter()
    {

        if (null == this.filterBean) {
            this.filterBean = new CertificationFilterBean(this.certificationId, this, this);
        }

        // In some cases (eg - after a reassignment), the contents of the filter
        // select boxes can change and lead to a "Validation Error: Value is not
        // valid" error.  To prevent this, we'll allow other components to
        // request the filters to be reset and clear the list of additional
        // filters.
        if (isResetFiltersFlagSet()) {
            filterBean.setFilters(new ArrayList<CertificationFilter>());
        }

        return filterBean;
    }

    /**
     * Set a flag that will reset the filters the next time they are requested.
     */
    @SuppressWarnings("unchecked")
    static void setResetFiltersFlag(BaseBean bean) {
        // This is stored on the session because the request to reset the
        // filters may come from a popup in one request, and the filters may
        // not be read again until the next request.
        bean.getSessionScope().put(SESSION_ATTR_RESET_FILTERS, true);
    }
    
    /**
     * Return whether the filters should be reset.  This removes the flag from
     * the sessions so that they are only reset once.
     */
    private boolean isResetFiltersFlagSet() {
        return (null != super.getSessionScope().remove(SESSION_ATTR_RESET_FILTERS));
    }
    
    public void setFilter(CertificationFilterBean filter)
    {
        this.filterBean = filter;
        save();
    }

    /**
     * Returns the type of entity this list displays, Identities, or AccountGroup.
     * If the property is not set, the certification is checked.
     *
     * @return the listType
     */
    public String getListType() {

        if (listType==null){
            try {
                if (getCertification() != null){
                    CertificationEntity.Type certEntityType = getCertification().getType().getEntityType();
                    
                    if (CertificationEntity.Type.AccountGroup.equals(certEntityType))
                        listType = LIST_TYPE_ACCOUNT;
                    else if (CertificationEntity.Type.BusinessRole.equals(certEntityType))
                        listType = LIST_TYPE_BIZ_ROLE;
                    else if (CertificationEntity.Type.DataOwner.equals(certEntityType))
                        listType = LIST_TYPE_DATA_OWNER;
                }
            } catch (GeneralException e) {
                throw new RuntimeException("Error retrieving certification.",e);
            }

            // if we can't find anything just punt and default to identity
            if (listType == null)
                listType = LIST_TYPE_IDENTITY;
        }

        return listType;
    }

    /**
     * Indicates what type entity this certification covers. If this property is
     * not set, the Certification will be checked for it's entity type.
     *
     * @param listType the listType to set
     */
    public void setListType(String listType) {
        this.listType = listType;
    }

    Certification getCertification() throws GeneralException {
        if (this.certification == null && certificationId != null)
            this.certification = getContext().getObjectById(Certification.class, this.certificationId);
        return certification;
    }

    boolean isContinuous() throws GeneralException {
        return (null != this.getCertification()) ?
            this.getCertification().isContinuous() : false;
    }
    
    /**
     * Get a CertificationItemSelector that can filter the items based on the
     * applied filter, or null if there is no filter on the items.
     * 
     * @return A CertificationItemSelector that can filter the items based on
     *         the applied filter, or null if there is no filter on the items.
     */
    CertificationItemSelector getCertificationItemSelector() {

        CertificationItemSelector selector = null;

        if (null != this.filterBean) {
            selector = this.filterBean.getCertificationItemSelector();
        }

        return selector;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // OVERRIDDEN METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    
    @Override
    public String getDefaultSortColumn() throws GeneralException
    {  
        // the default sort column gets overridden manually for continuous
        // certs, so only pass this upstream if there's no a value yet
        if(this.defaultSortColumn==null) 
            this.defaultSortColumn = super.getDefaultSortColumn();
        
        return this.defaultSortColumn;
    }
       
    public void setDefaultSortColumn(String column) {
        this.defaultSortColumn = column;
    }

    @Override
    public String getDefaultSortOrder() throws GeneralException
    {
        if(this.defaultSortOrder==null) {
            this.defaultSortOrder = "ASC";
        }
        return this.defaultSortOrder;
    }

    public void setDefaultSortOrder(String order) {
        this.defaultSortOrder = order;
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException
    {
        CertificationBean certBean = this.getCertificationBean();
        if (null == certBean.getObjectId()) {
            certBean.setObjectId(this.certificationId);
            certBean.setSkipAuthorization(true);
        }
        if (!certBean.isAuthorized(certBean.getObject())) {
            throw new GeneralException("Run for the hills!  This baby is coming down!");
        }
        
        QueryOptions ops = super.getQueryOptions();
        
        // We've already checked auth and can see the cert, so remove scoping
        // on the query for the items.  The logged in user should be allowed to
        // see all items in their cert.
        ops.setScopeResults(false);

        ops.add(Filter.eq(addEntityPropertyPrefix("certification"), getCertification()));

        if (null != this.filterBean)
        {
            Filter searchFilter = this.filterBean.getFilter();            
            if (null != searchFilter) {
                ops.add(searchFilter);
            }
        }

        addIdentityJoinIfRequired(ops);

        return ops;
    }

    // Only join here if this is an identity list and the projection columns
    // include an identity property.  If the filters being applied to this
    // page require joining to the Identity, they will do it.
    public void addIdentityJoinIfRequired(QueryOptions ops)
            throws GeneralException {

        if (this.certification.getType() == Certification.Type.DataOwner) {
            if (hasIdentityProjectionColumn()) {
                ops.add(Filter.join("targetId", "Identity.id"));
            }
        } else {
            if(getListType().equals(LIST_TYPE_IDENTITY) && hasIdentityProjectionColumn()) {
                ops.add(Filter.join(addEntityPropertyPrefix("identity"), "Identity.name"));
            }
        }
    }

    /**
     * Add the entity property prefix (eg - "parent.") as defined by the
     * {@link CertificationFilterContext#getEntityPropertyPrefix()} to the given
     * property if it exists.
     * 
     * @param  property  The property to which to prepend the prefix.
     * 
     * @return The given property with the prefix prepended (if it exists).
     */
    public String addEntityPropertyPrefix(String property) {

        String newProp = property;
        
        String prefix = this.getEntityPropertyPrefix();
        if (null != prefix) {
            newProp = prefix + property;
        }

        return newProp;
    }

    /**
     * Return true if any of the projection columns come from the identity.
     */
    private boolean hasIdentityProjectionColumn() throws GeneralException {

        List<String> cols = this.getProjectionColumns();
        if (null != cols) {
            for (String col : cols) {
                if (col.startsWith("Identity.")) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public Map<String, String> getSortColumnMap() throws GeneralException
    {
        Map<String,String> sortMap = new HashMap<String,String>();
        int i = this.getNonSortColumnCount();
        
        List<ColumnConfig> columns = getColumns();        
        
        if (null != columns && !columns.isEmpty()) {
            final int columnCount = columns.size();
        
            for(int j =0; j < columnCount; j++) {
            	sortMap.put(columns.get(j).getJsonProperty(), columns.get(j).getSortProperty());
            }
            
            i += columnCount;
        }

        // Allow the sub-class to add any additional sort columns.
        this.addCustomSortColumns(i, sortMap);

        return sortMap;
    }

    @Override
    public Map<String,ColumnConfig> getSortColumnConfigMap() throws GeneralException {
        
        Map<String,ColumnConfig> sortMap = super.getSortColumnConfigMap();        
        int i = this.getNonSortColumnCount() + Util.size(getColumns());

        // Allow the sub-class to add any additional sort columns.
        this.addCustomSortColumnConfigs(i, sortMap);

        return sortMap;
    }
    
    public List<ColumnConfig> getColumns() throws GeneralException {
        if(columns==null)
            loadColumnConfig();
        
        return columns;
    }
    
    public int getColumnCount() throws GeneralException {
        if(getColumns()==null) {
            return 0;
        }
        else return getColumns().size();
    }
    
    public List<String> getDisplayableColumns() throws GeneralException {
        List<ColumnConfig> cols = getColumns();
        List<String> displayableColumns = new ArrayList<String>(cols.size());
        for (ColumnConfig col : cols) {
            try {
                displayableColumns.add(super.getMessage(col.getHeaderKey()));
            }
            catch (MissingResourceException e) {
                // What if this isn't a key?  Fall back to just displaying the value.
                // TODO: log warning??
                displayableColumns.add(col.getHeaderKey());
                
            }
        }

        this.addExtraDisplayableColumns(displayableColumns);
        
        return displayableColumns;
    }
    
    /**
     * Return the list of attributes we request in the search projection.
     * Same as searchAttributes plus the hidden id.
     */
    @Override
    public List<String> getProjectionColumns() throws GeneralException {

        if (projectionAttributes == null) {
            projectionAttributes = new ArrayList<String>();
            
            List<ColumnConfig> cols = getColumns();
            if (cols != null) {
                for (ColumnConfig col : cols) {
                    if (col.getProperty()!=null && !col.getProperty().startsWith(CALCULATED_COLUMN_PREFIX)) {
                        projectionAttributes.add(col.getProperty());
                    }
                }
            }

            // Let the subclass add the columns that are static to this list
            this.addDefaultProjectionAttributes(projectionAttributes);
        }
        return projectionAttributes;
    }
    
    /** Used by a subclass to specify the name of the csv file
     * that will be created by the exportToCSV method 
     * Should be overridden by the subclass */
    public String getFilename() {
        return "certification";
    }

    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
        throws GeneralException {
        
        // super.convertRow() localizes the continuous state.  We don't want
        // this because we use the name of this in the CSS class that gets
        // applied to the "due date" column.  Save away the actual continuous
        // state before the super method tweaks it.
        ContinuousState continuousState = getContinuousState(row);
        
        Map<String,Object> map = super.convertRow(row, cols);

        map.put(CONTINUOUS_STATE_NAME,
                (null != continuousState) ? continuousState.name() : null);

        return map;
    }

    /**
     * Get the continuous state from the given row.
     */
    private ContinuousState getContinuousState(Object[] row)
        throws GeneralException {
        
        ContinuousState state = null;

        List<String> cols = getProjectionColumns();
        for (int i=0; i<cols.size(); i++) {
            String col = cols.get(i);
            if ("continuousState".equals(col)) {
                state = (ContinuousState) row[i];
                break;
            }
        }
        
        return state;
    }

    @Override
    public Object localizeValue(Object value, ColumnConfig config, String columnName) {
        // We made Certification.Type localizable, so it gets localized in convertRow by default, but we don't want that!
        // This is just for calculation in the view columns so leave it alone.
        if (CertificationItemColumn.COL_CERT_TYPE.equals(columnName)) {
            return value;
        }

        return super.localizeValue(value, config, columnName);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Action to re-execute a search.  Returns to the same page.
     */
    public String search()
    {
        setObjects(null);

        // Reset the first row since the results are going to change.
        getCertificationBean().getGridState().resetFirstRow();
        boolean impliedState = getCertificationBean().isImpliedStateDetailedViewFilterSetInSession();
        if (impliedState)
        {
        	getCertificationBean().saveInSessionStateDetailedViewFilterSet(EXPLICIT_FILTER_STATE);
        }
        return null;
    }

    /**
     * Action to reset the search filters.  Returns to the same page.
     */
    public String reset()
    {
        setObjects(null);
        boolean impliedState = getCertificationBean().isImpliedStateDetailedViewFilterSetInSession();
        if (impliedState)
        {
        	getCertificationBean().saveInSessionStateDetailedViewFilterSet(EXPLICIT_FILTER_STATE);
        }
        // Reset the first row since the results are going to change.
        getCertificationBean().getGridState().resetFirstRow();
        this.filterBean.setStatus(null);
        this.filterBean.setHasDifferences(null);
        this.filterBean.filters = new ArrayList<CertificationFilter>();
        save();
        return null;
    }
}
