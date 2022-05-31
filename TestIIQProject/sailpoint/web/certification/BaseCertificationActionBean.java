/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.List;
import java.util.Arrays;
import java.io.Writer;
import java.io.StringWriter;


import sailpoint.api.certification.RemediationManager;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.web.BaseObjectBean;
import org.json.JSONWriter;
import org.json.JSONArray;
import org.json.JSONException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public abstract class BaseCertificationActionBean extends BaseObjectBean<Certification> {

    private static Log log = LogFactory.getLog(BaseCertificationActionBean.class);

    // These are the message keys from the ResourceBundle for the different
    // assignment option strings. We also use these as the values of the
    // SelectItems in the assignment dropdown.
    public static final String ASSIGN_MANUAL_KEY = "assign_manual";
    public static final String ASSIGN_SELF_KEY = "assign_self";
    public static final String ASSIGN_APP_OWNER_KEY = "assign_app_owner";
    public static final String ASSIGN_DEFAULT_REMEDIATOR_KEY = "assign_default_remediator";

    // List of applications which relate to this cert action.
    // This includes all applications which have entitlements, bundles or
    // violations in the current cert.
    private List<Application> applications;

    private boolean readOnly;
       
    /**
     * Default constructor
     */
    protected BaseCertificationActionBean() {
        super();
    }

    /**
     * Gets the name of the owner (certifier) of this certification. We
     * leave this up to implementing classes since in some cases the
     * owner is stored on a CertificationAction instead of directly
     * on the CertificationActionBean.
     *
     * @return Name of the owner of the certification
     */
    public abstract String getOwnerName();

    /**
     * Sets the name of the owner (certifier) of this certification. We
     * leave this up to implementing classes since in some cases the
     * owner is stored on a CertificationAction instead of directly
     * on the CertificationActionBean.
     *
     * @param ownerName Name of the owner of the certification
     */
    public abstract void setOwnerName(String ownerName);

    /**
     * Initializes the applications list. 
     */
    protected abstract void initApplications();

    /**
     * Return a single unique application owner for all applications, or null if
     * there is not a single owner that is common to all applications.
     *
     * @return Unique application owner, or null.
     */
    Identity getApplicationOwner() {
        return RemediationManager.getUniqueAppIdentity(new RemediationManager.UniqueIdentityFetcher() {
            public Identity getUniqueIdentity(Application app) {
                return app.getOwner();
            }
        }, getApplications());
    }



    /**
     * Creates a select item value for a given user. In this case we concat
     * the identity's name and its displayable name with a delimiter '__'. The 
     * javascript then splits this string and updates the suggest component
     * with the two values.
     *
     * @param id The Identity
     * @return Concatenated string composed of the identity's name and 
     * 		   displayable name, separated by a delimiter.
     */
    private String createIdentityKey(Identity id){
        return id.getName() + "__" + id.getDisplayableName();
    }

    /**
     * Returns an object containing a json array of selectable items for
     * the 'quick assignment' combo box attached to the assignment suggest
     * component. For each item, the value is the assignee name.
     * The first item is the 'manual select' options in which the user selects
     * the assignee using the suggest box. Its value is empty.
     *
     * @return quick assignment options object
     */
    public String getQuickAssignmenOptionsJson(){

        JSONArray array = new JSONArray();
        array.put(Arrays.asList("", getMessage(ASSIGN_MANUAL_KEY)));

        Identity currentUser = null;
        try {
            currentUser = getLoggedInUser();
        } catch (GeneralException e) {
            log.error("Could not get current user!");
            return JsonHelper.failure();
        }
        if (null != currentUser)
            array.put(new JSONArray(Arrays.asList(currentUser.getName(), getMessage(ASSIGN_SELF_KEY))));

        Identity defaultRemediator = RemediationManager.getDefaultRemediator(getApplications(), getContext());
        if (null != defaultRemediator)
            array.put(new JSONArray(Arrays.asList(defaultRemediator.getName(),
                    getMessage(ASSIGN_DEFAULT_REMEDIATOR_KEY))));

        Identity appOwner = getApplicationOwner();
        if (null != appOwner)
            array.put(new JSONArray(Arrays.asList(appOwner.getName(), getMessage(ASSIGN_APP_OWNER_KEY))));

        Writer jsonString = new StringWriter();
        JSONWriter writer = new JSONWriter(jsonString);

        try {
            writer.object();
            writer.key("quickAssignmentOptions");
            writer.value(array);
            writer.endObject();
        } catch (JSONException e) {
            log.error(e);
            return JsonHelper.failure();
        }

        return jsonString.toString();

    }

    /**
     * Matches the owner name and the names of the app owner or logged in
     * user. If a match is found we return the correct option.
     *
     * Note that the user may not have used the select box to assign, but we'll
     * just assume they did to keep it simple.
     *
     * @return Value for the assignment option select box
     */
    public String getAssignment(){

        // This logic is faulty if the app owner, logged in user, or default
        // remediator are the same.  Can't really get around that unless we
        // store the assignment on the CertificationAction.  Oh well ... not a
        // huge deal.
        
        try {
            if (getOwnerName() == null) {
                return "";
            }else if (getOwnerName().equals(getLoggedInUser().getName())) {
                return createIdentityKey(getLoggedInUser());
            }else {
                Identity appOwner = getApplicationOwner();
                if (appOwner != null && getOwnerName().equals(appOwner.getName())){
                    getContext().attach(appOwner); // catch detached identities when restoring state
                    return createIdentityKey(appOwner);
                } else{
                    return "";
                }
            }
        } catch (GeneralException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Just here to meet javabean spec for this method. 
     * @param assignment
     */
    public void setAssignment(String assignment){}

    /**
     * True if the user seems to have selected the recipient using the dropdown,
     * rather than manually selecting it with the suggest box. We don't know
     * for sure what they originaly did, but we guess by matching the owner
     * with either the logged in user or the application owner.
     *
     * We could store a flag to indicate this, but it doesn't seem to be
     * worth it since this is just a UI convenience.
     *
     * @return True if the user seems to have selected the recipient
     *  using the dropdown.
     */
    public boolean isAssignmentSelected(){
        return getAssignment() != null && !"".equals(getAssignment()); 
    }


    /**
     * Get the list of applications which are affected by this certification.
     * This includes all applications which have entitlements, bundles or
     * violations in the current cert.
     *
     * @return  Applications list, null if not initialized.
     */
    public List<Application> getApplications() {
        if (applications ==  null)
            initApplications();

        return applications;
    }

    /**
     * Set the list of applications which are affected by this certification.
     * This includes all applications which have entitlements, bundles or
     * violations in the current cert.
     *
     * @param applications List of applications, null if not initialized
     */
    public void setApplications(List<Application> applications) {
        this.applications = applications;
    }

    /**
     * @return True if action is readonly
     */
    public boolean isReadOnly()
    {
        return readOnly;
    }

    /**
     * @param readOnly True if action is readonly
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

}
