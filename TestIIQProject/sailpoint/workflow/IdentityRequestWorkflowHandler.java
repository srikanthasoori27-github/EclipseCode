/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 * Author: Dan
 *
 */

package sailpoint.workflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.IdentityRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 *
 *
 *
 */
public class IdentityRequestWorkflowHandler extends StandardWorkflowHandler {
    
    
    private static Log log = LogFactory.getLog(IdentityRequestWorkflowHandler.class);
    
    IdentityRequestLibrary _lib;
    
    public IdentityRequestWorkflowHandler() {
        _lib = new IdentityRequestLibrary();
    }

    @Override
    public void endStep(WorkflowContext wfc) 
        throws GeneralException {
         
        // always call super first
        super.endStep(wfc);        
        Attributes<String,Object> args = wfc.getArguments();
        if ( args != null ) {
            if ( Util.getString(args, IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID) == null ) {
                ProvisioningProject project = (ProvisioningProject)Util.get(args, IdentityRequestLibrary.ARG_PROJECT);
                if ( project != null ) {                
                    IdentityRequest ir = (IdentityRequest)_lib.createIdentityRequest(wfc);
                    if ( ir != null ) {
                        WorkflowCase root = wfc.getRootWorkflowCase();
                        // unlikely but guard
                        if ( root != null ) {
                            // store this on the workflow case
                            root.put(IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID, ir.getId());
                        }
                    } 
                }         
            }            
        }        
    }
        
    @Override
    public void endWorkflow(WorkflowContext wfc)
            throws GeneralException   {
        
        super.endWorkflow(wfc);
        Attributes<String,Object> args = wfc.getArguments();
        if ( args != null ) {
            if ( Util.getString(args, IdentityRequestLibrary.ARG_IDENTITY_REQUEST_ID) != null ) {
                _lib.completeIdentityRequest(wfc);
            }
        }
    }
    
}
