/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 ** THIS FILE IS OBSOLETE
 **
 **/

/**
 * 
 */
package sailpoint.web;

import java.text.DateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.TaskManager;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.MissingManagedEntitlementsTask;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LocalizedDate;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.task.TaskResultBean;
import sailpoint.web.util.WebUtil;

/**
 * The ExplanationBean is responsible for saving newly-created 
 * and edited Explanations.
 * 
 * This bean behaves a little differently from our usual DTO beans
 * where the getters and setters hook directly to the DTO itself.
 * Instead, the bean looks up an existing Explanation object by the
 * id passed in, then sets the DTO fields based on the bean values.
 * This approach gets us around an odd situation that came up using
 * the standard DTO approach where changing the purview of an existing
 * Explanation by checking or unchecking the inherit option in the 
 * edit popup caused a loss of extended attribute data.  
 */
public class ExplanationBean extends BaseEditBean<ManagedAttribute>
    {
    private static Log log = LogFactory.getLog(ExplanationBean.class);
    
    public static final String MIME_CSV = "application/vnd.ms-excel";
    
    private static final String TASK_NAME = "Hidden Missing Managed Entitlements Scan";
    
    private static final String TASK_LABEL = "Missing Managed Entitlements Scan (%1$s) - %2$s";
    
    private String id;
    
    private String application;
    
    private String language;
    
    private String type;
    
    private String attribute;

    private String displayableName;

    private String value;

    private String ownerId;

    private boolean requestable;

    private String explanation;
    
    private boolean inherit;
    
    private boolean keepFormatting;

    private Application applicationObject;
        
    // default constructor
    public ExplanationBean() {}
    
    
    /**
     * Save the explanation.
     * 
     * @throws GeneralException 
     */
    public String saveExplanation() throws GeneralException
        {
        
        copyBeanValues();
        
        // make sure we're not duplicating something that already exists
        if (!isUnique())
            return "";
        
        super.saveAction();
            
        // after saving, we need to generate a new, empty explanation
        // associated with this bean for the UI, which won't happen  
        // unless the objectId on this bean is reset to null 
        this.setObjectId(null);
        
        return null;
        }

    /**
     * Make sure there's not already an explanation with the same
     * application/type/attribute/value by comparing the id of the given 
     * Explanation with the id of the object attached to this bean.
     * 
     * @return True if no matching explanation already exists;
     * 		   false otherwise
     * 
     * @throws GeneralException
     */
    private boolean isUnique() throws GeneralException
        {
        boolean valid = true;	
        
        // ugh, I hate this enum
        boolean permission = (ManagedAttribute.Type.Permission.name().equals(type));
        ManagedAttribute existingExpl = 
            ManagedAttributer.get(getContext(), application, permission, 
                                  attribute, value);

        if ((existingExpl != null) && (!existingExpl.getId().equals(this.getId())))
            {
            valid = false;
            
            // make sure we display the type just like the grid does
            Message msg = new Message(getObject().getType());
            String translatedType = 
                msg.getLocalizedMessage(getLocale(), getUserTimeZone());
            
            String label = translatedType + "/" + getAttribute() + "/" + getValue();
            addMessage(new Message(Message.Type.Error, 
                MessageKeys.EXPLANATION_ERR_DUPLICATE, label), null);
            }
        
        return valid;
        }


    /**
     * Copy the values from the bean to the backing DTO, with a couple of tweaks
     * 
     * @throws GeneralException 
     */
    private void copyBeanValues() throws GeneralException
        {
        ManagedAttribute expl = null;

        // find the existing object if we're editing
        if (!id.equals("new"))
            expl = getContext().getObjectById(ManagedAttribute.class, id);
        
        // otherwise use the one newly created by this bean
        if (expl == null)
            expl = getObject();
                

        Application newapp = getApplicationObject();

        if (expl.getApplication() != null && expl.getApplication() != newapp) {

            // look for an existing explanation that matches the changes 
            boolean permission = (ManagedAttribute.Type.Permission.name().equals(type));
            ManagedAttribute existing = 
                ManagedAttributer.get(getContext(), newapp, permission,
                                      attribute, value);

            if (existing == null)
                {
                // if there's not a matching explanation in existence, create 
                // a new object so we don't mess up the original explanation
                expl = getObject();
                }
            else if (existing.getAttributes().get(language) == null)
                {
                // if there IS a matching explanation and it doesn't currently 
                // have an explanation in the given language, change which object 
                // we're editing.  We've gotta spoof the id so the unique check
                // downstream doesn't fail.
                    // jsl - don't like this we shouldn't let you change the application
                id = existing.getId();
                expl = existing; 
                }
            else
                {
                // if the matching object already has an explanation in the
                // given language, let the code catch the duplicate downstream - 
                // nothing else is needed here
                }
            }
            
        
        expl.setApplication(newapp);
        expl.setType(type);
        expl.setAttribute(attribute);
        expl.setValue(value);
        expl.setDisplayName(displayableName);
        
        Identity owner = null;
        if (null != Util.getString(this.ownerId)) {
            owner = getContext().getObjectById(Identity.class, this.ownerId);
        }
        expl.setOwner(owner);

        expl.setRequestable(this.requestable);
        
        expl.addDescription(language, WebUtil.safeHTML(explanation));
        
        // permission explanations don't use the attribute field -
        // this is CYA here since the JS should have set it
        if (ManagedAttribute.Type.Permission.name().equals(getType())) {
            // jsl - need to fix the UI bean to not pass target as the value
            expl.setAttribute(value);
            expl.setValue(null);
        }
        
        // and finally, set the bean's object
        this.setObject(expl);
        }
                
    
    /**
     * Launches the task that searches for missing entitlement descriptions.
     * 
     * @return
     */
    public String launchDiscoveryTask()
        {
        try 
            {
            TaskSchedule ts = new TaskSchedule();
            TaskDefinition def = getContext().getObjectByName(TaskDefinition.class, TASK_NAME);
            ts.setTaskDefinition(def);
            ts.setLauncher(getLoggedInUserName());
            LocalizedDate lDate = new LocalizedDate(new Date(), DateFormat.FULL, DateFormat.LONG);
            ts.setName(TASK_NAME + " " + lDate.getLocalizedMessage(getLocale(), getUserTimeZone()));
            ts.setArgument(TaskSchedule.ARG_RESULT_NAME, getTaskResultName());
            ts.setArgument(MissingManagedEntitlementsTask.ARG_FULL_REPORT, "false");
            ts.setArgument(MissingManagedEntitlementsTask.ARG_APPLICATIONS, application);
            
            TaskManager tm = new TaskManager(getContext());
            tm.runNow(ts);
            } 
        catch (GeneralException e) 
            {
            log.error("Failed to launch " + TASK_NAME, e);
            }
        
        return "";
        }

    
    /**
     * 
     * 
     * @return
     */
    @SuppressWarnings("unchecked")
    public String viewLastTaskResult() 
        {
        try 
            {
            TaskResult lastResult = 
                getContext().getObjectByName(TaskResult.class, getTaskResultName());
            if (lastResult != null)
                getSessionScope().put(TaskResultBean.ATT_RESULT_ID, lastResult.getId());
            } 
        catch (GeneralException e) 
            {
            log.error("Failed to retrieve the last task result", e);
            getSessionScope().put(TaskResultBean.ATT_RESULT_ID, null);
            }
        
        return "";
        }
    
    
    /**
     * Convenience method to retrieve the Application object associated
     * with the app id in the bean's application field. 
     * 
     * @return The Application with the given id
     * 
     * @throws GeneralException
     */
    private Application getApplicationObject() throws GeneralException
        {
        if (applicationObject != null)
            return applicationObject;
        
        applicationObject = getContext().getObjectById(Application.class, application);
        if (applicationObject == null)
            throw new GeneralException("Unable to find app with id " + application);	
        
        return applicationObject;
        }
    
    
    /**
     * Builds the proper name for the missing entitlement descriptions
     * task, based on the current app and the logged in user 
     * 
     * @return String to use as the task result name
     * 
     * @throws GeneralException 
     */
    private String getTaskResultName() throws GeneralException
        {
        return String.format(TASK_LABEL, 
            getApplicationObject().getName(), getLoggedInUserName());
        }
    
    
    // overrides
    public ManagedAttribute createObject()
        {
        ManagedAttribute expl = new ManagedAttribute();
        
        // we need a default type to prevent JSF problems
        expl.setType(ManagedAttribute.Type.Entitlement.name());
        
        return expl;
        }
    
    
    /**
     * We actually don't want this to do anything for explanations
     */
    @Override
    protected void initObjectId() {}
    
    
    // getters and setters
    public String getId() throws GeneralException 
        {
        return id;
        }
    
    public void setId(String id) throws GeneralException 
        {
        this.id = id;
        }
    
    public String getApplication() throws GeneralException 
        {
        return application;
        }
    
    public void setApplication(String application) throws GeneralException 
        {
        this.application = application;
        }
    
    public String getLanguage() throws GeneralException 
        {
        return language;
        }
    
    public void setLanguage(String language)  
        {
        this.language = language;
        }

    public String getType() throws GeneralException 
        {
        return type;
        }

    public void setType(String type) throws GeneralException 
        {
        this.type = type;
        }

    public String getAttribute() throws GeneralException 
        {
        return attribute;
        }

    public void setAttribute(String attribute) throws GeneralException 
        {
        this.attribute = attribute;
        }


    public String getDisplayableName() {
        return displayableName;
    }


    public void setDisplayableName(String displayableName) {
        this.displayableName = displayableName;
    }
    
    public String getValue() throws GeneralException 
        {
        return value;
        }

    public void setValue(String value) throws GeneralException 
        {
        this.value = value;
        }

    public String getOwnerId() {
        return this.ownerId;
    }
    
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    
    public boolean isRequestable() {
        return this.requestable;
    }
    
    public void setRequestable(boolean requestable) {
        this.requestable = requestable;
    }
    
    public String getExplanation() throws GeneralException 
        {
        return explanation;
        }

    public void setExplanation(String explanation) throws GeneralException 
        {
        // don't save an empty string in the db
        this.explanation = (explanation.equals("")) ? null : explanation;
        }

    public boolean isInherit() 
        {
        return inherit;
        }
    
    public void setInherit(boolean inherit) 
        {
        this.inherit = inherit;
        }

    public boolean isKeepFormatting() {
        return keepFormatting;
    }


    public void setKeepFormatting(boolean keepFormatting) {
        this.keepFormatting = keepFormatting;
    }
    
    // inherited abstract methods
    @Override
    protected Class<ManagedAttribute> getScope() 
        {
        return ManagedAttribute.class;
        }

    @Override
    public boolean isStoredOnSession() 
        {
        return true;
        }
    }

    
