/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.extjs;

import java.util.Map;

import sailpoint.object.SailPointObject;

public interface TreeNodeBuilder<E extends SailPointObject> {
    /**
     * Build a sailpoint.web.ext.TreeNode from a SailPointObject
     * @param obj
     * @param additionalParameters An optional map of additional values that the builder might need
     *                             to properly generate a TreeNode
     * @return
     */
    TreeNode buildNode(E obj, Map<String, Object> additionalParameters, int pageSize);
}
