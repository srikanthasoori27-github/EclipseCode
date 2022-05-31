package sailpoint.object;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.role.MiningService;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.util.WebUtil;

@XMLClass
public class ITRoleMiningTaskResult extends AbstractXmlObject {
    private static final long serialVersionUID = -2854214385511652814L;
    private static final Log log = LogFactory.getLog(ITRoleMiningTaskResult.class);
    
    private SimplifiedEntitlementsKey entitlementSet;
    private EntitlementStatistics statistics;
    private int totalPopulation;
    
    /* This is an identifier that is assigned and used exclusively by the UI */ 
    private String identifier;

    /**
     * @exclude
     * This is only intended for the serializer.  
     * @deprecated - use {@link #ITRoleMiningTaskResult(sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlementsKey, sailpoint.object.ITRoleMiningTaskResult.EntitlementStatistics, int)} 
     */
    @Deprecated
    public ITRoleMiningTaskResult() {}
    
    public ITRoleMiningTaskResult(SimplifiedEntitlementsKey entitlementSet, EntitlementStatistics statistics, int totalPopulation) {
        this.entitlementSet = entitlementSet;
        this.statistics = statistics;
        this.totalPopulation = totalPopulation;
    }
    
    @XMLProperty
    public SimplifiedEntitlementsKey getEntitlementSet() {
        return entitlementSet;
    }

    public void setEntitlementSet(SimplifiedEntitlementsKey entitlementSet) {
        this.entitlementSet = entitlementSet;
    }

    @XMLProperty
    public EntitlementStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(EntitlementStatistics statistics) {
        this.statistics = statistics;
    }

    @XMLProperty
    public int getTotalPopulation() {
        return totalPopulation;
    }

    public void setTotalPopulation(int totalPopulation) {
        this.totalPopulation = totalPopulation;
    }
    
    @XMLProperty
    public String getIdentifier() {
        return identifier;
    }
    
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public boolean contains(SimplifiedEntitlement entitlement) {
        final boolean result;
        if (entitlementSet == null || entitlementSet.getSimplifiedEntitlements() == null) {
            result = false;
        } else {
            result = entitlementSet.getSimplifiedEntitlements().contains(entitlement);
        }
        return result;
    }
    
    @XMLClass
    public static class SimplifiedEntitlement {
        private String applicationId;
        private String applicationName;
        private String instance;
        private String nameOrTarget;
        private String rightOrValue;
        private String annotation;
        private String displayName;
        private boolean permission;
        
        /**
         * @exclude
         * This is only intended for the serializer.  
         * @deprecated - use one of the parameterized constructors
         */
        @Deprecated
        public SimplifiedEntitlement(){
        }
        
        /**
         * Constructor used to represent an entitlement attribute value
         * @param applicationId ID of the application to which this value's attribute belongs
         * @param applicationName name of the application to which this value's attribute belongs
         * @param instance name of the application instance to which this value's attribute belongs
         * @param name name of this value's attribute
         * @param value value being represented
         * @param displayName display name for the entitlement
         */
        public SimplifiedEntitlement(final String applicationId, final String applicationName, final String instance, final String name, final String value, final String displayName) {
            this.applicationId = applicationId;
            this.applicationName = applicationName;
            this.instance = instance;
            nameOrTarget = name;
            rightOrValue = value;
            annotation = null;
            permission = false;            
            if (displayName == null) {
                this.displayName = value; 
            } else {
                this.displayName = displayName;
            }
        }
        
        /**
         * Constructor used to represent a permission
         * @param applicationId ID of the application to which this permission belongs
         * @param applicationName name of the application to which this permission belongs
         * @param instance name of the application instance to which this permission belongs
         * @param target the permission's target
         * @param right the right being represented
         * @param annotation the annotation attached to this permission -- Note:  Annotations are being disregarded for the time being
         * @param displayName display name for the permission
         */
        public SimplifiedEntitlement(final String applicationId, final String applicationName, final String instance, final String target, final String right, final String annotation, final String displayName) {
            this.applicationId = applicationId;
            this.applicationName = applicationName;
            this.instance = instance;
            nameOrTarget = target;
            rightOrValue = right;
//            this.annotation = annotation;
            this.annotation = null; // We're disregarding annotations for now
            permission = true;
            if (displayName == null) {
                this.displayName = right + " on " + target;
            } else {
                this.displayName = displayName;
            }
        }
        
