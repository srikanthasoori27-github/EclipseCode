/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Defines the composition of a composite application and the relationship between the composite
 * and it's tier applications. ThisSailPoint.connector.DefaultCompositeConnector,
 * which is our base, out of the box connector for composites. 
 *
 * NOTE: CompositeConnector implementations can completely custom to a customer,
 * so it should not be assumed that a composite application will have this definition.
 */
@XMLClass
public class CompositeDefinition extends AbstractXmlObject {

    /**
     * Name of the primary tier application in this composite.
     */
    private String primaryTier;

    /**
     * Name of a rule used to generate composite links for a given identity.
     * If this rule is set, any correlation definitions on the tiers will be
     * ignored.
     *
     * This should be a rule of type SailPoint.Rule.Type.CompositeAccount.
     */
    private String accountRule;


    /**
     * Rule which converts an abstract provisioning plan on a composite into
     * a concrete plan composed of remediations on the tier applications. This
     * should be a rule of type SailPoint.Rule.Type.CompositeRemediation.
     */
    private String remediationRule;


    /**
     * Tier applications which make up this composite.
     */
    private List<Tier> tiers;

    /**
     * Default constructor.
     */
    public CompositeDefinition() {

    }

    /**
     * Retrieve a tier definition by application name.
     * @param appName Tier application name
     * @return Matching Tier object, or null.
     */
    public Tier getTierByAppName(String appName){
        if (appName != null && tiers != null){
            for (Tier tier : tiers){
                if (appName.equals(tier.getApplication()))
                    return tier;
            }
        }
        return null;
    }

    /**
     * List of the names of the tier applications in this composite.
     * @return Non-null List of app names.
     */
    @JsonIgnore
    public List<String> getTierAppList(){
        List<String> apps = new ArrayList<String>();
        for(CompositeDefinition.Tier tier : Util.iterate(getTiers())) {
            apps.add(tier.getApplication());
        }
        return apps;
    }

    /**
     * Name of a rule used to generate composite links for a given identity.
     * If this rule is set, any correlation definitions on the tiers will be
     * ignored.
     *
     * This should be a rule of type SailPoint.Rule.Type.CompositeAccount.
     */
    public String getAccountRule() {
        return accountRule;
    }

    @XMLProperty
    public void setAccountRule(String accountRule) {
        this.accountRule = accountRule;
    }

    public boolean hasAccountRule(){
        return accountRule != null && !"".equals(accountRule);
    }

    /**
     * Name of the primary tier application in this composite.
     */
    public String getPrimaryTier() {
        return primaryTier;
    }

    @XMLProperty
    public void setPrimaryTier(String primaryTier) {
        this.primaryTier = primaryTier;
    }

    /**
     * Tier applications which make up this composite.
     */
    public List<Tier> getTiers() {
        return tiers;
    }

    public void addTier(Tier tier){
        if (tiers==null)
            tiers = new ArrayList<Tier>();
        tiers.add(tier);
    }

    @XMLProperty(mode= SerializationMode.LIST,xmlname="Tiers")
    public void setTiers(List<Tier> tiers) {
        this.tiers = tiers;
    }

    /**
     * Rule which converts an abstract provisioning plan on a composite into
     * a concrete plan composed of remediations on the tier applications. This
     * should be a rule of type SailPoint.Rule.Type.CompositeRemediation.
     */
    public String getRemediationRule() {
        return remediationRule;
    }

    @XMLProperty
    public void setRemediationRule(String remediationRule) {
        this.remediationRule = remediationRule;
    }

    /**
     * Tier application within a composite. Other than just indicating
     * which apps are part of a composite, it also defines how to
     * correlate a given link on a tier to a link on the primary tier app.
     */
    @XMLClass
    public static class Tier extends AbstractXmlObject{

        /**
         * The tier application name.
         */
        private String application;

        /**
         * Name of rule which correlates a link on the composite's
         * primary tier to the tier referenced by this Tier object.
         * This should be a rule of type SailPoint.Rule.Type.CompositeTierCorrelation.
         */
        private String correlationRule;

        /**
         * Mapping of attributes that can be used to correlate a tier link
         * to a link on the primary tier. Keys are the tier attribute name,
         * values are the composite attribute name.
         */
        private Map<String, String> correlationMap;

        /**
         * An IdentitySelector that will be used to match tier's against Identities.
         */
        private IdentitySelector _selector;

        public Tier() {
        }

        public Tier(String application, String correlationRule, Map<String, String> correlationMap) {
            this.application = application;
            this.correlationMap = correlationMap;
            this.correlationRule = correlationRule;
        }

        public String getApplication() {
            return application;
        }

        @XMLProperty
        public void setApplication(String application) {
            this.application = application;
        }

        public Map<String, String> getCorrelationMap() {
            return correlationMap;
        }

        @XMLProperty
        public void setCorrelationMap(Map<String, String> correlationMap) {
            this.correlationMap = correlationMap;
        }

        public String getCorrelationRule() {
            return correlationRule;
        }

        public boolean hasCorrelationRule(){
            return correlationRule != null && !"".equals(correlationRule);
        }

        @XMLProperty
        public void setCorrelationRule(String correlationRule) {
            this.correlationRule = correlationRule;
        }

        @XMLProperty(mode=SerializationMode.INLINE)
        @JsonIgnore
        public IdentitySelector getIdentitySelector() {
            return _selector;
        }

        public void setIdentitySelector(IdentitySelector selector) {
            _selector = selector;
        }
    }
}
