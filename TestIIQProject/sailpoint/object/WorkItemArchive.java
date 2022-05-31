/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An archival model for WorkItem objects.  This is a "lives forever"
 * object like IdentitySnapshot.  
 *
 * Author: Jeff
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.server.WorkItemHandler;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.XMLReferenceResolver;


@XMLClass
public class WorkItemArchive extends SailPointObject implements Cloneable
{

    private static final long serialVersionUID = -6801252373967359503L;
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    // Some things that were top-level fields in WorkItem can be
    // stored in the system attributes Map so we only need one XML blob

    public static final String SYSATT_COMPLETION_COMMENTS = "completionComments";
    public static final String SYSATT_COMMENTS = "comments";
    public static final String SYSATT_OWNER_HISTORY = "ownerHistory";
    public static final String SYSATT_CERTIFICATION = "certification";
    public static final String SYSATT_CERTIFICATION_ENTITY = "certificationEntity";
    public static final String SYSATT_CERTIFICATION_ITEM = "certificationItem";
    public static final String SYSATT_REMEDIATION_ITEMS = "remediationItems";
    public static final String SYSATT_E_SIGNATURE = "electronicSignature";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(WorkItemArchive.class);

    /**
     * Name of the original work item owner. Still inherits an owner
     * property from SailPointObject but that is not used for the archive.
     */
    String _ownerName;

    /**
     * Name of a workgroup member that was assigned the item.
     * Note that this is not the same as the owner. The assignee can
     * only be set when the workitem owner is a workgroup, and it has no
     * effect other than serving as a flag in the UI.
     */
    String _assignee;

    /**
     * The name of the identity that was considered the originator of
     * the work item. Can be null for system generated items.
     */
    String _requester;

    /**
     * The name of the identity that completed the work item
     */
    String _completer;
    
    /**
     * The work item type.
     */
    WorkItem.Type _type;

    /**
     * Class to be notified whenever a change to the work item is persisted.
     * The class must implement the sailpoint.server.WorkItemHandler interface.
     * @ignore
     * Do we need this in the archive?
     */
    String _handler;

    /**
     * Optional URL fragment to the JSF include that will render the
     * work item details.
     * @ignore
     * TODO: May need a different renderer for archives!!
     */
    String _renderer;

    /**
     * The completion state. For a work item to be considered completed, it must
     * be assigned one of these states. Lack of a completion state indicates
     * that the item is still active.
     */
    WorkItem.State _state;

    /**
     * Severity level of the item.
     */
    WorkItem.Level _level;

    /**
     * The database object class of the associated object.
     */
    String _targetClass;

    /**
     * The unique database id of an associated object.
     */
    String _targetId;

    /**
     * The optional display name of an associated object.
     */
    String _targetName;

    /**
     * The id of the IdentityRequest that caused this workitem.
     * This property can be null for workitems that are not
     * part of an IdentityRequest. 
     */
    String _identityRequestId;

    /**
     * Original WorkItem ID, so the workitem 
     * archives can be found from objects that store just the original workitem's 
     * ID like IdentityRequest's approvalSummaries.
     */
    String _workItemId;

    /**
     * WorkItem attributes.
     */
    Attributes<String, Object> _attributes;

    /**
     * System attributes. Most of these are top-level properties
     * in WorkItem but they are stored in XML here since it is not necessary
     * to use them in queries.
     */
    Attributes<String, Object> _systemAttributes;

    /**
     * Date the object was created in the persistent store.
     */
    Date _archived;

    /**
     * Signed flag
     */
    boolean _signed;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public WorkItemArchive() {
    }

    public WorkItemArchive(WorkItem src) {
        clone(src);
    }

