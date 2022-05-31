/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This abstract class includes all of the methods needed for classes that
 * undergo certification. This base class provides the methods that the
 * generic certification classes need, while properties specific to any
 * subclasses are only referenced in the CertificationContext implementation
 * specific to the subclass type.
 */
public abstract class AbstractCertifiableEntity 
    extends SailPointObject implements Cloneable {

    /**
     * Gets the full name of the entity. This is used to construct the names
     * and descriptions of certifications.
     *
     * @return The full name of the entity.
     * 
     * @ignore
     * todo Use getDisplayableName()? that seems to be more of a convention in IdentityIQ
     */
    public abstract String getFullName();

    /**
     * Gets the list of detected roles on the entity that reference any of the given
     * applications. If the application list is null or empty all roles are returned.
     *
     * @param apps Applications by which the role list is filtered (null or empty will return all roles).
     * @return list of detected roles for the given entity.
     */
    public List<Bundle> getBundles(List<Application> apps){
        return new ArrayList<Bundle>();
    }

    /**
     * @return List of all detected roles for this entity.
     */
    public List<Bundle> getBundles(){
        return new ArrayList<Bundle>();
    }

    /**
     * Gets the detected roles on the entity that reference the given
     * application.  If the application is null, all detected roles are returned.
     *
     * @param app Applications by which the role list is filtered (null or empty will return all roles).
     * @return list of matching roles
     */
    public List<Bundle> getBundles(Application app) {
        return new ArrayList<Bundle>();
    }
    
    /**
     * Get the role detections on the entity
     * 
     * @return List of all role detections
     */
    public List<RoleDetection> getRoleDetections() {
        return new ArrayList<RoleDetection>();
    }
    
    /**
     * Get the role detections on the entity that reference the given application. 
     * If the application is null, all role detections are returned
     * 
     * @param app Applications by which the role list is filtered (null or empty will return all roles).
     * @return List of matching DetectedRoles
     */
    public List<RoleDetection> getRoleDetections(Application app) {
        return new ArrayList<RoleDetection>();
    }
    
    /**
     * Gets the list of role detections on the entity that reference any of the given
     * applications. If the application list is null or empty all role detections are returned.
     * 
     * @param apps Applications by which the role list is filtered (null or empty will return all roles).
     * @return Collection of matching RoleDetections for the given entity
     */
    public Collection<RoleDetection> getRoleDetections(List<Application> apps) {
        return new ArrayList<RoleDetection>();
    }

    /**
     * Gets the assigned roles on the entity
     * 
     * @return Distinct List of assigned roles
     */
    public List<Bundle> getAssignedRoles(){
        return new ArrayList<Bundle>();
    }

    /**
     * Gets the role assignments on the entity
     * 
     * @return List of all role assignments for this entity
     */
    public List<RoleAssignment> getRoleAssignments() {
        return new ArrayList<RoleAssignment>();
    }

    /**
     * Get the exceptions on this entity that reference the given
     * Applications.  If the Applications list is null or empty, all exceptions
     * for this entity are returned.
     *
     * @param apps Applications by which the role list is filtered (null or empty will return all roles).
     * @return  List of EntitlementGroups assigned to this entity
     */
    public List<EntitlementGroup> getExceptions(List<Application> apps){
        return new ArrayList<EntitlementGroup>();
    }

    /**
     * Get the latest certification for the given type.
     *
     * @param type Certification type
     * @return Latest CertificationLink or null if not found.
     */
    public CertificationLink getLatestCertification(Certification.Type type){
        return null;
    }

    /**
     * Remove certification link
     *
     * @param link CertificationLink to be removed.
     */
    public void remove(CertificationLink link){
    }

    /**
     * Returns a UI friendly name for the entity type.
     *
     * Currently this is used when the Certificationer needs
     * to return a warning such as 'no users to certify'.
     *
     * @param plural Should the type name be plural?
     * @return Entity type name is pluralized if plural flag is true
     */
    public abstract String getTypeName(boolean plural);

    /**
     * Indicates that you can difference this entity. In some cases,
     * such as ManagedAttribute objects, you cannot. This allows the
     * certification to skip the differencing step.
     *
     * @see sailpoint.api.Certificationer#addEntity
     * @see sailpoint.api.Certificationer#renderDifferences
     *
     * @return true if this entity can be differenced
     */
    public abstract boolean isDifferencable();

    /**
     * Return if this entity is "inactive".  If true, the entity
     * will optionally be put in the inactive list on the certification and
     * not require certification.
     * 
     * @return if this entity is "inactive".  If the entity does
     *         not support the notion of "inactive", this returns false.
     */
    public boolean isInactive() {
        // Return false by default since most entities don't support this.
        return false;
    }
}