        /**
         * @exclude
         * This is just supplied for the serializer's sake. Avoid using it in practice and stick to the
         * constructor
         * @deprecated - use constructor to set name or target
         */
        @Deprecated
        public void setNameOrTarget(String nameOrTarget) {
            this.nameOrTarget = nameOrTarget;
        }
        
        /**
         * Get the name of the attribute represented by this entitlement or the target for the Permission represented by this
         * entitlement, depending on which is appropriate
         * @return Name or Target 
         */
        @XMLProperty
        public String getNameOrTarget() {
            return nameOrTarget;
        }

        /**
         * @exclude
         * This is just supplied for the serializer's sake. Avoid using it in practice and stick to the
         * constructor
         * @deprecated - use constructor to set value or right
         */
        @Deprecated
        public void setRightOrValue(String rightOrValue) {
            this.rightOrValue = rightOrValue;
        }
        
        @XMLProperty
        public String getRightOrValue() {
            return rightOrValue;
        }

        /**
         * @exclude
         * This is just supplied for the serializer's sake. Avoid using it in practice and stick to the
         * constructor
         * @deprecated - use constructor to set annotation
         */
        @Deprecated
        public void setAnnotation(String annotation) {
            this.annotation = annotation;
        }

        @XMLProperty
        public String getAnnotation() {
            return annotation;
        }

        /**
         * @exclude
         * This is just supplied for the serializer's sake. Avoid using it in practice and stick to the
         * constructor
         * @deprecated - use constructor to set application ID
         */
        @Deprecated
        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        @XMLProperty
        public String getApplicationId() {
            return applicationId;
        }

        /**
         * @exclude
         * This is just supplied for the serializer's sake. Avoid using it in practice and stick to the
         * constructor
         * @deprecated - use constructor to set application name
         */
        @Deprecated
        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        @XMLProperty
        public String getApplicationName() {
            return applicationName;
        }

        /**
         * @exclude
         * This is just supplied for the serializer's sake. Avoid using it in practice and stick to the
         * constructor
         * @deprecated - use constructor to set instance
         */
        @Deprecated
        public void setInstance(String instance) {
            this.instance = instance;
        }
        
        @XMLProperty
        public String getInstance() {
            return instance;
        }

        /**
         * @exclude
         * This is just supplied for the serializer's sake. Avoid using it in practice and stick to the
         * constructor
         * @deprecated - use correct constructor for permissions
         */
        @Deprecated
        public void setPermission(boolean isPermission) {
            this.permission = isPermission;
        }
        
        @XMLProperty
        public boolean isPermission() {
            return permission;
        }

        /**
         * @exclude
         * This is just supplied for the serializer's sake. Avoid using it in practice and stick to the
         * constructor
         * @deprecated - use constructor to set display name
         */
        @Deprecated
        public void setDisplayName(final String displayName) {
            this.displayName = displayName;
        }
        
        @XMLProperty        
        public String getDisplayName() {
            if (displayName == null) {
                if (permission) {
                    displayName = getRightOrValue() + " on " + getNameOrTarget();
                } else {
                    displayName = getRightOrValue(); 
                }
            }
            return displayName;
        }
        
        public String getTooltip() {
            String tooltip;
            
            if (isPermission()) {
                // Leave out annotations for the time being
//                if (getAnnotation() != null) {
//                    displayName = getApplicationName() + " - " + getAnnotation() + ": " + getRightOrValue() + " on " + getNameOrTarget();
//                } else {
                    tooltip = getApplicationName() + " - " + getRightOrValue() + " on " + getNameOrTarget();
//                }
            } else {
                tooltip = getApplicationName() + " - " + getNameOrTarget() + " = " + getRightOrValue(); 
            }

            return tooltip;
        }
        
