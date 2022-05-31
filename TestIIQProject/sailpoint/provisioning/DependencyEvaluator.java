
package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Field;
import sailpoint.object.Field.ApplicationDependency;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Template;
import sailpoint.object.Template.Usage;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * Class to help manage plan dependencies and order of provisioning.
 * 
 * @author dan.smith
 *
 */
public class DependencyEvaluator {
    
    private static Log log = LogFactory.getLog(DependencyEvaluator.class);

    /**
     * The overall project so we can find dependencies being pushed 
     * at the same time as the dependent.
     */
    ProvisioningProject project;

    /**
     * Identity we are dealing with, used to lookup existing Link data.
     */
    Identity identity;
    
    /**
     * Good ol' context
     */
    SailPointContext context;

    /**
     * Making this protected as it is intended to be used only be the PlanEvaluator.
     * 
     * @param context
     * @param project
     * @param identity
     */
    protected DependencyEvaluator(SailPointContext context, ProvisioningProject project, Identity identity) {
        this.project = project;
        this.identity = identity;
        this.context = context;
    }

    /**
     * Take a provisioning plan, compute if it has dependencies and
     * check to see if those dependencies have been fulfilled.
     * 
     * @param plan
     * @return return true if the plan is ready for execution
     * @throws GeneralException
     */
    public boolean eval(ProvisioningPlan plan) throws GeneralException {            
        boolean ready = true;                  
        //
        // Cycle through the dependencies and determine
        // if they have been successfully executed.
        //
        List<Dependency> deps = getDependencies(plan);
        if ( Util.size(deps) > 0 ) {
            ready = dependenciesReady(plan, deps);
            if ( ready ) {                                   
                copyDependencyFields(plan, deps);
            } 
        }
        return ready;
    }        

    /**
     * Check through the project and see if there is any plans that
     * haven't been executed.
     * 
     * Also check with the dependency plans and make sure they aren't
     * in retry if they are, consider the dependents non ready as 
     * well.
     *
     * @param project
     * @return
     * @throws GeneralException
     */
    public boolean hasReadyPlans(ProvisioningProject project) throws GeneralException {        
        List<ProvisioningPlan> plans = project.getIntegrationPlans();            
        int plansReady = 0;            
        if ( plans != null ) {
            for ( ProvisioningPlan plan : plans ) {
                if ( plan == null ) {
                    continue;
                }         
                // If there is a plan un executed or needs retry is needs
                // to be tried
                if ( !plan.hasBeenExecuted() && !plan.needsRetry() && !dependencyNeedsRetry(plan, getDependencies(plan))  ) {
                    plansReady++;
                }                    
            }
        }        
        return ( plansReady > 0 ) ?  true :  false;
    }       

    /**
     * Return true if the plan has dependencies that still have to be 
     * executed and can be executed.
     * 
     * If the dependency plan(s) fail bail out and mark the dependent
     * application with an error.
     * 
     * If there are no plans in the current project for the dependencies
     * then check to see if there is an existing Link where we can
     * source the value. 
     * 
     * @param plan
     * @param appDeps
     * @return boolean true if a dependency is ready
     * 
     * @throws GeneralException
     */
    private boolean dependenciesReady(ProvisioningPlan plan, List<Dependency> appDeps) 
            throws GeneralException {

        if ( Util.size(appDeps) == 0  ) 
            return true;

        int ready = 0;            
        for ( Dependency dep : appDeps ) {
            if ( dep == null ) 
                continue;                
            if ( dependencyReady(plan, dep) ) {
                ready++;
            }
        }    
        if ( ready == Util.size(appDeps) ) {
            
            return true;
        }
        if ( log.isDebugEnabled() ) {
           log.debug("All Deps Ready: " + ready + " deps " + Util.size(appDeps));   
        }
        return false;
    }
    
    private QueryOptions getLinkQueryOptions(String appName) throws GeneralException {
        Application app = context.getObjectByName(Application.class, appName);
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("application", app));
        options.add(Filter.eq("identity", identity));
        
