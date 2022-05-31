/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Part of the Identity model.  Used to represent references
 * to past certifications made for this identity.
 *
 * This is currently an XmlObject so we're not messing with
 * a Hibernate mapping.  I think this is ok since we don't ever
 * start with an Certification id and try to find the Identities in it, 
 * we always start with an Identity and try to find the certifications
 * it was involved with.  Since these aren't stored as true references,
 * and we have the archive problem we can't use these meaningfully
 * in joins anyway.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;



/**
 * Part of the Identity model. Used to represent references
 * to past certifications made for this identity.
 */
@XMLClass
public class CertificationLink extends AbstractXmlObject
    implements Cloneable
{
    private static final long serialVersionUID = -8115971164749742395L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The generated unique id of the Certification.
     * There might no longer be an object with this id in the database,
     * in which case you must look for an CertificationArchive with
     * a _sourceId property of the same value.
     */
    String _id;

    /**
     * The type of the certification. Typically the certification list
     * will be filtered so that it only contains the most recent
     * certification of a given type.
     *
     * This could be obtained indirectly from the _certification, but keeping
     * it locally allows for filtering the list without having to fetch
     * an CertificationArchive. Same for _completed and _certifier.
     */
    Certification.Type _type;
    
    /**
     * ID of the application with which the Certification referenced by this
     * link is associated. This is only populated if the Certification is 
     * of type ApplicationOwner
     */
    String _applicationId;

    /**
     * The Date the Certification was signed.
     * This can be different than the date the entire Certification was
     * completed or signed off.
     *
     * The intent here is to get as close as possible to what the
     * user looked like when the certifier was looking at it. Since
     * the attribute is not stored in the archive, this is used to locate the
     * IdentitySnapshot that is within this range.
     */
    Date _completed;

    /**
     * For continuous certifications the date of last activity.
     */
    Date _modified;

    /**
     * The name of the Identities that performed the certification.
     * Unclear whether this should be the owner of the root Certification,
     * or the user to which this CertificationIdentity was delegated.
     */
    List<String> _certifiers;

    /**
     * The ID of the IdentitySnapshot that holds the state of the identity
     * at the time that the certification was created. This might be null
     * for non-identity certifications or for CertificationLinks that were
     * created before this property was added (in 6.0).
     */
    String _identitySnapshotId;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public CertificationLink() {
    }

    public CertificationLink(Certification att, CertificationEntity id) {

        _id = att.getId();
        _type = att.getType();  
        _applicationId = att.getApplicationId();

        if (id != null) {
            _completed = id.getCompleted();
            _modified = id.getModified();
            _identitySnapshotId = id.getSnapshotId();
        }

        // normally should get these from the identit, but if not
        // fall back to the root certification
        if (_completed == null)
            _completed = att.getSigned();

        if (_certifiers == null) {
            _certifiers = new ArrayList<String>(att.getCertifiers());
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The generated unique id of the Certification.
     * There might no longer be an object with this id in the database,
     * in which case you must look for an CertificationArchive with
     * a sourceId property of the same value.
     */
    @XMLProperty
    public String getId() {
        return _id;
    }
    
    public void setId(String s) {
        _id = s;
    }

    /**
     * The type of the certification. Typically the certification list
     * will be filtered so that it only contains the most recent
     * certification of a given type.
     *
     * We could get this indirectly from <code>certification</code>, but keeping
     * it locally allows for filtering of the list without having to fetch
     * an CertificationArchive. Same for <code>completed</code> and <code>certifier</code>.
     */
    @XMLProperty
    public Certification.Type getType() {
        return _type;
    }

    public void setType(Certification.Type t) {
        _type = t;
    }

    @XMLProperty
    public void setCompleted(Date d) {
        _completed = d;
    }

    /**
     * The Date the Certification was signed.
     * This can be different than the date the entire Certification was
     * completed or signed off.
     */
    public Date getCompleted() {
        return _completed;
    }

    @XMLProperty
    public void setModified(Date d) {
        _modified = d;
    }

    /**
     * For continuous certifications the date of last activity.
     */
    public Date getModified() {
        return _modified;
    }

    /**
     * The name of the Identities that performed the certification.
     */
    @XMLProperty
    public List<String> getCertifiers() {
        return _certifiers;
    }

    public void setCertifiers(List<String> s) {
        _certifiers = s;
    }
    
    /**
     * ID of the application with which the Certification referenced by this
     * link is associated. This is only populated if the Certification is 
     * of type ApplicationOwner
     */
    @XMLProperty 
    public String getApplicationId() {
        return _applicationId;
    }
    
    public void setApplicationId(String applicationId) {
        _applicationId = applicationId;
    }

    /**
     * Return the ID of the IdentitySnapshot that holds the state of the identity
     * at the time that the certification was created. This might be null for
     * non-identity certifications or for CertificationLinks that were created
     * before this property was added (in 6.0).
     */
    @XMLProperty
    public String getIdentitySnapshotId() {
        return _identitySnapshotId;
    }

    public void setIdentitySnapshotId(String id) {
        _identitySnapshotId = id;
    }

    public IdentitySnapshot getIdentitySnapshot(Resolver resolver)
        throws GeneralException {
        return (null != _identitySnapshotId)
            ? resolver.getObjectById(IdentitySnapshot.class, _identitySnapshotId)
            : null;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Object overrides
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public boolean equals(Object o) {

        if (o == this) {
            return true;
        }

        if (!(o instanceof CertificationLink)) {
            return false;
        }

        CertificationLink l = (CertificationLink) o;

        boolean typesEq = Util.nullSafeEq(_type, l.getType());
        boolean appsEq = Util.nullSafeEq(_applicationId, l.getApplicationId());
        boolean certifiersEq = Util.nullSafeEq(_certifiers, l.getCertifiers());

        return typesEq && (appsEq || certifiersEq);
    }

    @Override
    public int hashCode() {
        return ((null != _type) ? _type.hashCode() : -1)
             ^ ((null != _applicationId) ? _applicationId.hashCode() : -1)
             ^ ((null != _certifiers) ? _certifiers.hashCode() : -1);
    }
}
