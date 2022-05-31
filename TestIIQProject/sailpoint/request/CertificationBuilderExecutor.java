/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * The request executor for the new certification builder.
 * 
 * Author: Jeff
 *
 * This just forwards control to a partition handler in sailpoint.certification
 * so the generation code can be kept in one package.  It only handles
 * identity certs, but in theory we could add handlers for other types.
 *
 */

package sailpoint.request;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.certification.PartitionHandler;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.Request;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;

public class CertificationBuilderExecutor extends AbstractRequestExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    static Log log = LogFactory.getLog(CertificationBuilderExecutor.class);

    public static final String DEFINITION_NAME = "Certification Builder";

    /**
     * Name of the CertificationDefinition
     */
    public static final String ARG_DEFINITION = "definition";

    /**
     * Class doing the generation, saved for terminate requests.
     */
    PartitionHandler _handler;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The only argument that is required for all types is the
     * CertificationDefinition id so we can check that here.  All other
     * arguments will be checked by the partition handler.
     */
    @SuppressWarnings("unchecked")
    public void execute(SailPointContext context, Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        try {
            String defName = args.getString(ARG_DEFINITION);
            if (defName == null)
                throw new GeneralException("Missing CertificationDefinition name");

            CertificationDefinition def = context.getObjectByName(CertificationDefinition.class, defName);
            if (def == null)
                throw new GeneralException("Invalid CertificationDefinition name: " + defName);

            TaskMonitor monitor = new TaskMonitor(context, request, this);
        
            // forward the bulk of the processing to a handler
            // if we add more cert types to the new framework, we can use
            // the same executor for all of them

            _handler = new PartitionHandler(context, monitor, request, args, def);
            _handler.execute();
        }
        catch (Throwable t) {
            throw new RequestPermanentException(t);
        }
    }
        
    /**
     * Pass the termination request along to the PartitionHandler.
     */
    public boolean terminate() {
        if (_handler != null)
            _handler.terminate();
        return true;
    }

}
