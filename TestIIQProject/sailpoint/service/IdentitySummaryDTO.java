/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Lockinator;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.List;
import java.util.Map;

/**
 * An IdentitySummary holds enough information about an identity to display it in the UI.
 */
@XMLClass
public class IdentitySummaryDTO extends BaseDTO {

    private static final String ATTR_ID = "id";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_DISPLAY_NAME = "displayName";

    private static Log _log = LogFactory.getLog(IdentitySummaryDTO.class);
    /**
     * The name of the identity
     */
    private String name;

    /**
     * First name
     */
    private String firstName;

    /**
     * Last name
     */
    private String lastName;

    /**
     * The display name of the identity
     */
    private String displayName;

    /**
     * Whether the identity belongs to a workgroup or not
     */
    private boolean workgroup;

    /**
     * Whether or not identity is a pseudo identity. Default to false. Set to true if pseudo const is used.
     * Added so that we don't request identity details for a pseudo identity (identity that has not yet been created).
     */
    private boolean pseudo = false;

    /**
     * Whether the identity is currently locked or not
     */
    private boolean locked = false;

    public IdentitySummaryDTO() {
        super(null);
    }

    /**
     * Constructor.
     *
     * @param identity The Identity for which to create the summary.
     */
    public IdentitySummaryDTO(Identity identity) {
        super(identity.getId());
        this.name = identity.getName();
        this.firstName = identity.getFirstname();
        this.lastName = identity.getLastname();
        this.displayName = identity.getDisplayableName();
        this.workgroup = identity.isWorkgroup();
    }

    /**
     * Constructor.
     *
     * @param identityMap {Map}  A map containing properties for an identity.
     */
    public IdentitySummaryDTO(Map<String, Object> identityMap) {
        super((String) identityMap.get(ATTR_ID));
        this.name = (String) identityMap.get(ATTR_NAME);
        this.displayName = (String) identityMap.get(ATTR_DISPLAY_NAME);
        this.locked = false;
    }

    /**
     * Constructor
     *
     * @param identity  The Identity for which to create the summary.
     * @param locknator The lockinator to check whether identity is locked
     */
    public IdentitySummaryDTO(Identity identity, Lockinator locknator) {
        this(identity);
        try {
            //Check to see if any locks are active  
            this.locked = locknator.isUserLocked(identity);
        } catch (GeneralException e) {
            _log.error("The IdentityDTO could not determine if the identity is locked.", e);
        }
    }

    /**
     * Constructor.
     *
     * @param id ID of the identity
     * @param name Name of the identity
     * @param displayName Display name of the identity.
     */
    public IdentitySummaryDTO(String id, String name, String displayName) {
        super(id);
        this.name = name;
        this.displayName = displayName;
    }
    
    /**
     * Constructor for pseudo identity
     *
     * @param id          Id
     * @param name        Name
     * @param displayName Display name
     * @param workgroup   True if workgroup, otherwise false
     */
    public IdentitySummaryDTO(String id, String name, String displayName, boolean workgroup) {
        this(id, name, displayName);
        this.name = name;
        this.displayName = displayName;
        this.workgroup = workgroup;
        this.pseudo = true;
    }

    /**
     * Constructor for use with list services.
     *
     * @param object Map representation of object
     * @param cols   List of columns
     */
    public IdentitySummaryDTO(Map<String, Object> object, List<ColumnConfig> cols) {
        super(object, cols);
    }

    @XMLProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XMLProperty
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isWorkgroup() {
        return workgroup;
    }

    public void setWorkgroup(boolean workgroup) {
        this.workgroup = workgroup;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public boolean isPseudo() {
        return this.pseudo;
    }

    public void setPseudo(boolean pseudo) {
        this.pseudo = pseudo;
    }

    @XMLProperty
    public String getId() { return super.getId(); }
}
