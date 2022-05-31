/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.TaskDefinition;


/**
 * Converter for a list of task definitions.
 *
 * @author <a href="mailto:peter.holcomb@sailpoint.com">Peter Holcomb</a>
 */
public class TaskDefinitionListConverter extends BaseObjectListConverter<TaskDefinition> {

    /**
     * Constructor.
     */
    public TaskDefinitionListConverter() {
        super(TaskDefinition.class);
    }

    @Override
    protected String getObjectName(SailPointObject obj) {
        return (null != obj) ? ((Scope) obj).getDisplayableName() : null;
    }
}
