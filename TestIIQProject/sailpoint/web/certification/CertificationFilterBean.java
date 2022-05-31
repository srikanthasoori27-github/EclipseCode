/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;
import javax.faces.model.SelectItemGroup;

import sailpoint.api.CertificationService;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationItemSelector;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.search.ExtendedAttributeVisitor;
import sailpoint.search.ReflectiveMatcher;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterHelper;
import sailpoint.web.util.SelectItemByLabelComparator;


/**
 * A JSF bean for managing and retrieving the full filter to apply to the search
 * for certification entities or items.
 */
public class CertificationFilterBean {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    protected static final String BUSINESS_ROLE_FILTER =
        "businessRole";
    protected static final String HAS_ADDITIONAL_ENTITLEMENTS_FILTER =
        "hasAdditionalEntitlements";
    protected static final String HAS_POLICY_VIOLATIONS_FILTER =
        "hasPolicyViolations";
    protected static final String ITEM_TYPE_FILTER =
        "itemType";
    protected static final String ROLE_TYPE_FILTER =
        "roleType";
    protected static final String ADDITIONAL_ENTITLEMENT_APP_PREFIX =
        "addEntApp:";
    protected static final String ADDITIONAL_ENTITLEMENT_ATTR_PREFIX =
        "addEntAttr:";
    protected static final String ADDITIONAL_ENTITLEMENT_PERM_PREFIX =
        "addEntPerm:";

    protected static final String IDENTITY_PREFIX = "Identity.";
    protected static final String ITEM_PREFIX = "CertificationItem.";

    protected static final String RISK_SCORE_FILTER = "compositeScore";
   
    protected static final String LAST_NAME_FILTER =  "lastname";
    
    protected static final String FIRST_NAME_FILTER = "firstname";
    
   
    


    private AbstractCertificationItem.Status status;
    private Boolean hasDifferences;
    List<CertificationFilter> filters = new ArrayList<CertificationFilter>();
    private String selectedFilterId;

    private List<SelectItem> additionalFilterChoices;

    private String certificationId;
    private Certification certification;
    private CertificationFilterContext filterContext;
    private BaseBean baseBean;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor for JSF.
     */
    public CertificationFilterBean() {
        super();
    }

