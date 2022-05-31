/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A ColumnConfig holds information about columns of a grid, including the header
 * for the column, the name of the property with the contents of the column,
 * and a percent width for the column.
 * 
 * Currently, I've only added the properties that I need.  We may want to extend
 * this to include other things like sortable, visible, sortProperties, default
 * sort order, ignoreClick, etc...  If we make these rich enough, we can
 * completely drive the live grid rendering from the column configuration.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.text.DateFormat;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * A ColumnConfig holds information about columns of a grid, including the header
 * for the column, the name of the property with the contents of the column,
 * and a percent width for the column.
 */
@XMLClass
public class ColumnConfig extends AbstractXmlObject {

    /**
     * Enumeration for values of 'fixed' attribute. Only 'Right' is currently implemented.
     */
    @XMLClass
    public enum FixedPosition {
        Left,
        Right
    }

    /** The message key used to when rendering the header for the column **/
    private String headerKey;
    
    /** The property of the object that will be pulled from the database for this column **/
    private String property;
    
    /**
     * The name of the column. This is the record name. Usually it is going to be the
     * dataIndex but it does not have to be.
     */
    private String name;
    
    /** This is typically the property, but it is provided separately because often columns will
     * display data that is calculated from several different properties and the dataIndex is not
     * necessarily a property of the object
     */
    private String dataIndex;   
    
    /** This is typically the same as the property, but is provided in case the column is calculated
     * using a different property than what is stored in the dataIndex or property;
     */
    private String sortProperty;
    
    /** Bug #7682 - Add a secondary sort to columns that have non-unique values to ensure that the 
     * total list is sorted by unique values **/
    private String secondarySort;
    
    private boolean sortable;
    private boolean hideable;

    /**
     * True to disable HTML escaping of the column value.
     * This is intended primarily for use in JSON data sources
     * where the ability to embed {@code <br/>s}s for multi-line
     * text in table cells is desired.  Added specifically for rule generated
     * policy violation summaries that need to use line breaks
     * for formatting, bug#4420.
     */
    private boolean noEscape;

    /**
     * True to enable localization of the column value.
     * This is intended for pages that might contain columns of
     * message keys, but do not want the overhead of 
     * WebUtil.localizeMessage (look at it) for every cell in   
     * in a large datastore. Specifically this was added for
     * the policy violation page, but it might be useful elsewhere.
     */
    private boolean localize;

    /**
     * ext renderer function to use to render this column
     */
    private String renderer;

    /**
     * ext xtype of the editor component to use in this column
     */
    private String editorClass;
    private String dateStyle;
    private String timeStyle;

    /**
     * ext xtype of the plugin class for this column
     */
    private String pluginClass;

    // width as a percent value
    private int percentWidth;

    // width in pixels
    private int width;

    // minimum width in pixels
    private int minWidth;
    
    // Column width in stretchy numbers.  (i.e. all flex numbers are added up and 
    // the total divided by the number of columns to create a percentage width.  So
    // if we have 3 columns with flex of 2, 1, and 1 then the 2 column will be 50%
    // the width of the grid and the 1 columns are 25% of the width.)
    private float flex;
    
    /** whether the column is hidden as an option by default **/
    private boolean hidden;
    
    /** If you want the column to be in the datasource but not available from the list of columns **/
    private boolean fieldOnly;

    /**
     * Name of the java class that will evaluate the value of this column.
     * The class must implement sailpoint.web.view.ViewColumn
     * Note that this is only applicable when using a ViewBuild.
     */
    private String evaluator;
    
    /**
     * Name of the stateId. If not supplied, extJS 4.1 uses the header index if not specified
     */
    private String stateId;
    
    /**
     * FixedPosition to fix column. Only one column per table can be fixed. 
     */
    private FixedPosition fixed;

    /**
     * CSV list of properties that define a group if being grouped on this column. Defaults to sortproperty.
     */
    private String groupProperty;
    
    /**
     * Default constructor.
     */
    public ColumnConfig() {}

