/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A container for the results of a role mining process.
 * The creation date will be the date the mining was finished,
 * the owner will be the identity that launched the mining process.
 *
 * Author: Jeff
 *
 * ISSUES: Since role mining is implemented as a task, we could
 * just leave results in the TaskResult.  I'd like to have the
 * result model be a little more concrete though, mainly so we can
 * represent the list of candidate roles outside of the XML blob
 * of the TaskResult.  We can reconsider this someday...
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A container for the results of a role mining process.
 * The creation date will be the date the mining was finished,
 * the owner will be the identity that launched the mining process.
 *
 * These are a bit like task results, they are displayed and managed
 * by the undirected mining pages.   
 */
@XMLClass
public class RoleMiningResult extends SailPointObject implements Cloneable
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * True if the task is still running.
     */
    boolean _pending;

    /**
     * The list of candidate roles.
     */
    List<CandidateRole> _roles;

    /**
     * The configuration the result was created with.
     * This is an internal copy that might not match the
     * current configuration.
     */
    MiningConfig _config;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public RoleMiningResult() {
    }

    public boolean isNameUnique() {
        return false;
    }

    public void add(CandidateRole r) {
        if (r != null) {
            if (_roles == null)
                _roles = new ArrayList<CandidateRole>();
            _roles.add(r);
        }
    }

    public void remove(CandidateRole r) {
        if (_roles != null)
            _roles.remove(r);
    }

    public void add(List<CandidateRole> roles) {
        if (roles != null && roles.size() > 0) {
            if (_roles == null)
                _roles = new ArrayList<CandidateRole>();
            _roles.addAll(roles);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * True if the task is still running.
     */
    @XMLProperty
    public boolean isPending() {
        return _pending;
    }

    public void setPending(boolean b) {
        _pending = b;
    }

    /**
     * The list of candidate roles.
     */
    @XMLProperty(mode=SerializationMode.LIST,xmlname="CandidateRoles")
    public List<CandidateRole> getRoles() {
        return _roles;
    }

    public void setRoles(List<CandidateRole> roles) {
        _roles = roles;
    }
    
    @XMLProperty(mode=SerializationMode.INLINE,xmlname="MiningConfig")
    public void setConfig(MiningConfig config) {
        _config = config;
    }

    /**
     * The configuration the result was created with.
     * This is an internal copy that might not match the
     * current configuration.
     */
    public MiningConfig getConfig() {
        return _config;
    }

    //
    // Pseudo-properties for the UI
    //

    public int getRoleCount() {

        return (_roles != null) ? _roles.size() : 0;
    }

    public String getStatus() {
        return (_pending) ? "Pending" : "Complete";
    }

    public String getConfigName() {
        return (_config != null) ? _config.getName() : null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Lookup a role by name. Used in the UI when drilling into
     * a result. Assuming that the list is not too long, might want
     * to convert this to a HashMap.
     *
     * These are SailPointObjects but they are not in Hibernate yet so
     * they will not have an _id field. Assume that the id is the name
     * and that the Prospector has set unique names.
     */
    public CandidateRole getRole(String id) {

        CandidateRole found = null;
        if (id != null && _roles != null) {
            for (int i = 0 ; i < _roles.size() ; i++) {
                CandidateRole role = _roles.get(i);
                if (id.equals(role.getId()) ||
                    id.equals(role.getName())) {
                    found = role;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * @ignore
     * Note that you can only have persistent fields here, 
     * sigh wanted to use roleCount.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");        
        cols.put("created", "Date");
        //cols.put("roleCount", "Roles Identified");
        return cols;
    }

    public static String getDisplayFormat() {
        return "%-34s %s\n";
    }

}
