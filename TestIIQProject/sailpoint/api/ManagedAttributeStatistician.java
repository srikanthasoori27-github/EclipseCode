/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.AbstractLocalizableException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * The ManagedAttributeStatistician calculates statistics about managed
 * attribute usage within a population of identities.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ManagedAttributeStatistician {

    private static final Log log = LogFactory.getLog(ManagedAttributeStatistician.class);
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The results of calculating managed attribute statistics for a population
     * of identities.
     */
    public static class PopulationStats {
        
        private int maxAttributes;
        private Map<String,Integer> countsById;
        private int totalIdentities;
        private int totalAttributes;
        private Map<String,Integer> entitlementDataStats;

        public PopulationStats(int maxAttributes) {
            this.maxAttributes = maxAttributes;
            this.countsById = new HashMap<String,Integer>();
            this.entitlementDataStats = new HashMap<String,Integer>();
        }
        
        public void setTotalIdentities(int totalIdentities) {
            this.totalIdentities = totalIdentities;
        }
        
        private String getEntitlementKey(Application application, String attribute, String value) {
            StringBuilder buff = new StringBuilder();
            buff.append(application.getId()).append("::");
            buff.append(attribute).append("::");
            buff.append(value);
            return buff.toString();
        }

        
        // Private since only the statistician should change this.
        private void foundAttribute(ManagedAttribute ma)
            throws SizeExceededException {
            
            String key = getEntitlementKey(ma.getApplication(), ma.getAttribute(), ma.getValue());
            Integer count = this.entitlementDataStats.get(key);
            if (count == null) {
                count = 0;
            }
            
            this.countsById.put(ma.getId(), count);
            
            // Fail if too many entitlements.
            this.totalAttributes += count;
            if (this.totalAttributes > this.maxAttributes) {
                throw new SizeExceededException(MessageKeys.ERR_MANAGED_ATTRIBUTE_SEARCH_TOO_MANY_ATTRIBUTES, this.maxAttributes);
            }
        }

        /**
         * Return the IDs of the ManagedAttributes encountered on the identities
         * in this population.
         */
        public Collection<String> getManagedAttributeIds() {
            return this.countsById.keySet();
        }

        /**
         * Return the number of identities in the population that have the given
         * ManagedAttribute.
         */
        public int getCount(String managedAttributeId) {
            Integer count = this.countsById.get(managedAttributeId);
            return (null != count) ? count : 0;
        }

        /**
         * Return the total number of identities processed in the population.
         */
        public int getTotalIdentities() {
            return this.totalIdentities;
        }

        public void foundEntitlementData(Application application,
                String attribute, String value) {
            
            String entitlementKey = getEntitlementKey(application, attribute, value);
            Integer count = entitlementDataStats.get(entitlementKey);
            if (count == null) {
                count = 0;
            }
            count++;
            entitlementDataStats.put(entitlementKey, count);
        }
    }

    /**
     * A handy sub-class to assemble entitlement attribute data and make it better hashable.
     * @author trey.kirk
     *
     */
    private static class PopulationEntitlementData {

        private Application application;
        private String attribute;
        private String value;

        public PopulationEntitlementData(Application application, String attribute, String value) {
            this.application = application;
            this.attribute = attribute;
            this.value = value;
        }
        
        public Application getApplication() {
            return this.application;
        }
        
        public String getAttribute() {
            return this.attribute;
        }
        
        public String getValue() {
            return this.value;
        }
        
        @Override
        public int hashCode() {
            // A nice code Bloch for hashing
            int hash = 17;
            hash = 37 * hash + (this.application != null ? this.application.hashCode() : 0);
            hash = 37 * hash + (this.attribute != null ? this.attribute.hashCode() : 0);
            hash = 37 * hash + (this.value != null ? this.value.hashCode() : 0);
            return hash;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof PopulationEntitlementData)) {
                return false;
            }
            PopulationEntitlementData that = (PopulationEntitlementData) obj;
            boolean isEquals = Util.nullSafeEq(this.value, that.getValue()) &&
                    Util.nullSafeEq(this.attribute, that.getAttribute()) &&
                    Util.nullSafeEq(this.application, that.getApplication());
            return isEquals;
        }
    }

    /**
     * An exception that indicates that too many identities or entitlements were
     * found when calculating managed attribute stats.
     */
    @SuppressWarnings("serial")
    public static class SizeExceededException extends AbstractLocalizableException {

        /**
         * Constructor.
         * 
         * @param  msgKey  The message key to use.
         * @param  size    The max size.
         */
        public SizeExceededException(String msgKey, int size) {
            super(new Message(msgKey, size));
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public ManagedAttributeStatistician(SailPointContext context) {
        this.context = context;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Calculate and return the managed attribute stats for the population that
     * is matched by the given filter.
     * 
     * @param  identityFilters  The filters to restrict the population.
     * @param  appFilter        The possibly-null filter that restricts which
     *                          apps to include.
     * @param  managedAttrFilter The possibly-null filter that restricts which
     *                           managed attributes to include.
     * 
     * @return The stats for the given population.
     * 
     * @throws SizeExceededException  If too many identities or entitlements
     *   meet the given filters.
     */
    public PopulationStats crunchPopulationStats(List<Filter> identityFilters,
            Filter appFilter,
            Filter managedAttrFilter)
        throws GeneralException, SizeExceededException {

        int maxAttrs = getMax(Configuration.LCM_MANAGED_ATTRIBUTE_STATS_MAX_ATTRIBUTES);

        PopulationStats stats = new PopulationStats(maxAttrs);

        QueryOptions identityQo = new QueryOptions();
        identityQo.setDistinct(true);

        // Restrict to identities that have links on the given application filters.
        if (null != appFilter) {
            // Restrict to only include links that match the given app filter.
            identityQo.add(Filter.subquery("links.application.id", Application.class, "id", appFilter));
        }

        // Add Identity filter to Link QueryOpts
        if (!Util.isEmpty(identityFilters)) {
            identityQo.add(Filter.and(identityFilters));
        }

        // Calculate the number of identities in the result set
        int count = this.context.countObjects(Identity.class, identityQo);
        stats.setTotalIdentities(count);

        
        // I like a HashSet for this as it filters duplicates quickly when using well 
        // distributed hash values
        Set<PopulationEntitlementData> entitlementData = getEntitlementData(stats, identityFilters, appFilter);
        // Be a good hibernate citizen
        this.context.decache();

        // Now that we've got our flattened list of entitlement data, go get the actual attributes.
        // Since we've already filtered duplicate data out, the MAs returned should mostly, if not
        // completely, all be unique.
        crunchAttributes(stats, entitlementData, managedAttrFilter);
        return stats;
    }
    
    private Set<PopulationEntitlementData> getEntitlementData(PopulationStats stats, List<Filter> identityFilters, Filter appFilter) throws GeneralException {
        Set<PopulationEntitlementData> entitlementData = new HashSet<PopulationEntitlementData>();
        QueryOptions opts = new QueryOptions();
        opts.add(Filter.subquery("identity", Identity.class, "id", Filter.and(identityFilters)));
        opts.add(Filter.subquery("application", Application.class, "id", appFilter));
        opts.setDistinct(true);
        
        // Include identity.id in results to ensure we only get one result per identity entitlement per identity
        Iterator<Object[]> results = context.search(IdentityEntitlement.class, opts, "application,name,value,identity.id");
        while (results != null && results.hasNext()) {
            Object[] result = results.next();
            Application application = (Application)result[0];
            String attribute = (String)result[1];
            String value = (String)result[2];
            PopulationEntitlementData entitlementDatum = new PopulationEntitlementData(application, attribute, value);
            entitlementData.add(entitlementDatum);
            stats.foundEntitlementData(application, attribute, value);
        }
        return entitlementData;
    }

    /**
     * Return the system config value for the given key or -1.
     */
    private int getMax(String key) throws GeneralException {
        return this.context.getConfiguration().getAttributes().getInt(key, -1);
    }
    
    private void crunchAttributes(PopulationStats stats, 
            Collection<PopulationEntitlementData> entDatum,
            Filter managedAttrFilter) 
        throws GeneralException, SizeExceededException {
        Set<String> foundIds = new HashSet<String>();
        if (null != entDatum) {
            for (PopulationEntitlementData entData : entDatum) {
                ManagedAttribute ma = getManagedAttribute(entData.getApplication(), 
                        entData.getAttribute(), entData.getValue(), managedAttrFilter);
                if (ma != null) {
                    // If it's added to the set, it's unique
                    boolean added = foundIds.add(ma.getId());
                    if (added) {
                        stats.foundAttribute(ma);
                    }
                }
            }
        }
    }
    
    /** 
     * Get the managed attribute, including the passed-in filter 
     */
    private ManagedAttribute getManagedAttribute(Application app, String attr, String value, Filter managedAttrFilter) 
    throws GeneralException {
        ManagedAttribute ma = null;
        
        if (managedAttrFilter == null) {
            //TODO: Don't worry about type
             ma = ManagedAttributer.get(this.context, app.getId(), false, attr, value);
            if (ma == null) {
                if (log.isInfoEnabled()) {
                    log.info("Could not find managed attribute - " + app.getName() + "; " + attr + "; " + value);
                }
            }
        } else {
            // Copied from ManagedAttributeCache.getObjects(), dont want to change that class to allow additional filter
            // !! jsl - this probably needs to understand multiple group schemas now?
            // would be nice to have this in ManagedAttributer so all the case rules could be in one place
            List<Filter> filters = new ArrayList<Filter>();
            filters.add(Filter.eq("application", app));
            filters.add(Filter.ne("type", ManagedAttribute.Type.Permission.name()));
            filters.add(Filter.ignoreCase(Filter.eq("attribute", attr)));
            filters.add(Filter.ignoreCase(Filter.eq("value", value)));
            filters.add(managedAttrFilter);
            
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.and(filters));
            
            List<ManagedAttribute> maList = context.getObjects(ManagedAttribute.class, qo);
            if (maList.size() > 0) {
                ma = maList.get(0);
            }
        }
        
        return ma;
    }
}

