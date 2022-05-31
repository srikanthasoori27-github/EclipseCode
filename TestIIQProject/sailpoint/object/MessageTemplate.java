/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Encapculates a parameterized message.
 * 
 * Author: Jeff
 * 
 * This is similar to an EmailTemplate, except that it has no 
 * mail delivery baggage, it is only a string of text with
 * optional variable references.  The original use for these was 
 * to format the description and details fields for work items.
 *
 * This is also a lot like a message in a message
 * catalog, but unlike catalogs, these are intended
 * to contain customer editable text.
 *
 * The messages may be formated with our original variable
 * reference syntax $(...) or it may a Velocity template.
 *
 * In retrospect I'm not sure how useful this is.  Objects that
 * want to contain parameterized messages can just have
 * a String property and render it themselves rather than 
 * pushing that out into a shared SailPointObject.
 *
 * The only current use is to hold the template for rendering
 * the work item descriptions for policy violations.  Work item
 * description templates are a useful thing but we can also just
 * represent them directly in the WorkItemConfig class.
 * 
 */

package sailpoint.object;

import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.MessageRenderer;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;

/**
 * Used to define a parameterized message.
 * 
 * This is similar to an EmailTemplate, except that it has no 
 * mail delivery baggage, it is only a string of text with
 * optional variable references. The original use for these was 
 * to format the description and details fields for work items.
 *
 * The messages can be formatted with the original variable
 * 
 * This is used less (at all?) now that there are workflows.
 */
@XMLClass
public class MessageTemplate extends SailPointObject
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The message text.
     */
    private String _text;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public MessageTemplate()
    {
    }

    public void load() {
        // we're all XML baby, revel in it!
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getText() {
        return _text;
    }

    public void setText(String s) {
        _text = s;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Rendering
    //
    //////////////////////////////////////////////////////////////////////

    public String render(Map arguments) throws GeneralException {
        return MessageRenderer.render(_text, arguments);
    }

}
