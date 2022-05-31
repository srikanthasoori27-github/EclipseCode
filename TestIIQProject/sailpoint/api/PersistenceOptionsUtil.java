/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import sailpoint.object.PersistenceOptions;

/*
 * The PersistenceOptionsUtil is responsible to toggling a special flag that 
 * turn on the immutable modification.  It will remember the previous
 * state and allow it to be reset the context's options to the 
 * previous state when complete.
 * 
 * I needed this in a few different places so created this utility
 * class to prevent the code from being duplicated.
 * 
 * The typical call pattern is : 
 * 
 * try {
 *     configureImutableOption(context);
 * } finally {
 *     restoreImutableOption(context);
 * }
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class PersistenceOptionsUtil {
    
    /**
     * Store off the previous flag so we can put it back to its previous
     * state.
     */
    Boolean previousImmutableFlagValue;
    
    public PersistenceOptionsUtil() { }
        
    /*
     * Turn on the "force edit" flag that will allow us to update
     * the certification with information even after they are electronically
     * signed or otherwise marked Immutable.
     * 
     * Store off the previous version of the flag so we can restore it
     * to its original state when we are done. 
     * 
     */
    public void configureImmutableOption(SailPointContext context) {
        if ( context == null )
            return;

        PersistenceOptions ops = context.getPersistenceOptions();        
        if ( ops != null ) {
            previousImmutableFlagValue = ops.isAllowImmutableModifications();            
        } else
        if ( ops == null ) {
            ops = new PersistenceOptions();
        }
        ops.setAllowImmutableModifications(true);
        context.setPersistenceOptions(ops);
    }
    
    /*
     * This method restores the "force edit" flag that allows allows updates 
     * to already electronically signed certifications which get marked 
     * immutable after signing.
     * 
     * We do this in a few places where system type activities
     * are being performed like adorning workitem and remediation
     * item information to a certification.
     *
     */
    public void restoreImmutableOption(SailPointContext context) {
        if ( context == null ) 
            return;

        PersistenceOptions ops = context.getPersistenceOptions();
        if ( previousImmutableFlagValue != null ) {
            if ( ops != null ) {
                ops.setAllowImmutableModifications(previousImmutableFlagValue);
                context.setPersistenceOptions(ops);
            }
        } else { 
            // this indicates there were no options set on the
            // context when we configured this...
            context.setPersistenceOptions(null);
        }
    }

}
