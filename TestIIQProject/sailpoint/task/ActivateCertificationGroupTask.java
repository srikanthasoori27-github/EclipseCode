/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.util.Arrays;
import java.util.Iterator;

import sailpoint.api.Certificationer;
import sailpoint.api.Decacher;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

public class ActivateCertificationGroupTask extends AbstractTaskExecutor
{
    public final static String ARG_CERTIFICATION_GROUP_ID = "certificationGroupId";
    
    private SailPointContext context;
    
    private CertificationGroup certificationGroup;

    public void execute(SailPointContext context, TaskSchedule schedule,
            TaskResult result, Attributes<String, Object> args)
            throws Exception
    {
        this.context = context;
        
        String certificationGroupId = args.getString(ARG_CERTIFICATION_GROUP_ID);
        certificationGroup = context.getObjectById(CertificationGroup.class, certificationGroupId);
        updateStatus(CertificationGroup.Status.Activating);
        
        Certificationer certificationer = new Certificationer(context);
        
        Iterator<Certification> certifications = getCertifications();
        
        Decacher decacher = new Decacher(context);
        
        while (certifications.hasNext()) {
            Certification certification = certifications.next();
            certificationer.activate(certification.getId());
            
            decacher.increment();
        }        
        
        updateStatus(CertificationGroup.Status.Active);
    }
    
    /**
     * Updates the status for the certification group. This method saves the CertificationGroup and commits
     * the current transaction.
     *
     * @param status The new status.
     */
    private void updateStatus(CertificationGroup.Status status)
        throws GeneralException
    {
        certificationGroup.setStatus(status);
        context.saveObject(certificationGroup);
        context.commitTransaction();        
    }
    
    /**
     * Gets the certifications for this certification group.
     * @return An iterator to the list of certifications.
     * @throws GeneralException
     */
    private Iterator<Certification> getCertifications()
        throws GeneralException
    {
        QueryOptions options = new QueryOptions();
        options.add(Filter.containsAll("certificationGroups", Arrays.asList(certificationGroup)));
        //MEH 16349 we only want the parental unit(s), activate will recurse into children.
        options.add(Filter.isnull("parent"));
        options.setCloneResults(true);
        return context.search(Certification.class, options);
    }

    /**
     * Do not handle termination
     */
    public boolean terminate()
    {
        return false;
    }

}
