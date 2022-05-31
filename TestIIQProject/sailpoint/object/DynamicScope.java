/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Dynamic Scopes incorporate various existing ways of defining identity populations 
 * into a unified model.
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
@SuppressWarnings("serial")
@XMLClass
public class DynamicScope extends SailPointObject implements Cloneable {
    // private static final Log log = LogFactory.getLog(DynamicScope.class);
    
    //////////////////////////////////////////////////////////////////////
    //
    // Inner Classes
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * PopulationRequestAuthority is used to define the list of identities a user
     * can 'request for'. In the previous releases to version 6.4 this was defined 
     * in various configuration options but is now consolidated to this object.
     * 
     * Setting Configuration.LCM_REQUEST_CONTROLS_ALLOW_ALL to true in the map will  
     * ignore any other properties (Configuration.LCM_REQUEST_CONTROLS_DISABLE_SCOPING excluded)
     */
    public static class PopulationRequestAuthority extends AbstractXmlObject {
        
        /**
         * Match Configuration Object used to create the filter. 
         */
        public static class MatchConfig {
            private boolean matchAll;
            private boolean enableAttributeControl;
            private IdentityAttributeFilterControl identityAttributeFilterControl;
            private boolean enableSubordinateControl;
            private String subordinateOption;
            private int maxHierarchyDepth;
            private int maxHierarchyCount;
            private boolean enableCustomControl;
            private String customControl;

            /**
             * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY_OR_ALL. If set to true
             * an AND logical operation will be used to evaluate the combined attribute, subordinate and custom request controls. 
             * @return false (default) to OR conditions together
             */
            @XMLProperty
            public boolean isMatchAll() {
                return matchAll;
            }
            public void setMatchAll(boolean matchAll) {
                this.matchAll = matchAll;
            }
            
            /**
             * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_ENABLE_ATTRIBUTE_CONTROL
             * @return if attribute control is enabled
             */
            @XMLProperty
            public boolean isEnableAttributeControl() {
                return enableAttributeControl;
            }
            public void setEnableAttributeControl(boolean enableAttributeControl) {
                this.enableAttributeControl = enableAttributeControl;
            }
            
            /**
             * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_ATTRIBUTE_FILTER_CONTROL
             * @return filterControl object
             */
            @XMLProperty(mode=SerializationMode.UNQUALIFIED)
            public IdentityAttributeFilterControl getIdentityAttributeFilterControl() {
                return identityAttributeFilterControl;
            }
            public void setIdentityAttributeFilterControl(IdentityAttributeFilterControl identityAttributeFilterControl) {
                this.identityAttributeFilterControl = identityAttributeFilterControl;
            }
            
            /**
             * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_ENABLE_SUBORDINATE_CONTROL
             * @return is subordinate control enabled
             */
            @XMLProperty
            public boolean isEnableSubordinateControl() {
                return enableSubordinateControl;
            }
            public void setEnableSubordinateControl(boolean enableSubordinateControl) {
                this.enableSubordinateControl = enableSubordinateControl;
            }
            
            /**
             * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_SUBORDINATE_CHOICE
             * @return either "directOrIndirect" or "direct"
             */
            @XMLProperty
            public String getSubordinateOption() {
                return subordinateOption;
            }
            public void setSubordinateOption(String subordinateOption) {
                this.subordinateOption = subordinateOption;
            }
            
            /**
             * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_MAX_HIERARCHY_DEPTH
             * @return integer determining how far to traverse manager hierarchy
             */
            @XMLProperty
            public int getMaxHierarchyDepth() {
                return maxHierarchyDepth;
            }
            public void setMaxHierarchyDepth(int maxHierarchyDepth) {
                this.maxHierarchyDepth = maxHierarchyDepth;
            }
            
            /**
             * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_ENABLE_CUSTOM_CONTROL
             * @return is custom control enabled
             */
            @XMLProperty
            public boolean isEnableCustomControl() {
                return enableCustomControl;
            }
            public void setEnableCustomControl(boolean enableCustomControl) {
                this.enableCustomControl = enableCustomControl;
            }
            
            /**
             * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_CUSTOM_CONTROL
             * @return the Velocity template string used to filter the requester
             */
            @XMLProperty
            public String getCustomControl() {
                return customControl;
            }
            public void setCustomControl(String customControl) {
                this.customControl = customControl;
            }
            /*
             * Used in conjunction with hierarchy depth,
             * this can be set to prevent too many identities
             * from getting returned by stopping the full
             * depth iteration.
             */
            @XMLProperty
            public int getMaxHierarchyCount() {
                return maxHierarchyCount;
            }
            public void setMaxHierarchyCount(int maxHierarchyCount) {
                this.maxHierarchyCount = maxHierarchyCount;
            }

        }
        
        private boolean allowAll;
        private MatchConfig matchConfig;
        private boolean ignoreScoping;
        
        /**
         * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_ALLOW_ALL. This will allow anyone
         * to request for anyone else.
         * @return if allow all is enabled for this population.
         */
        @XMLProperty
        public boolean isAllowAll() {
            return allowAll;
        }
        public void setAllowAll(boolean allowAll) {
            this.allowAll = allowAll;
        }
        