    /**
     * Copy constructor
     * @param orig Original ColumnConfig
     */
    public ColumnConfig(ColumnConfig orig) {
        if (orig != null){
            this.headerKey = orig.getHeaderKey();
            this.property = orig.getProperty();
            this.name = orig.getName();
            this.dataIndex = orig.getName();
            this.sortProperty = orig.getSortProperty();
            this.secondarySort = orig.getSecondarySort();
            this.sortable = orig.isSortable();
            this.hideable = orig.isHideable();
            this.noEscape = orig.isNoEscape();
            this.localize = orig.isLocalize();
            this.renderer = orig.getRenderer();
            this.editorClass = orig.getEditorClass();
            this.dateStyle = orig.getDateStyle();
            this.timeStyle = orig.getTimeStyle();
            this.pluginClass = orig.getPluginClass();
            this.percentWidth = orig.getPercentWidth();
            this.width = orig.getWidth();
            this.minWidth = orig.getMinWidth();
            this.flex = orig.getFlex();
            this.hidden = orig.isHidden();
            this.fieldOnly = orig.isFieldOnly();
            this.evaluator = orig.getEvaluator();
            this.groupProperty = orig.getGroupProperty();
        }
    }

    /**
     * Constructor.
     */
    public ColumnConfig(String headerKey, String property, int percentWidth) {
        this.headerKey = headerKey;
        this.property = property;
        this.sortable = true;
        this.hideable = true;
        this.percentWidth = percentWidth;
    }
    
    public ColumnConfig(String headerKey, String property) {
        this.headerKey = headerKey;
        this.property = property;
        this.sortable = true;
        this.hideable = true;
    }

    @XMLProperty
    public String getHeaderKey() {
        return headerKey;
    }

    public void setHeaderKey(String header) {
        this.headerKey = header;
    }

    @XMLProperty
    public String getProperty() {
        return property;
    }
    
    @XMLProperty
    public String getName() {
        return name;
    }
    
    public void setName(String val) {
        name = val;
    }
    
    //Returns a json safe (converts a period to a hyphen/minus) property
    public String getJsonProperty() {
    	String jsonProperty = property;
    	if(property==null)
    		return "";
    	if(jsonProperty.contains(".")) {
    		jsonProperty = jsonProperty.replace('.', '-');
    	}
    	
    	return jsonProperty;
    }

    //Returns a json safe (converts a period to a hyphen/minus) property
    public String getJsonDataIndex() {
        String jsonDataIndex = getDataIndex();
        if(jsonDataIndex != null && jsonDataIndex.contains(".")) {
            jsonDataIndex = jsonDataIndex.replace('.', '-');
        }
        
        return jsonDataIndex;
    }
    
    public void setProperty(String property) {
        this.property = property;
    }

    @XMLProperty
    public int getPercentWidth() {
        return percentWidth;
    }

    public void setPercentWidth(int width) {
        this.percentWidth = width;
    }

    @XMLProperty
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    @XMLProperty
    public int getMinWidth() {
        return minWidth;
    }

    public void setMinWidth(int width) {
        this.minWidth = width;
    }
    
    @XMLProperty
    public float getFlex() {
        return flex;
    }

    public void setFlex(float d) {
        this.flex = d;
    }

    @XMLProperty
    public boolean isSortable() {
		return sortable;
	}

	public void setSortable(boolean sortable) {
		this.sortable = sortable;
	}

	@XMLProperty
	public boolean isHideable() {
		return hideable;
	}

	public void setHideable(boolean hideable) {
		this.hideable = hideable;
	}

    @XMLProperty
    public boolean isNoEscape() {
		return noEscape;
	}

	public void setNoEscape(boolean noEscape) {
		this.noEscape = noEscape;
	}

    @XMLProperty
    public boolean isLocalize() {
		return localize;
	}

	public void setLocalize(boolean b) {
		this.localize = b;
	}

	@XMLProperty
	public String getDataIndex() {
		if(dataIndex==null)
			return getJsonProperty();
		return dataIndex;
	}

