/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import sailpoint.object.GroupDefinition;


/**
 * Converter for a list of scopes.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class GroupDefinitionListConverter extends BaseObjectListConverter<GroupDefinition> {

    /**
     * Constructor.
     */
    public GroupDefinitionListConverter() {
        super(GroupDefinition.class);
    }

}
