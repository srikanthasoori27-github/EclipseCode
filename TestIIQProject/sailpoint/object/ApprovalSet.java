/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class used during the approval process. 
 * 
 * This is an object that can be used to examine the items
 * contained in a set. These typically live in the attributes
 * map of an Approval WorkItem. 
 * 
 */
@XMLClass
public class ApprovalSet extends AbstractXmlObject {

    /**
     * 
     */
    private static final long serialVersionUID = -61384782572617261L;

    /**
     * The line items contained in this set.
     */
    private List<ApprovalItem> _items;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ApprovalSet() {
        _items = new ArrayList<ApprovalItem>();
    }

    public ApprovalSet(List<ApprovalItem> items) {
        this();
        setItems(items);
    }

    public ApprovalSet(ApprovalSet set) {
        this();
        if (set != null) {
            for (ApprovalItem item : Util.safeIterable(set.getItems())) {
                add(item.clone());
            }
        }
    }

    public ApprovalSet clone() {
        return new ApprovalSet(this);
    }

    public void setItems(List<ApprovalItem> items) {
        if ( items != null ) _items.addAll(items);
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<ApprovalItem> getItems() {
        return _items;
    }

    public void add(ApprovalItem item) {
        if ( _items == null ) 
            _items = new ArrayList<ApprovalItem>();
        _items.add(item);
    }

    public void remove(ApprovalItem item) {
        if ( _items != null )  
            _items.remove(item);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Assimilation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * For cases when there are multiple approvers the Rejected state might need to be kept
     * for an item or the order of approvals can effect the outcome of the process.
     */
    public void assimilate(ApprovalSet set, String owner, String globalSignOffComments, boolean keepRejected) {
        if ( set != null ) {
            List<ApprovalItem> items = set.getItems();
            for ( ApprovalItem item : items ) {
                findAndMergeItem(item, owner, globalSignOffComments, keepRejected);
            }
        }
    }

    public ApprovalItem find(ApprovalItem item) {
        if ( item == null ) return null;

        List<ApprovalItem> localItems = getItems();
        if ( Util.size(localItems) > 0 ) {
            for (ApprovalItem localItem : localItems) {
                if ( ( !Util.nullSafeEq(item.getApplication(), localItem.getApplication(), true) ) ||
                     ( !Util.nullSafeEq(item.getNativeIdentity(), localItem.getNativeIdentity(), true) ) ||
                     ( !Util.nullSafeEq(item.getOperation(), localItem.getOperation(), true) ) ||
                     ( !Util.nullSafeEq(item.getName(), localItem.getName(), true) ) ||
                     ( !Util.nullSafeEq(item.getValueList(), localItem.getValueList(), true) ) ||
                     ( !Util.nullSafeEq(item.getInstance(), localItem.getInstance(), true) ) ||
                     ( !Util.nullSafeEq(item.getAssignmentId(), localItem.getAssignmentId(), true) ) ) {
                    continue;                             
                }
                return localItem;
            }
        }
        return null;
    }

    private List<Comment> mergeComments(List<Comment> old, List<Comment> neu) {
        
        List<Comment> merged = new ArrayList<Comment>();
        if ( Util.size(old) >  0 ) {
            merged.addAll(old);
        }
        if ( Util.size(neu) > 0 ) {
            for ( Comment comment : neu ) {
                if ( comment == null ) continue;
                String commentString = comment.toString();
                boolean found = false;
                if ( Util.size(old) > 0 ) {
                    for ( Comment existing : old ) {
                        if ( existing == null ) continue;
                        String existingString = existing.toString();
                        if ( existingString.compareTo(commentString) == 0 ) {
                            found = true;
                            break;
                        }
                    } 
                }
                if ( !found ) merged.add(comment);
            }
        }
        if ( Util.size(merged) > 0 ) {
            // order them by date
            Collections.sort(merged, Comment.SP_COMMENT_BY_DATE);
        }
        return ( Util.size(merged) > 0 ) ? merged : null;
    }

    public void findAndMergeItem(ApprovalItem item, String owner, String globalSignOffComments, boolean keepRejected) {
        ApprovalItem found = find(item);
        if ( found != null ) {
            if ( ( found.getState() != null ) &&
                 ( keepRejected ) && 
                 ( found.getState().equals(WorkItem.State.Rejected) ) ) {
                // do nothing with the state leave it rejected
            } else {
                found.setState(item.getState());
                if ( owner != null ) {
                    found.setOwner(owner);
                }
            }
            List<Comment> foundComments = found.getComments();
            List<Comment> comments = item.getComments();
            List<Comment> merged = mergeComments(foundComments,comments);
            if ( Util.getString(globalSignOffComments) != null ) {
                if ( merged == null ) {
                    merged = new ArrayList<Comment>();
                }
                merged.add(new Comment(globalSignOffComments, owner));
            }
            found.setComments(merged);

            // Update and changes to the start/end date
            found.setStartDate(item.getStartDate());
            found.setEndDate(item.getEndDate());
            found.setApprover(item.getApprover());

            found.setProvisioningState(item.getProvisioningState());
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    //////////////////////////////////////////////////////////////////////

    public static ApprovalSet castApprovalSet(Object o) {
        ApprovalSet aset = null;
        if (o instanceof ApprovalSet)
            aset = (ApprovalSet)o;
        return aset;
    }
    
    public boolean isEmpty() {
        return Util.size(_items) > 0 ? false : true;
    }
     
    public boolean isAllRejected() {
        List<ApprovalItem> items = getRejected();
        if ( Util.size(items) ==  Util.size(_items) ) {
            return true;
        }
        return false;
    }

    public List<ApprovalItem> getRejected() {
        List<ApprovalItem> items = new ArrayList<ApprovalItem>();
        for ( ApprovalItem item : _items ) {
            if ( item.isRejected() ) {
                items.add(item);
            }
        } 
        return items;
    }

    public boolean hasRejected() {
        if ( getRejected().size() > 0 ) {
            return true;
        }
        return false;
    }

    public boolean isAllApproved() {
        List<ApprovalItem> approved = getApproved();
        if ( Util.size(approved) ==  Util.size(_items) ) {
            return true;
        }
        return false;
    }

    public List<ApprovalItem> getApproved() {
        List<ApprovalItem> items = new ArrayList<ApprovalItem>();
        for ( ApprovalItem item : _items ) {
            if ( item.isApproved() ) {
                items.add(item);
            }
        } 
        return items;
    }

    public boolean hasApproved() {
        if ( getApproved().size() > 0 ) {
            return true;
        }
        return false;
    }

    /**
     * Mark all of the approved items in the set Pending.
     */
    public void initializeProvisioningState() {
        List<ApprovalItem> items = getItems();
        if ( Util.size(items) > 0 ) {
            for ( ApprovalItem item : items ) {
                if ( item.isApproved() ) {
                    item.setProvisioningState(ApprovalItem.ProvisioningState.Pending);
                } 
            }
        }
    }

    /**
     * Mark all of the approved items in the set Finished.
     */
    public void setAllProvisioned() {
        List<ApprovalItem> items = getItems();
        if ( Util.size(items) > 0 ) {
            for ( ApprovalItem item : items ) {
                if ( item.isApproved() ) {
                    item.setProvisioningState(ApprovalItem.ProvisioningState.Finished);
                } 
            }
        }
    }

    /**
     * Return true if all of the approved items have been provisioned.
     */
    public boolean isAllProvisioned() {
        List<ApprovalItem> items = getItems();
        if ( Util.size(items) > 0 ) {
            for ( ApprovalItem item : items ) {
                if ( ( item.isApproved() ) && ( !ApprovalItem.ProvisioningState.Finished.equals(item.getProvisioningState()))) 
                    return false;
            }
        }
        return true;
    }
}
