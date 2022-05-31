/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An object that may be stored in a ProvisioningProject that defines
 * the information that must be gathered from the user in order to 
 * complete provisionoing for this assignment.
 *
 * Author: Jeff
 *
 * There are two things we may need to ask about.  The first is
 * AccountSelections if there are multiple possible target accounts for
 * the assignment.
 *
 * The second is a list of provisioning policy Questions for additional
 * account attributes necessary to perform this assignment.
 *
 * Questions are not being used yet, but we will need to start managing
 * them per-assignment rather than at the project level once we allow
 * more than one assignment of the same role.  Or allow several assignments
 * to create different accounts on the same  application.
 *
 * Potentially the same issue for random AttributeRequests that aren't
 * part of the role or an entitlement assignment.  May need to generalize
 * this into ProvisioningQuestions?
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object that can be stored in a ProvisioningProject that defines
 * the information that must be gathered from the user in order to
 * complete provisioning for this assignment.
 */
@XMLClass
public class ProvisioningTarget extends AbstractXmlObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The unique generated id of the assignment.
     */
    String _assignmentId;

    /**
     * The name of the role that is being assigned.  
     * null for entitlement assignments.
     */
    String _role;

    /**
     * The name of the application whose entitlement is being assigned.
     * null for role assignments.
     */
    String _application;

    /**
     * The name of the entitlement attribute being assigned.
     * null for role assignments.
     */
    String _attribute;

    /**
     * The name of the entitlement being assigned.  
     * null for role assignments.
     */
    String _value;

    /**
     * True if this targets was generated for the Retain operation
     * and no other operations. These are added for dependency checking
     * but are not actually provisioned.  
     * 
     * @ignore
     * If they resolve to an account
     * we don't need to keep them in the plan (reduces clutter and churn
     * in the unit tests).  If they don't resolve we may or may not want to 
     * prompt for them.  Needs further thought...
     */
    boolean _retain;

    /**
     * List of ambiguous target accounts.
     */
    List<AccountSelection> _accounts;

    /**
     * List of provisioning policy questions.
     * 
     * @ignore
     * jsl - this isn't enough, when we get around to this we'll have
     * to maintain a question history list like ProvisioningProject does
     * for previously answered questions.
     */
    List<Question> _questions;

    //////////////////////////////////////////////////////////////////////
    //
    // Accessors
    //
    //////////////////////////////////////////////////////////////////////

    public ProvisioningTarget() {
    }

    public ProvisioningTarget(String assignmentId, Bundle role) {
        _assignmentId = assignmentId;
        _role = role.getName();
    }

    public ProvisioningTarget(Bundle role) {
        _role = role.getName();
    }

    public ProvisioningTarget(Application app, String attribute, String value) {
        _application = app.getName();
        _attribute = attribute;
        _value = value;
    }

    /**
     * Copy constructor. Shallow copies the lists of account selections.
     */
    public ProvisioningTarget(ProvisioningTarget target) {
        if (target != null) {
            _assignmentId = target._assignmentId;
            _application = target._application;
            _attribute = target._attribute;
            _value = target._value;
            _role = target._role;
            _retain = target._retain;
            if (target._accounts != null) {
                _accounts = new ArrayList<AccountSelection>(target._accounts);
            }
            if (target._questions != null) {
                _questions = new ArrayList<Question>(target._questions);
            }
        }
    }

    @XMLProperty
    public void setAssignmentId(String s) {
        _assignmentId = s;
    }

    /**
     * The unique generated if of the assignment.
     */
    public String getAssignmentId() {
        return _assignmentId;
    }

    @XMLProperty
    public String getRole() {
        return _role;
    }

    public void setRole(String s) {
        _role = s;
    }

    @XMLProperty
    public String getApplication() {
        return _application;
    }

    public void setApplication(String s) {
        _application = s;
    }

    @XMLProperty
    public String getAttribute() {
        return _attribute;
    }

    public void setAttribute(String s) {
        _attribute = s;
    }

    @XMLProperty
    public String getValue() {
        return _value;
    }

    public void setValue(String s) {
        _value = s;
    }

    @XMLProperty
    public boolean isRetain() {
        return _retain;
    }

    public void setRetain(boolean b) {
        _retain = b;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<AccountSelection> getAccountSelections() {
        return _accounts;
    }
    
    public void setAccountSelections(List<AccountSelection> accounts) {
        _accounts = accounts;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<Question> getQuestions() {
        return _questions;
    }

    public void setQuestions(List<Question> questions) {
        _questions = questions;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public void addAccountSelection(AccountSelection sel) {
        if (sel != null) {
            if (_accounts == null)
                _accounts = new ArrayList<AccountSelection>();
            _accounts.add(sel);
        }
    }

    /**
     * Add an AccountSelection for a list of target links.
     */
    public void addAccountSelection(List<Link> links, String sourceRole) {
        if (links != null) {
            AccountSelection sel = new AccountSelection(links);
            sel.setRoleName(sourceRole);
            addAccountSelection(sel);
        }
    }

    public void addAccountSelection(Application app, Link link, String sourceRole) {
        AccountSelection sel = new AccountSelection(app, link);
        sel.setRoleName(sourceRole);
        addAccountSelection(sel);
    }

    /**
     * Add multiple AccountSelections
     * @param accountSelections List of AccountSelection
     */
    public void addAccountSelections(List<AccountSelection> accountSelections) {
        for (AccountSelection accountSelection: Util.safeIterable(accountSelections)) {
            addAccountSelection(accountSelection);
        }
    }

    /**
     * Lookup the account selection for a given application.
     */
    public AccountSelection getAccountSelection(Application app, 
                                                String sourceRole) {
        AccountSelection found = null;
        if (_accounts != null) {
            for (AccountSelection account : _accounts) {
                if (app != null && app.getName().equals(account.getApplicationName()) &&
                    ((sourceRole == null && account.getRoleName() == null) ||
                     (sourceRole != null && sourceRole.equals(account.getRoleName())) ||
                     (sourceRole != null && Util.nullSafeContains(account.getFollowers(), sourceRole)))) {
                    found = account;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Return true if all of the AccountSelections have been answered.
     * 
     * @ignore
     * Absence of AccountSelections is considered an answer, though I don't
     * think that can happen now.
     */
    public boolean isAnswered() {
        boolean answered = true;
        if (_accounts != null) {
            for (AccountSelection selection : _accounts) {
                if (!selection.isAnswered()) {
                    answered = false;
                    break;
                }
            }
        }
        return answered;
    }

}
