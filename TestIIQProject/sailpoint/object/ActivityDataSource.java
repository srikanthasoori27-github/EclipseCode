/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class ActivityDataSource extends SailPointObject implements Cloneable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * The classname that implements fetching data from this datasource.
     */
    String _collector;

    String _type;

    /**
     * Configuration that is supported for this datasource. 
     */
    Attributes<String,Object> _config;

    /**
     * List of Activity targets
     */
    private List<String> _targets;

    /**
     * This is a Rule that can be invoked that will turn the event
     * subject into a native thing that can be usee to correlate back 
     * into some Identity.
     *
     */
    Rule _correlationRule;
    
    /**
     * This is a Rule that can be invoked which will normalize the 
     * collected data.
     *
     */
    Rule _transformationRule;

    /**
     * The last time the activity data was refreshed from the underlying
     * datasource.
     */
    Date _lastRefresh;

    public ActivityDataSource() {
    }

    @XMLProperty
    public String getType() {
        return _type;
    }

    public void setType(String type) {
        _type = type;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getConfiguration() {
        return _config;
    }

    public void setConfiguration(Attributes<String,Object> config) {
        _config = config;
    }

    /**
     * Sets a named configuration attribute.
     * Existing values are overwritten
     */
    public void setAttribute(String name, Object value) {
        _config.put(name,value);
    }

    /**
     * Sets a named configuration attribute.
     * Existing values are overwritten
     */
    public Object getAttributeValue(String name) {
        return _config.get(name);
    }

    /**
     * Rule that can be invoked which will turn the event
     * subject into a native thing that can be used to correlate back 
     * into some Identity.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getCorrelationRule() {
        return _correlationRule;
    }
    
    public void setCorrelationRule(Rule rule) {
        _correlationRule = rule;
    }
    
    /**
     * Rule that can be invoked which will normalize the 
     * collected data.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getTransformationRule() {
        return _transformationRule;
    }

    public void setTransformationRule(Rule rule) {
        _transformationRule = rule;
    }

    /**
     * The last time the activity data was refresed from the underlying
     * datasource.
     */
    public Date getLastRefresh() {
        return _lastRefresh;
    }

    @XMLProperty
    public void setLastRefresh(Date date) {
        _lastRefresh = date;
    }

    /**
     * The classname that implements fetching data from this datasource.
     */
    @XMLProperty
    public String getCollector() {
        return _collector;
    }

    public void setCollector(String collector) {
        _collector = collector;
    }
    
    /**
     * List of Activity targets
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
     * Traverse the composition hierarchy to cause everything to be loaded
     * from the persistent store.  
     *
     * @ignore
     * This is part of a performance enhancement
     * for the Aggregator, letting it clear the Hibernate cache regularly
     * but still hang onto fully fetched Application objects from earlier
     * sessions.
     *
     * Note that there is no attempt to load any associated Identity
     * objects such as the owner or the remediators.  These pull
     * in way too much stuff that is not needed for aggregation.
     */
    public void load() {

        if (_correlationRule != null)
            _correlationRule.load();

        if (_transformationRule != null)
            _transformationRule.load();
    }


}