    /**
     * These can have names but they are optional and non-unique.
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("name", "Work Item ID");
        cols.put("description", "Name");
        cols.put("ownerName", "Owner");
        return cols;
    }

    public static String getDisplayFormat() {
        return "%-34s %-20s %s\n";
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setOwnerName(String name) {
        _ownerName = name;
    }

    public String getOwnerName() {
        return _ownerName;
    }

    @XMLProperty
    public String getRequester() {
        return _requester;
    }

    public void setRequester(String name) {
        _requester = name;
    }

    @XMLProperty
    public String getAssignee() {
        return _assignee;
    }

    public void setAssignee(String name) {
        _assignee = name;
    }

    public String getCompleter() {
        return _completer;
    }

    @XMLProperty
    public void setCompleter(String _completer) {
        this._completer = _completer;
    }

    @XMLProperty
    public void setType(WorkItem.Type type) {
        _type = type;
    }

    public WorkItem.Type getType() {
        return _type;
    }

    @XMLProperty
    public void setHandler(String s) {
        _handler = s;
    }

    public String getHandler() {
        return _handler;
    }

    @XMLProperty
    public void setRenderer(String s) {
        _renderer = s;
    }

    public String getRenderer() {
        return _renderer;
    }

    @XMLProperty
    public void setState(WorkItem.State state) {
        _state = state;
    }

    public WorkItem.State getState() {
        return _state;
    }

    @XMLProperty
    public void setLevel(WorkItem.Level type) {
        _level = type;
    }

    public WorkItem.Level getLevel() {
        return _level;
    }

    @XMLProperty
    public void setTargetClass(String name) {
        _targetClass = name;
    }

    public String getTargetClass() {
        return _targetClass;
    }

    @XMLProperty
    public void setTargetId(String id) {
        _targetId = id;
    }

    public String getTargetId() {
        return _targetId;
    }

    @XMLProperty
    public void setTargetName(String name) {
        _targetName = name;
    }

    public String getTargetName() {
        return _targetName;
    }

    @XMLProperty
    public String getIdentityRequestId() {
        return _identityRequestId;
    }
    
    public void setIdentityRequestId(String id) {
        _identityRequestId = id;
    }

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }
    
    @XMLProperty(mode = SerializationMode.INLINE)
    public Attributes<String, Object> getSystemAttributes() {
        return _systemAttributes;
    }

    public void setSystemAttributes(Attributes<String, Object> a) {
        _systemAttributes = a;
    }
    
    /**
     * Return the date the object was created.
     */
    @XMLProperty
    public Date getArchived() {
        return _archived;
    }

    public void setArchived(Date created) {
        _archived = created;
    }

    /**
     * Return whether or not the object is esigned
     */
    @XMLProperty
    public boolean isSigned() {
      return _signed;
    }

    public void setSigned(boolean signed) {
      _signed = signed;
    }

    //////////////////////////////////////////////////////////////////////
    ///
    // Pseudo properties
    //
    //////////////////////////////////////////////////////////////////////

    private void setPseudo(String name, Object value) {
        if (name != null) {
            if (value == null) {
                if (_systemAttributes != null)
                    _systemAttributes.remove(name);
            }
            else {
                if (_systemAttributes == null)
                    _systemAttributes = new Attributes<String,Object>();
                _systemAttributes.put(name, value);
            }
        }
    }

    private Object getPseudo(String name) {
        Object value = null;
        if (name != null && _systemAttributes != null)
            value = _systemAttributes.get(name);
        return value;
    }

    public List<WorkItem.OwnerHistory> getOwnerHistory() {
        return (List<WorkItem.OwnerHistory>)getPseudo(SYSATT_OWNER_HISTORY);
    }

    public void setOwnerHistory(List<WorkItem.OwnerHistory> history) {
        setPseudo(SYSATT_OWNER_HISTORY, history);
    }
    
    public void addOwnerHistory(WorkItem.OwnerHistory history) {
        if (history != null) {
            List<WorkItem.OwnerHistory> histlist = getOwnerHistory();
            if (histlist == null) {
                histlist = new ArrayList<WorkItem.OwnerHistory>();
                setOwnerHistory(histlist);
            }
            histlist.add(history);
        }
    }

    public List<Comment> getComments() {
        return (List<Comment>)getPseudo(SYSATT_COMMENTS);
    }

    public void setComments(List<Comment> comments) {
        // empty lists are common, filter them to reduce XML clutter
        if (comments != null && comments.size() == 0) comments = null;
        setPseudo(SYSATT_COMMENTS, comments);
    }

    public String getCompletionComments() {
        return (String)getPseudo(SYSATT_COMPLETION_COMMENTS);
    }

    public void setCompletionComments(String s) {
        setPseudo(SYSATT_COMPLETION_COMMENTS, s);
    }

    public String getCertification() {
        return (String)getPseudo(SYSATT_CERTIFICATION);
    }

    public void setCertification(String id) {
        setPseudo(SYSATT_CERTIFICATION, id);
    }

    public Certification getCertification(Resolver resolver) throws GeneralException {
        String certid = getCertification();
        return (null != certid)
            ? resolver.getObjectById(Certification.class, certid) : null;
    }

    public String getCertificationEntity() {
        return (String)getPseudo(SYSATT_CERTIFICATION_ENTITY);
    }

    public void setCertificationEntity(String id) {
        setPseudo(SYSATT_CERTIFICATION_ENTITY, id);
    }
    
