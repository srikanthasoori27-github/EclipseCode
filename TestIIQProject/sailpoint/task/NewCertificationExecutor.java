/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Temporary executor to generate new "Focused" certifications until
 * the old UI that schedulers CertificationExecutor is available.
 *
 * Author: Jeff
 *
 * I sort of like being able to do it this way anyway as an alternative
 * for having to pre-create CertificationDefinition.
 * 
 * Not supporting termination since all this does is query for ids
 * and make partitions, which should be relatively fast.
 */

package sailpoint.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.certification.IdentityCertificationStarter;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;


public class NewCertificationExecutor extends AbstractTaskExecutor {

    private static final Log log = LogFactory.getLog(NewCertificationExecutor.class);

    /**
     * Default constructor.
     */
    public NewCertificationExecutor() {
    }

    public boolean terminate()
    {
        // TODO: if creating partitions gets too long will want to
        // support termination
        return false;
    }

    /**
     * IdentityCertificationBuilder does the work.
     */
    public void execute(SailPointContext context, TaskSchedule sched,
                        TaskResult result,
                        Attributes<String, Object> args) throws Exception {


        // For now, this is expected to be used with all options in
        // the TaskDefinition rather than the TaskSchedule
        // Probably will want to be able to merge schedule/definition args
        // like we do with other tasks

        IdentityCertificationStarter ics = new IdentityCertificationStarter(context, sched, result, args);
        ics.execute();

    }

}
