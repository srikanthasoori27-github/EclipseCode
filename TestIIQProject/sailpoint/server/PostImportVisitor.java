/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Describer;
import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Describable;
import sailpoint.object.LocalizedAttribute;
import sailpoint.object.Policy;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.Visitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Visitor that is called after an object is imported.  This should be used for
 * logic that requires an object to be persisted before it can be run.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class PostImportVisitor extends Visitor {

    private SailPointContext _context;
    private static Log log = LogFactory.getLog(PostImportVisitor.class);

    /**
     * Constructor.
     */
    public PostImportVisitor(SailPointContext context) {
        _context = context;
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Scope
    //  
    //////////////////////////////////////////////////////////////////////

    public void visitScope(Scope scope) throws GeneralException {
        
        // Paths must be updated after import because they require IDs to
        // already have been set.
        scope.updateSubtreePaths();
        
        // Consider this scope dirty.  If an object is imported that references
        // this scope before this scope is imported, a scope is created without
        // a path.  Now that the path has been calculated, mark this as dirty.
        new ScopeService(_context).queueDirtyScope(scope, false);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Bundle
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * send the description values to the localized attribute table
     */
    public void visitBundle(Bundle bundle) 
            throws GeneralException {
        visitDescribable(bundle);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Application
    //  
    //////////////////////////////////////////////////////////////////////

    /** send the description values to the localized attribute table     */
    
    public void visitApplication(Application application) 
            throws GeneralException {
        visitDescribable(application);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Policy
    //  
    //////////////////////////////////////////////////////////////////////

    /** send the description values to the localized attribute table **/
    
    public void visitPolicy(Policy policy) 
            throws GeneralException {
        visitDescribable(policy);
    }
    
    public void visitLocalizedAttribute(LocalizedAttribute localizedAttribute) 
        throws GeneralException {
        try {
            Describer.updateDescription(_context, localizedAttribute);
        } catch (ClassNotFoundException e) {
            throw new GeneralException("Failed to update the target object for LocalizedAttribute: " + localizedAttribute.toString(), e);
        }
    }
    
    private void visitDescribable(Describable describable) {
        if (describable != null) {
            Map<String, String> descriptions = describable.getDescriptions();
            Configuration systemConfig = Configuration.getSystemConfig();
            String defaultLocale = Localizer.getDefaultLocaleName(systemConfig);
            
            // Handle legacy descriptions
            String standAloneDescription = ((SailPointObject)describable).getDescription();
            // Note that we're not going to bother nulling out the description because it's not going to be persisted anywhere.
            // The column in which it used to go is gone from the database and it will never be exported because its serialization
            // mode has the legacy="true" annotation on it
            if ((Util.isEmpty(descriptions) || !descriptions.containsKey(defaultLocale)) && !Util.isNullOrEmpty(standAloneDescription)) {
                describable.addDescription(defaultLocale, standAloneDescription);
            }
            // Otherwise nothing needs to be done.  In practice the setter should be correcting inconsistencies for descriptions

            // Apply descriptions to localized attributes
            if (!Util.isEmpty(descriptions)) {
                Describer describer = new Describer(describable);
                describer.saveLocalizedAttributes(_context);
            }
        }        
    }
}
