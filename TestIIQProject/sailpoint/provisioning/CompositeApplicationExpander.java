/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A class used internally by PlanCompiler to process Composite applications, 
 * aka Virtual Applications.
 *
 * Author: Jeff/Dan
 * 
 * Factored out of PlanCompiler in 6.3 because it was getting too big.
 *
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.DefaultLogicalConnector;
import sailpoint.connector.LogicalConnector;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.tools.GeneralException;

public class CompositeApplicationExpander {

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    private static Log log = LogFactory.getLog(CompositeApplicationExpander.class);

    /**
     * Parent compiler.
     */
    PlanCompiler _comp;

    /**
     * A cache of CompositeConnectors keyed by application name.
     * Used when expanding plans written against composite apps
     * into plans against the tier apps.
     */
    Map<String,LogicalConnector> _compositeConnectors;

    //////////////////////////////////////////////////////////////////////  
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////  

    public CompositeApplicationExpander(PlanCompiler comp) {
        _comp = comp;
    }

    //////////////////////////////////////////////////////////////////////  
    //
    // Interface
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * For every plan that targets a composite application, 
     * let the connector modify or replace the plan.
     */
    public boolean expandCompositeApplications()
        throws GeneralException {

        int composites = 0;
        
        ProvisioningProject project = _comp.getProject();
        List<ProvisioningPlan> plans = project.getPlans();
        if (plans != null) {
            
            //Defect 24790 - We had an issue where the assignmentAttribute tends to make assignement sticky
            //In this issue here I am setting a attribute to bepass that later in the process. This is being set
            //as the default behavior for logical application.  There is a switch on the logical app to tuen this off
            for (ProvisioningPlan plan : plans){
                if (null != plan.getAccountRequests()){
                    for ( AccountRequest req : plan.getAccountRequests() ) {
                        Boolean isRetain = false;
                        Application app = _comp.getApplication(req.getApplication());
                        if (null != app && app.isLogical()){
                            Object obj = app.getAttributeValue(DefaultLogicalConnector.CONFIG_RETAIN_ENTITLEMENT_ON_REMOVE);
                            if(null != obj){
                                isRetain = (Boolean)obj;
                            }
                            if (!isRetain) {
                                if(null != req.getAttributeRequests()){
                                    for(AttributeRequest attr : req.getAttributeRequests()){
                                        attr.put(AttributeRequest.ATT_PREFER_REMOVE_OVER_RETAIN, true);
                                    }
                                }
                            }
                        }
                    }
                }
            } 
            
            //
            // During the expansion some plans can be removed
            // or added from the original list so make a copy
            // to protect against ConcurrentModification exceptions
            //
            List<ProvisioningPlan> copyOfPlans = new ArrayList<ProvisioningPlan>(plans);
            for (ProvisioningPlan plan : copyOfPlans ) {
                if (!plan.isIIQ()) {
                    composites += expandCompositeApplications(plan);
                }
            }
        }

        return (composites > 0);
    }

    /**
     * For each request to a composite app, let the connector
     * supply a set of replacement requests.  Because we can't
     * trust the connector to not damage other parts of the plan,
     * fake up a plan containing only the request of iterest.
     *
     * Note that prior to 5.1, we would always put the expanded 
     * requests in the same plan as the one containing the composite
     * request.  There are cases where the expansion may need to go
     * in a different partition so we have to take the plans
     * returned by the rule and partition them.
     */
    private int expandCompositeApplications(ProvisioningPlan plan)
        throws GeneralException {

        int composites = 0;

        Identity identity = _comp.getIdentity();

        // skip this if we don't have an Identity, connectors normally
        // need this to locate the tier links
        if (identity != null) {
            List<AccountRequest> accounts = plan.getAccountRequests();
            if (accounts != null) {

                // build a new list with the composites filtered out
                List<AccountRequest> filtered = new ArrayList<AccountRequest>();

                // keep a list of composite expansions for later partitioning
                List<ProvisioningPlan> expansions = new ArrayList<ProvisioningPlan>();

                for (AccountRequest account : accounts) {

                    String app = account.getApplication();
                    LogicalConnector con = getCompositeConnector(app);
                    if (con == null)
                        filtered.add(account);
                    else {
                        composites++;
                        ProvisioningPlan isolated = new ProvisioningPlan();
                        isolated.add(account);
                        if (log.isInfoEnabled()) {
                            log.info("Compiling composite application plan:");
                            log.info(isolated.toXml());
                        }

                        SailPointContext context = _comp.getContext();
                        ProvisioningPlan neu = 
                            con.compileProvisioningPlan(context, identity, isolated);

                        // Normally this will return a new plan, if it returns
                        // null assume this means it modified the original plan.    
                        if (neu == null) neu = isolated;

                        // propagate tracking ids from the composite
                        // to the expansion
                        List<AccountRequest> newAccounts = neu.getAccountRequests();
                        if (newAccounts != null) {

                            // the tracking Id we propagate to the expansions
                            String trackingId = plan.getTrackingId();
                            if (account.getTrackingId() != null)
                                trackingId = account.getTrackingId();

                            for (AccountRequest newa : newAccounts) {
                                // Transfer the tracking id from the original request
                                // to the converted request.  But don't overwrite if
                                // the connector happened to set its own
                                PlanUtil.setTrackingId(newa, trackingId, false);
                            }
                        }

                        if (log.isInfoEnabled()) {
                            log.info("Composite application plan result:");
                            log.info(neu.toXml());
                        }

                        // save for later
                        expansions.add(neu);
                    }
                }

                // save the filtered list
                plan.setAccountRequests(filtered);

                // and partition the expansions
                for (ProvisioningPlan exp : expansions)
                    _comp.partition(exp);
            }
        }

        return composites;
    }

    /**
     * Given an application name from a plan, return a 
     * CompositeConnector if this is a reference to a composite app.
     */
    private LogicalConnector getCompositeConnector(String appname)
        throws GeneralException {

        LogicalConnector con = null;

        if (_compositeConnectors == null)
            _compositeConnectors = new HashMap<String,LogicalConnector>();

        if (_compositeConnectors.containsKey(appname)) {
            con = _compositeConnectors.get(appname);
        }
        else {
            // We need to get the app directly since the cached version can cause
            // lazy initialization failures later on
            Application app = _comp.getApplication(appname);
            if (app != null) {
                // Currently, we can only tell if an application is a composite
                // by attempting to get a composite connector for it.
                String connectorString = app.getConnector();

                // Some connectors, namingly multiplexer's don't have a
                // connector classes so check before we construct.
                if (connectorString != null)
                    con = ConnectorFactory.getCompositeConnector(app);
            }

            _compositeConnectors.put(appname, con);
        }

        return con;
    }
        

}
