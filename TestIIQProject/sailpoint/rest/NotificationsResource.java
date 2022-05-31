package sailpoint.rest;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.RequestResult;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Rule;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;

@Path("/notifications")
public class NotificationsResource extends BaseResource {
    
    private static final Log log = LogFactory.getLog(NotificationsResource.class);

    public static class RecipientInfo {
        private String id;
        private String displayField;
        private String icon;
        
        public String getId() {
            return id;
        }
        
        public void setId(String val) {
            this.id = val;
        }
        
        public String getDisplayField() {
            return displayField;
        }
        
        public void setDisplayField(String val) {
            displayField = val;
        }
        
        public String getIcon() {
            return icon;
        }
        
        public void setIcon(String val) {
            icon = val;
        }
    }
    
    public static class ReminderSetting  {

        private boolean enabled;
        private int startHowManyDays;
        private int onceEveryHowManyDays;
        private boolean before;
        private boolean once;
        private boolean additionalEmailRecipientsPresent;
        private List<RecipientInfo> additionalRecipients;
        private String emailTemplateId;
        private String recipientsRuleId;

        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean val) {
            enabled = val;
        }
        
        public int getStartHowManyDays() {
            return this.startHowManyDays;
        }

        public void setStartHowManyDays(int val) {
            startHowManyDays = val;
        }
        
        public int getOnceEveryHowManyDays() {
            return onceEveryHowManyDays;
        }
        
        public void setOnceEveryHowManyDays(int val) {
            onceEveryHowManyDays = val;
        }

        public boolean isBefore() {
            return before;
        }

        public void setBefore(boolean val) {
            before = val;
        }

        public boolean isOnce() {
            return once;
        }

        public void setOnce(boolean val) {
            once = val;
        }
        
        public boolean isAdditionalEmailRecipientsPresent() {
            return additionalEmailRecipientsPresent;
        }

        public void setAdditionalEmailRecipientsPresent(boolean val) {
            additionalEmailRecipientsPresent = val;
        }

        public List<RecipientInfo> getAdditionalRecipients() {
            return additionalRecipients;
        }
        
        public void setAdditionalRecipients(List<RecipientInfo> val) {
            additionalRecipients = val;
        }
        
        public String getEmailTemplateId() {
            return emailTemplateId;
        }
        
        public void setEmailTemplateId(String val) {
            emailTemplateId = val;
        }

        public String getRecipientsRuleId() {
            return recipientsRuleId;
        }

