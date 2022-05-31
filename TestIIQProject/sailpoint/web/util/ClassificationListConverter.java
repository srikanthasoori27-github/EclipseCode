/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import sailpoint.object.Classification;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;


/**
 * Converter for a list of Classifications.
 *
 */
public class ClassificationListConverter extends BaseObjectListConverter<Scope> {

    /**
     * Constructor.
     */
    public ClassificationListConverter() {
        super(Classification.class);
    }

    @Override
    protected String getObjectName(SailPointObject obj) {
        if (obj instanceof Classification) {
            return (null != obj) ? ((Classification) obj).getDisplayableName() : null;
        } else {
            return null;
        }
    }
}
