/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

public enum IdentityProperty {
    NAME( "name", String.class ),
    INACTIVE( "inactive", Boolean.class );
    
    public String getPropertyName() {
        return propertyName;
    }
    
    public Class<?> getPropertyClass() {
        return propertyClass;
    }
    
    private IdentityProperty( String propertyName, Class<?> propertyClass ) {
        this.propertyName = propertyName;
        this.propertyClass = propertyClass;
    }
    
    private final Class<?> propertyClass;
    private final String propertyName;    
}
