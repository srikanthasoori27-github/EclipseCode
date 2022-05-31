/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.extjs;

import sailpoint.object.ColumnConfig;
import sailpoint.object.ReportColumnConfig;
import sailpoint.tools.Util;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
public class GridColumn {
    /**
     * Name of the field in the datasource this column maps to.
     */
    private String dataIndex;

    /**
     * Name of the renderer for this column
     */
    private String renderer;
    
    /**
     * Name of the pluginClass for this column
     */
    private String pluginClass;

    /**
     * Column width in pixels or 'auto'
     */
    private Object width;
    
    /**
     * Column percent width in terms of 0-100, overrides column width property
     */
    private Float flex;

    /**
     * Column header text
     */
    private String header;

    /**
     * True if column should not be displayed
     */
    private boolean hidden = false;

    /**
     * True if column should be hideable
     */
    private boolean hideable = true;

    /**
     * True if column is sortable
     */
    private boolean sortable;
    
    /**
     * Name to use as grid state identifier
     */
    private String stateId;

    public GridColumn(ColumnConfig column) {
        this.dataIndex = Util.getJsonSafeKey(column.getDataIndex());
        this.header = Util.getJsonSafeKey(column.getHeaderKey());
        // ExtJS 3.4 doesn't like grid widths of 0, so set them to null instead
        this.width = column.getWidth() != 0 ? column.getWidth() : null;
        this.sortable = column.isSortable();
        this.hideable = column.isHideable();
        this.renderer = column.getRenderer();
        this.pluginClass = column.getEditorClass();
        this.hidden = column.isHidden();
        // ExtJS 3.4 doesn't like grid widths of 0, so set them to null instead
        if(column.getFlex() != 0){
        	this.flex = (float)column.getFlex();
        }
        else if(column.getPercentWidth() != 0) {
        	this.flex = column.getPercentWidth() != 0 ? (float)column.getPercentWidth()/10 : null;
        }
        this.stateId = column.getStateId();
    }

    public GridColumn(ReportColumnConfig column) {
        //this.id = column.getDataIndex();
        this.dataIndex = Util.getJsonSafeKey(column.getField());
        this.header = Util.getJsonSafeKey(column.getHeader());
        // ExtJS 3.4 doesn't like grid widths of 0, so set them to null instead
        this.width = column.getWidth() != 0 ? column.getWidth() : null;
        this.sortable = column.isSortable();
        this.hidden = column.isHidden();

        // ExtJS 3.4 doesn't like grid widths of 0, so set them to null instead
        this.flex = 1F;
    }


    public GridColumn(String dataIndex) {
        this.dataIndex = Util.getJsonSafeKey(dataIndex);
    }

    public GridColumn(String dataIndex, String header, Object width, boolean sortable) {
        this(dataIndex);
        this.header = Util.getJsonSafeKey(header);
        this.width = width;
        this.sortable = sortable;
    }

    public GridColumn(String dataIndex, String header, Object width, boolean sortable, boolean hidden) {
        this(dataIndex, header, width, sortable);
        this.hidden = hidden;
    }

    public GridColumn(String dataIndex, String header, Object width, boolean sortable,
                     boolean hidden, boolean hideable) {
        this(dataIndex, header, width, sortable, hidden);
        this.hideable = hideable;
    }

    public String getDataIndex() {
        return dataIndex;
    }

    public void setHeader(String header) {
        this.header = Util.getJsonSafeKey(header);
    }

    public String getHeader() {
        return header;
    }

    public Object getWidth() {
        return width;
    }

    public boolean isSortable() {
        return sortable;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isHideable() {
        return hideable;
    }

    public void setHideable(boolean hideable) {
        this.hideable = hideable;
    }

    public String getRenderer() {
        return renderer;
    }
    
    public String getPluginClass() {
        return pluginClass;
    }

    public Float getFlex() {
        return flex;
    }

    public void setFlex(Float flex) {
        this.flex = flex;
    }
    
    public String getStateId() {
    	return this.stateId;
    }
    
    public void setStateId(String stateId) {
    	this.stateId = stateId;
    }
}
