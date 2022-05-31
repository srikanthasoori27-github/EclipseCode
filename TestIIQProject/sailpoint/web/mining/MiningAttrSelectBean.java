package sailpoint.web.mining;

import java.util.Comparator;

import sailpoint.object.SearchInputDefinition.PropertyType;

public class MiningAttrSelectBean {
    private String name;
    private String displayName;
    private boolean selected;
    private String value;
    private PropertyType type;
    
    public MiningAttrSelectBean(){
        super();
    }
    
    public MiningAttrSelectBean(String name, String displayName, String value, PropertyType type) {
        super();
        this.name = name;
        this.displayName = displayName;
        this.value = value;
        this.type = type;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
    
    public PropertyType getType() {
        return type;
    }

    public void setType(PropertyType type) {
        this.type = type;
    }
    
    public String getTypeAsString() {
        return type.toString();
    }

    public static final Comparator<MiningAttrSelectBean> BY_NAME_COMPARATOR = new Comparator<MiningAttrSelectBean>() {

        public int compare(MiningAttrSelectBean o1, MiningAttrSelectBean o2) {
            int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else {
                String val1 = o1.getDisplayName();
                if (val1 == null) {
                    val1 = o1.getName();
                }
                
                String val2 = o2.getDisplayName();
                if (val2 == null) {
                    val2 = o2.getName();
                }
                
                if (val1 == null) {
                    if (val2 == null) {
                        result = 0;
                    } else {
                        result = -1;
                    }
                } else {
                    if (val2 == null) {
                        result = 1;
                    } else {
                        result = val1.compareTo(val2);
                    }
                }
            }

            return result;
        }
    };
    
}
