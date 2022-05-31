/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SearchResultsIterator;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Recommendation;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterHelper;

/**
 * Abstract implementation of core ListFilterContext for all certification filters.
 */
public abstract class AbstractCertificationListFilterContext extends BaseListFilterContext {

    private static final Log log = LogFactory.getLog(AbstractCertificationListFilterContext.class);

    private static final String IDENTITY_PREFIX = "Identity.";

    protected static final String CHANGES_DETECTED_FILTER = "changesDetected";
    protected static final String CHANGES_DETECTED_PROP = "hasDifferences";

    // Flag to set in the ListFilterDTO to indicate additional entitlement filters
    protected static final String ADDITIONAL_ENTITLEMENT_FLAG = "additionalEntitlement";

    // Properties in a ListFilterValue value map from the client
    private static final String ADDITIONAL_ENTITLEMENT_APPLICATION_PROP = "application";
    private static final String ADDITIONAL_ENTITLEMENT_NAME_PROP = "name";
    private static final String ADDITIONAL_ENTITLEMENT_VALUE_PROP = "value";
    private static final String ADDITIONAL_ENTITLEMENT_IS_PERMISSION_PROP = "isPermission";

    // CertificationItem properties around additional entitlements
    private static final String CERT_ITEM_APPLICATION = "exceptionApplication";
    protected static final String CERT_ITEM_ATTRIBUTE_NAME = "exceptionAttributeName";
    private static final String CERT_ITEM_ATTRIBUTE_VALUE = "exceptionAttributeValue";
    private static final String CERT_ITEM_PERMISSION_NAME = "exceptionPermissionTarget";
    private static final String CERT_ITEM_PERMISSION_VALUE = "exceptionPermissionRight";
    private static final String CERT_ITEM_FIRSTNAME = "firstname";
    private static final String CERT_ITEM_LASTNAME = "lastname";
    private static final String CERT_ITEM_ROLE_NAME = "targetName";
    private static final String CERT_ITEM_TYPE = "type";

    private static final int ADDITIONAL_ENTITLEMENTS_CATEGORY_INDEX = 3;

    private String certificationId;
    private boolean joinedToIdentity = false;

    // Keep track of the top level attributes found up til now. This way derived classses can hardcode top-level
    // attributes and have them accounted for when computing the remaining ones in this base class.
    private Set<ListFilterDTO> topLevelFilters = new HashSet<>();


    /**
     * Constructor
     * @param certificationId ID of the certification
     */
    public AbstractCertificationListFilterContext(String certificationId) {
        super(null);
        
        this.certificationId = certificationId;
    }

    /**
     * Override to get the default filters for additional entitlements for each app.
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filterDTOList = new ArrayList<>();

        // Fetch all the exceptionApplications in the certification and make additional filters for them.
        List<String> exceptionApplications = getExceptionApplicationNames(context);
        for (String application: Util.iterate(exceptionApplications)) {
            ListFilterDTO appFilter = new ListFilterDTO(application, application, false, ListFilterDTO.DataTypes.String, locale, true);
            appFilter.setAttribute(ADDITIONAL_ENTITLEMENT_FLAG, true);
            appFilter.setAttribute(ADDITIONAL_ENTITLEMENT_APPLICATION_PROP, application);
            appFilter.setAttribute(ListFilterDTO.ATTR_SUGGEST_URL, getSuggestUrl());
            configureFilterCategory(context, null, MessageKeys.UI_ADDITIONAL_ENTITLEMENTS_CATEGORY_NAME, ADDITIONAL_ENTITLEMENTS_CATEGORY_INDEX, Collections.singletonList(appFilter));
            filterDTOList.add(appFilter);
        }

        return filterDTOList;
    }

    /**
     * Override to return comparator that does nothing, we will handle ordering ourselves.
     */
    @Override
    public Comparator<ListFilterDTO> getFilterComparator(Locale locale) {
        return new Comparator<ListFilterDTO>() {
            @Override
            public int compare(ListFilterDTO o1, ListFilterDTO o2) {
                return 0;
            }
        };
    }

    /**
     * @return True if we joined to identity table
     */
    public boolean isJoinedToIdentity() {
        return this.joinedToIdentity;
    }

