/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.task;

import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationGroup;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;

/**
 * Task executor used to prune certifications. Created to help automation tests.
 *
 * @author patrick.jeong
 */
public class PruneCertificationsExecutor extends AbstractTaskExecutor {

    /**
     * Cleanup certifications and certification groups.
     *
     * @param context
     * @param schedule
     * @param result
     * @param args
     * @throws Exception
     */
    public void execute(SailPointContext context, TaskSchedule schedule,
                        TaskResult result, Attributes<String, Object> args)
            throws Exception {

        context.decache();

        // Allow deletion of electronically signed certs as well
        PersistenceOptions po = new PersistenceOptions();
        po.setAllowImmutableModifications(true);
        context.setPersistenceOptions(po);

        Terminator terminator = new Terminator(context);
        terminator.deleteObjects(Certification.class, null);
        terminator.deleteObjects(CertificationGroup.class, null);
    }

    /**
     * Do not handle termination
     *
     * @return
     */
    public boolean terminate() {
        return false;
    }

}