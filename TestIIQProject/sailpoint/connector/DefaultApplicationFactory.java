/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.connector;

import java.util.List;

import sailpoint.api.SailPointFactory;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.ConnectorConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * The DefaultApplicationFactory factory is used to help
 * the UI tier build Applications seeded with default 
 * configuration.
 *
 * Starting in 5.2 this class moved from using the
 * ConnectorRegistry/ConnectorConfig objects to using
 * the stored "template" applications stored in the 
 * connector registry. 
 *
 * The ConnectorConfig object encapsulated much of the
 * same data that is stored on the Application, so 
 * its a natural progression. Now all of the data
 * can be found on the template applications.
 *
 * The template applications are the basis for all
 * new applications. Instead of calling the connector
 * to get the default objects, the xml representation
 * is used.  The template objects live in the ConnectorRegistry
 * Configuration object and are not "real" hibernate
 * objects and will not appear when querying IIQ db.
 */
@SuppressWarnings("deprecation")
public class DefaultApplicationFactory {

    /**
     * Given a the name of an application template return the default 
     * representation of the application defined in the registry.
     * 
     * The default application will be a cloned copy of the Template
     * application with the name removed and the Template reference
     * set to point back to the template from which it was derived.
     * 
     * This method looks up the default Application setting by
     * checking the template applications for a matching type.
     */ 
    public static Application getDefaultApplicationByTemplate(String templateName) 
        throws GeneralException {

        Application defaultApp = null;
        
        Application template = getTemplateByName(templateName);
        if ( template == null ) 
            template = getTemplateByType(templateName);
        
        if ( template != null ) {
            defaultApp = (Application) XMLObjectFactory.getInstance().cloneWithoutId(template, SailPointFactory.getCurrentContext());            
            Connector connector = ConnectorFactory.getConnector(defaultApp, null);
            Attributes<String,Object> mergedValues = new Attributes<String,Object>();
            if ( connector != null ) {
                // seed the default values if they are published by the connector
                List<AttributeDefinition> defs = connector.getDefaultAttributes();
                if ( Util.size(defs) > 0 ) {
                    Attributes<String,Object> defaultValues = deriveDefaultValues(defs);
                    if ( !Util.isEmpty(defaultValues) ) {
                        mergedValues.putAll(defaultValues);
                    }
                }
            }
            // Move over any default attributes defined on the template app
            // and remove formPath so the parent' holds the form.  
            Attributes<String,Object> templateValues = template.getAttributes();
            if ( !Util.isEmpty(templateValues) ) {
                mergedValues.putAll(templateValues);
                mergedValues.remove(Application.ATTR_FORM_PATH);
            }
            if ( !mergedValues.isEmpty() ) {
                defaultApp.setAttributes(mergedValues);
            }
            // null out the name
            defaultApp.setName(null);
            // We prefer the template to hold the form so null out in default
            defaultApp.setFormPath(null);
            defaultApp.setFormPathRules(null);
            defaultApp.setTemplateApplication(template.getName());
        }
        return defaultApp;
    }
        
