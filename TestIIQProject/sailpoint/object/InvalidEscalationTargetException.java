/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author Bernie Margolis
 */

package sailpoint.object;

/**
 * An exception thrown when a Notifiable item is escalated to someone not
 * capable of receiving the item.
 */
public class InvalidEscalationTargetException extends IllegalArgumentException {

    private static final long serialVersionUID = 198728206462136538L;
        
    private final String proposedTarget;
        
    public InvalidEscalationTargetException(Notifiable item, String escalationTarget, Throwable t) {
        super(escalationTarget + ", the proposed owner for " + item + ", is not valid.", t);
        proposedTarget = escalationTarget;
    }

    public String getProposedTarget() {
        return proposedTarget;
    }
}
