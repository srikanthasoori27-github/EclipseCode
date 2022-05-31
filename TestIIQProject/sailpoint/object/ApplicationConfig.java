/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * @author Bernie Margolis
 * 
 * NOTE: ApplicationEntitlementWeights are an attribute
 * under the ScoreDefinition for EntitlementScorer.  This makes sense
 * if you think that ApplicationConfig should only have things that
 * are global to all Scorers and ApplicationEntitlementWeights
 * are only relevant for EntitlementScorer.
 *
 * But it is also nice to keep all application score config in 
 * one place. For a future scoring algorithm
 * that also wanted entitlement weights that would then have
 * to be duplicated.  The downside of putting them here is that you
 * could not have different Scorers with different ideas about weight
 * but that seems unlikely given how hard it is to define these.
 *
 * It is better to merge ApplicationEntitlementWeights with this
 * class.  One could continue to be supported as a Scorer
 * attribute in case you want to override the defaults, but there
 * would be no UI for this.
 *
 * - jsl
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

/**
 * This class holds data related to the referenced application's impact on risk scoring
 */
public class ApplicationConfig extends AbstractXmlObject {

    private static final long serialVersionUID = -1100335241329859524L;

    Application _application;
    String _activityMonitoringFactor;
    
    public ApplicationConfig() {
        _application = null;
        _activityMonitoringFactor = null;
    }
    
    public ApplicationConfig(Application app) {
        _application = app;
        _activityMonitoringFactor = "0";
    }
    
    @XMLProperty
    public String getActivityMonitoringFactor() {
        return _activityMonitoringFactor;
    }

    public void setActivityMonitoringFactor(String activityMonitoringFactor) {
        this._activityMonitoringFactor = activityMonitoringFactor;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE, xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application application) {
        this._application = application;
    }
    
    /**
     * The UI displays these factors as percentages.  This method returns the factor in that format.
     */
    public String getUiActivityMonitoringFactor() {
        float factor = Float.parseFloat(_activityMonitoringFactor);
        int uiFactor = new Float((1.0 - factor) * 100).intValue(); 
        
        return String.valueOf(uiFactor);
    }
    
    /**
     * The UI displays these factors as percentages.  This method takes a UI input in that format
     * and converts it.
     */
    public void setUiActivityMonitoringFactor(String uiFactor) {
        float factor = Float.parseFloat(uiFactor);
        this._activityMonitoringFactor = String.valueOf(1.0 - (factor / 100.0));
    }
}
