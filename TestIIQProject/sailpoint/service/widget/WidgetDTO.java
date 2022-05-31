/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.widget;

import sailpoint.object.Widget;

import java.util.Map;

/**
 * Data transfer object for Widget
 *
 * @author patrick.jeong
 */
public class WidgetDTO {

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    public WidgetDTO(Widget widget) {
        this.id = widget.getId();
        this.name = widget.getName();
        this.title = widget.getTitle();
    }

    public WidgetDTO(Map<String, Object> widget) {
        this.id = (String)widget.get("id");
        this.name = (String)widget.get("name");
        this.title = (String)widget.get("title");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * widget id
     */
    private String id;

    /**
     * name of widget
     */
    private String name;

    /**
     * title message key
     */
    private String title;

    //////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
