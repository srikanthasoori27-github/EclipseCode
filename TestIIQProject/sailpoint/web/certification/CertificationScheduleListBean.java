/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationScheduler;
import sailpoint.api.TaskManager;
import sailpoint.api.Terminator;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.ColumnConfig;
import sailpoint.object.GridState;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.redirect.CertificationScheduleRedirectFilter;
import sailpoint.web.task.TaskScheduleListBean;
import sailpoint.web.util.NavigationHistory;

/**
 * JSF UI bean used for listing certifications.
 *
 */
public class CertificationScheduleListBean
        extends TaskScheduleListBean implements NavigationHistory.Page{
    private static final Log log = LogFactory.getLog(CertificationScheduleListBean.class);
    private static final String GRID_STATE = "certificationSchedulesGridState";

    private String newCertificationType;
    private GridState gridState;
    private String templateCertId;

    public CertificationScheduleListBean() {
        super();
    }

    public int getCertCount() throws GeneralException {
        final int numCerts;

        List<TaskSchedule> list = getObjects();
        if (list == null) {
            numCerts = 0;
        } else {
            numCerts = list.size();
        }

        return numCerts;
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException{
        QueryOptions qo = super.getQueryOptions();
        qo.setScopeResults(true);
        return qo;
    }

    /** performs filtering on the task results that we can't do through sql **/
    @Override
    protected void doFilter() throws GeneralException{
        if(objects!=null) {
            /** Filter by Result Type **/
            for(Iterator<TaskSchedule> iter = objects.iterator(); iter.hasNext();) {
                TaskSchedule object = iter.next();
                TaskDefinition def = object.getDefinition(getContext());

                // Need to leave schedules that have no cron expressions since
                // continuous certifications will be in this state.  Filter out
                // any lingering schedules created by the "run now" with the
                // immediate task runn.
                if (TaskManager.IMMEDIATE_SCHEDULE.equals(object.getDescription())) {
                    iter.remove();
                    continue;
                }

                if (def != null) {
                    TaskDefinition.Type type = def.getEffectiveType();
                    List<Capability> caps = getLoggedInUserCapabilities();
                    Collection<String> rights = getLoggedInUserRights();

                    if(type==null || !(type.equals(TaskDefinition.Type.Certification))) {
                        iter.remove();
                        continue;
                    } else if (doAuthorization &&
                            !Authorizer.hasAccess(caps, rights, def.getRights())) {
                        iter.remove();
                        continue;
                    }
                } else {
                    iter.remove();
                    continue;
                }

                if (!checkResult(object)) {
                    iter.remove();
                    continue;
                }
            }
        }
    }

    public String newCertificationAction() throws GeneralException, UnsupportedEncodingException {
        String outcome = "createCertification";

        CertificationDefinition templateDefinition = null;
        Certification.Type type;

        if (!Util.isNullOrEmpty(getTemplateCertId())) {
            // Using a certification group as template, load the definition to get the type
            CertificationGroup templateCertGroup = getContext().getObjectById(CertificationGroup.class, getTemplateCertId());
            templateDefinition = templateCertGroup.getDefinition();
            type = templateDefinition.getType();
        } else {
            // Otherwise new certification from the menu, get the type directly
            type = Certification.Type.valueOf(getNewCertificationType());
        }

        if (CertificationScheduleRedirectFilter.isNewScheduleType(type)) {
            // This will be handled by the CertificationScheduleRedirectFilter so we do not
            // need to load anything or modify the session
            outcome = buildOutcomeWithParameter(outcome, true, type, null, getTemplateCertId());
            NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
        } else {
            CertificationSchedule schedule = null;
            CertificationScheduler scheduler = new CertificationScheduler(getContext());

            if (templateDefinition != null) {
                schedule = scheduler.initializeScheduleBean(getLoggedInUser(), templateDefinition.getType());

                CertificationDefinition newDefinition = templateDefinition.createCopy();
                newDefinition.setId(null);
                newDefinition.setCreated(new Date());
                newDefinition.setName(null);
                newDefinition.setOwner(getLoggedInUser());

                schedule.setDefinition(newDefinition);
            } else {
                schedule = scheduler.initializeScheduleBean(getLoggedInUser(),
                        Certification.Type.valueOf(getNewCertificationType()));
            }

            CertificationScheduleDTO newCertSchedule = new CertificationScheduleDTO(getContext(), schedule);

            // Put the right stuff in the session before transitioning pages.
            CertificationScheduleBean.newSchedule(getSessionScope(), newCertSchedule);
            NavigationHistory.getInstance().back();
        }

        return outcome;
    }

    public String editScheduledCertificationAction() throws UnsupportedEncodingException {
        String outcome = "editCertification";

        try {
            CertificationScheduler scheduler = new CertificationScheduler(getContext());
            TaskSchedule input = getContext().getObjectById(TaskSchedule.class, getSelectedId());
            if (input == null) {
                Message msg = new Message(Message.Type.Error, MessageKeys.ERR_CERT_DOES_NOT_EXIST);
                addMessageToSession(msg, null);
                return null;
            } else {
                CertificationSchedule schedule = scheduler.getCertificationSchedule(input);
                Certification.Type type = schedule.getDefinition().getType();
                if (CertificationScheduleRedirectFilter.isNewScheduleType(type)) {
                    // This will be handled by the CertificationScheduleRedirectFilter so we do not
                    // need to load anything or modify the session
                    outcome = buildOutcomeWithParameter(outcome, false, type, getSelectedId(), null);
                    NavigationHistory.getInstance().saveHistory((NavigationHistory.Page) this);
                } else {
                    CertificationScheduleDTO editedCertSchedule = new CertificationScheduleDTO(getContext(), schedule);
                    // Put the right stuff in the session before transitioning pages.
                    CertificationScheduleBean.editSchedule(getSessionScope(), editedCertSchedule);
                    NavigationHistory.getInstance().back();
                }
            }
        } catch (GeneralException e) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.ERR_TASK_SCHED_UNAVAIL);
            addMessageToSession(msg, null);
            log.error(msg.getLocalizedMessage(), e);
            return null;
        }

        return outcome;
    }

    /**
     * Build an outcome string with parameters used by the redirect filter
     * @param outcome Base outcome string
     * @param isNew If true, we are creating a new schedule
     * @param type Type of the new Certification schedule
     * @param scheduleId String identifier for the task schedule for editing existing schedule
     * @param certGroupId ID for the certification group
     * @return Outcome string with all parameters
     */
    private String buildOutcomeWithParameter(String outcome, boolean isNew, Certification.Type type, String scheduleId, String certGroupId) throws UnsupportedEncodingException {
        StringBuilder stringBuilder = new StringBuilder(outcome);
        stringBuilder.append("?");
        stringBuilder.append(CertificationScheduleRedirectFilter.PARAMETER_TYPE);
        stringBuilder.append("=");
        stringBuilder.append(type);
        if (isNew) {
            stringBuilder.append("&");
            stringBuilder.append(CertificationScheduleRedirectFilter.PARAMETER_IS_NEW);
            stringBuilder.append("=");
            stringBuilder.append(true);
        }
        if (!Util.isNullOrEmpty(scheduleId)) {
            stringBuilder.append("&");
            stringBuilder.append(CertificationScheduleRedirectFilter.PARAMETER_SCHEDULE_ID);
            stringBuilder.append("=");
            stringBuilder.append(URLEncoder.encode(scheduleId, "UTF-8"));
        }
        if (!Util.isNullOrEmpty(certGroupId)) {
            stringBuilder.append("&");
            stringBuilder.append(CertificationScheduleRedirectFilter.PARAMETER_CERT_GROUP);
            stringBuilder.append("=");
            stringBuilder.append(certGroupId);
        }

        return stringBuilder.toString();
    }

    /**
     * Overridden to use the Terminator.  I'm not sure if this is safe for the
     * super class TaskScheduleListBean, so we'll override.
     */
    @Override
    public String delete() throws GeneralException {
        String selected = super.getSelectedId();
        if (null == selected)
            throw new GeneralException(MessageKeys.ERR_NO_DEF_SELECTED);

        TaskSchedule obj =
                (TaskSchedule)getContext().getObjectByName(TaskSchedule.class, selected);
        if (obj != null) {
            log.info("Deleting task: " + obj.getName());
            Terminator t = new Terminator(getContext());
            getContext().attach(obj);
            t.deleteObject(obj);
            getContext().commitTransaction();
            addMessage(new Message(Message.Type.Info,
                    MessageKeys.SCHEDULED_DELETED, obj.getName()), null);
        }
        return null;
    }

    public List<SelectItem> getCertTypes() throws GeneralException {
        Certification.Type[] types = Certification.Type.values();
        ArrayList<SelectItem> typeStrings = new ArrayList<SelectItem>(types.length);
        // Pull any "new" cert types to the top, we may need some other criteria if the set of these
        // continues to evolve over time.
        Arrays.sort(types, Comparator.comparing((Certification.Type type)-> !CertificationScheduleRedirectFilter.isNewScheduleType(type)));

        for (Certification.Type type : types) {
            try {
                // Filter out Identity certifications for now .
                if (Certification.Type.Identity == type) {
                    continue;
                }

                // Also filter deprecated types
                Field field = Certification.Type.class.getField(type.name());
                if (field != null && field.isAnnotationPresent(Deprecated.class)) {
                    continue;
                }

                String label = getMessage(type.getMessageKey());
                typeStrings.add(new SelectItem(type.toString(), label));

            } catch (NoSuchFieldException ex) {
                throw new GeneralException(ex);
            }
        }

        return typeStrings;
    }

    public List<SelectItem> getCertPhases() {
        Certification.Phase[] phases = Certification.Phase.values();
        ArrayList<SelectItem> phaseStrings = new ArrayList<SelectItem>(phases.length);

        for (int i = 0; i < phases.length; ++i) {
            String label = getMessage(phases[i].getMessageKey());
            phaseStrings.add(new SelectItem(phases[i].toString(), label));
        }

        return phaseStrings;
    }

    public String getNewCertificationType() {
        return newCertificationType;
    }

    public void setNewCertificationType(String newCertType) {
        newCertificationType = newCertType;
    }

    public String getTemplateCertId() {
        return templateCertId;
    }

    public void setTemplateCertId(String templateCertId) {
        this.templateCertId = templateCertId;
    }

    public GridState getGridState() {
        if(gridState==null) {
            gridState = loadGridState(GRID_STATE);
        }

        return gridState;
    }

    @Override
    public List<ColumnConfig> getColumns() {

        List<ColumnConfig> cols = super.getColumns();

        //Add latestResultId to the fields
        ColumnConfig cc = new ColumnConfig("latestResultId","latestResultId");
        cc.setFieldOnly(true);
        cols.add(cc);

        return cols;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHandler.Page interface
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getNavigationString() {
        return "viewCertificationSchedules";
    }

    public String getPageName() {
        return "Scheduled Certifications";
    }

    // No state.
    public Object calculatePageState() { return null; }
    public void restorePageState(Object state) {}
}
