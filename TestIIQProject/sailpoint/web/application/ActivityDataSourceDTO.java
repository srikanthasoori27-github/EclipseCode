/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.application;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.WebUtil;

public class ActivityDataSourceDTO {
    private static final Log log = LogFactory.getLog(ActivityDataSourceDTO.class);
    private String id;
    private String name;
    private String description;
    private String correlationRule;
    private String transformationRule;
    private String collector;
    private String type;
    private Attributes<String,Object> configuration;
    private List<String> targets;
    private Date modified;
    
    public ActivityDataSourceDTO() {
        // Need to have the configuration map there or the page misbehaves
        configuration = new Attributes<String, Object>();
        modified = new Date();
    }
    
    public ActivityDataSourceDTO(ActivityDataSource ads) {
        if (ads == null)
            throw new IllegalArgumentException("An attempt was made to create an ActivitityDataSourceDTO with a null ActivityDataSource");
        
        this.id = ads.getId();

        //bug-IIQBUGS-102  - XSS Vulnerability with name attribute,
        //calling stripHTML will wipe out the content and will avoid the XSS
        setName(ads.getName());

        this.description = ads.getDescription();
        this.collector = ads.getCollector();
        this.type = ads.getType();
        this.modified = ads.getModified();
        this.targets = ads.getTargets();
        this.configuration = new Attributes<String, Object>();
        
        Rule correlationRuleObj = ads.getCorrelationRule();
        if (correlationRuleObj != null) {
            this.correlationRule = correlationRuleObj.getName();
        }
        
        Rule transformationRuleObj = ads.getTransformationRule();
        if (transformationRuleObj != null) {
            this.transformationRule = transformationRuleObj.getName();
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
        //bug-IIQBUGS-102  - XSS Vulnerability with name attribute,
        //calling safeHTML will wipe out the content and will avoid the XSS
        this.name = WebUtil.stripHTML(name,true);
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
    
    public void setTransformationRule(String name) {
        transformationRule = name;
    }
    
    public String getTransformationRule() {
        return transformationRule;
    }
    
    public void setCollector(String collector) {
        this.collector = collector;
    }
    
    public String getCollector() {
        return collector;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
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
        else {
            if (type != null && type.equals(Configuration.CEF_LOG_FILE_COLLECTOR_TYPE)) {
                this.configuration = configuration;
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
     * Utility method that returns a current ActivityDataSource with all of the DTO's
     * values applied
     * @return Current ActivityDataSource with all of the DTO's values applied
     */
    public void updateActivityDataSource(SailPointContext ctx, ActivityDataSource adsToPopulate) throws GeneralException {        
        adsToPopulate.setName(name);
        adsToPopulate.setType(type);
        adsToPopulate.setDescription(description);
        adsToPopulate.setCollector(collector);
        adsToPopulate.setCorrelationRule(ctx.getObjectByName(Rule.class, correlationRule));
        adsToPopulate.setTransformationRule(ctx.getObjectByName(Rule.class, transformationRule));
        adsToPopulate.setConfiguration(configuration);
        adsToPopulate.setTargets(targets);
    }
    
    /**
     * Utility method that populates a mocked up ActivityDataSource object without adding rules.
     * This is helpful because it lets of fool the ActivityCollectorFactory into giving us a 
     * collector.  That is necessary because of the misguided implementation that cleans up 
     * configuration values.  We need to fix that eventually, but until then we're stuck 
     * with this.
     * @return
     */
    public ActivityDataSource buildPartialActivityDataSource() {
        ActivityDataSource returnedAds = new ActivityDataSource();
        returnedAds.setName(name);
        returnedAds.setType(type);
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
                SailPointFactory.getCurrentContext().getObjectByName(Configuration.class, "ActivityCollectorConfigPageRegistry");
            configPage = configPageRegistry.getString(type);
        } catch (GeneralException e) {
            log.error("The ActivityCollectorConfigPageRegistry is not available right now.", e);
        }
        
        if (configPage == null) {
            configPage = "defaultConfig.xhtml";
        }
        
        return configPage;

    }
}
