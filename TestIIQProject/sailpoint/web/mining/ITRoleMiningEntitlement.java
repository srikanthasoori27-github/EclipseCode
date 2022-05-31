package sailpoint.web.mining;

import java.util.Comparator;
import java.util.Locale;

import sailpoint.object.ManagedAttribute;


public class ITRoleMiningEntitlement {
    private String application;
    private String name;
    private String value;
    private String target;
    private Type type;

    // displayName and explanation are read-only values that are deliberately left out of consideration in .equals
    private String displayName;
    private String explanation;

    public enum Type {
        Attribute,
        Permission
    }
    
    public ITRoleMiningEntitlement(){}
    
    public ITRoleMiningEntitlement(Type type, String application, String nameOrTarget, String explanation, String value, String displayName) {
        this.application = application;
        this.setType( type );
        if (type == Type.Attribute) {
            this.name = nameOrTarget;
            this.explanation = explanation;
            this.value = value;
        } else {
            this.target = nameOrTarget;
        }
        
        if (displayName == null) {
            this.displayName = name + " = " + value;
        } else {
            this.displayName = displayName;
        }
    }
    
    public ITRoleMiningEntitlement(Type type, String application, String target) {
        this(type, application, target, null, null, null);
    }
    
    public ITRoleMiningEntitlement(ManagedAttribute managedAttribute, Locale locale) {
        this(!ManagedAttribute.Type.Permission.name().equals(managedAttribute.getType()) ? Type.Attribute : Type.Permission,
             managedAttribute.getApplicationId(),
             managedAttribute.getAttribute(),
             managedAttribute.getDescription(locale), 
             !ManagedAttribute.Type.Permission.name().equals(managedAttribute.getType()) ? managedAttribute.getValue() : null,
             managedAttribute.getDisplayableName());
    }
    
    public String getApplication() {
        return application;
    }
    
    public void setApplication(String application) {
        this.application = application;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getExplanation() {
        return explanation;
    }
    
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
    
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((application == null) ? 0 : application.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((target == null) ? 0 : target.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        ITRoleMiningEntitlement other = (ITRoleMiningEntitlement) obj;
        if (application == null) {
            if (other.application != null)
                return false;
        } else if (!application.equals(other.application))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (target == null) {
            if (other.target != null)
                return false;
        } else if (!target.equals(other.target))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }
    
    public void setType( Type type ) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public static final Comparator<ITRoleMiningEntitlement> ATTRIBUTE_COMPARATOR = new Comparator<ITRoleMiningEntitlement>() {
        public int compare(ITRoleMiningEntitlement o1, ITRoleMiningEntitlement o2) {
            int result;
            if (o1 == null && o2 == null) {
                result = 0;
            } else if (o1 == null) {
                result = -1;
            } else if (o2 == null) {
                result = 1;
            } else {
                String name1 = o1.getName();
                String name2 = o2.getName();
                if (name1 == null && name2 == null) {
                    result = 0;
                } else if (name1 == null) {
                    result = -1;
                } else if (name2 == null) {
                    result = 1;
                } else {
                    result = name1.compareTo(name2);
                }
                
                if (result == 0) {
                    String value1 = o1.getValue();
                    String value2 = o2.getValue();
                    if (value1 == null && value2 == null) {
                        result = 0;
                    } else if (value1 == null) {
                        result = -1;
                    } else if (value2 == null) {
                        result = 1;
                    } else {
                        result = value1.compareTo(value2);
                    }
                }
            }
            
            return result;
        }        
    };
    
    public static final Comparator<ITRoleMiningEntitlement> PERMISSION_COMPARATOR = new Comparator<ITRoleMiningEntitlement>() {
        public int compare(ITRoleMiningEntitlement o1, ITRoleMiningEntitlement o2) {
            int result;
            if (o1 == null && o2 == null) {
                result = 0;
            } else if (o1 == null) {
                result = -1;
            } else if (o2 == null) {
                result = 1;
            } else {
                String target1 = o1.getTarget();
                String target2 = o2.getTarget();
                if (target1 == null && target2 == null) {
                    result = 0;
                } else if (target1 == null) {
                    result = -1;
                } else if (target2 == null) {
                    result = 1;
                } else {
                    result = target1.compareTo(target2);
                }
            }
            
            return result;
        }
    };
}
