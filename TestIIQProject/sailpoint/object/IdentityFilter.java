/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.VelocityUtil;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass(xmlname="IdentityFilter")
public class IdentityFilter extends AbstractXmlObject {    
    private static final long serialVersionUID = 7091264784990841315L;
    private static final Log log = LogFactory.getLog(IdentityFilter.class);

    // Global filter ID
    public static final String GLOBAL_FILTER = "Global";

    public static final String INCLUDE_WORKGROUPS_FILTER = "IncludeWorkGroups";

    /**
     * Context for LCM population managers.
     */
    public static final String CONTEXT_LCM_POPULATION_MANAGER = "LcmPopulationManager";

    /**
     * Context for LCM Population. Limits to identities accessible by the requester. 
     */
    public static final String CONTEXT_LCM_POPULATION = "LcmPopulation";
    
    /**
     * Context for entitlement owners.
     */
    public static final String CONTEXT_ENTITLEMENT_OWNER = "EntitlementOwner";

    /**
     * Filter name for Manager Drop-downs.
     * Please note that this does not mean that the drop-down shows managers only.
     * This filter should include all identities who *can be* managers.
     * Also, this will not include workgroups, since workgroups cannot be managers.
     */
    public static final String IDENTITY_MANAGER_ATTRIBUTE = "IdentityManagerAttribute";

    private String _name;
    private boolean _ignoreGlobal;
    private List<String> _includedFilters;
    private Script _filterScript;

    // TODO: We may eventually want to deprecate using Velocity to generate the filters.
    // Instead just add a Script to the IdentitySelectorConfiguration element. See 
    // <IdentityFilter name="WorkgroupMembers"> for an example.
    private List<String> _orderBy;
    private Order _order; // ascending or descending
    private FilterSource _filter; // typically a Filter or FilterTemplate

    
    public IdentityFilter(){
        _ignoreGlobal = false;
        _orderBy = Arrays.asList("firstname");
        _order = Order.Ascending;
    }

    public IdentityFilter(FilterSource filter) {
        this();
        _filter = filter;
    }

    public IdentityFilter(FilterSource filter, boolean ignoreGlobal) {
        this();
        _filter = filter;
        _ignoreGlobal = ignoreGlobal;
    }
    
    @XMLProperty
    public String getName() {
        return _name;
    }
    
    public void setName(String name) {
        _name = name;
    }
    
    @XMLProperty
    public FilterSource getFilterSrc() {
        return _filter;
    }

    public void setFilterSrc(FilterSource filter) {
        _filter = filter;
    }

    @XMLProperty
    public boolean isIgnoreGlobal() {
        return _ignoreGlobal;
    }

    public void setIgnoreGlobal(boolean ignoreGlobal) {
        _ignoreGlobal = ignoreGlobal;
    }
    
    @XMLProperty
    public List<String> getOrderBy() {
        return _orderBy;
    }
    
    public void setOrderBy(List<String> orderBy) {
        _orderBy = orderBy;
    }
    
    @XMLProperty
    public Order getOrder() {
        return _order;
    }
    
    public void setOrder(Order order) {
        _order = order;
    }
    
    @XMLProperty
    public Script getFilterScript() {
        return _filterScript;
    }

    public void setFilterScript(Script script) {
        _filterScript = script;
    }
    
