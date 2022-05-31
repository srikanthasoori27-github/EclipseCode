/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rapidsetup.constraint.impl;

import sailpoint.object.Identity;
import sailpoint.rapidsetup.constraint.ConstraintContext;
import sailpoint.rapidsetup.constraint.ConstraintEvaluator;

public abstract class BaseConstraintEvaluator implements ConstraintEvaluator {

    public static final String ATTR_CHILDREN = "items";

    public static enum Age {
        OLD,
        NEW
    }

    protected static boolean hasChanged(ConstraintContext context, String attrName) {
        Identity newIdentity = context.getNewIdentity();
        Identity prevIdentity = context.getPrevIdentity();

        Object newAttrValue = newIdentity.getAttribute(attrName);
        Object prevAttrValue = prevIdentity.getAttribute(attrName);

        if (prevAttrValue == null && newAttrValue == null) {
            return false;
        }

        // At this point we know both values are not null, but is one of them null?
        return prevAttrValue == null || newAttrValue == null || !prevAttrValue.equals(newAttrValue);
    }
}
