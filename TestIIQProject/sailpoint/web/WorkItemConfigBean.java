/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A helper bean used to assist in the editing of WorkItemConfig objects.
 * This is a little odd in that it isn't a BaseObjectBean.  WorkItemConfigs
 * are currently not autonomous things, they are owned by something
 * like a TaskDefinition and are edited in the context of editing the
 * owning object.  We don't have a particularly good way to manage
 * cooperating BaseObjectBeans so we let TaskDefinitionBean (or whatever)
 * be in control.  
 *
 * We provide a few helper methods to edit object references.
 * This should be converted to WorkItemConfigDTO to fit in better
 * with the DTO model for policies.
 *
 * Author: Jeff
 */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.WorkItemConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class WorkItemConfigBean extends BaseDTO
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static Log log = LogFactory.getLog(WorkItemConfigBean.class);

    /**
     * Constants for setting hoursBetweenReminders using common time periods
     * names rather than calculating hours.  For consistency use the
     * same "granule" names we use for scorecards and group indexes.
     */
    public static final String FREQ_DAILY = Configuration.GRANULE_DAY;
    public static final String FREQ_WEEKLY = Configuration.GRANULE_WEEK;

    /**
     * SelectItem we force to the top of the rule selector.
     */
    public static final String RULE_SELECT_HEADER = "-- Select a Rule --";
    public static final String EMAIL_SELECT_HEADER = "-- Select an Email Template --";

    /**
     * Maximum number of reminders we'll let you set.
     * Maybe this isn't necessary, they might want to set an
     * unusually high number just to get "infinite" reminders?
     */
    public static final int MAX_MAX_REMINDERS = 1000;

    WorkItemConfig _config;
    List<SelectItem> _templateNames;
    List<SelectItem> _ruleNames;
    
    /**
     * The name of the selected electronic signature.  This is
     * stores in the arguments of the Approval step, but we
     * use this bean to hold the data in the ui.  We are
     * deprecating the concept of WorkItemConfig so 
     * decided to leave this as an argument override 
     * intead of adding more the WorkItemConfig.
     */
    String electronicSignature;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * These aren't registered in faces-config, they have to be
     * deliberately constructed by a parent bean, then pass into
     * the JSF includes that edit WorkItemConfigs.
     */
    public WorkItemConfigBean(WorkItemConfig config) {
        // bootstrap one if necessary
        if (config != null)
            _config = config;
        else
            _config = new WorkItemConfig();
    }

    /**
     * Clone one for editing.
     * This style is used by the newer DTO-based pages that
     * support drill-down and cancel.
     */
    public WorkItemConfigBean(WorkItemConfigBean src) 
        throws GeneralException {

        if(src!=null) {
            WorkItemConfig wic = src._config;
            if (wic != null)
                _config = spoclone(wic);
            else
                _config = new WorkItemConfig();

        // these don't need to be cloned
            _templateNames = src._templateNames;
            _ruleNames = src._ruleNames;
            electronicSignature = src.electronicSignature;
        } else {
            _config = new WorkItemConfig();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Validators
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * We don't have action handlers since we're not in control,
     * but we do supply validation functions to check things during
     * the parent bean save action.
     *
     * Because the fields are usually invisible unless the WorkItemConfig
     * is enabled we have to disable validation unless they have
     * chosen to see them.  Unfortunately JSF knows nothing about
     * our Javascript visibility shenanigans so we can't use
     * a JSF validator in the page.  Instead we defer validation
     * until you try to click the save button.
     *
     * Sadly this means that we lose field-specific error messages and
     * we have be careful that the terminology used in the error message
     * matches the names of the fields.
     */
    public boolean validate() {

        // don't bother if signoff isn't required
        if (!isEnabled())
            return true;
        
        List<Message> msgs = getValidationMessages();
        
        if (msgs == null || msgs.isEmpty()) {
            // if we made it this far, clean up unnecessary fields
            // based on the style
            _config.cleanupStyleProperties();
            return true;
        } else {
            for(Message msg : msgs){
                addMessage(msg, null);
            }
            return false;
        }
    }

    public List<Message> getValidationMessages(){

        // don't bother if signoff isn't required
        if (!isEnabled())
            return null;

        List<Message> messages = new ArrayList<Message>();

        String style = getEscalationStyle();

        // initial email is required regardless of style
        if (_config.getNotificationEmail() == null)
            messages.add(new Message(Message.Type.Error,
                    MessageKeys.ERR_WORKITEMCONFIG_NO_INITIAL_EMAIL_TEMPLATE));

        if (WorkItemConfig.STYLE_REMINDER.equals(style) ||
            WorkItemConfig.STYLE_BOTH.equals(style)) {

            if (_config.getHoursTillEscalation() <= 0)
                messages.add(new Message(Message.Type.Error,
                        MessageKeys.ERR_WORKITEMCONFIG_DAYS_BEFORE_REMINDER_POSITIVE));

            // actually, if this is zero we'll take a default
            // from SystemConfig, should we allow that?
            if (_config.getHoursBetweenReminders() <= 0)
                messages.add(new Message(Message.Type.Error,
                        MessageKeys.ERR_WORKITEMCONFIG_DAYS_BTWN_REMINDER_POSITIVE));

            // I suppose we could let this slide, but they asked...
            if (_config.getReminderEmail() == null)
                messages.add(new Message(Message.Type.Error,
                        MessageKeys.ERR_WORKITEMCONFIG_NO_REMINDER_EMAIL_TEMPLATE));
        }

        if (WorkItemConfig.STYLE_ESCALATION.equals(style)) {

            if (_config.getHoursTillEscalation() <= 0)
                messages.add(new Message(Message.Type.Error,
                        MessageKeys.ERR_WORKITEMCONFIG_DAYS_BEFORE_ESC_POSITIVE));
        }

        if (WorkItemConfig.STYLE_BOTH.equals(style)) {

            if (_config.getMaxReminders() <= 0)
                messages.add(new Message(Message.Type.Error,
                        MessageKeys.ERR_WORKITEMCONFIG_MAX_REMINDERS_POSITIVE));

            if (_config.getMaxReminders() > MAX_MAX_REMINDERS)
                messages.add(new Message(Message.Type.Error,
                        MessageKeys.ERR_WORKITEMCONFIG_MAX_REMINDERS_LIMIT,
                        Util.itoa(MAX_MAX_REMINDERS)));
        }

        if (WorkItemConfig.STYLE_ESCALATION.equals(style) ||
            WorkItemConfig.STYLE_BOTH.equals(style)) {

            if (_config.getEscalationRule() == null)
                messages.add(new Message(Message.Type.Error,
                        MessageKeys.ERR_WORKITEMCONFIG_NO_ESC_RULE));

            if (_config.getEscalationEmail() == null)
                messages.add(new Message(Message.Type.Error,
                        MessageKeys.ERR_WORKITEMCONFIG_NO_ESC_EMAIL_TEMPLATE));
        }

        Message ownerMessage = checkOwners();
        if (ownerMessage != null)
            messages.add(ownerMessage);

        if (messages.isEmpty()) {
            // if we made it this far, clean up unnecessary fields
            // based on the style
            _config.cleanupStyleProperties();
        }

        return messages;
    }
    
    /**
     * Override in derived class to have a different check for owners
     */
    protected Message checkOwners() {
        // signers are required regardless of style 
        if (_config.getOwners() == null)
            return new Message(Message.Type.Error,
                    MessageKeys.ERR_WORKITEMCONFIG_NO_SIGNERS);

        return null;
    }

    /**
     * Build a fresh PolicyAlert from the editing state.
     * If there are any references to other objects, they referenced
     * objects must be in the current Hibernate session.
     *
     * Since this isn't a true DTO, we just return the original
     * WorkItemConfig object but we have to re-fetch all of the references!
     *
     * NOTE: TaskDefinitionBean relies on this modifying the object
     * in place not creating a new one because unfortunately these
     * are first-class Hibernate objects and we have to worry about
     * deleting them.  
     */
    public WorkItemConfig commit(boolean validate) throws GeneralException {

        if (_config != null) {

            // must pass validation
            if (validate && !validate())
                throw new GeneralException(WebUtil.localizeMessage(MessageKeys.WORK_ITEM_CONFIG_VALIDATION_FAILED));

            refresh(_config.getOwners());
            _config.setOwnerRule((Rule)refresh(_config.getOwnerRule()));
            _config.setEscalationRule((Rule)refresh(_config.getEscalationRule()));
            _config.setNotificationEmail((EmailTemplate)refresh(_config.getNotificationEmail()));
            _config.setReminderEmail((EmailTemplate)refresh(_config.getReminderEmail()));
            _config.setEscalationEmail((EmailTemplate)refresh(_config.getEscalationEmail()));
            
        }
        
        return _config;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Select Item Populators
    //
    //////////////////////////////////////////////////////////////////////

    private <T extends SailPointObject> List<SelectItem> getSelectItems(Class<T> cls, String type, String header) {

        List<SelectItem> selectItems = new ArrayList<SelectItem>();
        selectItems.add(new SelectItem("", header));

        try {
            QueryOptions ops = new QueryOptions();
            if (type != null)
                ops.add(Filter.eq("type", type));
            List<String> props = new ArrayList<String>();
            props.add("name");
            Iterator<Object[]> result = getContext().search(cls, ops, props);
            while (result.hasNext()) {
                String name = (String)((result.next())[0]);
                selectItems.add(new SelectItem(name));
            }
        } 
        catch ( GeneralException ex ) {
            log.error(ex);
        }

        return selectItems;
    }

    /**
     * Return the names of EmailTemplates available for reminders
     * and escalations.  We don't have template types yet so just
     * return all of them.
     */
    public List<SelectItem> getEmailTemplates() {

        if (_templateNames == null) {
            _templateNames = getSelectItems(EmailTemplate.class, 
                                            null, 
                                            EMAIL_SELECT_HEADER);
        }
        return _templateNames;
    }

    /**
     * Return the names of EmailTemplates available for reminders
     * and escalations.  We don't have template types yet so just
     * return all of them.
     * @throws GeneralException 
     */
    public List<SelectItem> getEscalationRules() throws GeneralException {

        if (_ruleNames == null) {
            _ruleNames = WebUtil.getRulesByType(getContext(),  
                                                Rule.Type.Escalation,
                                                true);
        }
        return _ruleNames;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Property Wrappers
    //
    //////////////////////////////////////////////////////////////////////
    
    public WorkItemConfig getConfig() {
        return _config;
    }

    /**
     * Pseudo property to expose the item config options as
     * a "style" name so the UI can adjust the fields that are displayed.
     * This is just to get around the somewhat confusing semantics
     * of the daysTillEscalation and maxReminders values and also 
     * ensures we only prompt for things we will need.
     */
    public String getEscalationStyle() {
        return _config.getEscalationStyle();
    }

    public void setEscalationStyle(String s) {
        _config.setEscalationStyle(s);
    }

    /**
     * Provide a property that inverts the polarity of the disabled flag
     * so we can show it as a checkbox you have to check to display
     * the signoff configuration fields.
     */
    public boolean isEnabled() {
        return !_config.isDisabled();
    }

    public void setEnabled(boolean b) {
        _config.setDisabled(!b);
    }

    /**
     * Provide a property that inverts the polarity of the 
     * noWorkItem flag so we can show it as a checkbox you have 
     * to check to get a work item.  This is used on the policy 
     * pages where you may want notifications without a work item.
     */
    public boolean isWorkItemEnabled() {
        return !_config.isNoWorkItem();
    }

    public void setWorkItemEnabled(boolean b) {
        _config.setNoWorkItem(!b);
    }

    /**
     * Pseudo property to expose a single owmer.
     * Using the suggest component.
     */
    public Identity getOwner() {
        Identity owner = null;
        List<Identity> owners = _config.getOwners();
        if (owners != null && owners.size() > 0)
            owner = owners.get(0);
        return owner;
    }

    public void setOwner(Identity id) {
        List<Identity> owners = _config.getOwners();
        if (owners == null) {
            owners = new ArrayList<Identity>();
            owners.add(id);
            _config.setOwners(owners);
        }
        else {
            owners.clear();
            owners.add(id);
        }
    }

    /**
     * Expose the owners as a list of database ids
     * for the multiSuggest component.
     */
    public List<String> getOwnerIds() {

        List<String> ownerIds = new ArrayList<String>();
        List<Identity> owners = _config.getOwners();
        if (owners != null) {
            for (Identity i : owners)
                ownerIds.add(i.getId());
        }
        return ownerIds;
    }

    public void setOwnerIds(List<String> ownerIds) {
        try {
            List<Identity> newOwners = null;
            if (ownerIds != null && ownerIds.size() > 0) {

                Filter f = Filter.in("id", ownerIds);
                QueryOptions ops = new QueryOptions().add(f);
                ops.add(Filter.or(
                        Filter.eq(Identity.ATT_WORKGROUP_FLAG, true),
                        Filter.eq(Identity.ATT_WORKGROUP_FLAG, false)));
                SailPointContext con = getContext();

                newOwners = con.getObjects(Identity.class, ops);
            }

            _config.setOwners(newOwners);
        }
        catch (GeneralException e) {
            log.error("Unable to assign signoff owners.", e);
        }
    }

    /**
     * Expose max reminder count as a string.
     */
    public String getMaxReminders() {
        return Util.itoa(_config.getMaxReminders());
    }

    public void setMaxReminders(String s) {
        _config.setMaxReminders(Util.atoi(s));
    }

    private int getHoursFromDays(String str) {
        return Util.atoi(str) * 24;
    }
    
    private String getDaysFromHours(int hours) {
        int days = hours / 24;
        // round up just in case 
        if (days * 24 < hours)
            days++;
        return Util.itoa(days);
    }

    /**
     * Pseudo property to access hoursTillEscalation in day units.
     */
    public String getDaysTillEscalation() {

        return getDaysFromHours(_config.getHoursTillEscalation());
    }
    
    public String getDaysTillReminder() {

        return getDaysFromHours(_config.getHoursTillEscalation());
    }

    /**
     * We have two fields in the form with different labels
     * that end up setting the same thing, one will be hidden.
     * But since they both post we have to ignore the one that
     * isn't displayed.
     */
    public void setDaysTillReminder(String str) {

        String style = getEscalationStyle();
        if (WorkItemConfig.STYLE_REMINDER.equals(style) ||
            WorkItemConfig.STYLE_BOTH.equals(style))
            _config.setHoursTillEscalation(getHoursFromDays(str));
    }

    public void setDaysTillEscalation(String str) {
        String style = getEscalationStyle();
        if (WorkItemConfig.STYLE_ESCALATION.equals(style))
            _config.setHoursTillEscalation(getHoursFromDays(str));
    }

    /**
     * Pseudo property to access hoursBetweenReminders in day units.
     */
    public String getDaysBetweenReminders() {
        return getDaysFromHours(_config.getHoursBetweenReminders());
    }
    
    public void setDaysBetweenReminders(String str) {
        _config.setHoursBetweenReminders(getHoursFromDays(str));
    }

    public String getNotificationEmail() {
        String name = null; 
        EmailTemplate et = _config.getNotificationEmail();
        if (et != null)
            name = et.getName();
        return name;
    }

    public void setNotificationEmail(String name) {
        if (name == null || name.length() == 0) {
            // not sure if this can happen, but assume it means
            // to clear the template
            _config.setNotificationEmail(null);
        }
        else {
            EmailTemplate et = null;
            try {
                et = getContext().getObjectByName(EmailTemplate.class, name);
            }
            catch (Throwable t) {
                log.error(t);
            }

            if (et != null)
                _config.setNotificationEmail(et);
            else {
                // it must have been deleted while we were thinking
                // note that the error message has to go into
                // the parent bean

                addMessage(new Message(Message.Type.Error,
                                                MessageKeys.ERR_WORKITEMCONFIG_INVALID_EMAIL_TEMPLATE), null);
            }
        }
    }

    public String getReminderEmail() {
        String name = null; 
        EmailTemplate et = _config.getReminderEmail();
        if (et != null)
            name = et.getName();
        return name;
    }

    public void setReminderEmail(String name) {
        if (name == null || name.length() == 0) {
            // not sure if this can happen, but assume it means
            // to clear the template
            _config.setReminderEmail(null);
        }
        else {
            EmailTemplate et = null;
            try {
                et = getContext().getObjectByName(EmailTemplate.class, name);
            }
            catch (Throwable t) {
                log.error(t);
            }

            if (et != null)
                _config.setReminderEmail(et);
            else {
                // it must have been deleted while we were thinking
                // note that the error message has to go into
                // the parent bean
                addMessage(new Message(Message.Type.Error,
                                                MessageKeys.ERR_WORKITEMCONFIG_INVALID_EMAIL_TEMPLATE), null);
            }
        }
    }

    public String getEscalationEmail() {
        String name = null; 
        EmailTemplate et = _config.getEscalationEmail();
        if (et != null)
            name = et.getName();
        return name;
    }

    public void setEscalationEmail(String name) {
        if (name == null || name.length() == 0) {
            _config.setEscalationEmail(null);
        }
        else {
            EmailTemplate et = null;
            try {
                et = getContext().getObjectByName(EmailTemplate.class, name);
            }
            catch (Throwable t) {
                log.error(t);
            }
            if (et != null)
                _config.setEscalationEmail(et);
            else
                addMessage(new Message(Message.Type.Error,
                                                MessageKeys.ERR_WORKITEMCONFIG_INVALID_EMAIL_TEMPLATE), null);
        }
    }

    public String getEscalationRule() {
        String name = null; 
        Rule rule = _config.getEscalationRule();
        if (rule != null)
            name = rule.getName();
        return name;
    }

    public void setEscalationRule(String name) {
        if (name == null || name.length() == 0) {
            _config.setEscalationRule(null);
        }
        else {
            Rule rule = null;
            try {
                rule = getContext().getObjectByName(Rule.class, name);
            }
            catch (Throwable t) {
                log.error(t);
            }
            if (rule != null)
                _config.setEscalationRule(rule);
            else
                addMessage(new Message(Message.Type.Error,
                                                MessageKeys.ERR_WORKITEMCONFIG_INVALID_RULE), null);
        }
    }

    public String getElectronicSignature() {
        return electronicSignature;
    }

    public void setElectronicSignature(String esignature) {
        this.electronicSignature = esignature;
    }
 
}
