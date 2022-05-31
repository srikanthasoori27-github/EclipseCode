/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationGroup;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

/**
 * Cancels a CertificationGroup
 * 
 * @author jeff.upton
 */
public class CancelCertificationGroupTask extends AbstractTaskExecutor
{

    public final static String ARG_CERTIFICATION_GROUP_ID = "certificationGroupId";
    
    /**
     * Cancels the specified certification group.
     */
    public void execute(SailPointContext context, TaskSchedule schedule,
            TaskResult result, Attributes<String, Object> args)
            throws Exception
    {        
        String certificationGroupId = args.getString(ARG_CERTIFICATION_GROUP_ID);
        CertificationGroup certificationGroup = context.getObjectById(CertificationGroup.class, certificationGroupId);
        
        if (CertificationGroup.Status.Staged != certificationGroup.getStatus()) {
            throw new GeneralException("Unable to cancel a non-staged certification");
        }
        
        certificationGroup.setStatus(CertificationGroup.Status.Canceling);
        context.saveObject(certificationGroup);
        context.commitTransaction();
        
        Terminator terminator = new Terminator(context);
        terminator.deleteObject(certificationGroup);
    }

    /**
     * Do not handle termination
     */
    public boolean terminate()
    {
        return false;
    }

}