        /**
         * @return the configuration object used to filter who someone can request for.
         */
        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public MatchConfig getMatchConfig() {
            return matchConfig;
        }
        public void setMatchConfig(MatchConfig match) {
            this.matchConfig = match;
        }
        
        /**
         * In previous releases, this was the Configuration.LCM_REQUEST_CONTROLS_DISABLE_SCOPING
         * @return ignore scoping is enabled.
         */
        @XMLProperty
        public boolean isIgnoreScoping() {
            return ignoreScoping;
        }
        public void setIgnoreScoping(boolean ignoreScoping) {
            this.ignoreScoping = ignoreScoping;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Identity Selector that indicates whether or not a given Identity falls into this dynamic scope
     */
    private IdentitySelector _identitySelector;

    /**
     * References to Identities that are explicitly included in this DynamicScope
     */
    private List<Identity> _inclusions;

    /**
     * References to Identities that are explicitly excluded from this DynamicScope
     */
    private List<Identity> _exclusions;
    
    /**
     * Boolean property indicated whether the current DynamicScope should simply include everyone.
     * If this is set to true all other properties will effectively be ignored
     */
    private boolean _allowAll;
    
    /**
     * Configuration object used to generate a list of eligible identities a user can 'request for'.
     */
    private PopulationRequestAuthority _populationRequestAuthority;
    
    /**
     * Request control rule indicating the object scope for role requests. 
     */
    private Rule _roleRequestControl;
    
    /**
     * Request control rule indicating the object scope for application requests. 
     */
    private Rule _applicationRequestControl;
    
    /**
     * Request control rule indicating the object scope for managed attribute requests. 
     */
    private Rule _managedAttributeRequestControl;

    /**
     * Revoke control rule indicating the object scope for role removals.
     */
    private Rule _roleRemoveControl;

    /**
     * Revoke control rule indicating the object scope for application removals.
     */
    private Rule _applicationRemoveControl;

    /**
     * Revoke control rule indicating the object scope for managed attribute removals
     */
    private Rule _managedAttributeRemoveControl;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public DynamicScope() {
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
    
    public void visit(Visitor v) throws GeneralException {
        v.visitDynamicScope(this);
    }

    public String toString() {
        return new ToStringBuilder(this)
            .append("id", getId())
            .append("name", getName())
            .toString();
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////    

    @XMLProperty
    public IdentitySelector getSelector() {
        return _identitySelector;
    }

    public void setSelector(IdentitySelector identitySelector) {
        _identitySelector = identitySelector;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Identity> getInclusions() {
        return _inclusions;
    }

    public void setInclusions(List<Identity> inclusions) {
        _inclusions = inclusions;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Identity> getExclusions() {
        return _exclusions;
    }

    public void setExclusions(List<Identity> exclusions) {
        _exclusions = exclusions;
    }

    @XMLProperty
    public boolean isAllowAll() {
        return _allowAll;
    }

    public void setAllowAll(boolean allowAll) {
        _allowAll = allowAll;
    }
    
    /**
     * @return configuration object used to determine who an identity can request access for
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public PopulationRequestAuthority getPopulationRequestAuthority() {
        return _populationRequestAuthority;
    }

    public void setPopulationRequestAuthority(PopulationRequestAuthority populationRequestAuthority) {
        this._populationRequestAuthority = populationRequestAuthority;
    }
    
    /**
     * @return object request scoping rule for role requests
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getRoleRequestControl() {
        return _roleRequestControl;
    }

    public void setRoleRequestControl(Rule rule) {
        _roleRequestControl = rule;
    }

    /**
     * @return Object removal scoping rule for role remove
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getRoleRemoveControl() {
        return _roleRemoveControl;
    }

    public void setRoleRemoveControl(Rule _roleRemoveControl) {
        this._roleRemoveControl = _roleRemoveControl;
    }

    /**
     * @return Object removal scoping rule for application remove
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getApplicationRemoveControl() {
        return _applicationRemoveControl;
    }

    public void setApplicationRemoveControl(Rule _applicationRemoveControl) {
        this._applicationRemoveControl = _applicationRemoveControl;
    }

    /**
     * @return Object removal scoping rule for managed attribute
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getManagedAttributeRemoveControl() {
        return _managedAttributeRemoveControl;
    }

    public void setManagedAttributeRemoveControl(
            Rule _managedAttributeRemoveControl) {
        this._managedAttributeRemoveControl = _managedAttributeRemoveControl;
    }

    /**
     * @return object request scoping rule for Application requests
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getApplicationRequestControl() {
        return _applicationRequestControl;
    }

    public void setApplicationRequestControl(Rule applicationRequestControl) {
        _applicationRequestControl = applicationRequestControl;
    }

    /**
     * @return object request scoping rule for Managed Attribute requests
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getManagedAttributeRequestControl() {
        return _managedAttributeRequestControl;
    }

    public void setManagedAttributeRequestControl(Rule managedAttributeRequestControl) {
        _managedAttributeRequestControl = managedAttributeRequestControl;
    }

}
