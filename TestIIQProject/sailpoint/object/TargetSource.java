/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@XMLClass
public class TargetSource extends SailPointObject implements Cloneable {
    
    private static final long serialVersionUID = 1L;
    
    String _name;
    
    /**
     * The class name that implements fetching data from this datasource.
     */
    String _collector;


    /**
     * Configuration that is supported for this datasource. 
     */
    Attributes<String,Object> _config;

    /**
     * List of Unstructured targets
     */
    private List<String> _targets;

    /**
     * This is a Rule that can be invoked which will turn the event
     * subject into a native thing that can be used to correlate back 
     * into some Identity.
     */
    Rule _correlationRule;
    
    /**
     * This is a Rule that can be invoked which will normalize the 
     * collected data on the collector side.
     */
    Rule _transformationRule;

    /**
     * A last chance rule to set anything before a target gets persisted.
     */
    Rule _creationRule;

    /**
     * Rule to set anything on refresh
     */
    Rule _refreshRule;

    /**
     * The last time we refreshed the activity data from the underlying
     * datasource.
     */
    Date _lastRefresh;

    //////////////////////////////////////////////////////////////////////
    //
    // Keys in the TargetSource map model
    //
    //////////////////////////////////////////////////////////////////////
    public static final String ATT_PROVISIONING_OVERRIDDEN = "provisioningOverridden";
    public static final String ATT_TARGET_COLLECTOR = "targetCollector";
    public static final String ATT_SEARCH_ACCOUNT = "searchAttrForAcct";
    public static final String ATT_SEARCH_GROUP = "searchAttrForGrp";
    public static final String ATT_PROVISIONING_DISABLED = "disableProvisioning";
    public static final String APP_TYPE = "appType";
    public static final String TARGET_TYPE = "type";
    public static final String AD_APP = "Active Directory - Direct";
    public static final String SHAREPOINT_COLLECTOR = "SharePoint Collector";
    public static final String WINLOCAL_COLLECTOR = "Windows FileShare Collector";
    public static final String AD_ACCOUNT_SEARCH_ATT = "msDS-PrincipalName";
    public static final String AD_GROUP_SEARCH_ATT = "objectSid";
    public static final String SHAREPOINT_VERSION = "SPVersion";
    public static final String SHAREPOINT_NEW_VER  = "2013";
    public static final String ATT_TARGET_HOSTS = "targetHosts";
    public static final String ATT_HIERARCHY = "hierarchyAttribute";

    //////////////////////////////////////////////////////////////////////
    //
    // Misc Constants
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * This represents an overridden provisioning action in which a Manual
     * Work Item needs to be created.
     */
    public static final String ARG_MANUAL_WORK_ITEM = "Manual Work Item";


    public TargetSource() {
    }
    
  
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getConfiguration() {
        return _config;
    }
    
    /**
    /**
     * This function sets target source configuration
     * This function also the sets default search attribute of the target source based on the target
     * collector type. These search attributes will be use for cross application provisioning.
     * Collector types - SharePoint 2007/2010, Windows FileShare are configured for
     * The AD application then search attribute for account & group is the 'msDS-PrincipalName'.
     * For SharePoint 2013 search attribute for the group is 'objectSid'.
     */
    public void setConfiguration(Attributes<String,Object> config) {

        // jsl - this is a Hibernate property setter, you ordinarilly don't have side effects
        // I guess this is doing a dynamic upgrade?  Added a null check after starting to
        // use sparse TargetSources in the unit tests
        if (config != null && 
            config.containsKey(APP_TYPE)
            && config.getString(APP_TYPE).equalsIgnoreCase(AD_APP)) {
            
            //Checking if search attributes are already configured for this target source.
            if (!config.containsKey(ATT_SEARCH_ACCOUNT)
                    && (!config.containsKey(ATT_SEARCH_GROUP))) {
                if (config.containsKey(TARGET_TYPE)
                        && config.get(TARGET_TYPE) != null) {
                    config.put(ATT_SEARCH_ACCOUNT, AD_ACCOUNT_SEARCH_ATT);
                    
                    // checking if target collector type is SharePoint Collector & it's version 
                    if (config.getString(TARGET_TYPE).equalsIgnoreCase(
                            SHAREPOINT_COLLECTOR)
                            && config.getString(SHAREPOINT_VERSION)
                                    .equalsIgnoreCase(SHAREPOINT_NEW_VER)) {
                        config.put(ATT_SEARCH_GROUP, AD_GROUP_SEARCH_ATT);
                    } else {
                        config.put(ATT_SEARCH_GROUP, AD_ACCOUNT_SEARCH_ATT);
                    }
                }
            }
        }
        _config = config;
    }