    /**
     * @param joined True if we joined to identity table
     */
    public void setJoinedToIdentity(boolean joined) {
        this.joinedToIdentity = joined;
    }

    /**
     * Convert a certification item type to a select item. Needed because we want to use the short message key.
     * @param type CertificationItem Type
     * @param locale Locale
     * @return SelectItem
     */
    protected ListFilterDTO.SelectItem getSelectItem(CertificationItem.Type type, Locale locale) {
        String label = new Message(type.getShortMessageKey()).getLocalizedMessage(locale, null);
        return new ListFilterDTO.SelectItem(label, type);
    }

    /**
     * Convert a recommendation decision to a select item.
     * @param recommendation The RecommendedDecision to convert
     * @param locale Locale
     * @return SelectItem
     */
    protected ListFilterDTO.SelectItem getSelectItem(Recommendation.RecommendedDecision recommendation, Locale locale) {
        String label = new Message(recommendation.getMessageKey()).getLocalizedMessage(locale, null);
        return new ListFilterDTO.SelectItem(label, recommendation);
    }

    /**
     * @return the identity object config, or null if no identity attributes exist for this cert type.
     */
    protected ObjectConfig getIdentityObjectConfig(SailPointContext context) throws GeneralException {
        return ObjectConfig.getObjectConfig(Identity.class);
    }

    /**
     * @return the certification id
     */
    protected String getCertificationId() {
        return this.certificationId;
    }

    /**
     * @return the identity filter string format
     */
    protected abstract String getIdentityFilterStringFormat(SailPointContext context) throws GeneralException;

    /**
     * Add the join filter for Identity properties, if needed.
     */
    protected abstract Filter checkJoin(Certification.Type type, Filter filter, String property);

    /**
     * Returns the set of names of preferred identity attributes to display as top-level filters, or null if none
     * are preferred. For most cert types, this will return null.
     */
    protected abstract List<String> getPreferredTopLevelIdentityAttributeNames();

    /**
     * Create a list of filters from a corresponding list of attributes
     * @param attributes List of ObjectAttributes
     * @param clazz Class for the column suggests
     * @param prefix Prefix for the filter property name, if required.
     * @param filterString Filter string for the suggest, if required.
     * @param locale Locale
     * @param additional if true these are additional filters, otherwise they are top-level
     * @return List of fully realized ListFilterDTOs
     */
    protected List<ListFilterDTO> createListFilterDTOs(List<ObjectAttribute> attributes, Class clazz, String prefix, String filterString, Locale locale, boolean additional) {

        List<ListFilterDTO> listFilterDTOList = new ArrayList<>();
        for (ObjectAttribute attribute : Util.iterate(attributes)) {
            ListFilterDTO listFilterDTO = super.createListFilterDTO(attribute, locale);
            if (prefix != null) {
                listFilterDTO.setProperty(prefix + listFilterDTO.getProperty());
            }
            String formattedFilterString = (filterString == null) ? null : String.format(filterString, this.certificationId);

            // Create column suggests for all normal string attributes, including the filter string for cert limitations
            if (ListFilterDTO.DataTypes.String.equals(listFilterDTO.getDataType())) {
                listFilterDTO.configureColumnSuggest(clazz.getSimpleName(), attribute.getName(), formattedFilterString, getSuggestUrl());
            }

            // Be sure to set the additional flag for any non-top level attributes.
            listFilterDTO.setAdditional(additional);
            
            listFilterDTOList.add(listFilterDTO);
        }
        
        return listFilterDTOList;
    }
    
    protected void configureFilterCategory(SailPointContext context, String prefix, String categoryName, int categoryIndex, List<ListFilterDTO> dtos) throws GeneralException {
        for (ListFilterDTO dto: dtos) {
            dto.setAdditional(true);
            dto.configureCategory(categoryName, categoryIndex);
        }
    }

