/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import org.json.JSONString;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.tools.JsonHelper;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.util.WebUtil;

/**
 * 
 * A GridState holds information about how a grid should be displayed. This is used
 * when transitioning in the UI to remember how a grid was previously
 * displayed so you can go back to the same row in the grid, use the same
 * sorting, etc... This is used in conjunction with the SailPoint.GridState
 * javascript object by converting this object to JSON and passing the
 * JSON-created object to the SailPoint.GridState.
 *
 * @ignore
 * TODO: This object has been hijacked and is used largely for storing the
 * column ordering, sort ordering, and column widths permanently in Identity's
 * preferences.  This should be split into two objects:
 * 
 *  - GridPreferences: This just holds the persistent name and state fields that
 *    are read and set with the SailPointStateProvider javascript class.  These
 *    will be stored in the Identity preferences.
 *    
 *  - GridState: This just holds transient information about the grid for a
 *    session in the UI.  This would include the firstRow, sortColumn, and
 *    sortOrder properties, which can be passed around in the UI so that beans
 *    can know the state of a grid.  This is mainly used in a master-detail
 *    pattern where the detail page needs to know about the master grid from
 *    which the detail was selected.  The sort information can be used for the
 *    details "pager", and the first row information can be used when
 *    transitioning back to the grid to remember where the user came from.  Now
 *    that we are using paging rather than scrolling, it makes sense to change
 *    this to "page" rather than "firstRow".  This object is read and set with
 *    the GridState javascript class.
 *    
 * Splitting this apart will both get rid of obsolete information that is being
 * stored in the user's preferences (for example - firstRow), and also make it more clear
 * how the object is being used (persistent or transient) and how it is
 * interacted with (through the SailPointStateProvider or the GridState
 * javascript class).
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class GridState extends AbstractXmlObject implements JSONString {

    private String name;
    private int firstRow;
    private int scrollPosition;
    private String sortColumn;
    private String sortOrder;
    private String state;
    private int pageSize;
    
    private Attributes<String,Object> attributes;

    /**
     * Default constructor.
     */
    public GridState() {
        //this.resetFirstRow();
        this.sortColumn = null;
        this.sortOrder = null;
    }

    public void resetFirstRow() {
        this.firstRow = -1;
    }

    public int getFirstRow() {
        return firstRow;
    }

    public void setFirstRow(int firstRow) {
        this.firstRow = firstRow;
    }

    @XMLProperty(legacy=true)
    public void setFirstRowXml(int firstRowXml) { }

    public String getSortColumn() {
        return sortColumn;
    }

    public void setSortColumn(String sortColumn) {
        // Normally want dumb setters for XMLProperties ... however this property
        // shouldn't really be persistent (see class javadoc).  Make sure this
        // doesn't contain an XSS attack (see bug 6096).
        WebUtil.detectXSS(sortColumn);
        this.sortColumn = sortColumn;
    }

    @XMLProperty(legacy=true)
    public void setSortColumnXml(String sortColumnXml) { }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        // Normally want dumb setters for XMLProperties ... however this property
        // shouldn't really be persistent (see class javadoc).  Make sure this
        // doesn't contain an XSS attack (see bug 6096).
        WebUtil.detectXSS(sortOrder);
        this.sortOrder = sortOrder;
    }
    
    @XMLProperty(legacy=true)
    public void setSortOrderXml(String sortOrderXml) { }
    
    public int getScrollPosition() {
        return scrollPosition;
    }

    public void setScrollPosition(int scrollPosition) {
        this.scrollPosition = scrollPosition;
    }
    
    @XMLProperty(legacy=true)
    public void setScrollPositionXml(int scrollPositionXml) { }

    /**
     * Convert this GridState to JSON.
     */
    @JsonIgnore
    public String getJSON() {
        return JsonHelper.toJson(this);
    }
    
    public String toJSONString() {
        return getJSON();
    }

    @XMLProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    @XMLProperty
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    @XMLProperty
    public int getPageSize() {
        if (pageSize == 0) {
            pageSize = 25;
        }
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void setAttribute(String attribute) {
        
        WebUtil.detectXSS(attribute);
        
        if (attributes == null)
            attributes = new Attributes<String,Object>();
        
        // the attribute will come in as a JSON-ish name:value pair
        String[] att = attribute.split(":", 2);
        if (att.length == 2)
            attributes.put(att[0], att[1].trim());
        else
            attributes.remove(att[0]);
    }
}