        return options;
    }

    /**
     * Check on dependency to see if its been executed and ready
     * so the dependent plans can be executed.
     * 
     * @param dependentPlan
     * @param dep
     * @return
     * @throws GeneralException
     */
    private boolean dependencyReady(ProvisioningPlan dependentPlan, Dependency dep ) 
            throws GeneralException{

        List<String> appNames = dep.getEffectiveDependencyApplicationNames();
        if ( appNames == null ) 
            return true;

        int ready = 0;
        for ( String appName : appNames ) {           
            if ( appName == null ) continue;

            ProvisioningPlan dependencyPlan = getDependencyPlan(appName);
            if ( dependencyPlan != null ) {
                // First off, if the dependency plan failed, fail the dependents too..
                if (ProvisioningResult.STATUS_FAILED.equals(dependencyPlan.getNormalizedStatus())) {
                    ProvisioningResult result = dependentPlan.getResult();
                    if ( result == null ) {
                        result = new ProvisioningResult();
                        dependentPlan.setResult(result);
                    }
                    result.setStatus(ProvisioningResult.STATUS_FAILED);
                    result.addError("Dependency application(s) " + dependencyPlan.getApplicationNames() + " failed, unable to fulfill dependent requests on application(s)." + dependentPlan.getApplicationNames());
                    return false;
                }
                // if the dependency plan needs a retry we can't do anything with the dependent
                if ( dependencyPlan.needsRetry()  )
                    return false;

                if ( dependencyPlan.hasBeenExecuted() ) {
                    ready++;
                }  

            } else {
                // Check to see if one of the links has it
                if ( identity != null ) {
                    //prevent lazy init error in case of dropped hibernate session     
                    if(context.countObjects(Link.class, getLinkQueryOptions(appName)) > 0) {
                        ready++;
                    } else {
                        throw new GeneralException("Dependency was not found in request or on the Link!");
                    }
                    
                }

            }
        }

        if ( log.isDebugEnabled() ) {
            log.debug("Dep Ready: " + ready + " dep apps " + Util.size(appNames) );   
         }
        if ( ready == Util.size(appNames) ) {
            return true;
        }
        return false;
    }    

    /**
     * 
     * Dig into a plan, looking for the dependency status. If the dependency
     * is marked retry return true, otherwise return false.
     * 
     * @param plan
     * @param appDeps
     * @return true when the dependent application needs a retry
     * @throws GeneralException
     */
    private boolean dependencyNeedsRetry(ProvisioningPlan plan, List<Dependency> appDeps) 
            throws GeneralException {

        if ( appDeps == null ) 
            return false;

        for ( Dependency dep : appDeps ) {
            List<String> deps = dep.getEffectiveDependencyApplicationNames();
            if ( deps == null ) break;

            for ( String appName : deps ) {
                if ( appName != null ) {
                    ProvisioningPlan dependencyPlan = project.getPlan(appName);
                    if ( dependencyPlan != null ) {
                        // if the dependency plan needs a retry we can't do anything with the dependent
                        if ( dependencyPlan.needsRetry()  ) {
                            return true;
                        }
                    }
                }
            }
        }    
        return false;
    }

    /**
     * Copy over the dependency values on to the dependent plan.
     * 
     * @param plan
     * @throws GeneralException
     */
    private void copyDependencyFields(ProvisioningPlan dependentPlan, List<Dependency> appDeps)                
            throws GeneralException {

        if ( Util.size(appDeps) == 0 )
            return;

        //
        // For each dependency copy it over to the correct place
        //
        for ( Dependency dep : appDeps ) {
            if ( dep == null || dep.isAppOnly() ) 
                continue;

            List<Field> fields = dep.getFields();
            for ( Field field : fields ) {
                ApplicationDependency fieldDep = field.getAppDependency();
                String dependencyApp = fieldDep.getApplicationName();
                String dependencyAttr = fieldDep.getSchemaAttributeName();
                Object dependencyValue = null;

                ProvisioningPlan dependencyPlan = project.getPlan(dependencyApp);
                if ( dependencyPlan != null ) {
                    dependencyValue = getDependencyValueFromPlan(dependencyPlan, fieldDep);
                } else {
                    // get it from the link..
                    if ( identity != null ) {
                        Iterator<Link> links = context.search(Link.class, getLinkQueryOptions(dependencyApp));
                        while ( links != null && links.hasNext() ) {
                            Link link = links.next();
                            if ( link != null ) {
                                dependencyValue = link.getAttribute(dependencyAttr);
                                if ( dependencyValue == null ) {
                                    Application application = link.getApplication();
                                    // In some cases we'll remove the native identitifer's value from
                                    // the map same with display name
                                    if ( isNativeIdentifier(application, dependencyAttr)) {
                                        dependencyValue = link.getNativeIdentity();
                                    } else
                                    if ( isDisplayName(application, dependencyAttr) ) {
                                        dependencyValue = link.getDisplayName();
                                    }
                                }
                            }                                
                        }
                    }                        
                }
                if ( dependencyValue == null ) {
                    throw new GeneralException("Unable to find dependencyValue ["+dependencyAttr+"] for application ["+dependencyApp+"] using current project or an existing link.");                    
                }
                List<AccountRequest> reqs = dependentPlan.getAccountRequests();
                if ( Util.size(reqs) == 1  ) {
                    String depFieldName = field.getName();
                    AccountRequest dependentReq = reqs.get(0);
                    if ( dependentReq != null ) {      
                        Application application = dependentReq.getApplication(context);
                        if ( isNativeIdentifier(application, depFieldName)) {
                            dependentReq.setNativeIdentity(Util.otoa(dependencyValue));
                        } else {
                            AttributeRequest req = dependentReq.getAttributeRequest(depFieldName);
                            if ( req == null ) {
                                req = new AttributeRequest(depFieldName, Operation.Add, dependencyValue);
                                dependentReq.add(req);
                            } else {
                                req.setValue(dependencyValue);
                            }
                        }
                    } else {
                        throw new GeneralException("Dependent request was found, but null.");
                    }
                } else {
                    if ( Util.size(reqs) == 0 )
                        throw new GeneralException("Unable to find the dependent request.");
                    else
                        // more than one request
                        throw new GeneralException("The dependent plan had more then one provisioning plan for the dependent application. This feature is supported only on connectors that support synchronous provisioning.");
                }
            }           
        }
    }

    /**
     * 
     * Dig out the dependency value from the plan.
     * 
     * @param dependencyPlan
     * @param dependency
     * @return
     * @throws GeneralException
     */
    private Object getDependencyValueFromPlan(ProvisioningPlan dependencyPlan, ApplicationDependency dependency) 
            throws GeneralException {

        Object value = null;
        if ( dependencyPlan != null ) {
            String appName = dependency.getApplicationName();
            String attrName = dependency.getSchemaAttributeName();
            AccountRequest req = getCreateAccountRequest(dependencyPlan, appName);
            if ( req != null ) {
                // Check for an attribute request
                AttributeRequest attr = req.getAttributeRequest(attrName);
                if ( attr == null ) {
                    Application application = req.getApplication(context);
                    // We may have normalized this (removed it from the attribute set) and set it as the accoutns 
                    // the nativeIdentitifer                    
                    if ( isNativeIdentifier(application , attrName )) {
                        value = req.getNativeIdentity();
                    }
                } else {
                    value = attr.getValue();
                }
            }
        }
        return value;

    }        

    ///////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    //////////////////////////////////////////////////////////////////////        

    /**
     * Build and validate the dependencies for a provisioning plan.
     * 
     * @param plan
     * @return
     * @throws GeneralException
     */
    private List<Dependency> getDependencies(ProvisioningPlan plan) 
            throws GeneralException {

        List<Dependency> appDeps = new ArrayList<Dependency>();
        if ( plan != null ) {                
            List<AccountRequest> reqs = plan.getAccountRequests();
            if ( reqs != null ) {
                for ( AccountRequest req : reqs ) {
                    //bug#26848 -- Dependencies only relevant in Create operation
                    if (AccountRequest.Operation.Create.equals(req.getOperation())) {
                        Application app = req.getApplication(context);
                        if ( app != null ) {
                            if ( Util.size(app.getDependencies()) > 0 ) {
                                Dependency dep = new Dependency();
                                dep.init(app);
                                appDeps.add(dep);
                            }
                        }
                    }
                }
            }
        }            
        return appDeps;
    }         

    /**
     * Look through the schema for the application and see if the identity attribute
     * is equal to the name of the attribute we are suppose to move over.
     *  
     * @param appName
     * @param schemaAttributeName
     * @return
     * @throws GeneralException
     */
    private boolean isNativeIdentifier(Application app, String schemaAttributeName ) 
            throws GeneralException {

        if ( app != null && schemaAttributeName != null) {
            Schema schema = app.getAccountSchema();
            if ( schema != null ) {
                String identity = schema.getIdentityAttribute();
                if ( Util.nullSafeEq(schemaAttributeName, identity) ) {
                    return true;
                }
            }
        }            
        return false;
    }

    
    private boolean isDisplayName(Application app, String schemaAttributeName)
            throws GeneralException {
        
        if ( app != null && schemaAttributeName != null ) { 
            Schema schema = app.getAccountSchema();
            if ( schema != null ) {
                String displayName = schema.getDisplayAttribute();
                if ( Util.nullSafeEq(schemaAttributeName, displayName) ) {
                    return true;
                }
            }
        }            
        return false;
    }

    /**
     * From a plan peal out the Create request for a given application.
     * 
     * @param dependencyPlan
     * @param forApplication
     * @return
     */
    private AccountRequest getCreateAccountRequest(ProvisioningPlan dependencyPlan, String forApplication) {            
        if ( dependencyPlan != null ) {
            List<AccountRequest> reqs = dependencyPlan.getAccountRequests(forApplication);
            if ( reqs != null  ) {
                for ( AccountRequest appReq : reqs ) {
                    if ( Util.nullSafeEq(appReq.getOperation(), AccountRequest.Operation.Create) ) {
                        return appReq;                            
                    }
                }
            }
        }
        return null;
    }

    /**
     * 
     * Retrieve the dependency plan from the project and VERIFY the plan 
     * has a Create request.
     * 
     * @param appName
     * @return
     */        
    private ProvisioningPlan getDependencyPlan(String appName) {
        ProvisioningPlan dependencyPlan = project.getPlan(appName);
        if ( dependencyPlan != null ) {
            AccountRequest req = getCreateAccountRequest(dependencyPlan, appName);
            if ( req == null ) {
                return null;
            }
        }            
        return dependencyPlan;
    }

    /**
     * Internal model used to describe the
     * dependencies for an application.
     * 
     * If there are application level dependencies that are listed
     * on the Application object, then they will dictate order of the
     * requests.
     * 
     * Field level dependencies will dictate what values will be copied
     * from the dependency.
     * 
     * @author dan.smith
     *
     */
    private class Dependency {
        /**
         * Application level dependencies.
         */
        private List<Application> dependencies;

        /**
         * Field level dependencies;
         */
        private List<Field> fields;

        /**
         * If there are no field level dependencies, 
         * its app only.
         */
        private boolean appOnly;

        public Dependency() {      
            appOnly = false;
            fields = null;
            dependencies = null;                
        }            

        /**
         * Calculate the dependency information for
         * an application.
         * 
         * Use the combination of the application level
         * setting and field level settings to build
         * up this object.
         * 
         * The application level dependencies are 
         * used for ordering only and won't have
         * field level dependencies.
         * 
         * @param app
         * @throws GeneralException
         */
        public void init(Application app) throws GeneralException {

            // make sure we are going to go cycle-elic
            ObjectUtil.validateDependencies(app);

            fields = getDependentFields(app);                
            dependencies = app.getDependencies();

            if ( Util.size(fields) > 0 ) {
                appOnly = false;
            } else
                if ( Util.size(dependencies) > 0 ) {
                    appOnly = true;
                }
        }

        /**
         * Get the names of all the applications that are
         * dependencies.   This include all of the top level
         * configured applications and all applications
         * referenced in fields.
         * 
         * @return
         */
        public List<String> getEffectiveDependencyApplicationNames() {
            List<String> appNames = new ArrayList<String>();

            if ( dependencies != null ) {
                for ( Application app : dependencies ) {
                    if ( app != null ) {
                        appNames.add(app.getName());
                    }
                }
            }       

            if ( fields != null ) {
                for ( Field field : fields) {
                    ApplicationDependency appDep = field.getAppDependency();
                    if ( appDep != null ) {
                        String appName = appDep.getApplicationName();
                        if ( appName != null && !appNames.contains(appName) ) {
                            appNames.add(appName);
                        }
                    }
                }
            }
            return (appNames.size() > 0) ? appNames : null;
        }

        /**
         * Lookup and return the dependent fields for an application.  Dig though the
         * provisioning policy for create and look for fields with ApplicationDependencies
         * on them, if they are both non-null add them to the list of dependencies.
         * 
         * @param app
         * @return
         * @throws GeneralException
         */
        private List<Field> getDependentFields(Application app) 
                throws GeneralException {

            List<Field> dependentFields = new ArrayList<Field>();        
            List<Template> templates = app.getTemplates();
            if ( Util.size(templates) > 0 ) {
                for ( Template temp : templates ) {
                    if ( temp == null ) continue;                    
                    Usage usage = temp.getUsage();
                    if ( Util.nullSafeEq(usage, Usage.Create) ) {
                        List<Field> fields = temp.getFields(context);
                        if ( Util.size(fields) > 0 ) {
                            for ( Field field : fields ) {
                                if ( field.getAppDependency() != null )  {             
                                    dependentFields.add(field);
                                }
                            }
                        }
                    }
                }
            }
            return dependentFields;
        }

        public boolean isAppOnly() {
            return this.appOnly;
        }

        public List<Field> getFields() {
            return fields;
        }
    }
}