    public List<IdentityFilter> getIncludedFilters() throws GeneralException {
        List<String> includedFilterReferences = getIncludedFilterReferences();
        List<IdentityFilter> includedFilters = new ArrayList<IdentityFilter>();
        
        if (includedFilterReferences != null && !includedFilterReferences.isEmpty()) {
            Configuration selectorConfig = Configuration.getIdentitySelectorConfig();
            Map<String, IdentityFilter> filters = (Map<String, IdentityFilter>) selectorConfig.get(Configuration.IDENTITY_FILTERS);
            for (String filterReference : includedFilterReferences) {
                IdentityFilter resolvedReference = filters.get(filterReference);
                if (resolvedReference == null) {
                    if (log.isWarnEnabled())
                        log.warn("The " + _name + " IdentityFilter references the " + filterReference + 
                                 " IdentityFilter, but it does not exist.  It will be ignored.");
                } else {
                    includedFilters.add(resolvedReference);
                }
            }
        }
        
        return includedFilters;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getIncludedFilterReferences() {
        return _includedFilters;
    }
    
    public void setIncludedFilterReferences(List<String> filterReferences) {
        _includedFilters = filterReferences;
    }
    
    public QueryOptions buildQuery(Map<String, Object> requestParameters, SailPointContext context) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        
        List<QueryOptions.Ordering> orderings = getOrderings();
        
        if (orderings == null || orderings.isEmpty()) {
            orderings = Arrays.asList(new QueryOptions.Ordering("name", true));
        }

        if(getFilterScript() != null && getFilterScript().getSource() != null){
            if(null == context){
                throw new NullPointerException("Expecting a non-null context!");
            }
            qo = (QueryOptions)context.runScript(getFilterScript(), requestParameters);
        }
        else {
        
            List<FilterSource> filterSources = new ArrayList<FilterSource>();
            if(null != getFilterSrc()) {
                filterSources.addAll(Arrays.asList(getFilterSrc()));
            }
            List<IdentityFilter> includedFilters = getIncludedFilters();
            
            // Add included filters if needed as well
            if (includedFilters != null && !includedFilters.isEmpty()) {
                for (IdentityFilter includedFilter : includedFilters) {
                    filterSources.add(includedFilter.getFilterSrc());
                }
            }
            
            List<Filter> filters = new ArrayList<Filter>();
            
            for (FilterSource filterSource : filterSources) {
                FilterBuilder builder;
                
                if (filterSource.getFilterTemplate() != null) {
                    builder = new TemplateFilterBuilder();
                } else { 
                    builder = new BasicFilterBuilder();
                }
                
                try {
                    builder.setFilterSource(filterSource);
                    Filter filter = builder.getFilter(requestParameters);
                    if (filter != null) {
                        filters.add(filter);
                    }
                } catch (GeneralException e) {
                    if (log.isErrorEnabled())
                        log.error("Failed to create a filter from this filter source: " + 
                                  filterSource.toString(), e);
                }
            }
    
            qo.add(filters.toArray(new Filter[filters.size()]));
        }
        
        qo.setOrderings(getOrderings());
        
        return qo;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IdentityFilter: [");
        if (_filter != null)
            sb.append("filter: [").append(_filter.toString()).append("], ");
        sb.append("isIgnoreGlobal: ").append(isIgnoreGlobal()).append(", ");
        if(_filterScript != null)
            sb.append("script: ").append(_filterScript.getLanguage()).append(", ");
        if (_orderBy != null)
            sb.append("orderBy: ").append(_orderBy).append(", ");
        if (_order != null)
            sb.append("order: ").append(_order).append(", ");
        if (_includedFilters != null)
            sb.append("includedFilters: [").append(_includedFilters.toString()).append("]");
        sb.append("]");
        return sb.toString();
    }
    
    @XMLClass(xmlname="Order")
    public static enum Order {
        Ascending,
        Descending
    }
    
    @XMLClass(xmlname="FilterSource")
    public static class FilterSource {
        private Filter _filter; 
        private FilterTemplate _filterTemplate;
        
        public FilterSource() {
        }
        
        @XMLProperty(xmlname="BasicFilter")
        public Filter getFilter() {
            return _filter;
        }
        
        public void setFilter(Filter filter) {
            _filter = filter;
        }
        
        @XMLProperty(xmlname="ParameterizedFilter")
        public FilterTemplate getFilterTemplate() {
            return _filterTemplate;
        }
        
        public void setFilterTemplate(FilterTemplate filterTemplate) {
            _filterTemplate = filterTemplate;
        }
        
    }
    
    @XMLClass(xmlname="FilterTemplate")
    public static class FilterTemplate {
        private String _filterString;
        private List<String> _filterParameters;
        
        public FilterTemplate(){
        }
        
        public FilterTemplate(String filterString, Collection<String> parameters) {
            _filterString = filterString;
            _filterParameters = new ArrayList<String>();
            _filterParameters.addAll(parameters);
        }
        
        @XMLProperty(mode=SerializationMode.ELEMENT)
        public String getFilterString() {
            return _filterString;
        }
        
        public void setFilterString(String filterString) {
            _filterString = filterString;
        }
        
        @XMLProperty
        public List<String> getFilterParameters() {
            return _filterParameters;
        }
        
        public void setFilterParameters(List<String> parameters) {
            _filterParameters = parameters;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("FilterTemplate: [");
            if (_filterString != null)
                sb.append("filterString: ").append(_filterString).append(", ");
            if (_filterParameters != null)
                sb.append("parameters: [").append(_filterParameters.toString()).append("]");
            sb.append("]");
            return sb.toString();
        }
    }
    
    public static abstract class FilterBuilder {
        protected FilterSource _filterSource;
        
        public void setFilterSource(FilterSource filterSource) {
            _filterSource = filterSource;
        }

        public abstract Filter getFilter(Map<String, Object> requestParams) throws GeneralException;
    }
    
    private static class BasicFilterBuilder extends FilterBuilder {
        public BasicFilterBuilder() {}
        
        public Filter getFilter(Map<String, Object> requestParams) {
            return (Filter) _filterSource.getFilter();
        }
    }
    
    private static class TemplateFilterBuilder extends FilterBuilder {
        public TemplateFilterBuilder() {}
        
        public Filter getFilter(Map<String, Object> requestParams) throws GeneralException {
            FilterTemplate template = _filterSource.getFilterTemplate();
            List<String> arguments = template.getFilterParameters();
            Map <String, Object> parameters = new HashMap<String, Object>();
            if (arguments != null && !arguments.isEmpty()) {
                for (String argument: arguments) {
                    if (requestParams.containsKey(argument)) {
                        parameters.put(argument, requestParams.get(argument));
                    }
                }
            }
            
            String filterString = template.getFilterString();
            if (filterString == null || filterString.trim().length() == 0) {
                return null; // In case they never specified a FilterString at all
            }
            String filterStringToCompile = VelocityUtil.render(filterString, parameters, null, null);
            if (filterStringToCompile.trim().length() == 0) {
                return null;
            }

            filterStringToCompile = unescapeBackslashes(filterStringToCompile);
            Filter compiledFilter = Filter.compile(filterStringToCompile);
            return compiledFilter;
        }
    }
    
    private List<QueryOptions.Ordering> getOrderings() {
        List<QueryOptions.Ordering> orderings = new ArrayList<QueryOptions.Ordering>();
        if (_orderBy != null && !_orderBy.isEmpty()) {
            for (String orderBy : _orderBy) {
                QueryOptions.Ordering ordering = new QueryOptions.Ordering(orderBy, _order == Order.Ascending);
                orderings.add(ordering);
            }
        }
        
        return orderings;
    }

    /**
     * Returns a string with escaped backslashes
     *
     * @param s
     * @return escaped backslash string
     */
    public static String unescapeBackslashes(String s) {
        String unescapedFilter = "";
        unescapedFilter = StringEscapeUtils.unescapeEcmaScript(s);
        if (unescapedFilter.contains("\\")) {
            unescapedFilter = unescapedFilter.replace("\\", "\\\\");
        }
        return unescapedFilter;
    }
}
