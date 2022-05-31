/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.PersistenceOptionsUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationPhaseConfig;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Filter;
import sailpoint.object.NotificationConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Tag;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.tools.DateUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This Task is invoked when a user makes a change to a CertificationDefinition for an active Certification.
 * The code applies the necessary changes to each certification and related work items.
 * @author jeff.upton
 */
public class ApplyCertificationDefinitionChangesTask extends AbstractTaskExecutor
{
    public static final String TASK_NAME = "Apply Certification Definition Changes";
    
    public static final String ARG_CERTIFICATION_GROUP_ID = "certificationGroupId";

    private SailPointContext _context;
    
    private String _certificationGroupId;
    
    /**
     * Applies the CertificationDefinition changes to all related Certifications and WorkItems.
     */
    public void execute(SailPointContext context, TaskSchedule schedule,
            TaskResult result, Attributes<String, Object> args)
            throws Exception
    {
        _context = context;
        _certificationGroupId = args.getString(ARG_CERTIFICATION_GROUP_ID);
        
        PersistenceOptionsUtil forcer = new PersistenceOptionsUtil();
        try {
            forcer.configureImmutableOption(_context);
            CertificationSchedule certificationSchedule = loadCertificationSchedule(_certificationGroupId);
            applyDefinitionChanges(certificationSchedule);
        } finally {
            forcer.restoreImmutableOption(_context);
        }
    }
    
    /**
     * Applies changes made to the CertificationDefinition to Certifications in
     * the CertificationGroup.
     */
    private void applyDefinitionChanges(CertificationSchedule schedule)
        throws GeneralException
    {
    	CertificationDefinition definition = schedule.getDefinition();
    	
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("certificationGroups.id", _certificationGroupId));

        Iterator<Certification> certifications = new IncrementalObjectIterator<Certification>(_context, Certification.class, queryOptions);

