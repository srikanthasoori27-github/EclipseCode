/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementation of the visitor pattern to encapsulate 
 * class-specific logic related to exporting XML. 
 */

package sailpoint.server;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Argument;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.LocalizedAttribute;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Policy;
import sailpoint.object.SailPointObject;
import sailpoint.object.Signature;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.object.Visitor;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class ExportVisitor extends Visitor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(ExportVisitor.class);

    private final String SP_CLASS_PREFIX = "sailpoint.object.";
    
    SailPointContext _context;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ExportVisitor(SailPointContext context)  {
        _context = context;
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Explanation
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Explanations can reference an app in the purview field.  That 
     * reference will be an id in the db.  However, we need it referenced 
     * by name in the export file for cross-system transport where the ids
     * won't match, so look up the app and substitute the name for the id.
     * 
     * Explanations can also reference connectors in the purview field.
     * However, connectors are identifiable by the periods in their FQNs.
     *
     * UPDATE: This is no longer needed, as of 6.0 ManagedAttribute has a 
     * formal Application reference.
     */
    public void visitManagedAttribute(ManagedAttribute explanation) 
        throws GeneralException  {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // LocalizedAttribute
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Localized attributes are not required to have a targetClass and targetName
     * for use at runtime, but when we export with the "clean" option
     * we have to add them so we can resolve the targetId on import.
     */
    public void visitLocalizedAttribute(LocalizedAttribute att)
        throws GeneralException {

        if (att.getTargetClass() == null || att.getTargetName() == null) {

            SailPointObject obj = findLocalizedTarget(att);
            if (obj == null) 
                log.error("Unable to resolve target object for LocalizedAttribute: " + 
                          att.getName());
            else {
                // kludge to get around cglib wrappers, need a Util for this
                Class cls = org.hibernate.Hibernate.getClass(obj);
                att.setTargetClass(cls.getSimpleName());
                att.setTargetName(obj.getName());
            }
        }
    }

    /**
     * Converts SailPoint object ids to names in argument map for TaskDefinition.
     */
    @Override
    public void visitTaskDefinition(TaskDefinition obj) throws GeneralException {
        visitSailPointObject(obj);
        
        Signature signature = obj.getEffectiveSignature();

        if ( signature != null ) {
            Map<String,Object> argMap = obj.getArguments();
            List<Argument> args = signature.getArguments();
            for (Argument argument : args) {
                String argName = argument.getName();
                Object value = argMap.get(argName);
                if (value != null) {
                    Class clazz = loadClass(argument.getType());
                    if (clazz != null) {
                        value = ObjectUtil.convertIdsToNames(_context, clazz, value);
                        obj.setArgument(argName, value);
                    }
                }
            }
        }

    }

    /**
     * Converts SailPoint object ids to names in argument map for IdentityTrigger.
     */
    @Override
    public void visitIdentityTrigger(IdentityTrigger obj) throws GeneralException {
        visitSailPointObject(obj);
        
        Attributes<String,Object> map = obj.getParameters();
        if (map != null && !map.isEmpty()) {
            convertIdToNameForAttribute(map, IdentityTrigger.PARAM_WORKFLOW, Workflow.class);
        }
    }

    /**
     * Converts SailPoint object ids to names in argument map for CertificationDefinition.
     */
    @Override
    public void visitCertificationDefinition(CertificationDefinition obj) throws GeneralException {
        visitSailPointObject(obj);
        
        obj.setApplicationIds(ObjectUtil.convertToNames(_context, Application.class, obj.getApplicationIds()));
        obj.setIncludedApplicationIds(ObjectUtil.convertToNames(_context, Application.class, obj.getIncludedApplicationIds()));
        obj.setBusinessRoleIds(ObjectUtil.convertToNames(_context, Bundle.class, obj.getBusinessRoleIds()));
        obj.setOwnerIds(ObjectUtil.convertToNames(_context, Identity.class, obj.getOwnerIds()));
        obj.setIdentitiesToCertify(ObjectUtil.convertToNames(_context, Identity.class, obj.getIdentitiesToCertify()));
    }


    /**
     * Converts SailPoint object ids to names in argument map for TaskSchedule.
     */
    @Override
    public void visitTaskSchedule(TaskSchedule obj) throws GeneralException {
        visitSailPointObject(obj);
        
        Map<String,Object> map = obj.getArguments();
        if (map != null && !map.isEmpty()) {
            convertIdToNameForAttribute(map, TaskSchedule.ARG_CERTIFICATION_DEFINITION_ID, CertificationDefinition.class);
        }
    }


    private Class loadClass(String name) {
        String className = name.replace(SP_CLASS_PREFIX, "");
        return Util.getSailPointObjectClass(className);
    }


    /**
     * Converts object id in the attribute value to name in the map.
     * 
     * @param map The Map contains the attribute
     * @param string the attribute name
     * @param clazz the object class
     * @throws GeneralException 
     */
    private void convertIdToNameForAttribute(Map<String,Object> map,
                                             String attrName, Class clazz) throws GeneralException {

        Object value = map.get(attrName);
        if (value != null && clazz != null) {
            value = ObjectUtil.convertIdsToNames(_context, clazz, value);
            map.put(attrName, value);
        }
    }


    /**
     * Kludge to locate the target object for a LocalizedAttribute.
     * If targetClass is missing there is nothing obvious in the name
     * or the id that says what class this is so we have to probe
     * all of the possible classes.  Should really try to fix the UI
     * or whatever it is that generates these to set the targetClass.
     */
    private SailPointObject findLocalizedTarget(LocalizedAttribute att)
        throws GeneralException {

        SailPointObject found = null;
        
        // either the targetClass or targetName are missing
        // if we had a targetClass we could favor that, but it 
        // complicates the code and in practice you'll never have a 
        // targetClass without a targetName
        if (att.getTargetId() != null) {

            Class[] classes = {Application.class, 
                               Policy.class,
                               Bundle.class};

            for (Class cls : classes) {
                found = _context.getObjectById(cls, att.getTargetId());
                if (found != null)
                    break;
            }
        }

        return found;
    }

}

    
