package sailpoint.service.listfilter;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interface for the context the ListFilterService can understand
 */
public interface ListFilterContext {

    /**
     * Check if this filter context is enabled.
     * @param context SailPointContext
     * @return True if enabled, otherwise false
     * @throws GeneralException
     */
    boolean isEnabled(SailPointContext context) throws GeneralException;

    /**
     * Fetch a list of ListFilterDTOs for the object attributes
     * @param context SailPointContext
     * @return List of ListFilterDTO
     * @throws GeneralException
     */
    List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale) throws GeneralException;

    /**
     * Fetch a list of "default" filters, that are baked in and do not rely on ObjectAttributes
     * @param context SailPointContext
     * @param locale Locale
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException;

    /**
     * Convert a FilterDTO and a value to a Filter object for use in a search
     * @param filterDTO FilterDTO to use 
     * @param filterValue ListFilterValue to match
     * @param context SailPointContext
     * @return Filter object
     * @throws GeneralException
     */
    Filter convertFilter(ListFilterDTO filterDTO, ListFilterValue filterValue, SailPointContext context) throws GeneralException;

    /**
     * Convert a parameter to a filter without a matching FilterDTO
     * @param propertyName Name of property
     * @param filterValue ListFilterValue to match
     * @param context SailPointContext
     * @return Filter object, or null               
     */
    Filter convertFilter(String propertyName, ListFilterValue filterValue, SailPointContext context) throws GeneralException;

    /**
     * Convert a collection of query parameters to a list of deep links filters. Removes any consumed parameters 
     * from the map.
     * @param filterValueMap map of ListFilterValues
     * @param context SailPointContext
     * @return A list of Filter objects, which may be empty
     */
    List<Filter> convertDeepLinksFilters(Map<String, ListFilterValue> filterValueMap, SailPointContext context) throws GeneralException;

    /**
     * Returns true if the parameters contain deep links parameters for this type of context.
     * @param queryParameters map of parameters
     * @return true if deep links
     */
    boolean hasDeepLinksParams(Map<String, String> queryParameters) throws GeneralException;

    /**
     * Get the Comparator to sort the list of filters.
     * @return Comparator for ListFilterDTOs
     */
    Comparator<ListFilterDTO> getFilterComparator(Locale locale);

    /**
     * Get the Comparator to sort the list of filters.
     * @return Comparator for ListFilterDTOs
     */
    Comparator<ListFilterDTO> getFilterLabelComparator(Locale locale);

    /**
     * Do any final post-processing of the filters as a group, once all of them have been created.
     * @return the processed filters
     */
    List<Filter> postProcessFilters(List<Filter> filters) throws GeneralException;

    /**
     * Get the relative URL for the suggest endpoint used for the filters that need suggest.
     * @return String url, relative to context path, including servlet.
     */
    String getSuggestUrl();

    /**
     * Parse the ListFilterValue value and collapse it to the name/id instead of a full suggest map.  This is useful
     * for filters sent through POST that come in as suggest objects, we can store as simple strings.
     * @param filterDTO ListFilterDTO for the value
     * @param filterValue ListFilterValue for the value.
     */
    void collapseFilterValue(ListFilterDTO filterDTO, ListFilterValue filterValue);

    /**
     * Processes ListFilterValues that were collapsed by collapseFilterValue but should be expanded back to suggest objects
     * for consumption in the UI.
     * @param filterDTO ListFilterDTo for the value
     * @param filterValue ListFilterValue for the value
     * @param context SailPointContext, to fetch suggest result
     * @param locale Locale, for suggest result display name
     * @throws GeneralException
     */
    void expandFilterValue(ListFilterDTO filterDTO, ListFilterValue filterValue, SailPointContext context, Locale locale) throws GeneralException;
}