        while (certifications.hasNext()) {
            Certification certification = certifications.next();
            certification.setCertificationDefinitionId(definition.getId());
            certification.setTags(new ArrayList<Tag>(definition.getTags()));

            Date currentPhaseStart = getCurrentPhaseStart(certification);

            List<CertificationPhaseConfig> phaseConfigs = schedule.getDefinition().createPhaseConfig(_context);
            certification.setPhaseConfig(phaseConfigs);

            CertificationPhaseConfig currentPhaseConfig = certification.getPhaseConfig(certification.getPhase());
            if (currentPhaseStart != null && currentPhaseConfig != null) {
                certification.setNextPhaseTransition(currentPhaseConfig.getDuration().addTo(currentPhaseStart));
            }

            certification.setExpiration(certification.calculateExpirationDate());
            certification.setAutomaticClosingDate(certification.calculateAutomaticClosingDate(_context));

            _context.saveObject(certification);
            _context.commitTransaction();

            updateWorkItems(certification, schedule);

            _context.decache(certification);
        }
    }

    /**
     * Gets the start date of the current phase for the specified Certification.
     * @param certification The certification to use.
     * @return The start date of the current phase, or null it cannot be calculated.
     */
    private Date getCurrentPhaseStart(Certification certification)
    {
        return getPhaseStart(certification, certification.getPhase());
    }
    
    /**
     * Calculates the start date of the specified phase for a certification.
     * @param certification The certification to check.
     * @param phase The phase to check.
     * @return The start date of the specified phase, or null for the Staged phase.
     */
    private Date getPhaseStart(Certification certification, Certification.Phase phase)
    {
        if (Certification.Phase.Staged.equals(phase)) {
            return null;
        }
        
        if (Certification.Phase.Active.equals(phase)) {
            return certification.getActivated();
        }
        
        Certification.Phase previousPhase = getPreviousPhase(certification, phase);
        if (previousPhase != null) {
            CertificationPhaseConfig previousPhaseConfig = certification.getPhaseConfig(previousPhase);
            if (previousPhaseConfig != null) {            
                return previousPhaseConfig.getDuration().addTo(getPhaseStart(certification, previousPhase)); 
            }
        }
        
        throw new IllegalStateException("Unable to determine the start of the specified phase");
    }
    
    /**
     * Gets the previous phase for the specified certification phase.
     * @param certification The certification to check.
     * @param phase The phase to check.
     * @return The previous phase according to the phase config, or null if no previous phase exists.
     */
    private Certification.Phase getPreviousPhase(Certification certification, Certification.Phase phase)
    {
        List<CertificationPhaseConfig> phaseConfig = certification.getPhaseConfig();
        if (phaseConfig != null) {
            for (int i = 0; i < phaseConfig.size(); ++i) {
                if (Util.nullSafeEq(phaseConfig.get(i).getPhase(), phase) && i > 0) {
                    return phaseConfig.get(i - 1).getPhase();
                }
            }
        }
        
        return null;
    }

    /**
     * Updates the work items related to a Certification.
     * @param certification The certification to use.
     * @param schedule The related CertificationSchedule
     * @throws GeneralException
     */
    private void updateWorkItems(Certification certification, CertificationSchedule schedule) 
        throws GeneralException
    {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("certification", certification.getId()));

        Iterator<WorkItem> workItems = new IncrementalObjectIterator<WorkItem>(_context, WorkItem.class, queryOptions);

        while (workItems.hasNext()) {
            WorkItem workItem = workItems.next();

            if (workItem.getType() == WorkItem.Type.Certification || workItem.getType() == WorkItem.Type.Delegation) {
                Date startDate = schedule.getActiveStartDate() == null? DateUtil.getCurrentDate() : schedule.getActiveStartDate();
                Date endDate = certification.getExpiration();

                workItem.setExpiration(endDate);
                reAssignWakeupDate(certification, workItem, Certification.Phase.Active, startDate, endDate);
            }

            if (workItem.getType() == WorkItem.Type.Challenge) {
                workItem.setExpiration(schedule.getChallengeEndDate());
            }

            if (workItem.getType() == WorkItem.Type.Remediation) {
                // TQM: Should it be schedule.getRemediationStartDate()?
                Date startDate = schedule.getActiveStartDate() == null? DateUtil.getCurrentDate() : schedule.getActiveStartDate();
                Date endDate = schedule.getRemediationEndDate();
                
                workItem.setExpiration(endDate);
                reAssignWakeupDate(certification, workItem, Certification.Phase.Remediation, startDate, endDate);
            }

            _context.saveObject(workItem);
            _context.commitTransaction();

            _context.decache(workItem);
        }
    }
    
    private NotificationConfig getNotificationConfig(Certification certification, Certification.Phase phase) 
    {
        NotificationConfig notificationConfig = null;
        CertificationPhaseConfig phaseConfig = certification.getPhaseConfig(phase);
        if (phaseConfig != null) {
            notificationConfig = phaseConfig.getNotificationConfig();
        }
        return notificationConfig;
    }

    /**
     * Assigns the correct wake-up date to a work item using the notification
     * settings for the appropriate phase.
     * @param certification The certification to which the work item belongs.
     * @param workItem The work item to update.
     * @param phase The phase notification setting to use.
     */
    private void reAssignWakeupDate(Certification certification, WorkItem workItem, Certification.Phase phase, Date startDate, Date endDate)
    {
        NotificationConfig newNotificationConfig = getNotificationConfig(certification, phase);
        if (newNotificationConfig != null) {
            NotificationConfig oldNotificationConfig = workItem.getNotificationConfig();

            if (oldNotificationConfig == null) {
                oldNotificationConfig = new NotificationConfig();
                oldNotificationConfig.setConfigs(new ArrayList<NotificationConfig.IConfig>());
            }
            
            newNotificationConfig.setStartDate(startDate);
            newNotificationConfig.setEndDate(endDate);
            workItem.setNotificationConfig(newNotificationConfig);
            
            newNotificationConfig.reAssignWakeupDate(workItem, oldNotificationConfig);
        }
    }
    
    /**
     * Loads a CertificationSchedule object.
     * @param certificationGroupId The certification group.
     * @return The CertificationSchedule.
     */
    private CertificationSchedule loadCertificationSchedule(String certificationGroupId)
        throws GeneralException
    {
        CertificationGroup certificationGroup = _context.getObjectById(CertificationGroup.class, certificationGroupId);
        CertificationDefinition certificationDefinition = certificationGroup.getDefinition();
        
        CertificationSchedule result = new CertificationSchedule(_context, null, certificationDefinition);
        result.setFirstExecution(certificationGroup.getCreated());
        result.setActivated(getActivationDate(certificationGroupId));
        
        return result;        
    }
    
    /**
     * Gets the activiation date off of the first ceritifcation in the group.
     * TODO: Remove duplicate code? This is almost identical to CertificationScheduleService.getActivationDate.
     * The only difference is that this doesn't order the result.
     * @param certificationGroupId The certification group id.
     * @return The activation date, or null if none exists.
     * @throws GeneralException
     */
    private Date getActivationDate(String certificationGroupId)
        throws GeneralException
    {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("certificationGroups.id", certificationGroupId));
        queryOptions.setResultLimit(1);
        
        Iterator<Object[]> rows = _context.search(Certification.class, queryOptions, Arrays.asList("activated"));
        
        if (rows.hasNext()) {
            Object[] row = rows.next();
            return (Date)row[0];
        }        
        
        return null;
    }

    /**
     * Do not handle termination.
     */
    public boolean terminate()
    {
        return false;
    }
   
}
