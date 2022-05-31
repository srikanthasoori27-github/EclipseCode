package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class IdentityAttributeFilterControl {
    private String operation;
    private List<IdentityAttributeFilterControl> children;
    private String name;
    private String displayName;
    private boolean isSelectable;
    
    // NOTE: these two attributes are not used (because they're not persistent), but we want to trick the JSON serializer into
    // rendering them to JSON for the UI's sake
    private boolean isLeafAttribute;
    private boolean isGrouping;
    
    @XMLProperty
    public String getOperation() {
        return operation;
    }
    
    public void setOperation(String operation) {
        this.operation = operation;
    }
    
    /**
     * This is a nonpersistent value that is used by the UI to determine how this control item 
     * is rendered.
     * @return boolean
     */
    public boolean isGrouping() {
        return operation != null;
    }
    
    public void setGrouping(boolean isGrouping) {
        // This is a non-persistent value that is only used by the UI.
        // We only include this method so that the JSON deserializer doesn't flip out.  
        // For serialization we rely on whether or not operation is populated for the actual value
    }
    
    /**
     * This is a nonpersistent value that is used by the UI to determine how this control item 
     * is rendered.
     * @return boolean
     */
    public boolean isLeafAttribute() {
        return operation == null;
    }
        
    public void setLeafAttribute() {
        // This is a non-persistent value that is only used by the UI.
        // We only include this method so that the JSON deserializer doesn't flip out.  
        // For serialization we rely on whether or not operation is populated for the actual value
    }
    
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<IdentityAttributeFilterControl> getChildren() {
        return children;
    }
    
    public void setChildren(List<IdentityAttributeFilterControl> children) {
        this.children = children;
    }
    
    @XMLProperty
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    @XMLProperty
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * This is a nonpersistent value that is used by the UI to determine whether or not to 
     * put a checkbox next to the control item. The UI Bean manually sets this when needed
     */
    public boolean isSelectable() {
        return isSelectable;
    }
    
    public void setSelectable(boolean isSelectable) {
        this.isSelectable = isSelectable;
    }
    
    public Filter getFilter(Identity requester) {
        Filter myFilter;
        ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
        
        if (isGrouping()) {
            List<Filter> subFilters = new ArrayList<Filter>();
            
            for (IdentityAttributeFilterControl child : children) {
                subFilters.add(child.getFilter(requester));
            }
                
            if (operation.equals("or")) {
                myFilter = Filter.or(subFilters);
            } else {
                myFilter = Filter.and(subFilters);
            }
        } else {
            Object requesterValue = requester.getAttribute(getName());
            
            if (requesterValue == null) {
                myFilter = Filter.isnull(getName());
            } else {
                ObjectAttribute attributeDef = identityConfig.getObjectAttribute(getName());
                if (requesterValue instanceof Collection) {
                    myFilter = Filter.containsAll(getName(), (Collection) requesterValue);
                } else {
                    if (BaseAttributeDefinition.TYPE_IDENTITY.equals(attributeDef.getType())) {
                        myFilter = Filter.eq(getName() + ".name", (String) requesterValue);
                    } else if (BaseAttributeDefinition.TYPE_BOOLEAN.equals(attributeDef.getType())) {
                        // iiqetn-5420 - This case handles boolean attributes the most notable
                        // of which is the Inactive identity attribute.
                        myFilter = Filter.eq(attributeDef.getName(), requesterValue);
                    } else {
                        myFilter = Filter.eq(getName(), (String) requesterValue);
                    }
                }
            }
        }
        
        return myFilter;
    }
}