	public void setDataIndex(String dataIndex) {
		this.dataIndex = dataIndex;
	}

	@XMLProperty
	public String getSortProperty() {
		if(sortProperty==null)
			return getProperty();
		return sortProperty;
	}

	public void setSortProperty(String sortProperty) {
		this.sortProperty = sortProperty;
	}

    @XMLProperty
    public String getEditorClass() {
        return editorClass;
    }

    public void setEditorClass(String editorClass) {
        this.editorClass = editorClass;
    }

    @XMLProperty
    public String getPluginClass() {
        return pluginClass;
    }

    public void setPluginClass(String pluginClass) {
        this.pluginClass = pluginClass;
    }

    @XMLProperty
    public String getEvaluator() {
        return evaluator;
    }

    public void setEvaluator(String evaluator) {
        this.evaluator = evaluator;
    }

    @XMLProperty
    public String getDateStyle() {
        return dateStyle;
    }

    public Integer getDateStyleValue(){
        if (dateStyle==null || dateStyle.toLowerCase().equals("none"))
            return null;
        else if (dateStyle.toLowerCase().equals("short"))
            return DateFormat.SHORT;
        else if (dateStyle.toLowerCase().equals("long"))
            return DateFormat.LONG;
        else if (dateStyle.toLowerCase().equals("full"))
            return DateFormat.FULL;
        else if (dateStyle.toLowerCase().equals("medium"))
            return DateFormat.MEDIUM;

        return null;
    }

    public void setDateStyle(String dateStyle) {
        this.dateStyle = dateStyle;
    }

    @XMLProperty
    public String getTimeStyle() {
        return timeStyle;
    }

    public Integer getTimeStyleValue(){
        if (timeStyle==null || timeStyle.toLowerCase().equals("none"))
            return null;
        else if (timeStyle.toLowerCase().equals("short"))
            return DateFormat.SHORT;
        else if (timeStyle.toLowerCase().equals("long"))
            return DateFormat.LONG;
        else if (timeStyle.toLowerCase().equals("full"))
            return DateFormat.FULL;
        else if (timeStyle.toLowerCase().equals("medium"))
            return DateFormat.MEDIUM;

        return null;
    }

    

    public void setTimeStyle(String timeStyle) {
        this.timeStyle = timeStyle;
    }

    @Override
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = PRIME * result + ((headerKey == null) ? 0 : headerKey.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final ColumnConfig other = (ColumnConfig) obj;
		if (headerKey == null) {
		    // IIQCB-2023 - return false when headerKey is null, do not overwrite existing null headerKey ColumnConfigs like 'id'
		    return false;
		} else if (!headerKey.equals(other.headerKey)) {
		    return false;
		}
		return true;
	}

	@XMLProperty
	public String getRenderer() {
		return renderer;
	}

	public void setRenderer(String renderer) {
		this.renderer = renderer;
	}

    @XMLProperty
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
    
    @XMLProperty
    public boolean isFieldOnly() {
        return fieldOnly;
    }

    public void setFieldOnly(boolean fieldOnly) {
        this.fieldOnly = fieldOnly;
    }

    @XMLProperty
    public String getSecondarySort() {
        return secondarySort;
    }

    public void setSecondarySort(String secondarySort) {
        this.secondarySort = secondarySort;
    }

    @XMLProperty
	public String getStateId() {
		if(this.stateId != null)
			return this.stateId;
		else
			return getDataIndex();
				
	}

	public void setStateId(String stateId) {
		this.stateId = stateId;
	}

    @XMLProperty
    public FixedPosition getFixed() { return this.fixed; }
    
    public void setFixed(FixedPosition fixed) {
         this.fixed = fixed;
    }
    
    @XMLProperty
    public String getGroupProperty() {
        if (this.groupProperty == null) {
            return getSortProperty();
        }
        
        return this.groupProperty; 
    }
    
    public void setGroupProperty(String groupProperty) { 
        this.groupProperty = groupProperty;
    }
}
