/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.util;

import sailpoint.object.SailPointObject;
import sailpoint.object.Tag;
import sailpoint.tools.GeneralException;


/**
 * Convert a list of IDs to Tags and vice versa.  This allows transient objects
 * so that we can auto-create tags when a name is added to the list.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class TagListConverter extends BaseObjectListConverter<Tag> {

    /**
     * Default constructor.
     */
    public TagListConverter() {
        super(Tag.class);
        this.allowTransientObjects = true;
    }

    @Override
    protected SailPointObject createObject(String name) throws GeneralException {
        return new Tag(name);
    }
}
