/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementation of IntegrationExecutor interface used for system testing.
 * 
 * Author: Jeff
 *
 * This is similar to TestExecutor but doesn't have the simulated errors.
 * The purpose is to print stuff to the console so you can try things in
 * the UI and see that the integration is being called.
 *
 * The file test/integration.xml has an IntegrationConfig that uses
 * this class.  Tweak that as necessary to control which roles
 * are sync'd.
 *
 * I'm putting this in the core product rather than in the test
 * branch so we have it accessible for debugging in POCs and deployments.
 */

package sailpoint.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.integration.RoleDefinition;
import sailpoint.integration.ProvisioningPlan;

import sailpoint.object.Bundle;
import sailpoint.object.ProvisioningResult;
import sailpoint.tools.Util;

/**
 * Implementation of IntegrationExecutor interface used for system testing.
 * This prints information to stdout and keeps role and provisioning information
 * in memory.
 *
 * @ignore
 * This is similar to TestExecutor but doesn't have the simulated errors.
 * The purpose is to print stuff to the console so you can try things in
 * the UI and see that the integration is being called.
 *
 * The file test/integration.xml has an IntegrationConfig that uses
 * this class.  Tweak that as necessary to control which roles
 * are sync'd.
 */
public class TraceExecutor extends AbstractIntegrationExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Special argument passed in ProvisioningPlan's integration data
     * map to simulate errors for testing.
     */
    public static final String ARG_SIMULATE_ERROR = "simulateError";


    /**
     * "database" of roles that are being managed.
     */
    static List<RoleDefinition> _roles = null;

    /**
     * History of provisioning requests that were processed.
     */
    static List<ProvisioningPlan> _plans = null;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public TraceExecutor() {
    }

    /**
     * Print the given object to stdout.
     */
    public static void println(Object o) {
        System.out.println(o);
    }

    /**
     * Convert the given Map to a JSON string.
     */
    public String jsonify(Map map) {
        String json = null;
        // this can throw, eat it
        try {
            json = JsonUtil.render(map);
        }
        catch (Throwable t) {
            println("TraceExecutor: Unable to jsonify Map!");
        }
        return json;
    }

    /**
     * Convert the given RoleDefinition to a JSON string.
     */
    public String jsonify(RoleDefinition def) {
        return jsonify(def.toMap());
    }

    /**
     * Convert the given ProvisioningPlan to a JSON string.
     */
    public String jsonify(ProvisioningPlan plan) {
        return jsonify(plan.toMap());
    }

    /**
     * Clear the RoleDefinitions and ProvisioningPlans that are stored in memory.
     */
    static public void reset() {
        _roles = null;
        _plans = null;
    }

    /**
     * Return the RoleDefinitions that have been added through
     * {@link #addRole(RoleDefinition)}.
     */
    static public List<RoleDefinition> getRoles() {
        return _roles;
    }

    /**
     * Return the ProvisioningPlans that have been added through
     * {@link #provision(String, ProvisioningPlan)}.
     */
    static public List<ProvisioningPlan> getProvisions() {
        return _plans;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationInterface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Configure this integration - this is a no-op.
     */
    public void configure(Map args) throws Exception {
    }

    /**
     * Print a message and return a successful ping response.
     */
    public String ping() throws Exception {

        println("TraceExecutor: ping");

        return "Good morning starshine, the earth says hello!";
    }

    /**
     * Return the current list of "manageable" roles.
     */
    public List listRoles() throws Exception {

        List names = new ArrayList();
        if (_roles != null) {
            for (RoleDefinition role : _roles)
                names.add(role.getName());
        }

        println("TraceExecutor: listRoles");
        println(names);

        return names;
    }

    /**
     * Create or update the definition of a role.
     */
    public RequestResult addRole(RoleDefinition def) throws Exception {

        RequestResult result = null;

        println("TraceExecutor: addRole");
        println(jsonify(def));

        String name = def.getName();

        if (name == null) {
            result = new RequestResult();
            result.addError("Missing role name");
        }
        else {
            if (_roles == null)
                _roles = new ArrayList<RoleDefinition>();

            RoleDefinition existing = findRole(name);
            if (existing != null)
                _roles.remove(existing);
            _roles.add(def);
        }
        
        return result;
    }

    private RoleDefinition findRole(String name) {
        RoleDefinition found = null;
        if (name != null && _roles != null) {
            for (RoleDefinition role : _roles) {
                if (name.equals(role.getName())) {
                    found = role;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Delete a role.
     */
    public RequestResult deleteRole(String name) throws Exception {

        RequestResult result = null;

        println("TraceExecutor: deleteRole " + name);

        if (name == null) {
            result = new RequestResult();
            result.addError("Missing role name");
        }
        else if (_roles == null || !_roles.contains(name)) {
            result = new RequestResult();
            result.addError("Role does not exist");
        }
        else {
            _roles.remove(name);
        }
        
        return result;
    }

    /**
     * Override to handle non-identity plans (i.e. for group provisioning)
     */
    @Override
    public ProvisioningResult provision(sailpoint.object.ProvisioningPlan plan)
            throws Exception {
        ProvisioningResult result = new ProvisioningResult();
        
        // Convert to integration plan 
        ProvisioningPlan integrationPlan = new ProvisioningPlan(plan.toMap());
        String identity = (integrationPlan.getIdentity() == null) ? "non-identity request" : integrationPlan.getIdentity();
        doProvision(identity, integrationPlan, true);

        // set this so we can test propagation of async request ids
        result.setRequestID("42");

        return result;
    }
    
    /**
     * Make changes to an identity defined by a ProvisioningPlan.
     */
    public RequestResult provision(String identity, ProvisioningPlan plan)
        throws Exception {

        RequestResult result = new RequestResult();
        if (identity == null) {
            result.addError("Missing identity");
        }
        
        doProvision(identity, plan, (identity != null));
        
        // set this so we can test propagation of async request ids
        result.setRequestID("42");

        return result;
    }

    private void doProvision(String provisionLabel, ProvisioningPlan plan, boolean addToPlans) throws Exception {
        println("TraceExecutor: provision " + provisionLabel);
        println(jsonify(plan));

        // hack for testing, if this magic attribute is set
        // throw an exception
        if (Util.otob(plan.get(ARG_SIMULATE_ERROR)))
            throw new Exception("Simulated error!");

        if (addToPlans) {
            if (_plans == null)
                _plans = new ArrayList<ProvisioningPlan>();
            _plans.add(plan);
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationExecutor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Print a message to stdout saying that the role is finished.
     * {@inheritDoc}
     */
    public void finishRoleDefinition(Bundle src, 
                                     RoleDefinition dest)
        throws Exception {

        println("TraceExecutor: finishRoleDefinition " + src.getName());
    }


}
