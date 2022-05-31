/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * 
 * A class used to represent detected native changes
 * found from aggregation to aggregation.
 * 
 * These are stored on the identity then processed
 * by the refresh task.
 * 
 * @see sailpoint.api.NativeChangeDetector
 * 
 * Perform native change detection when included in a LifeCycle event. * 
 *  
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * @since 6.0
 * 
 */
@XMLClass
public class NativeChangeDetection extends LinkSnapshot {

    /**
     * Of course, you need a serialVersionUID what would
     * happen if it was not defined.  
     */
    private static final long serialVersionUID = 5755641041151367190L;

    /**
     * Operation that indicates if it is a modify,
     * create or delete on the link. Defaults to modify.
     */
    Operation operation;
       
    /**
     * List of changes detected on the link when there 
     * has been a modification to an existing link.
     * 
     * When there has been a new link or the link is
     * detected this list will be null.
     * 
     */
    List<Difference> differences;
    
    /**
     * Workflow variable "flow" value for native change events.
     * @see sailpoint.workflow.IdentityLibrary#VAR_FLOW
     */
    public static final String FLOW_CONFIG_NAME = "NativeChange";
    
    public NativeChangeDetection() {
        super();
        operation = Operation.Modify;
    }
        
    /**
     * For Deletes it nice to have a snapshot of the entire
     * Link, but for the cases of Modify and Create a summary
     * of the interesting attributes is lighter weight
     * and more focused. 
     * 
     * @param link Link to pull interesting info from
     */
    public void assimilateAccountInfo(Link  link) {
        setApplication(link.getApplicationName());
        setNativeIdentity(link.getNativeIdentity());
        setInstance(link.getInstance());                
    }
    
    @XMLProperty
    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }
     
    @XMLProperty
    public List<Difference> getDifferences() {
        return differences;
    }

    public void setDifferences(List<Difference> differences) {
        this.differences = differences;
    }

    public void add(Difference d) {
       if ( d != null ) {
           if ( differences == null )
              differences = new ArrayList<Difference>();

           if ( !differences.contains(d) ) {
               differences.add(d);
           }
       }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Return true if there are no differences defined
     * in this change detection.
     * 
     * @return empty true when there are no differences
     */
    public boolean isEmpty() {
        return ( Util.size(differences) > 0 ) ? false : true;
    }

    /**
     * Compare the link information (application, instance and nativeIdentity)
     * and if they all match return true, otherwise return false.
     * 
     * @param link Link to match
     * @return matches when the Detection matches the link
     */
    public boolean matches(LinkInterface link) {    
        
        if ( link == null) 
            return false;
        
        if ( ( Util.nullSafeCompareTo(getNativeIdentity(), link.getNativeIdentity()) == 0 ) &&
             ( Util.nullSafeCompareTo(getInstance(), link.getInstance()) == 0 ) &&
             ( Util.nullSafeCompareTo(getApplicationName(), link.getApplicationName()) == 0 ) ) {
            
            return true;            
        }
        return false;
    }

}
