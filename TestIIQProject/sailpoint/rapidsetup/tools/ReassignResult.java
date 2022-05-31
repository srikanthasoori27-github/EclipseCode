/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.Util;

public class ReassignResult {

    /**
     * the name of the identity that is the new owner of the objects
     */
    private String newOwnerName;

    /**
     * a map which tracks the objects which have had their ownership changed
     * to be optionally used later by email notifications
     */
    private Map<String, List<String>> ownershipMap;

    public ReassignResult(String newOwnerName, Map<String,List<String>> ownershipMap) {
        this.newOwnerName = newOwnerName;
        this.ownershipMap = new HashMap<>(ownershipMap);
    }

    public String newOwnerName() {
        return newOwnerName;
    }

    public  Map<String,List<String>> getOwnershipMap() {
        return ownershipMap;
    }

    public String toDebugString() {
        StringBuffer buf = new StringBuffer();
        if (Util.isNotNullOrEmpty(newOwnerName) && !ownershipMap.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : ownershipMap.entrySet()) {
                String classNameKey = entry.getKey();
                List<String> objectNames = entry.getValue();
                for(String objectName : Util.safeIterable(objectNames)) {
                    buf.append("Changed the " + classNameKey + " to " + newOwnerName + " for object '" + objectName + "'\n");
                }
            }
        }
        return buf.toString();
    }

}
