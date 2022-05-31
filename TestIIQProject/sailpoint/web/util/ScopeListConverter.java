/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;


/**
 * Converter for a list of scopes.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ScopeListConverter extends BaseObjectListConverter<Scope> {

    /**
     * Constructor.
     */
    public ScopeListConverter() {
        super(Scope.class);
    }

    @Override
    protected String getObjectName(SailPointObject obj) {
        return (null != obj) ? ((Scope) obj).getDisplayableName() : null;
    }
}
