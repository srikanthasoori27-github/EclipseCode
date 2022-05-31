/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form.renderer.extjs.item;

import java.util.Map;

/**
 * @author: peter.holcomb
 *
 * An extjs specific form item that has extjs related properties on it.  This should only contain logic that
 * is specific to rendering an form item inside of an extjs page.
 */
public class FormItemDTO extends sailpoint.service.form.renderer.item.FormItemDTO {


    public static final String ATT_MAX_WIDTH = "maxWidth";
    public static final String ATT_WIDTH = "width";
    public static final int MAX_WIDTH = 512;
    /**
     * If true the component will be enabled as a suggest component
     * (default behavior).
     */
    private boolean suggest = true;

    private boolean authoritative;
    private Integer tabIndex;
    private boolean displayOnly;
    private String xtype;
    private Integer width;

    public String getXtype() {
        if(xtype==null) {
            xtype = this.getType();
        }
        return xtype;
    }

    public void setXtype(String xtype) {
        this.xtype = xtype;
    }

    public Integer getTabIndex() {
        return tabIndex;
    }

    public void setTabIndex(Integer ti) {
        this.tabIndex = ti;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public boolean isSuggest() {
        return suggest;
    }

    public void setSuggest(boolean suggest) {
        this.suggest = suggest;
    }

    public boolean isDisplayOnly() {
        return displayOnly;
    }

    public void setDisplayOnly(boolean val) {
        displayOnly = val;
    }

    public boolean isAuthoritative() {
        return authoritative;
    }

    public void setAuthoritative(boolean val) {
        authoritative = val;
    }

    /**
     * Json serializer. This allows us to include the attributes
     * map as field object properties.
     *
     * @return
     */
    @Override
    public Map toMap() {

        Map map = super.toMap();

        map.put("suggest", suggest);
        map.put("displayOnly", displayOnly);
        map.put("authoritative", authoritative);
        if (width != null)
            map.put("width", width);
        if (tabIndex != null)
            map.put("tabIndex", tabIndex);
        map.put("xtype", xtype);

        /* We always want to accept the xtype attribute if it is set on the field and allow
        it to override the calculated xtype
         */
        if (getAttributes() != null) {
            for (Object key : getAttributes().getKeys()) {
                if ("xtype".equals(key)) {
                    map.put(key, getAttributes().get(key));
                }
            }
        }

        return map;
    }
}