    /**
     * Override filter conversion to use the correct property (and add a join to the Identity table) if we're
     * searching on an identity property.
     */
    @Override
    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context)
        throws GeneralException {

        Filter filter;
        if (value == null) {
            // TODO: We should handle nulls in BaseListFilterContext, but too scared to do that right now.
            filter = FilterHelper.getNullFilter(filterDTO.getProperty());
        } else {
            filter = super.convertFilterSingleValue(filterDTO, value, operation, context);
        }
        filter = checkJoin(getCertificationType(context), filter, filterDTO.getProperty());

        if (Util.nullSafeEq(filterDTO.getAttribute(ADDITIONAL_ENTITLEMENT_FLAG), true)) {
            // Value for additional entitlement filters is a JSON string representing a map with values.
            Map<String, Object> valueMap;
            if (isJsonMap(value)){
                valueMap = JsonHelper.mapFromJson(String.class, Object.class, value);
            } else {
                valueMap = sailpoint.service.listfilter.ListFilterService.stringToMap(value);
            }
            List<Filter> filters = new ArrayList<>();
            filters.add(Filter.eq(CERT_ITEM_APPLICATION, valueMap.get(ADDITIONAL_ENTITLEMENT_APPLICATION_PROP)));
            boolean isPermission = Util.otob(valueMap.get(ADDITIONAL_ENTITLEMENT_IS_PERMISSION_PROP));

            // Name is optional
            if (valueMap.containsKey(ADDITIONAL_ENTITLEMENT_NAME_PROP)) {
                filters.add(Filter.eq(isPermission ? CERT_ITEM_PERMISSION_NAME : CERT_ITEM_ATTRIBUTE_NAME, valueMap.get(ADDITIONAL_ENTITLEMENT_NAME_PROP)));
            }

            // Value is optional
            if (valueMap.containsKey(ADDITIONAL_ENTITLEMENT_VALUE_PROP)) {
                filters.add(Filter.eq(isPermission ? CERT_ITEM_PERMISSION_VALUE : CERT_ITEM_ATTRIBUTE_VALUE, valueMap.get(ADDITIONAL_ENTITLEMENT_VALUE_PROP)));
            }

            filter = filters.size() == 1 ? filters.get(0) : Filter.and(filters);
        }

        return filter;
    }
    
    /**
     * Checks to see if a string can be desserialized from a JSON to a Map
     * 
     * @param value
     * @return returns true if the JSON can be deserialized as a map
     */
    private static Boolean isJsonMap(String value){
        try {
        	Map map = JsonHelper.fromJson(Map.class, value);
        } catch (Exception e) {
        	//if we get an exception from this it is most likely a single value string
            return false;
        }
    	return true;
    }

    /**
     * Get all the exception application names that are part of this cert.
     */
    protected List<String> getExceptionApplicationNames(SailPointContext context) throws GeneralException {
        // IIQSAW-1277: Get apps for this cert and any children reassignment certs.
        // We have a situation where if all items from an application are reassigned, then that app no longer will
        // be part of the list here. This means that we cannot match the filter up anymore, even though it will still
        // exist after the reassignment. So look one level down too. In most cases the apps will line up, but if
        // there's a slight mis match I don't see that as a huge problem.

        Set<String> applications = new LinkedHashSet<>();

        // Get Child reassignment cert ids
        QueryOptions childCertOptions = new QueryOptions();
        childCertOptions.add(Filter.eq("parent.id", this.certificationId));
        childCertOptions.add(Filter.eq("bulkReassignment", true));
        List<String> certIds = new ArrayList<>();
        List<String> childCertIds = ObjectUtil.getObjectIds(context, Certification.class, childCertOptions);
        if (!Util.isEmpty(childCertIds)) {
            certIds.addAll(childCertIds);
        }

        // Add current cert id
        certIds.add(this.certificationId);

        // Search across all those ids for distinct application names
        SearchResultsIterator searchResults = ObjectUtil.searchAcrossIds(context, CertificationItem.class, certIds, null, Collections.singletonList(CERT_ITEM_APPLICATION), "parent.certification.id", true);
        while (searchResults.hasNext()) {
            String applicationName = (String)searchResults.next()[0];
            if (!Util.isNothing(applicationName)) {
                applications.add(applicationName);
            }
        }

        return new ArrayList<>(applications);
    }

    /**
     * Return whether the given filter is searching on an identity property.
     */
    protected boolean isIdentityFilter(String property) {

        return property != null && property.startsWith(IDENTITY_PREFIX);
    }
    
    /**
     * Returns the type for the certification.
     * @param context Context to look up in
     * @return The type or null
     */
    protected Certification.Type getCertificationType(SailPointContext context) throws GeneralException {

        Certification certification = context.getObjectById(Certification.class, this.certificationId);
        return certification != null ? certification.getType() : null;
    }

    /**
     * Helper method to build a filter
     * @param cls Class of the object being queried
     * @param propertyName Name of the property
     * @param filterValue ListFilterValue
     * @param isEqual True if this should be equals, false if this should be not equals.
     * @param convertToString If true, convert filter value(s) to strings, otherwise leave as-is
     * @return Filter combining values
     */
    protected Filter getEqualityFilter(Class cls, String propertyName, ListFilterValue filterValue, boolean isEqual, boolean convertToString) throws GeneralException {

        List<Filter> filters = new ArrayList<>();
        List<?> valueList;
        try {
            valueList = (convertToString) ? getValueStringList(filterValue) : getValueList(filterValue, propertyName, cls);
        } catch (GeneralException ge) {
            // If we cant cast the filter value, we may have some an invalid filter. This can happen in certs specifically
            // because we have dynamic list of filters for application names, and if cert contents change then so can the set of
            // available apps. We try to handle this but dont blow everything up if we dont.
            if (log.isWarnEnabled()) {
                log.warn("Unable to convert filter", ge);
            }
            return null;
        }

        Filter filter = null;
        for (Object value: Util.iterate(valueList)) {
            filters.add(getSimpleFilter(propertyName, value, (isEqual) ? ListFilterValue.Operation.Equals : ListFilterValue.Operation.NotEquals));
        }

        if (!Util.isEmpty(filters)) {
            if (filters.size() == 1) {
                filter = filters.get(0);
            } else {
                filter = (isEqual) ? Filter.or(filters) : Filter.and(filters);
            }
        }
        
        return filter;
    }

    /**
     * Get the certification definition. If not found, throws exception. 
     */
    protected CertificationDefinition getCertificationDefinition(SailPointContext context) throws GeneralException {

        String definitionId = null;
        CertificationDefinition definition = null;

        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("id", this.certificationId));
        Iterator<Object[]> results = context.search(Certification.class, options, "certificationDefinitionId");
        if (results.hasNext()) {
            definitionId = (String)results.next()[0];
        }

        if (definitionId != null) {
            definition = context.getObjectById(CertificationDefinition.class, definitionId);
        }

        if (definition == null) {
            throw new GeneralException("Unable to find CertificationDefinition for Certification " + this.certificationId);
        }
        
        return definition;
    }

    /**
     * Given an object config, create filters for all searchable attributes in the config and configure them for the
     * specified category.
     *
     * @param context
     * @param objectAttributes
     * @param columnSuggestsClass
     * @param prefix
     * @param filterString
     * @param categoryName
     * @param categoryIndex
     * @param locale
     */
    protected List<ListFilterDTO> createFiltersForCategory(SailPointContext context, List<ObjectAttribute> objectAttributes, Class columnSuggestsClass,
                               String prefix, String filterString, String categoryName, int categoryIndex, Locale locale)
            throws GeneralException {
        List<ListFilterDTO> filterDTOs = createListFilterDTOs(objectAttributes, columnSuggestsClass, prefix, filterString, locale, true);
        configureFilterCategory(context, prefix, categoryName, categoryIndex, filterDTOs);

        return filterDTOs;
    }

    /**
     * Return the list of identity attributes which should be used for top-level filters, and remove them from the
     * specified list. This is computed in the following way:
     * 1. If the certification is configured with a CSV of attribute names in certificationItemFilterAttributes, use those
     *    names to select identity attriutes.
     * 2. Otherwise:
     *    Fill in with identity attributes until we have a set of four, assuming that derived classes will have already
     *    placed type-specific hardcoded object attributes in topLevelAttributes.
     *
     * This method is generic enough that it should work for all cert types and does not need to be overridden.
     *
     * NOTE: A few basic assumptions go into this:
     *    1. The certificationItemFilterAttributes contains only identity attribute names.
     *    2. Derived classes will override getObjectAttributeFilters() to hardcode any type-specific top-level attributes.
     *    3. getDefaultFilters() will never return top-level attributes.
     *
     * @param context SailPointContext
     * @param identityAttributes List of ObjectAttributes. Will be modified by removing top level attributes.
     * @return List of ObjectAttributes that should be top level attributes.
     * @throws GeneralException
     */
    protected List<ObjectAttribute> getTopLevelIdentityAttributes(SailPointContext context, List<ObjectAttribute> identityAttributes)
            throws GeneralException {
        List<ObjectAttribute> topLevelFilterAttributes = new ArrayList<>();
        CertificationDefinition definition = getCertificationDefinition(context);

        // get the configured attribute names for the cert item
        String configuredAttributeNames = definition.getCertificationItemFilterAttributes(context);
        if (configuredAttributeNames != null) {
            // CSV list of attribute names to include, from the cert definition or system properties
            final List<String> configuredNameList = Util.csvToList(configuredAttributeNames);
            // iterate over all of the searchable attributes to look for configured ones
            topLevelFilterAttributes.addAll(findTopLevelAttributes(configuredNameList, identityAttributes));

            // sort the configured attributes
            Collections.sort(topLevelFilterAttributes, new Comparator<ObjectAttribute>() {
                @Override
                public int compare(ObjectAttribute o1, ObjectAttribute o2) {
                    return Util.nullSafeCompareTo(configuredNameList.indexOf(o1.getName()), configuredNameList.indexOf(o2.getName()));
                }
            });
        } else {
            // If nothing is configured, fill in with identity attributes until the number of top level filters equals 4.
            // These will not be marked as additional filters.

            // First see if the derived class prefers to use specific identity attributes, and use those.
            topLevelFilterAttributes.addAll(findTopLevelAttributes(getPreferredTopLevelIdentityAttributeNames(), identityAttributes));

            // Fill in with remaining identity attributes until we've got a total of 4.
            int numTopLevel = topLevelFilterAttributes.size() + this.topLevelFilters.size();
            // Only add to topLevelFilterAttributes when there is something to add.
            if (numTopLevel <= 3) {
                topLevelFilterAttributes.addAll(identityAttributes.subList(0, Math.min(identityAttributes.size(), 4 - numTopLevel)));
            }
            identityAttributes.removeAll(topLevelFilterAttributes);
        }

        return topLevelFilterAttributes;
    }

    /**
     * Finds the identity attributes with a name in the given list of top level attribute names.
     * @param names the top level attribute names to search for
     * @param identityAttributes the identity attributes
     * @return the top level identity attributes
     */
    private List<ObjectAttribute> findTopLevelAttributes(List<String> names, List<ObjectAttribute> identityAttributes) {
        List<ObjectAttribute> results = new ArrayList<>();

        // iterate over all of the searchable attributes to look for configured ones
        Iterator<ObjectAttribute> attributesIterator = identityAttributes.iterator();
        while (attributesIterator.hasNext()) {
            ObjectAttribute attribute = attributesIterator.next();
            // If a selected name
            if (Util.nullSafeContains(names, attribute.getName())) {
                results.add(attribute);
                attributesIterator.remove();
            }
        }

        return results;
    }

    /**
     * Add filters to the running set of top level filters.
     * @param filters filters to add.
     */
    protected void addTopLevelFilters(List<ListFilterDTO> filters) {
        topLevelFilters.addAll(filters);
    }

    /**
     * Clear the running set of top level filters.
     */
    protected void clearTopLevelFilters() {
        if (!Util.isEmpty(topLevelFilters)) {
            topLevelFilters.clear();
        }
    }

    /**
     * Return the running set of top level filters.
     * @return The collected top level filters.
     */
    protected Set<ListFilterDTO> getTopLevelFilters() {
        return topLevelFilters;
    }

    protected boolean hasRecommendations(SailPointContext context) {
        try {
            CertificationDefinition definition = getCertificationDefinition(context);
            return definition.getShowRecommendations();
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean hasAutoApprovals(SailPointContext context) {
        try {
            CertificationDefinition definition = getCertificationDefinition(context);
            return definition.isAutoApprove();
        } catch (Exception e) {
            return false;
        }
    }
}
