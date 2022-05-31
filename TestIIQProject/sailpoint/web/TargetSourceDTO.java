/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.TargetSource;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class TargetSourceDTO {
    private static final Log log = LogFactory.getLog(TargetSourceDTO.class);
    private String id;
    private String name;
    private String description;
    private String correlationRule;
    private String creationRule;
    private String refreshRule;
    private String collector;
    private String type;
    private Attributes<String,Object> configuration;
    private List<String> targets;
    private Date modified;

    public TargetSourceDTO() {
        // Need to have the configuration map there or the page misbehaves
        configuration = new Attributes<String, Object>();
        modified = new Date();
    }
    
    public TargetSourceDTO(TargetSource ads) {
        if (ads == null)
            throw new IllegalArgumentException("An attempt was made to create an TargetSourceDTO with a null TargetSource");
        
        this.id = ads.getId();
        this.name = ads.getName();
        this.description = ads.getDescription();
        this.collector = ads.getCollector();
        this.modified = ads.getModified();
        this.targets = ads.getTargets();
        this.configuration = new Attributes<String, Object>();
        
        Rule correlationRuleObj = ads.getCorrelationRule();
        if (correlationRuleObj != null) {
            this.correlationRule = correlationRuleObj.getName();
        }
        
        Rule creationRule = ads.getCreationRule();
        if (creationRule != null) {
            this.creationRule = creationRule.getName();
        }

        Rule refreshRule = ads.getRefreshRule();
        if (refreshRule != null) {
            this.refreshRule = refreshRule.getName();
        }
        
        Attributes<String, Object> masterConfig = ads.getConfiguration();
        if (masterConfig != null) {
            configuration.putAll(masterConfig);
        }    
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public void setCorrelationRule(String name) {
        correlationRule = name;
    }
    
    public String getCorrelationRule() {
        return correlationRule;
    }
    
    public void setCreationRule(String name) {
        creationRule = name;
    }
    
    public String getCreationRule() {
        return creationRule;
    }

    public void setRefreshRule(String name) { refreshRule = name; }

    public String getRefreshRule() { return refreshRule; }
    
    public void setCollector(String collector) {
        this.collector = collector;
    }
    
    public String getCollector() {
        return collector;
    }
    
    public void setType(String type) {
        this.type = type;
        configuration.put("type", type); 
    }
    
    public String getType() {
        if(type == null) {
            if(configuration != null && configuration.containsKey("type"))
                type = configuration.getString("type");
        }
        return type;
    }
    public void setAppType(String type) {
         configuration.put("appType", type); 
    }
    
    public Attributes<String,Object> getConfiguration() {
        return configuration;
    }
    
    public Date getModified() {
        return modified;
    }
    
    public void setModified(Date modified) {
        this.modified = modified;
    }
    
    public void setConfiguration(Attributes<String, Object> configuration) {
        // Don't null out the configuration or we will break the JSF page... clear it instead
        if (configuration == null) {
            if (this.configuration == null) {
                // This should never be the case, but just to be sure...
                this.configuration = new Attributes<String, Object>();
            } else {
                this.configuration.clear();
            }
        }
    }
    
    public Object getAttributeValue(String attributeName) {
        Object attributeValue;
        
        if (attributeName == null)
            attributeValue = null;
        else
            attributeValue = configuration.get(attributeName);
        
        return attributeValue;
    }
    
    /**
     * Utility method that returns a current TargetSource with all of the DTO's
     * values applied
     * @return Current TargetSource with all of the DTO's values applied
     */
    public void updateTargetSource(SailPointContext ctx, TargetSource adsToPopulate) throws GeneralException {        
        adsToPopulate.setName(name);
        adsToPopulate.setDescription(description);
        adsToPopulate.setCollector(collector);
        adsToPopulate.setCorrelationRule(ctx.getObjectByName(Rule.class, correlationRule));
        adsToPopulate.setCreationRule(ctx.getObjectByName(Rule.class, creationRule));
        adsToPopulate.setRefreshRule(ctx.getObjectByName(Rule.class, refreshRule));
        adsToPopulate.setConfiguration(configuration);
        adsToPopulate.setTargets(targets);
    }
    
    /**
     * Utility method that populates a mocked up TargetSource object without adding rules.
     * This is helpful because it lets of fool the TargetCollectorFactory into giving us a 
     * collector.  That is necessary because of the misguided implementation that cleans up 
     * configuration values.  We need to fix that eventually, but until then we're stuck 
     * with this.
     * @return
     */
    public TargetSource buildPartialTargetSource() {
        TargetSource returnedAds = new TargetSource();
        returnedAds.setName(name);
        returnedAds.setCollector(collector);
        returnedAds.setConfiguration(configuration);
        return returnedAds;
    }
    
    public void addTarget(String target) {
        if (targets == null)
            targets = new ArrayList<String>();
        if (!targets.contains(target))
            targets.add(target);
    }
    
    public void removeTarget(String target) {
        if (targets != null)
            targets.remove(target);
    }
    
    public List<String> getTargets() {
        return targets;
    }
    
    public void setTargets(List<String> targets) {
        this.targets = targets;
    }
    
    public String getConfigPage() {
        String configPage = null;
        
        try {
            Configuration configPageRegistry = 
                SailPointFactory.getCurrentContext().getObjectByName(Configuration.class, "TargetCollectorRegistry");
            if (configPageRegistry != null) {
                Attributes attrs = configPageRegistry.getAttributes();
                //_collectorTypes = getCollectorTypebyAppType(attrs);
                if (attrs != null) {
                    for (Object key : attrs.keySet()) {
                        String keyVal = key.toString();
                        HashMap<String, Object> colConfigMap =(HashMap<String, Object>) attrs.get(key);
                        for (Object keyType : colConfigMap.keySet()) {                            
                            if(keyType.toString().equalsIgnoreCase("page")){
                                if(keyVal.equalsIgnoreCase(type)) {
                                    configPage = colConfigMap.get(keyType).toString();
                                    break;
                                }
                            }
                        }
                        if(configPage != null)
                            break;
                    }
                }
            }           
        } catch (GeneralException e) {
            log.error("The TargetCollectorConfigPageRegistry is not available right now.", e);
        }
        
        if (configPage == null) {
            configPage = "sharepointRWUnstructuredConfig.xhtml";
        }
        return configPage;

    }

    /**
     *  Get the Overriding Target collector used for provisioning.
     */
    public String getTargetCollector () {
        return (String) getAttributeValue(TargetSource.ATT_TARGET_COLLECTOR);
    }

    /**
     *  Set the Overriding Target collector used for provisioning.
     */
    public void setTargetCollector (String targetCollector) {
        configuration.put(TargetSource.ATT_TARGET_COLLECTOR, targetCollector);
    }

    /**
     *  Get the Override Default Provisioning flag.
     */
    public boolean isProvisioningOverridden () {
        Object provisioningOverridden = getAttributeValue(TargetSource.ATT_PROVISIONING_OVERRIDDEN);
        return (provisioningOverridden != null) ? (Boolean) provisioningOverridden : false;
    }

    /**
     *  Set the Override Default Provisioning flag.
     */
    public void setProvisioningOverridden (boolean provisioningOverridden) {
        configuration.put(TargetSource.ATT_PROVISIONING_OVERRIDDEN, provisioningOverridden);
    }

    public boolean isProvisioningDisabled() {
        return Util.getBoolean(configuration, TargetSource.ATT_PROVISIONING_DISABLED);
    }
}