        public String getDisplayId() {
            return WebUtil.escapeHTMLElementId(getApplicationId() + "#" + 
                    (getNameOrTarget() == null ? "" : getNameOrTarget()) + "#" + 
                    (getRightOrValue() == null ? "" : getRightOrValue())); // Leave out annotations for now + "#" +
//                    (getAnnotation() == null ? "" : getAnnotation()));
        }
        
        @Override
        public int hashCode() {
            // Note that we intentionally leave the displayName out of this
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((annotation == null) ? 0 : annotation.hashCode());
            result = prime * result
                    + ((applicationId == null) ? 0 : applicationId.hashCode());
            result = prime * result
                    + ((instance == null) ? 0 : instance.hashCode());
            result = prime * result
                    + ((nameOrTarget == null) ? 0 : nameOrTarget.hashCode());
            result = prime * result + (permission ? 1231 : 1237);
            result = prime * result
                    + ((rightOrValue == null) ? 0 : rightOrValue.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            // Note that we intentionally leave the displayName out of this
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SimplifiedEntitlement other = (SimplifiedEntitlement) obj;
            if (annotation == null) {
                if (other.annotation != null)
                    return false;
            } else if (!annotation.equals(other.annotation))
                return false;
            if (applicationId == null) {
                if (other.applicationId != null)
                    return false;
            } else if (!applicationId.equals(other.applicationId))
                return false;
            if (instance == null) {
                if (other.instance != null)
                    return false;
            } else if (!instance.equals(other.instance))
                return false;
            if (nameOrTarget == null) {
                if (other.nameOrTarget != null)
                    return false;
            } else if (!nameOrTarget.equals(other.nameOrTarget))
                return false;
            if (permission != other.permission)
                return false;
            if (rightOrValue == null) {
                if (other.rightOrValue != null)
                    return false;
            } else if (!rightOrValue.equals(other.rightOrValue))
                return false;
            return true;
        }
        
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("SimplfifiedEntitlement: [applicationId = ")
                .append(applicationId)
                .append(", applicationName = ")
                .append(applicationName)
                .append(", instance = ")
                .append(instance)
                .append(", displayName = ")
                .append(displayName)
                .append(", nameOrTarget = ")
                .append(nameOrTarget)
                .append(", rightOrValue = ")
                .append(rightOrValue)
                .append(", annotation = ")
                .append(annotation)
                .append(", permission = ")
                .append(permission)
                .append("]");
            return builder.toString();
        }
    }
        
    @XMLClass
    public static class SimplifiedEntitlementsKey {
        private Set<SimplifiedEntitlement> simplifiedEntitlements;
        
        /**
         * @exclude
         * This is only intended for the serializer.  
         * @deprecated use one of the other constructors provided, depending
         * on whether they want to represent permissions or attributes
         */
        @Deprecated
        public SimplifiedEntitlementsKey(){}
        
        public SimplifiedEntitlementsKey(final Link link, Set<SimplifiedEntitlement> entitlements, boolean isExcluded) {
            if (entitlements == null) {
                entitlements = new HashSet<SimplifiedEntitlement>();
            }
            simplifiedEntitlements = new HashSet<SimplifiedEntitlement>();
            addAttributes(link, entitlements, isExcluded);
            addPermissions(link, entitlements, isExcluded);
        }
        
        @XMLProperty(mode=SerializationMode.SET)
        public Set<SimplifiedEntitlement> getSimplifiedEntitlements() {
            return simplifiedEntitlements;
        }
        
        /**
         * @exclude
         * This is just supplied for the serializer's sake.
         * @deprecated use {@link #addLink(Link, java.util.Set, boolean)}
         */
        @Deprecated
        public void setSimplifiedEntitlements(Set<SimplifiedEntitlement> entitlements) {
            this.simplifiedEntitlements = entitlements;
        }
        