    public CertificationEntity getCertificationEntity(Resolver resolver) 
        throws GeneralException {
        String entid = getCertificationEntity();
        return (null != entid)
            ? resolver.getObjectById(CertificationEntity.class, entid)
            : null;
    }

    // TODO: Need to get the Enumeration in and out of the map...

    public void setEntityType(CertificationEntity.Type entityType) {
    }

    public CertificationEntity.Type getEntityType() {
        return null;
    }

    public String getCertificationItem() {
        return (String)getPseudo(SYSATT_CERTIFICATION_ITEM);
    }

    public void setCertificationItem(String id) {
        setPseudo(SYSATT_CERTIFICATION_ITEM, id);
    }

    public CertificationItem getCertificationItem(Resolver resolver) 
        throws GeneralException {
        String itemid = getCertificationItem();
        return (null != itemid)
            ? resolver.getObjectById(CertificationItem.class, itemid)
            : null;
    }

    public List<RemediationItem> getRemediationItems() {
        return (List<RemediationItem>)getPseudo(SYSATT_REMEDIATION_ITEMS);
    }

    public void setRemediationItems(List<RemediationItem> items) {
        // empty lists are common, filter them to reduce XML clutter
        if (items != null && items.size() == 0) items = null;
        setPseudo(SYSATT_REMEDIATION_ITEMS, items);
    }

    @XMLProperty
    public void setWorkItemId(String workItemId) {
        _workItemId = workItemId; 
    }

    public String getWorkItemId() {
        return _workItemId;
    }

    /**
     * Override SailPointObject getSignoff in order to return WorkItemArchive signoffs
     * 
     * @ignore
     * NOTE: This will be rewritten after we change the WorkItemArchive to store 
     * signoffs in the spt_sign_off_history table instead of in the WorkItemArchive
     * attributes map
     */
    @Override
    public List<SignOffHistory> getSignOffs() {
        List<SignOffHistory> signatures = new ArrayList<SignOffHistory>();
        SignOffHistory soh = (SignOffHistory)getPseudo(SYSATT_E_SIGNATURE);
        if(soh != null) {
            signatures.add(soh);
        }
        return signatures;
    }

    /**
     * Add a signoff to the archive
     * @param esig Signature being added to the list of signoffs. WorkItems
     *             will typically only contain a single signoff, but 
     *             multiples can be added.
     * 
     * @ignore            
     * NOTE: This will be rewritten after we change the WorkItemArchive to store 
     * signoffs in the spt_sign_off_history table instead of in the WorkItemArchive
     * attributes map
     */
    @Override
    public void addSignOff(SignOffHistory esig) {
        setPseudo(SYSATT_E_SIGNATURE, esig);
    }
    
