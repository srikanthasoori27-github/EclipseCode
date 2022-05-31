package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ActivityConfig;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.Application;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.ApplicationActivity.Action;
import sailpoint.object.ApplicationActivity.Result;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.tools.GeneralException;

/**
 * Identity Activities. This is called by IdentityDTO to delegate the activity
 * related things.
 * 
 */
public class ActivityHelper {
    
    private static final Log log = LogFactory.getLog(ActivityHelper.class);
    
    private IdentityDTO parent;
    // indicates if there are more activities to display beyond what's on the page
    private boolean moreActivities;
    // Activities for this identity
    private List<ActivityBean> activities;
    /**
     * Mapping of application to businessrole so we can display which
     * applicastiona are already enabled for actvitiy monitoring
     * because of a business role assignment.
     */
    private Map<String, String> activityBusinessRoles;


    // Number of activities to display in the activity panel.
    private Integer activityResultSize = new Integer(IdentityDTO.DEFAULT_ACTIVITY_COUNT);


    public ActivityHelper(IdentityDTO parent) {
        this.parent = parent;
    }
    
    private void initActivities()
        throws GeneralException {

        this.activities = new ArrayList<ActivityBean>();
    
        Identity identity = this.parent.getObject();
    
        if ( identity != null ) {
            // Assuming this will scale, but put a result limit
            // on it just in case.
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("identityId", identity.getId()));
            ops.setOrderBy("timeStamp");
            ops.setOrderAscending(false);
    
            // If result size specififed, retrieve the requested number of records + 1.
            // The extra record will tell us if there are more records beyond the requested
            // result size.
            if (this.activityResultSize != null)
                ops.setResultLimit(this.activityResultSize + 1);
    
            List<ApplicationActivity> applicationActivities = this.parent.getContext().getObjects(ApplicationActivity.class, ops);
            if (applicationActivities != null) {
                for (ApplicationActivity applicationActivity : applicationActivities) {
                    this.activities.add(new ActivityBean(applicationActivity));
                }
            }
    
            // If there are resultSize + 1 activities retrieved, set a flag indicating
            // that there are more records. Then remove the extra record before returning
            // the result set.
            if (this.activityResultSize != null && this.activities.size() > this.activityResultSize){
                this.moreActivities = true;
                this.activities.remove(this.activityResultSize.intValue());
            }
        }

    }

    /**
     * @return Max number of activities to display, null if all should display
     */
    public Integer getActivityResultSize() {
        return this.activityResultSize;
    }
    
    /**
     * Returns activity list for this identity. A max of getResultSize() records are
     * returned. If the activity list has not been loaded (list==null) it will be loaded.
     *
     * @return Activity list for this identity
     * @throws GeneralException
     */
    public List<ActivityBean> getActivities() throws GeneralException{

        if (this.activities == null)
            initActivities();

        return this.activities;
    }

    
    /**
     * Indicates we should display a button to all the user to display all activities.
     *
     * @return True if the identity has more that DEFAULT_ACTIVITY_COUNT activities of
     *  if the request result size is null
     */
    public boolean isMoreActivities() throws GeneralException{

        if (getActivityResultSize() == null)
            return false;

        if (getActivities() == null)
            initActivities();

        return this.moreActivities;
    }

    /**
     * Action method that indicates a user wants to display all activities rather
     * than just the limited number displayed in the initial activity list.
     *
     * Resets the size of the activity results list and reloads the
     * activity list data.
     *
     * @return view name
     * @throws GeneralException
     */
    public String seeMoreActivities() throws GeneralException {

        setActivityResultSize(null);
        initActivities();

        return "moreActivities";
    }


    /**
     * @param activityResultSize Max number of activities to display, null if all should display
     */
    public void setActivityResultSize(Integer activityResultSize) {
        this.activityResultSize = activityResultSize;
    }
  
    /**
     * Provide a list of the links assigned to this identity that are for
     * applications that have activity data sources associated with them.
     *
     * @return a list of activity enabled links
     */
    public List<Link> getActivityEnabledLinks() {
        List<Link> links = null;
        Identity id = this.parent.getObject();
        if ( id != null )
            links = id.getLinks();

        List<Link> aeLinks = new ArrayList<Link>();
        if ( links != null ) {
            for ( Link link : links ) {
                Application app = link.getApplication();
                if ( app != null ) {
                    List<ActivityDataSource> adsList =
                                                 app.getActivityDataSources();
                    if ( adsList != null && adsList.size() > 0 ) {
                        aeLinks.add(link);
                    }
                }
            }
        }

        return aeLinks;
    }


    /**
     * Retrieve the datasources that are selected to be enabled for
     * activity collection.
     */
    public Map<String,Boolean> getActivitySelections() throws GeneralException {

        Map<String,Boolean> selections = this.parent.getState().getActivitySelections();
        if ( ( selections == null ) || ( selections.size() == 0) ) {
            Identity obj = this.parent.getObject();
            ActivityConfig activityConfig = null;
            if ( obj != null )
                activityConfig = obj.getActivityConfig();

            if ( activityConfig != null ) {
                Set<String> apps =
                    activityConfig.getEnabledApplications();
                if ( apps != null ) {
                    for ( String app : apps) {
                        selections.put(app, true);
                    }
                }
            }
            this.parent.getState().setActivitySelections(selections);
        }
        return selections;
    }
    
    /**
     * First check the user specific configuration if not enabled then
     * Look through the business roles and determine if any of them
     * are activity enabled.
     */
    public boolean isActivityEnabled() throws GeneralException {
        
        Identity identity = this.parent.getObject();

        boolean enabled = false;
        if ( identity == null ) return enabled;

        // check the configuration on the identity too...
        ActivityConfig config = identity.getActivityConfig();
        if ( config != null ) {
            enabled = config.enabled();
        }
        if ( enabled ) return true;

        // if not enabled at the identity level dig into the
        // business roles and check to see if they are enabled
        List<Bundle> bundles = identity.getBundles();
        if ( bundles != null ) {
            for ( Bundle bundle : bundles ) {
                if ( bundle == null ) continue;

                ActivityConfig bundleConfig = bundle.getActivityConfig();
                if ( ( bundleConfig == null ) || ( !bundleConfig.enabled() )) {
                    continue;
                } else {
                    enabled = true;
                    break;
                }
            }
        }
        return enabled;
    }

    /**
     * jsl - this is a bit of a misnomer since it only applies
     * roles with activity configs?  Not sure where this is used.
     */
    public Map<String,String> getBusinessRoleSummary() throws GeneralException {

        if ( this.activityBusinessRoles != null ) return this.activityBusinessRoles;

        this.activityBusinessRoles = new HashMap<String,String>();
        Identity identity = this.parent.getObject();
        if ( identity != null ) {
            List<Bundle> bundles = identity.getBundles();
            if ( bundles != null ) {
                this.activityBusinessRoles = new HashMap<String,String>();
                for ( Bundle bundle : bundles ) {

                    if ( bundle == null ) continue;

                    ActivityConfig config = bundle.getActivityConfig();
                    if ( ( config == null ) || ( !config.enabled() ) ) {
                        continue;
                    }

                    String businessRole =  bundle.getName() ;
                    List<Profile> profiles = bundle.getProfiles();
                    if ( profiles != null ) {
                        for ( Profile profile : profiles ) {
                            Application app = profile.getApplication();
                            if ( app == null ) {
                                continue;
                            }
                            String appName = app.getName();
                            if ( ( businessRole != null ) && ( appName != null ) ) {
                                String s = this.activityBusinessRoles.get(appName);
                                if ( s == null ) {
                                    this.activityBusinessRoles.put(appName, businessRole);
                                } else {
                                    s = s + "," + businessRole;
                                    this.activityBusinessRoles.put(appName, s);
                                }
                            }
                        }
                    }
                }
            }
        }
        return this.activityBusinessRoles;
    }

    /**
     * Add an AttributeRequest to add or remove applications from the
     * ActivityConfig.  Rather than just use Operation.Set to slam in
     * a new value, we'll calculate just the differences which is more 
     * useful to see. 
     *
     * This will be called twice, once to calculate the adds and again
     * to calculate the removes.
     *
     * The ActivityConfig ultimately will have the Application database ids
     * but in the plan we'll use names so they are meaningful in
     * an approval.  IIQEvaluator will convert them back.
     */
    void addActivityConfigChanges(Identity ident, 
                                          AccountRequest account,
                                          boolean adds) 
        throws GeneralException {

        List<String> names = null;

        ActivityConfig current = ident.getActivityConfig();
        Map<String,Boolean> activitySelections = getActivitySelections();

        if (activitySelections != null && activitySelections.size() > 0) {

            Iterator<Map.Entry<String,Boolean>> it = activitySelections.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String,Boolean> entry = it.next();
                // must be the same polarity
                if (adds == entry.getValue()) {
                    String id = entry.getKey();
                    Application app = this.parent.getContext().getObjectById(Application.class, id);
                    if (app == null)
                        log.error("Application evaporated: " + id);
                    else {
                        boolean include = false;
                        if (adds)
                            include = (current == null || !current.isEnabled(id));
                        else 
                            include = (current != null && current.isEnabled(id));

                        if (include) {
                            if (names == null)
                                names = new ArrayList<String>();
                            names.add(app.getName());
                        }
                    }
                }
            }
        }

        if (names != null) {
            AttributeRequest req = new AttributeRequest();
            req.setName(ProvisioningPlan.ATT_IIQ_ACTIVITY_CONFIG);
            req.setOperation((adds) ? Operation.Add : Operation.Remove);
            req.setValue(names);
            account.add(req);
        }
    }

    /**
     * 
     * ApplicationActivity backing bean
     *
     */
    public static class ActivityBean {

        private String id;
        private Action action;
        private String target;
        private String sourceApplication;
        private Result result;
        private Date timeStamp;

        public ActivityBean(ApplicationActivity activity) {
            this.id = activity.getId();
            this.action = activity.getAction();
            this.target = activity.getTarget();
            this.sourceApplication = activity.getSourceApplication();
            this.result = activity.getResult();
            this.timeStamp = activity.getTimeStamp();
        }
        
        public String getId() {
            return this.id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Action getAction() {
            return this.action;
        }

        public void setAction(Action action) {
            this.action = action;
        }

        public String getTarget() {
            return this.target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getSourceApplication() {
            return this.sourceApplication;
        }

        public void setSourceApplication(String sourceApplication) {
            this.sourceApplication = sourceApplication;
        }

        public Result getResult() {
            return this.result;
        }

        public void setResult(Result result) {
            this.result = result;
        }
        
        public Date getTimeStamp() {
            return this.timeStamp;
        }
        
        public void setTimeStamp(Date val) {
            this.timeStamp = val;
        }
    }
    
}
