/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.faces.model.SelectItemGroup;
import javax.faces.validator.ValidatorException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationService;
import sailpoint.api.Explanator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AbstractCertificationItem.ContinuousState;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Type;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PropertyInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.search.ExternalAttributeFilterBuilder;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.SelectItemByLabelComparator;

/**
 * A filter on a single property in a certification.
 * 
 * @author peter.holcomb, Kelly Grizzle
 */
public class CertificationFilter {
    
    
    private static final Log log = LogFactory.getLog(CertificationFilter.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private static long filterCounter = 0;

    private String id;
    private String propertyName;
    private String value;
    private String value2;
    private String logicalOp;
    private Boolean booleanValue;
    private Date dateValue;

    private String certificationId;
    private CertificationFilterContext filterContext;
    private BaseBean baseBean;
    private Map<String,ObjectAttribute> searchAttributes;

    private String _lastSelectionValue;

    private String _lastSelectionValue2;

    private String _lastSelectionPropety;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     * 
     * @param  certificationId  The ID of the certification.
     * @param  prop             The default property name.
     * @param  baseBean         The BaseBean that provide some services.
     */
    public CertificationFilter(String certificationId, String prop,
                               CertificationFilterContext ctx,
                               BaseBean baseBean)
    {
        this.id = Long.toString(filterCounter++);
        this.certificationId = certificationId;

        // The first value in the "additional filter" is automatically
        // selected so we'll show the appropriate text, boolean, or select
        // list field.
        this.propertyName = prop;

        attachContext(ctx, baseBean);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    void attachContext(CertificationFilterContext ctx, BaseBean baseBean) {
        this.filterContext = ctx;
        this.baseBean = baseBean;
    }

    public String getId()
    {
        return id;
    }

    public String getPropertyName()
    {
        return propertyName;
    }

    public void setPropertyName(String propertyName)
    {
        this.propertyName = propertyName;
    }

    public String getValue()
    {        
        return value;
    }

    public void setValue(String value)
    {
        this.value = value;
    }

    public String getValue2() {
        return value2;
    }

    public void setValue2(String value2) {
        this.value2 = value2;
    }

    public Boolean getBooleanValue()
    {
        return booleanValue;
    }

    public void setBooleanValue(Boolean booleanValue)
    {
        this.booleanValue = booleanValue;
    }

    /**
     * @return the logicalOp
     */
    public String getLogicalOp() {
        return logicalOp;
    }

    /**
     * @param logicalOp the logicalOp to set
     */
    public void setLogicalOp(String logicalOp) {
        this.logicalOp = logicalOp;
    }

    public Date getDateValue() {
        return ( this.dateValue == null ) ? new Date() : this.dateValue;
    }

    public void setDateValue(Date date) {
        this.dateValue = date;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    public boolean isBooleanFilter() {
        // No longer have any boolean filters - now support yes/no.
        return false;
    }

    public boolean isTextFilter() {
        return false;
    }
    
    public boolean isRiskFilter() {
        return (CertificationFilterBean.RISK_SCORE_FILTER.equals(this.propertyName));
    }

    public boolean isIdentityFilter() {
        return (!isTextFilter() && !isRiskFilter() && propertyName!=null &&
                propertyName.startsWith(CertificationFilterBean.IDENTITY_PREFIX));
    }
    
    public boolean isBRFilter() {
        return propertyName!=null && propertyName.equals(CertificationFilterBean.BUSINESS_ROLE_FILTER);
    }
    
    public boolean isLastNameFilter() {
        boolean checkLastNameFilter = propertyName!= null && propertyName.equals(CertificationFilterBean.LAST_NAME_FILTER);
        return checkLastNameFilter;
    }
    
    public boolean isFirstNameFilter() {
        boolean checkLastNameFilter = propertyName!= null && propertyName.equals(CertificationFilterBean.FIRST_NAME_FILTER);
        return checkLastNameFilter;
    }

    public boolean isItemTypeFilter() {
        return propertyName!=null && propertyName.equals(CertificationFilterBean.ITEM_TYPE_FILTER);
    }

    public boolean isRoleTypeFilter() {
        return propertyName!=null && propertyName.equals(CertificationFilterBean.ROLE_TYPE_FILTER);
    }

    public boolean isYesNoFilter() {
        return propertyName!=null && (propertyName.equals(CertificationFilterBean.HAS_ADDITIONAL_ENTITLEMENTS_FILTER) ||
               propertyName.equals(CertificationFilterBean.HAS_POLICY_VIOLATIONS_FILTER) || 
               isItemBooleanFilter());
    }

    /**
     * Return whether selecting the first level value is available.
     */
    public boolean isLevel1SelectEnabled() throws GeneralException {
        return isLevelSelectEnabled(Certification.EntitlementGranularity.Attribute);
    }

    /**
     * Return whether selecting the second level value is available.
     */
    public boolean isLevel2SelectEnabled() throws GeneralException {
        return isLevelSelectEnabled(Certification.EntitlementGranularity.Value) &&
        (null != Util.getString(this.value) && null == getAdditionalEntitlementPermTarget());
    }

    private boolean isLevelSelectEnabled(Certification.EntitlementGranularity gran)
    throws GeneralException {
        return null != this.propertyName &&
            this.propertyName.startsWith(CertificationFilterBean.ADDITIONAL_ENTITLEMENT_APP_PREFIX) &&
            (gran.compareTo(getCertification().getEntitlementGranularity()) <= 0);
    }

    private String getAdditionalEntitlementApp() {
        return stripPrefix(this.propertyName, CertificationFilterBean.ADDITIONAL_ENTITLEMENT_APP_PREFIX);
    }

    private String getAdditionalEntitlementAttrName() {
        return stripPrefix(this.value, CertificationFilterBean.ADDITIONAL_ENTITLEMENT_ATTR_PREFIX);
    }

    private String getAdditionalEntitlementPermTarget() {
        return stripPrefix(this.value, CertificationFilterBean.ADDITIONAL_ENTITLEMENT_PERM_PREFIX);
    }

    private String stripPrefix(String value, String prefix) {

        if ((null != value) && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return null;
    }

    public List<SelectItem> getLevel1SelectItems() throws GeneralException {
        CertificationService svc = new CertificationService(getContext());
        Certification cert = getCertification();

        String app = getAdditionalEntitlementApp();

        List<String> attrs =
            svc.getAdditionalEntitlementAttributes(cert, app);
        List<String> targets =
            svc.getAdditionalEntitlementPermissionTargets(cert, app);
        
        // WRT Bug 14038, we want to make sure our current value is in our list of available values,
        // even if the certification doesn't have an item with that value.  To do that, we'll have
        // to check if the property name is an entitlement or a permission
        String type = getSearchAttrType();
        if (type != null) {
            if (type.equals(CertificationFilterBean.ADDITIONAL_ENTITLEMENT_ATTR_PREFIX)) {
                validateCurrentSelection(_lastSelectionValue, attrs);
            } else if (type.equals(CertificationFilterBean.ADDITIONAL_ENTITLEMENT_PERM_PREFIX)) {
                validateCurrentSelection(_lastSelectionValue, targets);
            }
        }

        List<SelectItem> attrItems = new ArrayList<SelectItem>();
        for (String attr : attrs) {
            attrItems.add(new SelectItem(CertificationFilterBean.ADDITIONAL_ENTITLEMENT_ATTR_PREFIX + attr, attr));
        }

        List<SelectItem> permItems = new ArrayList<SelectItem>();
        for (String target : targets) {
            permItems.add(new SelectItem(CertificationFilterBean.ADDITIONAL_ENTITLEMENT_PERM_PREFIX + target, target));
        }

        SelectItemGroup attrGroup = new SelectItemGroup(baseBean.getMessage(MessageKeys.ATTRIBUTES));
        attrGroup.setSelectItems(attrItems.toArray(new SelectItem[] {}));

        SelectItemGroup permGroup = new SelectItemGroup(baseBean.getMessage(MessageKeys.PERMISSIONS));
        permGroup.setSelectItems(permItems.toArray(new SelectItem[] {}));

        List<SelectItem> items = new ArrayList<SelectItem>();
        items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_ENTITLEMENT)));
        items.add(attrGroup);
        items.add(permGroup);

        return items;
    }

    public List<SelectItem> getLevel2SelectItems() throws GeneralException {
        CertificationService svc = new CertificationService(getContext());
        Certification cert = getCertification();

        String app = getAdditionalEntitlementApp();
        String attr = getAdditionalEntitlementAttrName();
        String perm = getAdditionalEntitlementPermTarget();

        List<String> values = null;

        if (null != perm) {
            values = svc.getAdditionalEntitlementPermissionRights(cert, app, perm);
        }
        else {
            values = svc.getAdditionalEntitlementAttributeValues(cert, app, attr);
        }

        validateCurrentSelection(_lastSelectionValue2, values);
        
        List<SelectItem> items = new ArrayList<SelectItem>();
        items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_VALUE)));
        ManagedAttribute manAttr = null;
        
        List<String> appIds = ObjectUtil.getObjectIds(getContext(), Application.class, new QueryOptions(Filter.eq("name", app)));
        String appId = app;
        if (appIds != null && appIds.size()>0) {
            appId = appIds.get(0);
        }
        
        for (String value : values) {
            // Use displayable name
            String displayVal = Explanator.getDisplayValue(appId, attr, value);
            items.add(new SelectItem(value, displayVal));
        }

        return items;
    }
    
    /*
     * Inspects the current list of values and ensures the value we already have selected
     * is one of them.  The assumption is that the filter was built from a UI that drives
     * the available values ensuring an invalid value couldn't have ever been provided.
     * Thus, it's required that 'isTextFilter' is false 
     */
    private void validateCurrentSelection(String lastSelection, List<String> values) throws GeneralException {
        if (!isTextFilter()) {
            // if our validateValue is null or the values list is null or empty, skip all this
            if (lastSelection != null && !"".equals(lastSelection.trim())
                    && values != null && !values.contains(lastSelection)
                    && propertyName != null && propertyName.equals(_lastSelectionPropety)) {
                // check that our last selection is in our values list. If not, add it
                    values.add(lastSelection);
                    Collections.sort(values);
                
            }
        }
    }
    
    /**
     * This is less a validation and more of a preservation of the selected value
     * @param facesContext
     * @param arg1
     * @param value
     * @throws ValidatorException
     */
    public void preserveSelection(FacesContext facesContext, UIComponent arg1, Object value) throws ValidatorException {
        // TK: This is being called as a jsf validation check, but it's not actually validating anything.  Rather, 
        // we're holding on to the last value selected so our pull-downs can happily coexist
        // with that value when the supporting data would have otherwise removed it (like on a reassign)
        // Wanna Know More? Bug 14038
        if (value != null) {
            _lastSelectionValue = String.valueOf(value);
            _lastSelectionPropety = propertyName;
        }
    }
    
    /**
     * This is less a validation and more of a preservation of the selected value2
     * @param facesContext
     * @param arg1
     * @param value
     * @throws ValidatorException
     */
    public void preserveSelectionValue2(FacesContext facesContext, UIComponent arg1, Object value) throws ValidatorException {

        if (value != null) {
            _lastSelectionValue2 = String.valueOf(value);
            _lastSelectionPropety = propertyName;
        }
    }
    
    public List<SelectItem> getIdentitySelectItems() throws GeneralException {

        CertificationService svc = new CertificationService(getContext());
        List<String> values = svc.getIdentityAttributes(getCertification(), propertyName);
        validateCurrentSelection(_lastSelectionValue, values);
        List<SelectItem> items = new ArrayList<SelectItem>();
        if(values==null || values.isEmpty() || values.get(0)==null) {
            items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_NO_VALUES)));
        } else {
            items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_VALUE)));
            for(String value : values) {
                items.add(new SelectItem(value, value));
            }
        }
        return items;
    }

    public List<SelectItem> getBRSelectItems() throws GeneralException {
        
        CertificationService svc = new CertificationService(getContext());
        List<String> values = svc.getBusinessRoles(getCertification());
        validateCurrentSelection(_lastSelectionValue, values);
        return populateSelectItem(values);
    }

    public List<SelectItem> getLastNameSelectItems() throws GeneralException {    	
    	CertificationService svc = new CertificationService(getContext());
        List<String> values = svc.getLastNames(getCertification());
        validateCurrentSelection(_lastSelectionValue, values);
        return populateSelectItem(values);
    }

    public List<SelectItem> getFirstNameSelectItems() throws GeneralException {
    	CertificationService svc = new CertificationService(getContext());
        List<String> values = svc.getFirstNames(getCertification());
        validateCurrentSelection(_lastSelectionValue, values);
        List<SelectItem> items = populateSelectItem(values);
        return items;
    }

    public List<SelectItem> getItemTypeSelectItems() throws GeneralException {

        List<SelectItem> items = new ArrayList<SelectItem>();

        items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_VALUE)));
        Type certType = getCertification().getType();
        CertificationItem.Type[] certItemTypesToAdd = null;
        
        if (certType != null && certType.isIdentity()) {
            certItemTypesToAdd = new CertificationItem.Type[] { 
                CertificationItem.Type.Bundle, 
                CertificationItem.Type.PolicyViolation, 
                CertificationItem.Type.Exception 
            };
        } else if (certType != null && certType == Certification.Type.BusinessRoleComposition) {
            certItemTypesToAdd = new CertificationItem.Type[] {
                CertificationItem.Type.BusinessRoleGrantedCapability,
                CertificationItem.Type.BusinessRoleGrantedScope,
                CertificationItem.Type.BusinessRoleHierarchy,
                CertificationItem.Type.BusinessRolePermit,
                CertificationItem.Type.BusinessRoleProfile,
                CertificationItem.Type.BusinessRoleRequirement
            };
        }
        
        if (certItemTypesToAdd.length > 0) {
            for (int i = 0; i < certItemTypesToAdd.length; ++i) {
                items.add(new SelectItem(certItemTypesToAdd[i].toString(), this.baseBean.getMessage(certItemTypesToAdd[i].getMessageKey())));
            }
    
            Collections.sort(items, new SelectItemByLabelComparator(baseBean.getLocale()));
        }
        
        return items;
    }

    public List<SelectItem> getRoleTypeSelectItems() throws GeneralException {

        List<SelectItem> items = new ArrayList<SelectItem>();

        items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_VALUE)));
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        List<RoleTypeDefinition> roleTypes = roleConfig.getRoleTypesList();
        
        if (roleTypes != null) {
            for (RoleTypeDefinition type : roleTypes) {
                items.add(new SelectItem(type.getName(), type.getDisplayableName()));
            }            
        }
        
        Collections.sort(items, new SelectItemByLabelComparator(baseBean.getLocale()));

        return items;
    }
    
    public List<SelectItem> getContinuousStateSelectItems() throws GeneralException {

        List<SelectItem> items = new ArrayList<SelectItem>();

        items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_VALUE)));
        for (ContinuousState state : ContinuousState.values()) {
            items.add(new SelectItem(state.toString(), state.getLocalizedMessage(this.baseBean.getLocale(), this.baseBean.getUserTimeZone())));
        }

        return items;
    }

    public List<SelectItem> getLogicalOpSelectItems() {
        List<SelectItem> items = new ArrayList<SelectItem>();
        items.add(new SelectItem(Filter.LogicalOperation.EQ.name(), Filter.LogicalOperation.EQ.getDisplayName()));
        items.add(new SelectItem(Filter.LogicalOperation.LT.name(), Filter.LogicalOperation.LT.getDisplayName()));
        items.add(new SelectItem(Filter.LogicalOperation.GT.name(), Filter.LogicalOperation.GT.getDisplayName()));
        
        return items;
    }

    /**
     * Populating for UI component (example selectOneMenu) with values retrieved from Certification Service
     * @param values retrieved values
     * @return Select Items List
     */
	private List<SelectItem> populateSelectItem(List<String> values) {
		List<SelectItem> items = new ArrayList<SelectItem>();
        if(values==null) {
            items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_NO_VALUES)));
        } else {
            items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_VALUE)));
            for(String value : values) {
                items.add(new SelectItem(value, value));
            }
        }
		return items;
	}

    ////////////////////////////////////////////////////////////////////////////
    //
    // Item Extended attribute helper methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public boolean isItemFilter() {
        return propertyName != null && propertyName.startsWith(CertificationFilterBean.ITEM_PREFIX);
    }

    public boolean isItemTextFilter() {
        if ( isItemFilter() ) {
            String type = getSearchAttrType();
            if ( ( type == null ) || 
                 ( PropertyInfo.TYPE_STRING.equals(type) ) ) {
                return true;
            }
        } 
        return false;
    }

    public boolean isItemBooleanFilter() {
        if ( isItemFilter() ) {
            String type = getSearchAttrType();
            if ( ( type != null ) &&
                 ( PropertyInfo.TYPE_BOOLEAN.equals(type) ) ) {
                return true;
            }
        } 
        return false;
    }

    public boolean isDateFilter() {
        return isItemDateFilter();
    }
    
    private boolean isItemDateFilter() {
        if ( isItemFilter() ) {
            String type = getSearchAttrType();
            if ( ( type != null ) &&
                 ( PropertyInfo.TYPE_DATE.equals(type) ) ) {
                return true;
            }
        } 
        return false;
    }

    private String stripItemPrefix() {
        String fixedPropName = this.propertyName;
        if ( this.propertyName != null ) {
            int index = this.propertyName.lastIndexOf(".");
            if ( index != -1 ) {
                fixedPropName = this.propertyName.substring(index+1);
            }
        }
        return fixedPropName;
    }

    private Map<String,ObjectAttribute> getSearchAttributes() {
        if (searchAttributes == null) {
            searchAttributes = new HashMap<String,ObjectAttribute>();
            ObjectConfig config = CertificationItem.getObjectConfig();
            if (config != null) {
                List<ObjectAttribute> custom = config.getCustomAttributes();
                if (custom != null) {
                    for (ObjectAttribute att : custom)
                        searchAttributes.put(att.getName(), att);
                }
            }
        }
        return searchAttributes;
    }

    private String getSearchAttrType() {
        String type = null;
        ObjectAttribute attr = getSearchAttributes().get(stripItemPrefix());
        if ( attr != null ) {
            type = attr.getType();                
        }
        return type;
    }

    public List<SelectItem> getItemSelectItems() throws GeneralException {

        CertificationService svc = new CertificationService(getContext());
        List<String> values = svc.getExtendedItemAttributes(getCertification(), propertyName);
        validateCurrentSelection(_lastSelectionValue, values);
        List<SelectItem> items = new ArrayList<SelectItem>();
        if(values==null || values.isEmpty() || values.get(0)==null) {
            items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_NO_VALUES)));
        } else {
            items.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_VALUE)));
            for(String value : values) {
                items.add(new SelectItem(value, value));
            }
        }
        return items;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get a filter that can either by applied to the QuerytOptions to
     * filter CertificationIdentities, or to a CertificationItemSelector to
     * filter items.
     */
    protected Filter getFilter() {

        Filter filter = null;

        String val = Util.getString(this.value);
        String addtEntApp = getAdditionalEntitlementApp();

        if (null != addtEntApp) {
            filter = Filter.eq("exceptionApplication", addtEntApp);

            String addtEntAttr = getAdditionalEntitlementAttrName();
            String addtEntTarget = getAdditionalEntitlementPermTarget();
            String val2 = Util.getString(this.value2);

            if (null != addtEntAttr) {
                filter = Filter.and(filter, Filter.eq("exceptionAttributeName", addtEntAttr));
                if (null != val2) {
                    filter = Filter.and(filter, Filter.eq("exceptionAttributeValue", val2));
                }
            }

            if (null != addtEntTarget) {
                filter = Filter.and(filter, Filter.eq("exceptionPermissionTarget", addtEntTarget));
                if (null != val2) {
                    filter = Filter.and(filter, Filter.eq("exceptionPermissionRight", val2));
                }
            }
        }
        else if (CertificationFilterBean.BUSINESS_ROLE_FILTER.equals(this.propertyName) && (null != val))
        {
            //bug 20109 - Changed the Prop to targetDisplayName to get the dispayable name
            filter = Filter.and(Filter.eq("type", CertificationItem.Type.Bundle),
                                Filter.eq("targetDisplayName", val));
        }
        else if (CertificationFilterBean.LAST_NAME_FILTER.equals(this.propertyName) && (null != val) && this.filterContext != null)
        {
        	//add parent. prefix depending on the List Bean being used for the view
            filter = Filter.eq(this.filterContext.addEntityPropertyPrefix(this.propertyName), val);
        }
        else if (CertificationFilterBean.FIRST_NAME_FILTER.equals(this.propertyName) && (null != val) && this.filterContext != null)
        {
        	//add parent. prefix depending on the List Bean being used for the view
            filter = Filter.eq(this.filterContext.addEntityPropertyPrefix(this.propertyName), val);
        }
        else if (CertificationFilterBean.HAS_ADDITIONAL_ENTITLEMENTS_FILTER.equals(this.propertyName))
        {
            filter = getBooleanFilter("type", CertificationItem.Type.Exception);
        }
        else if (CertificationFilterBean.HAS_POLICY_VIOLATIONS_FILTER.equals(this.propertyName)) 
        {
            filter = getBooleanFilter("type", CertificationItem.Type.PolicyViolation);
        }
        else if (CertificationFilterBean.ITEM_TYPE_FILTER.equals(this.propertyName) && (null != val)) {
            CertificationItem.Type type = CertificationItem.Type.valueOf(val);
            try {
                if (getCertification().getType() == Certification.Type.BusinessRoleComposition) {
                    // The notnull check is only because Filter.collectionCondition insists on a composite filter
                    filter = Filter.collectionCondition("items", Filter.and(Filter.eq("type", type), Filter.notnull("id")));
                } else {
                    filter = Filter.eq("type", type);
                }
            } catch (GeneralException e) {
                log.error("The CertificationFilterBean was unable to determine certification type", e);
            }
        } else if (CertificationFilterBean.ROLE_TYPE_FILTER.equals(this.propertyName) && (null != val)) {
            filter = Filter.and(Filter.join("targetId", "Bundle.id"), Filter.eq("Bundle.type", val));
        } else if(CertificationFilterBean.RISK_SCORE_FILTER.equals(this.propertyName) && (this.filterContext != null) &&
                (null != val)) {
            try {      
            	//add parent. prefix depending on the List Bean being used for the view
                filter = new LeafFilter(Enum.valueOf(Filter.LogicalOperation.class, this.logicalOp),
                                        this.filterContext.addEntityPropertyPrefix(this.propertyName), Integer.parseInt(val));
                
            } catch (NumberFormatException nfe) {
                filter = null;
            }
        }
        else if ( isItemFilter() ) {
            String stripedName = stripItemPrefix();
            ObjectAttribute idAttr = getSearchAttributes().get(stripedName);
            if ( idAttr != null ) {
                if ( isItemBooleanFilter() ) {
                    filter = getBooleanFilter(stripedName, "true");
                } else 
                if ( isDateFilter() ) {
                    filter = getDateFilter(stripedName);
                } else if (null != val) {
                    filter = Filter.eq(stripedName, val);
                }
            }
        }
        else if (null != val) {
            String identityProp =
                CertificationService.getIdentityPropertyIfMulti(this.propertyName);

            // Multi-valued properties need a special query.
            if (null != identityProp) {
                List<String> vals = new ArrayList<String>();
                vals.add(val);
                filter =
                    ExternalAttributeFilterBuilder.buildAndFilter(
                          ExternalAttributeFilterBuilder.IDENTITY_EXTERNAL,
                          "Identity.id", identityProp, vals, "EQ");
            }
            else {
                filter = Filter.ignoreCase(Filter.eq(this.propertyName, val));
            }
        }

        return filter;
    }

    private Filter getBooleanFilter(String propertyName, Object value) {

        Filter filter = null;

        if (this.booleanValue != null) {
            if ( this.booleanValue == false ) {
                filter = Filter.ne(propertyName, value);
            } else {
                filter = Filter.eq(propertyName, value);
            }
        }

        return filter;
    }

    private Filter getDateFilter(String propertyName) {

        Filter.LogicalOperation op = Filter.LogicalOperation.EQ;
        if (null != this.logicalOp) {
            op = Enum.valueOf(Filter.LogicalOperation.class, this.logicalOp);
        }
        Date dateValue = getDateValue();
        return new LeafFilter(op, propertyName, dateValue);
    }
    
    /**
     * Return whether this filter is a collection condition on the items
     * collection.
     * 
     * @return True if this filter is a collection condition on the items
     *         collection.
     */
    boolean isItemsCollectionCondition() {
        
        // Don't need a collection condition over the items entity's items
        // collection if we're querying over items.
        if (this.filterContext.isDisplayingItems()) {
            return false;
        }

        String addtEntApp = getAdditionalEntitlementApp();
        return ((null != addtEntApp) ||
                CertificationFilterBean.BUSINESS_ROLE_FILTER.equals(this.propertyName) ||
                CertificationFilterBean.HAS_ADDITIONAL_ENTITLEMENTS_FILTER.equals(this.propertyName) ||
                isItemFilter() ||
                CertificationFilterBean.HAS_POLICY_VIOLATIONS_FILTER.equals(this.propertyName) ||
                CertificationFilterBean.ITEM_TYPE_FILTER.equals(this.propertyName));
    }

    /**
     * Return whether this filter should allow line-item selection (ie - be a
     * part of a CertificationItemSelector).  False if we're viewing items
     * because the list will already be filtered to items that match.
     * 
     * @return True if this filter should allow line-item selection.
     */
    boolean isSelectable() {

        // Selectability is already at a line item level when displaying items.
        if (this.filterContext.isDisplayingItems()) {
            return false;
        }

        // Otherwise, we allow filtering by additional entitlements and business
        // roles to select individual items.  We're also allowing selection if
        // there is an item filter, so we can do something like remediate all
        // "contractor" accounts.
        String addtEntApp = getAdditionalEntitlementApp();
        return ((null != addtEntApp) ||
                CertificationFilterBean.BUSINESS_ROLE_FILTER.equals(this.propertyName) ||
                isItemFilter());
    }

    /**
     * Return whether this filter will need to join to the identity table.
     */
    boolean joinToIdentity() {
        return (null != this.propertyName) &&
        this.propertyName.startsWith("Identity.") &&
        (null != Util.getString(this.value));
    }

    /**
     * Clear the values selected for this filter.
     */
    void clearValues() {
        this.booleanValue = null;
        this.value = null;
        this.value2 = null;
    }

    private SailPointContext getContext() throws GeneralException {
        // Ideally, we would get the context off of the baseBean, but there is
        // race condition that may cause a closed context to be returned.  See
        // bug 7553.
        return SailPointFactory.getCurrentContext();
    }

    private Certification getCertification() throws GeneralException {
        return getContext().getObjectById(Certification.class, this.certificationId);
    }

}
