/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Model object for home page widget.
 *
 * @author patrick.jeong
 */
@XMLClass
public class Widget extends SailPointObject {

    private static final long serialVersionUID = -7167473879188441L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    /**
     *  Message key for title
     */
    private String title;

    //selector to filter using isManager property or the rights
    private IdentitySelector selector;

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * Default constructor.
     */
    public Widget() {}

    @XMLProperty
    public IdentitySelector getSelector() {
        return selector;
    }

    public void setSelector(IdentitySelector selector) {
        this.selector = selector;
    }

}