    /**
     * Sets a named configuration attribute.
     * Existing values will be overwritten
     */
    public void setAttribute(String name, Object value) {
        if (value == null) {
            if (_config != null)
                _config.remove(name);
        }
        else {
            if (_config == null)
                _config = new Attributes<String,Object>();
            _config.put(name, value);
        }
    }

    /**
     * Sets a named configuration attribute.
     * Existing values will be overwritten
     */
    public Object getAttributeValue(String name) {
        return _config.get(name);
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getCorrelationRule() {
        return _correlationRule;
    }
    
    public void setCorrelationRule(Rule rule) {
        _correlationRule = rule;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getCreationRule() {
        return _creationRule;
    }

    public void setCreationRule(Rule rule) {
        _creationRule = rule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getRefreshRule() {
        return _refreshRule;
    }

    public void setRefreshRule(Rule r) {
        _refreshRule = r;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getTransformationRule() {
        return _transformationRule;
    }

    public void setTransformationRule(Rule rule) {
        _transformationRule = rule;
    }

    public Date getLastRefresh() {
        return _lastRefresh;
    }

    @XMLProperty
    public void setLastRefresh(Date date) {
        _lastRefresh = date;
    }

    @XMLProperty
    public String getCollector() {
        return _collector;
    }

    public void setCollector(String collector) {
        _collector = collector;
    }

    /**
     * List of unstructured targets
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getTargets() {
        if (_targets == null) {
            _targets = new ArrayList<String>();
        }
        
        return _targets;
    }
    
    public void setTargets(List<String> targets) {
        _targets = targets;
    }
    
    public void addTarget(String target) {
        if (target != null) {
            if (_targets == null) {
                _targets = new ArrayList<String>();
            }
            
            // Only one target allowed
            if (!_targets.contains(target)) {
                _targets.add(target);
            }
        }
    }
    
    public void removeTarget(String target) {
        if (target != null && _targets != null) {
            // There should be only one, but let's play it safe
            while (_targets.contains(target)) {
                _targets.remove(target);
            }
        }
    }

    /**
     * Clone this object.
     * <p>
     * For the Cloneable interface.
     */
    public Object clone() {
        Object obj = null;
        try
        {
            // TODO: implement a deep copy!!
            obj = super.clone();
        }
        catch (CloneNotSupportedException e)
        {
            // Won't happen.
        }
        return obj;
    }

    /**
     * is provisioning overridden ?
     */
    public boolean isProvisioningOverridden() {
        return Util.getBoolean(_config, ATT_PROVISIONING_OVERRIDDEN);
    }

    /**
     * get overriding provisioning action
     */
    public String getOverridingAction() {
        return isProvisioningOverridden() ? Util.getString(_config, ATT_TARGET_COLLECTOR) : null;
    }

    /**
     * True if the collector itself supports provisioning, or if provisioning is overridden to a collector that does
     * @return whether the Collector supports provisioning
     */
    public boolean supportsProvisioning() {
        if (Util.getBoolean(_config, ATT_PROVISIONING_DISABLED)) {
            //If Provisioning is disabled, return false
            return false;
        } else {
            return (isProvisioningOverridden() && !Util.nullSafeEq(getOverridingAction(), ARG_MANUAL_WORK_ITEM));
        }
    }

    /**
     * @exclude
     * Traverse the composition hierarchy to cause everything to be loaded
     * from the persistent store.  This is part of a performance enhancement
     * for the Aggregator, letting it clear the Hibernate cache regularly
     * but still hang onto fully fetched Application objects from earlier
     * sessions.
     *
     * Note that we're not attempting to load any associated Identity
     * objects such as the owner or the remediators.  These just pull
     * in way too much stuff and we don't need them for aggregation.
     */
    public void load() {

        if (_correlationRule != null)
            _correlationRule.load();

        if (_transformationRule != null)
            _transformationRule.load();
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitTargetSource(this);
    }

}