    /**
     * Get the list of the AttributeDefinitions published by the 
     * connector listed on the named template.
     * 
     * Older connectors (pre 5.2) defined these attributes on
     * the connectorClass, now the template applications define
     * a formPath that points to a custom .xhtml file that 
     * drives the configuration of the connector.
     *  
     * @param templateName
     * @return
     * @throws GeneralException
     */
    public static List<AttributeDefinition> getDefaultConfigAttributesByTemplate(String templateName) 
        throws GeneralException {        
    
        Application template = getTemplateByName(templateName);        
        if ( template == null ) {
            template = getTemplateByType(templateName);            
        }

        String clazzName = ( template != null ) ? template.getConnector() : null;                
        return ( clazzName != null ) ? getDefaultAttributes(clazzName) : null;                
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Get the template application fro the ConnectorRegistry given the name
     * of the template.
     */
    public static Application getTemplateByName(String name) 
        throws GeneralException {

        List<Application> templates = getTemplates();        
        if ( Util.size(templates) >  0 ) {
            for ( Application app : templates )  {
                String templateName  = app.getName();
                if ( Util.nullSafeEq(templateName, name ) )
                    return app;
            }
        }        
        return null;
    }
    
    /**
     * Get all of the application listed in the ConnectorRegistry
     * under the key 'applicationTemplates'.
     */    
    public static Application getTemplateByType(String type) 
        throws GeneralException {

        List<Application> templates = getTemplates();        
        if ( Util.size(templates) >  0 ) {
            for ( Application app : templates )  {
                String templateType = app.getType();
                if ( Util.nullSafeEq(templateType, type ) )
                    return app;
            }
        }        
        return null;
    }

    private static Attributes<String,Object> deriveDefaultValues(List<AttributeDefinition> defs) {
        // Set the default values in the map for convenience
        Attributes<String,Object> defaultValues = new Attributes<String,Object>();
        if ( defs != null ) {
            for ( AttributeDefinition def : defs ) {
                String name = def.getInternalOrName();
                Object value = def.getDefaultValue();
                if ( ( name != null ) && ( value != null ) ) {
                    defaultValues.put(name, value);
                }
            }
        }
        return defaultValues;
    }
    
    /**
     * Dig into the ConnectorConfig and get all of the registered application templates.     
     */
    private static List<Application> getTemplates() throws GeneralException {

        List<Application> templates = null;
        Configuration connectorRegistry = SailPointFactory.getCurrentContext().getObjectByName(Configuration.class, Configuration.CONNECTOR_REGISTRY);
        if ( connectorRegistry != null ) {
            templates = (List<Application>)connectorRegistry.getList(Configuration.APPLICATION_TEMPLATES);
        }
        return templates;
    }
        
    ///////////////////////////////////////////////////////////////////////////
    //
    // Depcrecated method use alternatives when possible.
    //
    //  As of 5.2 we moved to Template application concept which replaces
    //  many of the methods to help configure a default application.
    //
    // At some point these methods may become unreliable or removed.
    //    
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Return a new Application object with default settings using the
     * class name.
     * 
     * Starting in 5.2 using the connnectorClass to create new applications 
     * has been deprecated and all applications should be created 
     * using the type specified in the ConnectorRegistry.
     *
     * @see #getDefaultApplicationByTemplate(String)
     *
     */
    @Deprecated
    public static Application getDefaultApplication(String className) 
        throws GeneralException {

        Application application = new Application();
        application.setConnector(className);
        Connector connector = ConnectorFactory.getConnector(application, null);

        List<AttributeDefinition> defs = connector.getDefaultAttributes();
        if ( Util.size(defs) > 0 ) {
            Attributes<String,Object> defaultValues = deriveDefaultValues(defs);
            application.setAttributes(defaultValues);
        }

        String typeString = connector.getConnectorType();
        application.setName("New " + typeString + " Application");
        application.setType(typeString);

        application.setSchemas(connector.getDefaultSchemas());
        application.setFeatures(connector.getSupportedFeatures());

        return application;
    }
        
    /**
     * Return a List of Attribute Definitions defined by a
     * Connector class. <p>
     *
     * This is the OLD way of describing attributes to the UI tier. In 
     * new connector they define a xhtml form that will be used to 
     * custom render any of the required configuration attributes. 
     * This has been deprecated, because a custom form gives  us
     * better looking ui's and can describe field to field relationships
     * unlike a flat list of attribute definitions.
     *
     * The xhtml file is kept in the ConnectorRegistry configuration 
     * object with the template application objects.
     * <p>
     * 
     * Starting in 5.2 we'll started passing in the application type instead
     * of the connector class so we can support multiple application types
     * per class.  
     */
    @Deprecated
    public static List<AttributeDefinition> getDefaultConfigAttributes(String className) 
        throws GeneralException {        
        
        return getDefaultAttributes(className);
    }   
        
    /**
     * Depreciated way to get default attributes from a Connector. 
     * This has been replaced with defining an .xhtml page on the 
     * Application ( formPath ) that handles the collection of the
     * configuration attributes.
     *  
     * @param clazzName
     * @return
     */
    @Deprecated
    private static List<AttributeDefinition> getDefaultAttributes(String clazzName)
        throws GeneralException {
        
        // Setup a dummy application and call the method on the connector class
        Application application = new Application();
        application.setConnector(clazzName);
        //  instances not relevant here...
        Connector connector = ConnectorFactory.getConnector(application, null);
        return (connector != null) ? connector.getDefaultAttributes() : null;
    }
    
    /**
     * Dig into the ConnectorConfig and get all of the registered connectors.     
     */
    private static List<ConnectorConfig> getConfigs() throws GeneralException {

        List<ConnectorConfig> configs = null;
        Configuration connectorRegistry = SailPointFactory.getCurrentContext().getObjectByName(Configuration.class, Configuration.CONNECTOR_REGISTRY);
        if ( connectorRegistry != null ) {
            configs = (List<ConnectorConfig>)connectorRegistry.getList(Configuration.INSTALLED_CONNECTORS);
        }
        return configs;
    }
    

    /**
     * Return the name of the template that was found for the defined 
     * connector. This method is used by upgraders and the 
     * import visitor to fix up existing application as they are 
     * imported. This only work in cases where there is a 
     * single template per connector class.
     * 
     *  This method is depreciated because from 5.2 on, because
     *  the one to one relationship assumed between connector and
     *  application type is no longer valid.
     *  
     */
    @Deprecated
    public static String lookupTemplateByConnector(String connector) 
        throws GeneralException {

        Configuration connectorRegistry = SailPointFactory.getCurrentContext().getObjectByName(Configuration.class, Configuration.CONNECTOR_REGISTRY);
        if ( connectorRegistry != null ) {
            List<Application> templates = (List<Application>)connectorRegistry.getList(Configuration.APPLICATION_TEMPLATES);
            if ( Util.size(templates) > 0 ) {
                for ( Application template: templates ) {
                    String connectorClass = template.getConnector();
                    if ( Util.nullSafeEq(connectorClass, connector) ) {
                        return template.getName();
                    }
                }
            }
        }
        return null;
    }
}
