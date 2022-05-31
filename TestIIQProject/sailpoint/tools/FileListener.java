/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;


/**
 * Interface that defines how a file watcher can notify an interested party
 *
 */
public interface FileListener {

        /**
         * Invoked on a registered listener if the file changed.
         * 
         * @param file the file that changed
         */
    void fileChanged(String fileName);
}  // interface FileListener