    /**
     * Constructor.
     * @param certificationId
     */
    public CertificationFilterBean(String certificationId,
                                   CertificationFilterContext ctx,
                                   BaseBean baseBean) {
        this();
        this.certificationId = certificationId;
        attachContext(ctx, baseBean);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Attach the context to this bean.  This is called when a filter is
     * restored from the session.
     */
    void attachContext(CertificationFilterContext ctx, BaseBean baseBean) {
        this.filterContext = ctx;
        this.baseBean = baseBean;

        if (null != this.filters) {
            for (CertificationFilter filter : this.filters) {
                filter.attachContext(ctx, baseBean);
            }
        }
    }

    public String getCertifictionId(){
        return this.certificationId;
    }

    public Boolean getHasDifferences()
    {
        return hasDifferences;
    }

    public void setHasDifferences(Boolean hasDifferences)
    {
        this.hasDifferences = hasDifferences;
    }

    public AbstractCertificationItem.Status getStatus()
    {
        return status;
    }

    public void setStatus(AbstractCertificationItem.Status status)
    {
        this.status = status;
    }

    public List<CertificationFilter> getFilters()
    {
        return filters;
    }

    public String getSelectedFilterId()
    {
        return selectedFilterId;
    }

    public void setSelectedFilterId(String filterToRemoveId)
    {
        this.selectedFilterId = filterToRemoveId;
    }

    public List<SelectItem> getStatusChoices()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem("", baseBean.getMessage(MessageKeys.SELECT_STATUS)));
        try {
	        for (AbstractCertificationItem.Status status : AbstractCertificationItem.Status.values())
	        {
	            if (getCertification().getAllowedStatuses().contains(status))
	                list.add(new SelectItem(status, baseBean.getMessage(status.getMessageKey())));
	        }
        } catch(Exception e) {
        	
        }
        return list;
    }

    /**
     * Generates select item list with the filter groups appropriate for the given
     * certification type, whether the user is viewing the worksheet or identity view
     * and the entitlements included in the certification.
     *
     *
     * @return List of filters for given certification
     * @throws GeneralException
     */
    public List<SelectItem> getAdditionalFilterChoices() throws GeneralException
    {
    	if(getCertification()==null) {
    		return new ArrayList<SelectItem>();
    	}
        Certification.Type certType = getCertification().getType();

        if (null == this.additionalFilterChoices) {

            Comparator selectItemComparator = new SelectItemByLabelComparator(baseBean.getLocale());

            this.additionalFilterChoices = new ArrayList<SelectItem>();

            List<SelectItem> certificationItems = new ArrayList<SelectItem>();

            // Account group and role composition certs don't deal with biz roles. Role mebership certs do.
            if (!certType.isType(Certification.Type.BusinessRoleComposition,
                    Certification.Type.AccountGroupMembership, Certification.Type.AccountGroupPermissions, Certification.Type.DataOwner)){
                certificationItems.add(new SelectItem(BUSINESS_ROLE_FILTER, baseBean.getMessage(MessageKeys.BUSINESS_ROLE)));
            }

            // Only identity certs have violation or addtl entitlements, with the exception of business role
            // membership certs which only cover busines roles, so this filter is not needed.
            if (!certType.isType(Certification.Type.BusinessRoleMembership,
                    Certification.Type.AccountGroupMembership, Certification.Type.AccountGroupPermissions, Certification.Type.DataOwner)){

                // only show item type filter if we're looking at entitlement centric view. On identity
                // view display a more generic entitlement 'Detected filters'
                if (!this.filterContext.isDisplayingItems()) {
                    // role composition never have addl ents or violations. Acct group certs dont
                    // have violations and only have addtl ents so why filter?
                    certificationItems.add(new SelectItem(HAS_ADDITIONAL_ENTITLEMENTS_FILTER, baseBean.getMessage(MessageKeys.ADDTL_ENTS_DETECTED)));
                    certificationItems.add(new SelectItem(HAS_POLICY_VIOLATIONS_FILTER, baseBean.getMessage(MessageKeys.POLICY_VIOLATIONS_DETECTED)));
                }
                else{
                    certificationItems.add(new SelectItem(ITEM_TYPE_FILTER, baseBean.getMessage(MessageKeys.ITEM_TYPE)));
                }
            }
            
            if (certType.isType(Certification.Type.BusinessRoleComposition)) {
                certificationItems.add(new SelectItem(ROLE_TYPE_FILTER, baseBean.getMessage(MessageKeys.ROLE_TYPE)));
            }
            
            // if any certification items were created add the 'Certification Properties' group
            if (!certificationItems.isEmpty()){
                Collections.sort(certificationItems, selectItemComparator);
                SelectItemGroup certificationGroup = new SelectItemGroup(baseBean.getMessage(MessageKeys.CERT_PROPS));
                certificationGroup.setSelectItems(certificationItems.toArray(new SelectItem[]{}));
                this.additionalFilterChoices.add(certificationGroup);
            }


            // add "Identity Properties", note that this includes role membership certs
            if (CertificationEntity.Type.Identity.equals(certType.getEntityType())){
                List<SelectItem> identityItems = new ArrayList<SelectItem>();
                identityItems.add(new SelectItem(FIRST_NAME_FILTER, baseBean.getMessage(MessageKeys.FIRST_NAME)));
                identityItems.add(new SelectItem(LAST_NAME_FILTER, baseBean.getMessage(MessageKeys.LAST_NAME)));
                identityItems.add(new SelectItem(RISK_SCORE_FILTER, baseBean.getMessage(MessageKeys.RISK_SCORE)));
                ObjectConfig config = Identity.getObjectConfig();
                if (config != null) {
                    List<ObjectAttribute> atts = config.getCustomAttributes();
                    if (atts != null) {
                        for (ObjectAttribute att : atts) {
                        	if (!att.isType(ObjectAttribute.TYPE_IDENTITY)) {
                        		identityItems.add(new SelectItem(IDENTITY_PREFIX + att.getName(), 
                                    att.getDisplayableName(baseBean.getLocale())));
                        	}
                        }
                    }
                }
                Collections.sort(identityItems, selectItemComparator);
           
                if (!identityItems.isEmpty()){
                    SelectItemGroup identityGroup = new SelectItemGroup(baseBean.getMessage(MessageKeys.IDENTITY_PROPS));
                    identityGroup.setSelectItems(identityItems.toArray(new SelectItem[]{}));
                    this.additionalFilterChoices.add(identityGroup);
                }

                List<SelectItem> accountAttrs = new ArrayList<SelectItem>();
                config = CertificationItem.getObjectConfig();
                if (config != null) {
                    List<ObjectAttribute> atts = config.getCustomAttributes();
                    if (atts != null) {
                        for (ObjectAttribute att : atts) {
                            // don't include multi valued attrs since we don't yet
                            // have an external attribute table for cert items.
                            if (!att.isMulti()) {
                                accountAttrs.add(new SelectItem(ITEM_PREFIX + att.getName(),
                                                                att.getDisplayableName(baseBean.getLocale())));
                            }
                        }
                    }
                }

                if (!accountAttrs.isEmpty()){
                    Collections.sort(accountAttrs, selectItemComparator);
                    SelectItemGroup itemGroup = new SelectItemGroup(baseBean.getMessage(MessageKeys.ACCOUNT_PROPS));
                    itemGroup.setSelectItems(accountAttrs.toArray(new SelectItem[]{}));
                    this.additionalFilterChoices.add(itemGroup);
                }
            }

            // "Additional Entitlements" filter group, ignores role certifications
            // since they have no addtl entitlements
            if (!certType.isType(Certification.Type.BusinessRoleMembership,
                    Certification.Type.BusinessRoleComposition)){
                CertificationService svc = new CertificationService(getContext());
                List<SelectItem> addtEntItems = new ArrayList<SelectItem>();
                List<String> addtEntApps =
                    svc.getAdditionalEntitlementApplications(getCertification());
                for (String app : addtEntApps) {
                    addtEntItems.add(new SelectItem(ADDITIONAL_ENTITLEMENT_APP_PREFIX + app, app));
                }

                if (!addtEntItems.isEmpty()){
                    Collections.sort(addtEntItems, selectItemComparator);
                    SelectItemGroup addtEntGroup = new SelectItemGroup(baseBean.getMessage(MessageKeys.ADDITIONAL_ENTITLEMENTS));
                    addtEntGroup.setSelectItems(addtEntItems.toArray(new SelectItem[]{}));
                    this.additionalFilterChoices.add(addtEntGroup);
                }
            }
        }

        return this.additionalFilterChoices;
    }

    /**
     * Indicates if we should show the Add Filter link. We don't show it
     * if there are no available filters for the given certification.
     *
     * @return True if there are additional filters for this certification
     * @throws GeneralException
     */
    public boolean isAllowAddFilter() throws GeneralException{
        return !getAdditionalFilterChoices().isEmpty();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext getContext() throws GeneralException {
        // Ideally, we would get the context off of the baseBean, but there is
        // race condition that may cause a closed context to be returned.  See
        // bug 7553.
        return SailPointFactory.getCurrentContext();
    }
    
    private Certification getCertification() {
        if (null == this.certification) {
        	try {
        		this.certification =
        			getContext().getObjectById(Certification.class, this.certificationId);
        	} catch(Exception e) {
        		
        	}
        }
        return this.certification;
    }

    public Filter getFilter()
    {
        Filter filter = FilterHelper.getEqFilter("summaryStatus", this.status);

        if (null != this.hasDifferences) {
            Filter hasDiffs = FilterHelper.getEqFilter("hasDifferences", this.hasDifferences);
            Filter isNew = FilterHelper.getEqFilter(this.filterContext.addEntityPropertyPrefix("newUser"), this.hasDifferences);

            // If we're looking for users with differences, return any that have
            // differences OR are new.
            Filter differencesFilter;
            if (this.hasDifferences) {
                differencesFilter = FilterHelper.or(hasDiffs, isNew);
            }
            else {
                // If we're looking for users without differences, return any
                // that don't have diffs AND are not new.
                differencesFilter = FilterHelper.and(hasDiffs, isNew);
            }
            
            filter = FilterHelper.and(filter, differencesFilter);
        }

        if (null != filters)
        {
            boolean joinToIdentity = false;

            List<CertificationFilter> otherFilters = new ArrayList<CertificationFilter>();
            Map<String, List<CertificationFilter>> duplicates = new HashMap<String, List<CertificationFilter>>();

            for (CertificationFilter current : filters)
            {
                if(current.getFilter()!=null) {
                    otherFilters.add(current);

                    String propertyName = current.getPropertyName();
                    List<CertificationFilter> theseFilters = duplicates.get(propertyName);
                    if(theseFilters==null)
                        theseFilters = new ArrayList<CertificationFilter>();
                    theseFilters.add(current);
                    duplicates.put(propertyName, theseFilters);

                    joinToIdentity |= current.joinToIdentity();
                }
            }
            if(!otherFilters.isEmpty()) {
                filter = FilterHelper.and(orDuplicates(duplicates, otherFilters), filter);
            }

            if (joinToIdentity) {
                filter = FilterHelper.and(Filter.join(this.filterContext.addEntityPropertyPrefix("identity"), "Identity.name"), filter);
            }
        }
        return filter;
    }

    /** We want to find any filters that are over the same attributes and or them
     * together instead of anding them.  anding them produces no results. **/
    private Filter orDuplicates(Map<String, List<CertificationFilter>> duplicates,
            List<CertificationFilter> filters) {
        List<Filter> itemCollectionConditions = new ArrayList<Filter>();
        List<Filter> newFilters = new ArrayList<Filter>();

        Set<String> keys = duplicates.keySet();
        for(String key : keys) {
            List<CertificationFilter> theseFilters = duplicates.get(key);
            if(theseFilters!=null && theseFilters.size()>1) {

                List<Filter> tempFilters = new ArrayList<Filter>();
                for(CertificationFilter tempCertFilter : theseFilters) {
                    tempFilters.add(tempCertFilter.getFilter());
                }

                /** Handle filters over multivalued attributes **/
                CertificationFilter certIdentFilter = theseFilters.get(0);
                if(certIdentFilter.isItemsCollectionCondition()) {
                    newFilters.add(Filter.collectionCondition("items", Filter.or(tempFilters)));
                } else if(RISK_SCORE_FILTER.equals(key)) {
                    newFilters.add(Filter.and(tempFilters));
                }    else {
                    newFilters.add(Filter.or(tempFilters));
                }

            } else if(theseFilters!=null) {
                CertificationFilter thisFilter = theseFilters.get(0);
                if(thisFilter.isItemsCollectionCondition()) {
                    itemCollectionConditions.add(thisFilter.getFilter());
                } else {
                    newFilters.add(thisFilter.getFilter());
                }
            }
        }

        if (!itemCollectionConditions.isEmpty()) {
            newFilters.add(Filter.collectionCondition("items", Filter.and(itemCollectionConditions)));
        }
        return Filter.and(newFilters);
    }

    /**
     * Create a selector if any of the filters can filter certification
     * items.
     */
    CertificationItemSelector getCertificationItemSelector() {

        // Don't return a selector if we're looking at items.
        if (this.filterContext.isDisplayingItems()) {
            return null;
        }

        CertificationItemSelector selector = null;

        if (null != this.filters) {
            Filter filter = null;
            for (CertificationFilter current : this.filters) {
                if (current.isSelectable()) {
                    // OR these together since we're checking one
                    // CertificationItem at a time (ie - no single item can
                    // meet multiple different criteria).
                    filter = FilterHelper.or(current.getFilter(), filter);
                }
            }

            if (null != filter) {
                final Filter selectorFilter = filter;

                selector = new CertificationItemSelector() {
                    public boolean matches(CertificationItem item) throws GeneralException {

                        ExtendedAttributeVisitor v =
                        new ExtendedAttributeVisitor(CertificationItem.class);
                        selectorFilter.accept(v);
                        
                        ReflectiveMatcher matcher = new ReflectiveMatcher(selectorFilter);
                        return matcher.matches(item);
                    }
                };
            }
        }

        return selector;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Action to add a new filter to the search.  Returns to the same page.
     */
    public String addFilter() throws GeneralException {
        CertificationFilter f =
            new CertificationFilter(this.certificationId,
                    getFirstAdditionalFilter(getAdditionalFilterChoices()),
                    this.filterContext, this.baseBean);
        this.filters.add(f);
        return null;
    }

    /**
     * Get the first filter value from the additional filters list.
     * 
     * @param  items  The list of select items or select item groups.
     */
    private String getFirstAdditionalFilter(List<SelectItem> items)
        throws GeneralException {

        if (null != items) {
            for (SelectItem item : items) {
                if (item instanceof SelectItemGroup) {
                    SelectItem[] subItems = ((SelectItemGroup) item).getSelectItems();
                    if (null != subItems) {
                        // Recurse.
                        String filter = getFirstAdditionalFilter(Arrays.asList(subItems));
                        if (null != filter) {
                            return filter;
                        }
                    }
                }
                else {
                    if (null != item.getValue()) {
                        return (String) item.getValue();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Action to remove the filter with the selectedFilterId.  Returns to the
     * same page.
     */
    public String removeFilter()
    {
        if (null != this.selectedFilterId)
        {
            for (Iterator<CertificationFilter> it=this.filters.iterator(); it.hasNext(); )
            {
                if (this.selectedFilterId.equals(it.next().getId()))
                {
                    it.remove();
                    break;
                }
            }
        }

        this.selectedFilterId = null;
        return null;
    }

    /**
     * An action listener that is called when a filter selection has changed.
     */
    public void filterSelectionChanged(ActionEvent e) {

        if (null != this.selectedFilterId) {
            for (CertificationFilter current : this.filters) {
                if (this.selectedFilterId.equals(current.getId())) {
                    current.clearValues();
                }
            }
        }

        this.selectedFilterId = null;
    }

    /**
     * An action listener that is called when the selected value for a filter
     * has changed.
     */
    public void filterValueChanged(ActionEvent e) {
        // No-op.
    }

	public void setFilters(List<CertificationFilter> filters) {
		this.filters = filters;
	}
}