        public void setRecipientsRuleId(String val) {
            recipientsRuleId = val;
        }
    }
    
    public static class EscalationSetting {
        
        private boolean enabled;
        private boolean before;
        private int startHowManyDays;
        private int onceEveryHowManyDays;
        private int maxReminders;
        private boolean additionalEmailRecipientsPresent;
        private List<RecipientInfo> additionalRecipients;
        private String emailTemplateId;
        private String recipientsRuleId;
        private String escalationRuleId;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean val) {
            enabled = val;
        }
        
        public boolean isBefore() {
            return before;
        }
        
        public void setBefore(boolean val) {
            before = val;
        }
        
        public int getStartHowManyDays() {
            return this.startHowManyDays;
        }
        
        public void setStartHowManyDays(int val) {
            this.startHowManyDays = val;
        }
        
        public int getOnceEveryHowManyDays() {
            return this.onceEveryHowManyDays;
        }
        
        public void setOnceEveryHowManyDays(int val) {
            this.onceEveryHowManyDays = val;
        }
        
        public int getMaxReminders() {
            return this.maxReminders;
        }
        
        public void setMaxReminders(int val) {
            this.maxReminders = val;
        }
        
        public boolean isAdditionalEmailRecipientsPresent() {
            return this.additionalEmailRecipientsPresent;
        }
        
        public void setAdditionalEmailRecipientsPresent(boolean val) {
            this.additionalEmailRecipientsPresent = val;
        }
        
        public List<RecipientInfo> getAdditionalRecipients() {
            return this.additionalRecipients;
        }
        
        public void setAdditionalRecipients(List<RecipientInfo> val) {
            this.additionalRecipients = val;
        }
        
        public String getEmailTemplateId() {
            return this.emailTemplateId;
        }
        
        public void setEmailTemplateId(String val) {
            this.emailTemplateId = val;
        }
        
        public String getRecipientsRuleId() {
            return this.recipientsRuleId;
        }
        
        public void setRecipientsRuleId(String val) {
            this.recipientsRuleId = val;
        }
        
        public String getEscalationRuleId() {
            return this.escalationRuleId;
        }
        
        public void setEscalationRuleId(String val) {
            this.escalationRuleId = val;
        }
    }
    
    public static class NotificationSetting {
        
        private boolean enabled;
        private List<ReminderSetting> reminderSettings;
        private List<EscalationSetting> escalationSettings;
        
        private Date startDate;
        
        private Date endDate;

        public NotificationSetting() {}
        
        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean val) {
            this.enabled = val;
        }

        public List<ReminderSetting> getReminderSettings() {
            return reminderSettings;
        }

        public void setReminderSettings(List<ReminderSetting> val) {
            reminderSettings = val;
        }
        
        public List<EscalationSetting> getEscalationSettings() {
            return this.escalationSettings;
        }

        public void setEscalationSettings(List<EscalationSetting> val) {
            this.escalationSettings = val;
        }

        public Date getStartDate() {
            return startDate;
        }

        public void setStartDate(Date val) {
            startDate = val;
        }

        public Date getEndDate() {
            return endDate;
        }

        public void setEndDate(Date val) {
            endDate = val;
        }
        
        @Deprecated
        public ReminderSetting getDefaultReminderSetting() {
            if (reminderSettings != null && reminderSettings.size() > 0) {
                return reminderSettings.get(0);
            }
            return null;
        }
        
        @Deprecated
        public EscalationSetting getDefaultEscalationSetting() {
            if (escalationSettings != null && escalationSettings.size() > 0) {
                return escalationSettings.get(0);
            }
            return null;
        }
    }
    
    
    public static class TypedResult<T> extends RequestResult {
        private T result;
        
        public T getResult() {
            return result;
        }
        
        public TypedResult(T result) {
            this.result = result;
        }
    }
    
    @GET
    @Path("/reminder")
    public TypedResult<ReminderSetting> getSettings(@QueryParam("certId") String certId, @QueryParam("reminderNumber") int reminderNumber)
    		throws GeneralException {
    	
        authorize(new RightAuthorizer(SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));
    	
        return new TypedResult<ReminderSetting>(createOneReminder());
    }

    private ReminderSetting createOneReminder() {
        
        ReminderSetting settings = new ReminderSetting();
        settings.startHowManyDays = new Random().nextInt(7) + 1;
        settings.onceEveryHowManyDays = new Random().nextInt(2) + 1;
        settings.before = new Random().nextBoolean();
        settings.once = new Random().nextBoolean();
        settings.additionalEmailRecipientsPresent = true;
        try {
            EmailTemplate template = getContext().getObjectByName(EmailTemplate.class, "Bulk Reassignment");
            settings.emailTemplateId = template.getId();
            
            Rule rule = getContext().getObjectByName(Rule.class, "Example Email Recipient Rule");
            settings.recipientsRuleId = rule.getId();
        } catch (Exception e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        }
        settings.additionalRecipients = new ArrayList<RecipientInfo>();
        RecipientInfo recipientInfo = new RecipientInfo();
        recipientInfo.id = "id1";
        recipientInfo.displayField = "First Guy";
        recipientInfo.icon = "userIcon";
        settings.additionalRecipients.add(recipientInfo);
        recipientInfo = new RecipientInfo();
        recipientInfo.id = "id2";
        recipientInfo.displayField = "First Group";
        recipientInfo.icon = "groupIcon";
        settings.additionalRecipients.add(recipientInfo);
        return settings;
    }
    
    @GET
    public TypedResult<NotificationSetting> getSettings(@QueryParam("certId") String certId) 
    		throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));

        NotificationSetting config = new NotificationSetting();
        
        Calendar cal = Calendar.getInstance();
        config.setStartDate(cal.getTime());
        
        cal.add(Calendar.DAY_OF_YEAR, 30);
        config.setEndDate(cal.getTime());

        List<ReminderSetting> settings = new ArrayList<ReminderSetting>();
        
        int numReminders = new Random().nextInt(5) + 1;

        for (int i=0; i<numReminders; ++i) {
            settings.add(createOneReminder());
        }
        
        config.setReminderSettings(settings);
        
        return new TypedResult<NotificationSetting>(config);
    }
}
