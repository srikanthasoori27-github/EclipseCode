/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.GeneralException;


/**
 * A tag is simply a name that you can associate with objects in the system.
 * One or more tags are usually set on an object, which can later be used for
 * displaying and searching.  Think tagging a blog post.
 * 
 * @ignore
 * This class is slim, but is its own SailPointObject so that we can easily get
 * a list of all tags in the system.  I considered just letting objects store
 * a list of strings as their tags, but getting a list of existing tags would
 * require one or more "select distinct" queries, which may not even be possible
 * if we store the tags in XML.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class Tag extends SailPointObject {

    /**
     * Default constructor.
     */
    public Tag() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param  name  The name of this tag.
     */
    public Tag(String name) {
        this();
        setName(name);
    }

    @Override
    public void visit(Visitor visitor) throws GeneralException {
        visitor.visitTag(this);
    }
}
