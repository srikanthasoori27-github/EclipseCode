/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Base UI bean for subclasses of BaseConstraint.
 * SEMI OBSOLETE: Only activity policies use this now,
 * need to move them into the NewPolicyBean DTO world.
 * 
 * Author: Peter, Jeff
 *
 * As of 3.0 this is now a full DTO copy of the BaseConstraint.
 * I'm trying to move to a model where the GenericConstraint 
 * objects at least can be fully independent of the persistent
 * model to avoid the usual Hibernate crap with multi-session 
 * conversations.
 * 
 * ...Well didn't get that far, need to revisit this later - jsl
 *
 */
package sailpoint.web.policy;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.BaseConstraint;
import sailpoint.object.Policy;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;

public class ConstraintBean extends BaseBean implements Serializable {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(ConstraintBean.class);
    
    /**
     * Unique id of the constraint.
     * This is generated since we may be dealing with new objects.
     */
    String uid;

    /**
     * Brief Description.
     */
    String summary;
    
    /**
     * 
     * Longer description.
     */
    String description; 

    /**
     * True if disabled.
     */
    boolean disabled;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    public ConstraintBean() {
        super();
    }
    
    public ConstraintBean(BaseConstraint src) {
        super();
        setConstraint(src);
    }
    
    public void setConstraint(BaseConstraint src) {
        
        // must always generate an id in case this is a new object
        if (uid == null)
            uid = uuid();
        
        // save it here so we can match them
        src.setUid(uid);

        disabled = src.isDisabled();

        // The intent was to use violationSummary for the brief
        // description but the test files currently use description.
        // If we fall back to description, may want to truncate it.

        summary = Util.unxml(src.getName());
        description = Util.unxml(src.getDescription());
        if (summary == null)
            summary = description;
        
        log.debug("Constraint Bean id: ["+uid+"] disabled: [" +disabled+"] summary:["+summary+"]");
    }
    
    static public String uuid() {
        return new java.rmi.dgc.VMID().toString();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Should Be Overriden
     */
    public BaseConstraint getSourceConstraint(Policy p) {
        return null;
    }
    
    /**
     * @return the id
     */
    public String getUid() {
        return uid;
    }

    /**
     * @return the disabled
     */
    public boolean isDisabled() {
        return disabled;
    }

    /**
     * @param disabled the disabled to set
     */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
    
    /**
     * @return the summary
     */
    public String getSummary() {
        return summary;
    }

    /**
     * @param summary the summary to set
     */
    public void setSummary(String summary) {
        this.summary = summary;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

}
