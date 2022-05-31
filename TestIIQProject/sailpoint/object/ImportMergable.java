/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

/**
 * As the name implies this interface is used during import to perform object specific merge behavior.
 * Currently, QuickLink and WebResource are the only objects which utilize this interface
 * @author chris.annino
 *
 */
public interface ImportMergable {
    
    /**
     * It is important to stress that the parameter obj is the original "from" object. The "to" is the instance
     * class of this interface.
     * @param obj the object merging from
     */
    public void merge(Object obj);
}
