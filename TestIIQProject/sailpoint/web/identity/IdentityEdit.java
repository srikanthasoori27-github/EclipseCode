/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object encapsulating the editing state for one identity object.
 *
 * This object must be completely serializable
 * in order to support clustering.
 */
package sailpoint.web.identity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.web.AttributeEditBean;
import sailpoint.web.EventBean;
import sailpoint.web.identity.DetectedRolesHelper.DetectedRoleBean;
import sailpoint.web.identity.LinksHelper.LinkBean;
import sailpoint.web.policy.ViolationViewBean;

public class IdentityEdit implements Serializable {

    // for some reason my eclipse is chocking when generating this.
    // please feel free to replace with generated one
    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(IdentityEdit.class);

    /**
     * A list of special beans to represent assigned business roles.
     */
    List<DetectedRoleBean> _detectedRoles;

    /**
     * A list of beans representing the assigned roles.
     */
    List<RoleAssignmentBean> _assignedRoles;

    /**
     * A list of beans represent the assigned entitlements/attributes
     */
    List<AttributeAssignmentBean> _assignedEntitlements;

    /**
     * A list of special beans to represent entitlement exceptions.
     */
    List<ExceptionBean> _exceptions;

    /**
     * A list of objects representing policy violations.
     * Don't need a wrapper bean for these yet.
     */
    List<ViolationViewBean> _violations;

    /**
     * A list of objects representing policy violations.
     * owned by the logged in user.
     */
    List<ViolationViewBean> _violationsOwnedByLoggedInUser;
    
    /**
     * Map keyed by IdentitySnapshot object ids containing the
     * selection status.
     */
    Map<String,Boolean> _snapshotSelections;

    /**
     * Map keyed by Request object ids containing the
     * selection status.
     */
    Map<String,Boolean> _eventSelections;

    /**
     * Map keyed by Link object ids containing the selection 
     * status of the links.
     */
    Map<String,Boolean> _linkSelections;
    
    /**
     * A list of objects describing all the identity snapshots 
     * associated with this object.  This is generated from a projection
     * query since the list may be long and snapshots could be large   
     * and we don't want to drag them all into memory just to show a 
     * summary table.
     */
    List<SnapshotBean> _snapshots;

    /**
     * A list of objects describing all events scheduled for this identity.
     */
    List<EventBean> _events;

    /**
     * A list of application ids, that were selected to be enabled
     * for activity montioring.
     */
    Map<String,Boolean> _activitySelections;

    /**
     * The password value.  If this wasn't modified in the browser
     * it will have a fake value.
     */
    String _password;

    /**
     * The password confirmation value.  This must be the same as
     * _password when the identity is saved.
     */
    String _confirmPassword;
    
    /** Option to set the password to expire so that the user has to choose a new
     * password the next time that they log in.
     */
    boolean _expirePassword;
    
    /**
     * A bean that we use to help edit identity attributes
     */
    AttributeEditBean _attributeEditor;
    
    /**
     * A flag indicating whether the attribute display is in an editable state or not
     */
    boolean _attributesEditable;

    List<LinkBean> _links;

    // CapabilityHelper
    /**
     * List of edited capability names
     */
    private List<String> capabilities;

    // ScopeHelper
    /**
     * List of edited scope ids
     */
    private List<String> controlledScopes;

    /**
     * String for "controls assigned scope" value (see ScopeHelper for possible values)
     */
	private String controlsAssignedScope;

	// ForwardingHelper
    /**
     * Name of forward identity.  Will be null when not changed, empty string when cleared, name when set.
     */
    private String forward;

    /**
     * Whether forward start date is checked and set
     */
    private Boolean forwardStart;

    /**
     * Whether forward end date is checked and set
     */
    private Boolean forwardEnd;

    /**
     * Date set for forward start
     */
    private Date forwardStartDate;

    /**
     * Date set for forward end
     */
    private Date forwardEndDate;

	
	private String _identityId;