    public void addMultipleSignOff(List<SignOffHistory> sigs) {
        for(SignOffHistory sig: sigs) {
            addSignOff(sig);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public void clone(WorkItem src) {

        // SailPointObject

        // hmm, we need to know the creation date of the original WorkItem,
        // putting it here means we'll lose the creation date of the
        // archive which seems less important
        _created = src.getCreated();
        _assignedScope = src.getAssignedScope();
        _assignedScopePath = src.getAssignedScopePath();

        // WorkItem

        _name = src.getName();
        _description = src.getDescription();
        _type = src.getType();
        _handler = src.getHandler();
        _renderer = src.getRenderer();
        _state = src.getState();
        _level = src.getLevel();
        _targetClass = src.getTargetClass();
        _targetId = src.getTargetId();
        _targetName = src.getTargetName();
        _identityRequestId = src.getIdentityRequestId();
        _workItemId = src.getId();

        Identity owner = src.getOwner();
        _ownerName = (owner != null) ? owner.getName() : null;

        Identity requester = src.getRequester();
        _requester = (requester != null) ? requester.getName() : null;

        Identity assignee = src.getAssignee();
        _assignee = (assignee != null) ? assignee.getName() : null;
        
        // Generate the archived date
        _archived = new Date();

        // technically we should deep copy these but we only create
        // new archives in a small window and they are immediately 
        // persisted which effectively does the copy

        // Some of these are in the attributes map but they'll be moved
        // to another map with just system info
        setCompletionComments(src.getCompletionComments());
        setCompleter(src.getCompleter());
        setComments(src.getComments());
        setOwnerHistory(src.getOwnerHistory());
        setCertification(src.getCertification());
        setCertificationEntity(src.getCertificationEntity());
        setCertificationItem(src.getCertificationItem());
        setRemediationItems(src.getRemediationItems());
        addMultipleSignOff(src.getSignOffs());
        for(SignOffHistory soh : src.getSignOffs()) {
            if(soh.isElectronicSign()) {
                //If we have electronic Signature, lock down the workItem
                setImmutable(true);
                setSigned(true);
                break;
            }
        }
        // prune and scrub the non-system  attributes
        _attributes = scrubAttributes(src.getAttributes());

        // scrub the approvalSet in the _attributes, if it has one
        if (_attributes != null) {
            Object approvalSet = _attributes.get("approvalSet");
            if (approvalSet != null && approvalSet instanceof ApprovalSet) {
                ObjectUtil.scrubPasswords((ApprovalSet)approvalSet);
            }
        }

    }

    public WorkItemHandler getHandlerInstance() throws GeneralException {

        WorkItemHandler handler = null;
        if (_handler != null) {
            try {
                Class cls = Class.forName(_handler);
                handler = (WorkItemHandler)cls.newInstance();
            }
            catch (Throwable t) {
                // log this but don't let it prevent the commit
                if (log.isErrorEnabled())
                    log.error("Invalid work item handler class [" + handler + "]: " +
                              t.getMessage(), t);
            }
        }
        return handler;
    }

    public Object getAttribute(String name) {
        return ((_attributes != null) ? _attributes.get(name) : null);
    }

    public void setAttribute(String name, Object value) {
        if (name != null) {
            if (_attributes == null)
                _attributes = new Attributes<String, Object>();
            // sigh, I wish Attributes did this
            if (value == null)
                _attributes.remove(name);
            else
                _attributes.put(name, value);
        }
    }

    public Object get(String name) {
        return getAttribute(name);
    }

    public String getString(String name) {
        return (_attributes != null) ? _attributes.getString(name) : null;
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : false;
    }

    public Date getDate(String name) {
        return (_attributes != null) ? _attributes.getDate(name) : null;
    }

    public List getList(String name) {
        return (_attributes != null) ? _attributes.getList(name) : null;
    }

    /**
     * Return the Form used for rendering this item.
     * A copy of this is maintained so there is no need to go back to the
     * Approval object.
     */
    public Form getForm() {
        Form form = null;
        Object o = get(WorkItem.ATT_FORM);
        if (o instanceof Form)
            form = (Form)o;
        return form;
    }

    /**
     * Return the TaskResult for the associated workflow if any.
     */
    public TaskResult getTaskResult(Resolver r) 
        throws GeneralException {

        TaskResult result = null;
        String resid = getString(WorkItem.ATT_TASK_RESULT);
        if (resid != null)
            result = r.getObjectById(TaskResult.class, resid);

        return result;
    }
    
    /**
     * Make a copy of the WorkItem attributes map, removing some things
     * that are not needed.  This also removes passwords.
     */
    private Attributes<String, Object> scrubAttributes(Attributes<String, Object> attributes) {

        Attributes<String, Object> scrubbed = new Attributes<String, Object>(attributes);

        // these are either in the system attributes map or we don't need them
        scrubbed.remove(WorkItem.ATT_ARCHIVE);
        scrubbed.remove(WorkItem.ATT_SIGNATURE);
        scrubbed.remove(WorkItem.ATT_ELECTRONIC_SIGNATURE);
        
        // CompletedBy is stored on a top level attribute
        scrubbed.remove(WorkItem.ATT_COMPLETER);

        // this will look for map keys with one of the usual passwordy names
        ObjectUtil.scrubPasswords(scrubbed);

        // Next, hit any known special cases
        Object wiFormObject = scrubbed.get(WorkItem.ATT_FORM);
        // This form is probably live, clone it
        if (wiFormObject != null && wiFormObject instanceof Form) {
            Form wiForm = ((Form)wiFormObject);
            Form clone = null;
            try {
                clone = (Form) wiForm.deepCopy((XMLReferenceResolver)null);
                Iterator<Field> fieldIter = clone.iterateFields();
                while (fieldIter.hasNext()) {
                    Field field = fieldIter.next();
                    if (Field.TYPE_SECRET.equals(field.getType()) || 
                        ObjectUtil.isSecret(field.getName())) {
                        field.setValue(null);
                    }
                        
                }
            } catch (GeneralException e) {
                log.error("Could not archive Form", e);
            }
            scrubbed.put(WorkItem.ATT_FORM, clone);
        }

        


        return scrubbed;
    }
    
    //////////////////////////////////////
    // Need these for the default renderers
    //////////////////////////////////////
    @XMLProperty
    public void setWakeUpDate(Date d) {
        //stub
    }

    public Date getWakeUpDate() {
        return null;
    }

    @XMLProperty
    public void setExpiration(Date d) {
        //stub
    }

    public Date getExpiration() {
        return null;
    }
}
