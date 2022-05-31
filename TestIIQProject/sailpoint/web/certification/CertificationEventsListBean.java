/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.certification;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationScheduler;
import sailpoint.api.CertificationTriggerHandler;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.QueryOptions;
import sailpoint.object.CertificationDefinition.CertifierSelectionType;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.trigger.BaseIdentityTriggersListBean;
import sailpoint.web.util.NavigationHistory;


/**
 * Bean for listing IdentityTriggers for certifications.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationEventsListBean extends BaseIdentityTriggersListBean {

    private static final Log LOG = LogFactory.getLog(CertificationEventsListBean.class);
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHOD OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Override
    protected void addHandlerFilter(QueryOptions qo) {
        qo.add(Filter.eq("handler", CertificationTriggerHandler.class.getName()));
    }

    @Override
    protected String getDeletedMessageKey() {
        return MessageKeys.CERT_EVENT_DELETED;
    }
    
    /**
     * Return the column configuration for the grid.
     */
    @Override
    public List<ColumnConfig> getColumns() throws GeneralException {
        return super.getUIConfig().getCertificationEventsTableColumns();
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Create a new event.
     */
    public String newEventAction() throws GeneralException {

        CertificationSchedule schedule = new CertificationSchedule(getContext(), getLoggedInUser());
        schedule.getDefinition().setType(Certification.Type.Identity);
        schedule.getDefinition().setCertifierSelectionType(CertifierSelectionType.Manager);

        // Get a schedule bean with the defaults.
        CertificationScheduleDTO newCertSchedule = new CertificationScheduleDTO(getContext(), schedule);

        // Put the right stuff in the session before transitioning pages.
        CertificationScheduleBean.newEvent(getSessionScope(), newCertSchedule);

        NavigationHistory.getInstance().back();
        return "createEvent";
    }

    /**
     * Edit the selected event.
     */
    public String editEventAction() {
        try {
            CertificationScheduler scheduler = new CertificationScheduler(getContext());
            IdentityTrigger trigger = getContext().getObjectById(IdentityTrigger.class, getSelectedId());
            if (trigger == null) {
                Message msg = new Message(Message.Type.Error,
                        MessageKeys.ERR_CERT_EVENT_DOES_NOT_EXIST);
                addMessageToSession(msg, null);
                return null;
            } else {
                CertificationDefinition def = trigger.getCertificationDefinition(getContext());
                // todo jfb - shouldnt this already be set
                def.setType(Certification.Type.Identity);
                // todo jfb - who is the scheduler?
                CertificationSchedule schedule = new CertificationSchedule(getContext(), null, def);
                CertificationScheduleDTO editedCertSchedule = new CertificationScheduleDTO(getContext(), schedule);

                // Put the right stuff in the session before transitioning pages.
                CertificationScheduleBean.editEvent(getSessionScope(), editedCertSchedule, trigger.getId());
            }
            NavigationHistory.getInstance().back();
        } catch (GeneralException e) {
            Message msg = new Message(Message.Type.Error, MessageKeys.ERR_CERT_EVENT_UNAVAIL);
            addMessageToSession(msg, null);
            LOG.error(msg.getLocalizedMessage(), e);
            return null;
        }

        return "editEvent";
    }
}
