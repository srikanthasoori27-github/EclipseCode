/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A model for object signoffs.
 *
 * This was added for the JPMC requirement for "report signoff" but
 * it is more general.
 * 
 * Author: Jeff
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class Signoff extends AbstractXmlObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Signoff.class);

    /**
     * A list of objects containing information about individual signoffs.  
     */
    List<Signatory> _signatories;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Signoff() {
    }

    @XMLProperty
    public void setSignatories(List<Signatory> sigs) {
        _signatories = sigs;
    }
    
    public List<Signatory> getSignatories() {
        return _signatories;
    }

    public void add(Signatory sig) {
        if (sig != null) {
            if (_signatories == null)
                _signatories = new ArrayList<Signatory>();
            _signatories.add(sig);
        }   
    }

    /**
     * Locate a signatory by work item id.
     */
    public Signatory find(WorkItem item) {
        Signatory found = null;
        if (item != null && _signatories != null) {
            String id = item.getId();
            for (Signatory sig : _signatories) {
                if (id.equals(sig.getWorkItemId())) {
                    found = sig;
                    break;
                }
            }
        }
        return found;
    }        

    /**
     * Locate a signatory by name.
     * 
     * @ignore
     * We actually don't need this now that Signatorys
     * are tagged with the work item id.
     * 
     * We now need this for TaskResultAuthorizer.
     */
    public Signatory find(String name) {
        Signatory found = null;
        if (name != null && _signatories != null) {
            for (Signatory sig : _signatories) {
                if (name.equals(sig.getName())) {
                    found = sig;
                    break;
                }
            }
        }
        return found;
    }        

    /**
     * Add an empty signatory entry for the work item.
     * This is what you normally call to build the Signatory
     * list.
     *
     * Note that multiple work items assigned to the same
     * identity are allowed, which also means they have
     * to sign twice. This can be caused by work item
     * forwarding.
     */
    public Signatory add(WorkItem item) {

        Signatory sig = null;

        if (item != null) {
            if (item.getId() == null) {
                // caller must have saved this by now
                log.error("Can't create Signatory for WorkItem that hasn't been saved");
            }
            else {
                sig = find(item);
                if (sig != null) {
                    // can't have the same item twice
                    log.error("Can't create Signatory for duplicate WorkItem");
                }
                else {
                    Identity owner = item.getOwner();
                    if (owner == null) {
                        log.error("Can't create Signatory for WorkItem that has no owner");
                    }
                    else {
                        sig = new Signatory();
                        sig.setOwner(owner);
                        sig.setWorkItemId(item.getId());
                        add(sig);
                    }
                }
            }
        }

        return sig;
    }

    /**
     * Assimilate a completed signoff work item.
     */
    public Signatory finish(WorkItem item) {

        Signatory sig = null;

        if (item != null) {
            sig = find(item);
            if (sig == null) {
                // hmm, it must have gotten lost?
                // could either ignore or bootstrap a new one
                log.warn("No Signatory matching WorkItem");
                sig = add(item);
            }

            if (sig != null) {
                sig.setDate(new Date());
                sig.setComments(item.getCompletionComments());
                sig.setApproved(item.getState() == WorkItem.State.Finished);
                sig.setWorkItemId(null);
            }
        }

        return sig;
    }

    /**
     * Return the number of signoffs still pending.
     */
    public int getPendingSignoffs() {
        int count = 0;
        if (_signatories != null) {
            for (Signatory sig : _signatories) {
                if (sig.isPending())
                    count++;
            }
        }
        return count;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Signatory
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Inner class to represent the signoff of a particular user.
     */
    @XMLClass
    public static class Signatory {

        /**
         * Name of the entity that performed the signoff.
         * This is ordinarily the name of an Identity that was
         * also the owner of a WorkItem.
         */
        String _name;


        /**
         * Alternate display name of the identity, typically
         * this is the full name.
         */
        String _displayName;

        /**
         * The date the signoff work item was marked finished.
         */
        Date _date;

        /**
         * Signoff comments, ordinarily copied from the WorkItem
         * completion comments.
         */
        String _comments;

        /**
         * True if the signoff was "approved".
         * If this is false, and a signoff date is set,
         * then this is considered to have been "rejected".
         */
        boolean _approved;

        /**
         * The id of the WorkItem associated with this signature.
         * This will be null once the signoff date is set which
         * normally means the work item has been deleted.
         * 
         * @ignore
         * This is used to correlate work items with Signatorys
         * since it is possible with work item forwarding to have
         * multiple signoff work items assigned to the same identity.
         * We could try to be smarter and collapse signoff work items if
         * an identity has more than one, but it could also be argued that
         * receiving identity may want to forward the redundant work item
         * to someone else for consideration.
         */
        String _workItemId;

        public Signatory() {
        }
        
        public Signatory(String name) {
            _name = name;
        }

        @XMLProperty
        public void setName(String s) {
            _name = s;
        }

        public String getName() {
            return _name;
        }

        @XMLProperty
        public void setDisplayName(String s) {
            _displayName = s;
        }

        public String getDisplayName() {
            return _displayName;
        }

        public void setOwner(Identity owner) {
            if (owner != null) {
                _name = owner.getName();
                String dname = owner.getDisplayableName();
                // only set this if different so we can hide the row
                if (dname != null && !dname.equals(_name))
                    _displayName = dname;
            }
        }

        @XMLProperty
        public void setDate(Date d) {
            _date = d;
        }

        public Date getDate() {
            return _date;
        }

        @XMLProperty
        public void setApproved(boolean b) {
            _approved = b;
        }

        public boolean isApproved() {
            return _approved;
        }

        @XMLProperty(mode=SerializationMode.ELEMENT)
        public void setComments(String s) {
            _comments = s;
        }

        public String getComments() {
            return _comments;
        }

        @XMLProperty
        public void setWorkItemId(String s) {
            _workItemId = s;
        }

        public String getWorkItemId() {
            return _workItemId;
        }

        /**
         * Pseudo-property for JSF. 
         */
        public boolean isPending() {
            return (_workItemId != null);
        }

        /**
         * Pseudo-property for JSF. 
         */
        public boolean isRejected() {
            return (_workItemId == null && !_approved);
        }

    }
}
    