        /**
         * Add a link
         * @param link Link that you want to add
         * @param entitlements Set of <code>SimplifiedEntitlement</code> containing information about the entitlements we want to take from the link
         * @param isExcluded true if the list of entitlements set is a list of excluded entitlements; false if it's a list of included entitlements
         */
        public void addLink(final Link link, Set<SimplifiedEntitlement> entitlements, boolean isExcluded) {
            addAttributes(link, entitlements, isExcluded);
            addPermissions(link, entitlements, isExcluded);
        }

        /**
         * Check if this meets the required number of entitlements for candidacy 
         * @param minEntitlementsPerRole The minimum number of entitlements required to consider an entitlement set a candidate role
         * @return true if this entitlement set should be part of a candidate role; false otherwise
         */
        public boolean meetsEntitlementsRequirement(int minEntitlementsPerRole) {
            return simplifiedEntitlements.size() >= minEntitlementsPerRole;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((simplifiedEntitlements == null) ? 0 : simplifiedEntitlements.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SimplifiedEntitlementsKey other = (SimplifiedEntitlementsKey) obj;
            if (simplifiedEntitlements == null) {
                if (other.simplifiedEntitlements != null)
                    return false;
            } else if (!simplifiedEntitlements.equals(other.simplifiedEntitlements))
                return false;
            return true;
        }
        
        @Override
        public String toString() {
            String entitlementsSetString = simplifiedEntitlements == null ? "[]" : Arrays.asList(simplifiedEntitlements.toArray()).toString();
            String result = "SimplifiedEntitlementsKey: [entitlementSet: " + entitlementsSetString + "]";
            return result;
        }

        public boolean isSuperSetOf(SimplifiedEntitlementsKey otherKey) {
            boolean isSuperset = true;
            
            isSuperset &= this.getSimplifiedEntitlements().containsAll(otherKey.getSimplifiedEntitlements());
            
            return isSuperset;
        }
        
        /**
         * Utility method that returns only the entitlements in this key that correspond to the specified application
         * @param applicationId ID of the application whose entitlements are being returned
         * @return the entitlements in this key that correspond to the specified application
         */
        public Set<SimplifiedEntitlement> getSimplifiedEntitlementsByApplication(String applicationId) {
            Set<SimplifiedEntitlement> entitlementsByApp = new HashSet<SimplifiedEntitlement>();
            if (simplifiedEntitlements != null) {
                for (SimplifiedEntitlement entitlement : simplifiedEntitlements) {
                    if (entitlement.getApplicationId().equals(applicationId)) {
                        entitlementsByApp.add(entitlement);
                    }
                }
            }
            return entitlementsByApp;
        }
        
        private void addAttributes(Link link, Set<SimplifiedEntitlement> entitlements, boolean isExcluded) {
            Attributes<String, Object> attributes = link.getEntitlementAttributes();
            String applicationId = link.getApplication().getId();
            String applicationName = link.getApplicationName();
            String instance = link.getInstance();
            if (attributes != null && !attributes.isEmpty()) {
                Set<String> names = attributes.keySet();
                for (String name : names) {
                    try {
                        Object attributeValue = attributes.get(name);

                        if (attributeValue instanceof String) {
                            String displayName = MiningService.getDisplayName(applicationId, name, (String) attributeValue);
                            addValue(applicationId, applicationName, instance, name, (String)attributeValue, displayName, entitlements, isExcluded);
                        } else if (attributeValue instanceof Collection) {
                            Collection attributeValues = (Collection) attributeValue;
                            if (attributeValues != null && !attributeValues.isEmpty()) {
                                for (Object value : attributeValues) {
                                    if (value != null) {
                                        String displayName = MiningService.getDisplayName(applicationId, name, (String) value);
                                        addValue(applicationId, applicationName, instance, name, value.toString(), displayName, entitlements, isExcluded);
                                    }
                                }
                            }
                        }
                    } catch (ClassCastException e) {
                        if (log.isDebugEnabled())
                            log.debug("Skipping non-string attribute: " + name);
                    }
                }
            }
        }
        
        private void addValue(final String applicationId, final String applicationName, final String instance, final String name, String attributeValue, String displayName, Set<SimplifiedEntitlement> entitlements, boolean isExcluded) {
            SimplifiedEntitlement entitlementToAdd = new SimplifiedEntitlement(applicationId, applicationName, instance, name, attributeValue, displayName);
            if (isNotFiltered(entitlementToAdd, entitlements, isExcluded)) {
                simplifiedEntitlements.add(entitlementToAdd);
            }
        }
        
        private void addPermissions(Link link, Set<SimplifiedEntitlement> entitlements, boolean isExcluded) {
            // TODO: The display name for permissions is tricky because we break them up.  
            //       Ignore the ManagedAttribute display name for now.
            List<Permission> permissions = link.getPermissions();
            String applicationId = link.getApplication().getId();
            String applicationName = link.getApplicationName();
            String instance = link.getInstance();
            if (permissions != null && !permissions.isEmpty()) {
                for (Permission permission : permissions) {
                    String target = permission.getTarget();
                    String annotation = permission.getAnnotation();
                    List<String> rights = permission.getRightsList();
                    if (rights != null && !rights.isEmpty()) {
                        for (String right : rights) {
                            SimplifiedEntitlement entitlementToAdd = new SimplifiedEntitlement(applicationId, applicationName, instance, target, right, annotation, null);
                            if (isNotFiltered(entitlementToAdd, entitlements, isExcluded)) {
                                simplifiedEntitlements.add(entitlementToAdd);
                            }
                        }
                    } else {
                        SimplifiedEntitlement entitlementToAdd = new SimplifiedEntitlement(applicationId, applicationName, instance, target, null, annotation, null);
                        if (isNotFiltered(entitlementToAdd, entitlements, isExcluded)) {
                            simplifiedEntitlements.add(entitlementToAdd);
                        }
                    }
                }
            }
        }

        private boolean isNotFiltered(SimplifiedEntitlement entitlement, Set<SimplifiedEntitlement> entitlements, boolean isExcluded) {
            boolean result;

            if (entitlements == null || entitlements.isEmpty()) {
                result = false;
            } else {
                SimplifiedEntitlement equivalenceClass;
                
                // Use the specified entitlement to generate an equivalence class that we check for to make sure it should be included
                if (entitlement.isPermission()) {
                    equivalenceClass = new SimplifiedEntitlement(entitlement.getApplicationId(), null, null, entitlement.getNameOrTarget(), null, null, null);
                } else {
                    equivalenceClass = new SimplifiedEntitlement(entitlement.getApplicationId(), null, null, entitlement.getNameOrTarget(), entitlement.getRightOrValue(), null);
                }
                
                result = entitlements.contains(equivalenceClass);
            }
            
            if (isExcluded) {
                result = !result;
            }
            
            return result;
        }        
    }

    
    @XMLClass
    public static class EntitlementStatistics {
        private int exactMatches;
        private int superMatches;
        
        public EntitlementStatistics() {
            exactMatches = 0;
            superMatches = 0;
        }
        
        /**
         * @exclude
         * This is just supplied for the serializer's sake.  
         * @deprecated use {@link #addExactMatch()}
         */
        @Deprecated
        public void setExactMatches(int exactMatches) {
            this.exactMatches = exactMatches;
        }
        
        /**
         * Get the number of identities that matches this entitlement group exactly
         */
        @XMLProperty
        public int getExactMatches() {
            return exactMatches;
        }
        
        /**
         * @exclude
         * This is just supplied for the serializer's sake.  
         * @deprecated use {@link #addSuperMatches(int)}
         */
        @Deprecated
        public void setSuperMatches(int superMatches) {
            this.superMatches = superMatches;
        }

        /**
         * Get the number of identities whose entitlement group was a superset of this one
         */
        @XMLProperty
        public int getSuperMatches() {
            return superMatches;
        }
        
        public void addExactMatch() {
            exactMatches++;
        }
        
        public void addSuperMatches(int numSuperMatches) {
            superMatches += numSuperMatches;
        }
    }
}
