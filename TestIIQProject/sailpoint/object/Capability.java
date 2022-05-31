/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A capability is a logical grouping of rights within IdentityIQ. These can
 * inherit rights from other capabilities to allow a hierarchy of capabilities.
 * Authorization in IdentityIQ will be checked against the SPRights rather than
 * the capabilities. A capability simply serves as a container of rights that
 * can be assigned to an Identity. There is one special capability called
 * SystemAdministrator that gets full access to everything in the system.
 */
@XMLClass
public class Capability extends SailPointObject {

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The name of the special system administrator capability in the system
     * that allows full access (essentially skipping auth checking).
     */
    public static final String SYSTEM_ADMINISTRATOR = "SystemAdministrator";
    
    /**
     * The name of the special help desk capability that is used to denote that
     * an identity is in the help desk population.
     */
    public static final String HELP_DESK = "HelpDesk";
    
    
    /**
     * The name of the special administrator capability in the system
     * that allows full access to forms.
     */
    public static final String FORM_ADMINISTRATOR = "FormAdministrator";

    /**
     * the name of identity operations capability in the system that allows full access to identity 
     * operations
     */
    public static final String IDENTITY_OPERATIONS_ADMIN = "RapidSetupIdentityOperationsAdministrator";
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A message key with the displayable name of a right.
     */
    private String displayName;

    /**
     * The rights that are granted by this capability.
     */
    private List<SPRight> rights;

    /**
     * A list of capabilities from which rights can be inherited.
     */
    private List<Capability> inheritedCapabilities;

    /**
     * Return whether this capability applies to the Identity Analyzer product.
     * False if this only applies to IdentityIQ.
     */
    private boolean appliesToAnalyzer;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public Capability() {
        super();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A message key with the displayable name of a right.
     */
    public String getDisplayName() {
        return displayName;
    }

    @XMLProperty
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * A list of capabilities from which rights can be inherited.
     */
    public List<Capability> getInheritedCapabilities() {
        return inheritedCapabilities;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public void setInheritedCapabilities(List<Capability> caps) {
        this.inheritedCapabilities = caps;
    }

    public void addInheritedCapability(Capability cap) {

        if (cap != null) {
            if (getInheritedCapabilities() == null) {
                this.inheritedCapabilities = new ArrayList<Capability>();
            }
            this.inheritedCapabilities.add(cap);
        }
    }

    /**
     * The rights that are granted by this capability.
     */
    public List<SPRight> getRights() {
        return rights;
    }

    @XMLProperty(xmlname="RightRefs", mode=SerializationMode.REFERENCE_LIST)
    public void setRights(List<SPRight> rights) {
        this.rights = rights;
    }

    /**
     * True if this capability applies to the Identity Analyzer product.
     * False if this only applies to IdentityIQ.
     */
    public boolean getAppliesToAnalyzer() {
        return appliesToAnalyzer;
    }

    @XMLProperty
    public void setAppliesToAnalyzer(boolean b) {
        this.appliesToAnalyzer = b;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return either the display name (possibly a message key) or the name of
     * this capability.
     */
    public String getDisplayableName() {
        return (null != this.displayName) ? this.displayName : super.getName();
    }
    
    /**
     * Return either the display name (possibly a message key) or the name of
     * this capability.
     */
    public String getDisplayableName(Locale locale) {
        String displayName = getDisplayableName();
        String name = Internationalizer.getMessage(displayName, locale);
        return (null != name) ? name : displayName;
    }
    
    /**
     * Return the SPRights granted by this capability and all inherited
     * capabilities.
     */
    public List<SPRight> getAllRights() {
        
        List<SPRight> allRights = new ArrayList<SPRight>();

        if (null != this.rights) {
            allRights.addAll(this.rights);
        }

        if (null != this.inheritedCapabilities) {
            // There is no circular reference checking here, so - as with most
            // recursive composites - bad data could cause infinite recursion.
            // Add protection here if we ever bump into this problem.
            for (Capability cap : this.inheritedCapabilities) {
                allRights.addAll(cap.getAllRights());
            }
        }
        
        return allRights;
    }

    /**
     * Helper to return whether the given list of capabilities has the special
     * system administrator capability.
     * 
     * @param  caps  A possibly-null list of capabilites.
     */
    public static boolean hasSystemAdministrator(List<Capability> caps) {
         return hasCapability(SYSTEM_ADMINISTRATOR, caps);
    }
    
    public static boolean hasCapability(String name, List<Capability> caps) {
    	for (Capability cap : Util.safeIterable(caps)) {
    		if (Util.nullSafeEq(name, cap.getName())) {
    			return true;
    		}
    	}
    	
    	return false;
    }
}