	/** 
	 * the  active tab.
	 * This is the value before the value is changed by
	 * the value set in the page. 
	 * We need this initial value because otherwise the tab
	 * is not rendered and state is not maintained.
	 */
	private int _activeTab;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    public IdentityEdit() {
        
        if (log.isInfoEnabled()) {
            log.info("IdentityEdit()");
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getIdentityId() {
        return _identityId;
    }
    
    public void setIdentityId(String val) {
        _identityId = val;
    }
    
    public int getActiveTab() {
    	return _activeTab;
    }
    
    public void setActiveTab(int val) {
    	_activeTab = val;
    }
    
    public void setDetectedRoles(List<DetectedRoleBean> l) {
        _detectedRoles = l;
    }

    public List<DetectedRoleBean> getDetectedRoles() {
        return _detectedRoles;
    }
    
    public void setAssignedRoles(List<RoleAssignmentBean> l) {
        _assignedRoles = l;
    }

    public List<RoleAssignmentBean> getAssignedRoles() {
        return _assignedRoles;
    }

    public void setAssignedEntitlements(List<AttributeAssignmentBean> atts) { _assignedEntitlements = atts; }

    public List<AttributeAssignmentBean> getAssignedEntitlements() {
        return _assignedEntitlements;
    }

    public void setExceptions(List<ExceptionBean> l) {
        _exceptions = l;
    }

    public List<ExceptionBean> getExceptions() {
        return _exceptions;
    }

    public void setViolations(List<ViolationViewBean> l) {
        _violations = l;
    }

    public List<ViolationViewBean> getViolations() {
        return _violations;
    }

    public void setViolationsOwnedByLoggedInUser(List<ViolationViewBean> val) {
        _violationsOwnedByLoggedInUser = val;
    }

    public List<ViolationViewBean> getViolationsOwnedByLoggedInUser() {
        return _violationsOwnedByLoggedInUser;
    }

    public Map<String,Boolean> getSnapshotSelections() {
        if (_snapshotSelections == null)
            _snapshotSelections = new HashMap<String,Boolean>();
        return _snapshotSelections;
    }

    public void clearSnapshotSelections() {
        _snapshotSelections = null;
    }

    public Map<String,Boolean> getLinkSelections() {
        if (_linkSelections == null)
            _linkSelections = new HashMap<String,Boolean>();
        return _linkSelections;
    }

    public void clearLinkSelections() {
        _linkSelections = null;
    }
    
    public void clearAssignedRoleSelections() {
        if (_assignedRoles != null) {
            for (RoleAssignmentBean assignedRoleBean : _assignedRoles) {
                assignedRoleBean.setSelected(false);
            }
        }
    }

    public List<SnapshotBean> getSnapshots() {
        return _snapshots;
    }

    public void setSnapshots(List<SnapshotBean> snaps) {
        _snapshots = snaps;
    }

    /**
     * Add a new snapshot we generated live.
     * Since these are by definition newest, they go in the front of the list.
     */
    public void add(SnapshotBean snap) {

        if (_snapshots == null)
            _snapshots = new ArrayList<SnapshotBean>();
        _snapshots.add(0, snap);
    }

    /**
     * Remove a snapshot from the list after it has been logically deleted.
     */
    public void removeSnapshot(String id) {

        if (_snapshots != null) {
            for (SnapshotBean b : _snapshots) {
                if (b.getId().equals(id)) {
                    b.setPendingDelete(true);
                    break;
                }
            }
        }
    }

    public List<EventBean> getEvents() {
        return _events;
    }

    public void setEvents(List<EventBean> events) {
        _events = events;
    }

    public void add(EventBean event) {
        if (event != null) {
            if (_events == null)
                _events = new ArrayList<EventBean>();
            _events.add(event);
        }
    }

    public void removeEvent(String id) {
        if (_events != null) {
            for (EventBean b : _events) {
                if (b.getId().equals(id)) {
                    b.setPendingDelete(true);
                    break;
                }
            }
        }
    }

    public Map<String,Boolean> getEventSelections() {
        if (_eventSelections == null)
            _eventSelections = new HashMap<String,Boolean>();
        return _eventSelections;
    }

    public void clearEventSelections() {
        _eventSelections = null;
    }

    public Map<String,Boolean> getActivitySelections() {
        if ( _activitySelections == null ) {
            _activitySelections = new HashMap<String,Boolean>(); 
        }
        return _activitySelections;
    }

    public void setActivitySelections(Map<String,Boolean> selections) {        
        _activitySelections = selections;
    }

    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        _password = password;
    }

    public String getConfirmPassword() {
        return _confirmPassword;
    }

    public void setConfirmPassword(String password) {
        _confirmPassword = password;
    }
    
    public AttributeEditBean getAttributeEditor() {
        return _attributeEditor;
    }
    
    public void setAttributeEditor(AttributeEditBean editedAttributes) {
        _attributeEditor = editedAttributes;
    }

    public boolean isAttributesEditable() {
        return _attributesEditable;
    }

    public void setAttributesEditable(boolean editable) {
        _attributesEditable = editable;
    }

    public boolean isExpirePassword() {
        return _expirePassword;
    }

    public void setExpirePassword(boolean password) {
        _expirePassword = password;
    }

    public void removeLink(String linkId) {
        LinkBean link = findLinkById(linkId);
        link.setPendingDelete(true);
        link.setPendingMove(false);
    }
    
    public void moveLink(String linkId, String targetIdentityName) {
        LinkBean link = findLinkById(linkId);
        link.setPendingMove(true);
        link.setPendingDelete(false);
        link.setTargetIdentityName(targetIdentityName);
    }
    
    private LinkBean findLinkById(String linkId) {
        for (LinkBean link : _links) {
            if (link.getId().equals(linkId)) {
                return link;
            }
        }
        throw new IllegalStateException("link with id: " + linkId + " not found");
    }
    
    public List<LinkBean> getLinks() {
        return _links;
    }
    
    public void setLinks(List<LinkBean> val) {
        _links = val;
    }
    
    boolean isAnyLinkForRemove() {
        if (_links == null) {
            return false;
        }
        for (LinkBean link : _links) {
            if (link.isPendingDelete()) {
                return true;
            }
        }
        return false;
    }
    
    boolean isAnyLinkForMove() {
        if (_links == null) {
            return false;
        }
        for (LinkBean link : _links) {
            if (link.isPendingMove()) {
                return true;
            }
        }
        return false;
    }
    
    boolean isAnyLinkEdited() {
        if (_links == null) {
            return false;
        }
        for (LinkBean link : _links) {
            if (link.isEditable()) {
                return true;
            }
        }
        return false;
    }
    
    boolean isAnySnapshotForDelete() {
        
        if (_snapshots == null) {
            return false;
        }
        
        for (SnapshotBean snapshot : _snapshots) {
            if (snapshot.isPendingDelete()) {
                return true;
            }
        }
        return false;
    }
    
    boolean isAnyEventForDelete() {
        
        if (_events == null) {
            return false;
        }
        
        for (EventBean event : _events) {
            if (event.isPendingDelete()) {
                return true;
            }
        }
        return false;
    }

	public void setControlledScopes(List<String> val) {
        controlledScopes = val;
	}
	
	public List<String> getControlledScopes() {
        return controlledScopes;
	}

	public void setCapabilities(List<String> caps) {
		capabilities = caps;
	}
	
	public List<String> getCapabilities() {
		return capabilities;
	}

	public void setControlsAssignedScope(String val) {
		controlsAssignedScope = val;
	}
	
	public String getControlsAssignedScope() {
		return controlsAssignedScope;
	}

    public String getForward() {
        return forward;
    }

    public void setForward(String forward) {
        this.forward = forward;
    }

    public Boolean getForwardStart() {
        return forwardStart;
    }

    public void setForwardStart(Boolean forwardStart) {
        this.forwardStart = forwardStart;
    }

    public Boolean getForwardEnd() {
        return forwardEnd;
    }

    public void setForwardEnd(Boolean forwardEnd) {
        this.forwardEnd = forwardEnd;
    }

    public Date getForwardStartDate() {
        return forwardStartDate;
    }

    public void setForwardStartDate(Date forwardStartDate) {
        this.forwardStartDate = forwardStartDate;
    }

    public Date getForwardEndDate() {
        return forwardEndDate;
    }

    public void setForwardEndDate(Date forwardEndDate) {
        this.forwardEndDate = forwardEndDate;
    }
}
