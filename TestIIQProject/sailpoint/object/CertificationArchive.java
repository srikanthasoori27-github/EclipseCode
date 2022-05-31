/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The representation for one archived certification process.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Indexes;
import sailpoint.tools.Index;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.XMLReferenceResolver;

/**
 * The representation for one archived certification process.
 */
@Indexes({@Index(property="created")})
@XMLClass
public class CertificationArchive extends SailPointObject implements Cloneable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * The generated unique id of the original Certification.
     * This is preserved so that there is a soft reference to
     * an Certification (as does the CertificationLink hanging
     * off the Identity) but still allow these to be archived
     * and deleted without violating a Hibernate integrity constraint,
     * and providing a way to locate the archive that replaced it.
     */
    String _certificationId;

    /**
     * The IDs of the archived child certifications of the top-level
     * certification in this archive. Since child certifications are
     * archived within their parents, this list is flattened and stored
     * when the archive is created so it can be searched for the archive that
     * holds a child certification.
     */
    List<String> _childCertificationIds;

    /**
     * ID of the primary(CertificationGroup.Type==Certification) CertificationGroup
     * that contained this Certification. Certifications created prior
     * to 5.1 will not have a CertificationGroup.
     */
    String _certificationGroupId;

    /**
     * The name of the user that created this certification.
     * Note that this is not necessarily the same as the owner, it
     * will typically be a manager of the owner.
     */
    String _creatorName;

    /**
     * The name of the user that currently owns this certification.
     * This will be set by the creator when the certification process
     * is started, the owner can be changed through delegation.
     * NOTE: Currently delegation history other
     * than that recorded through the audit log is not recorded, may need something.
     */
    String _ownerName;

    /**
     * The date the certification process was completed.
     * The Certificationer and UI should not allow an certification to
     * be marked complete until all of the CertificationIdentities
     * have been finished.
     */
    Date _signed;

    /**
     * The date a certification expires, or is due. The UI requires an expiration
     * date, so this value should be non-null. However, it is possible to create a
     * certification with a null expiration date from the console, so the value
     * should be checked.
     */
    Date _expiration;

    /**
     * Optional comments on the certification process.
     */
    String _comments;

    /**
     * XML blob containing serialized Certification.
     */
    String _archive;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public CertificationArchive() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setCertificationId(String s) {
        _certificationId = s;
    }

    /**
     * The generated unique id of the original Certification.
     * This is preserved so that ther is a soft reference to
     * a Certification (and to the CertificationLink hanging
     * off the Identity) but still allow these to be archived
     * and deleted without violating a Hibernate integrity constraint,
     * and providing a way to locate the archive that replaced it.
     */
    public String getCertificationId() {
        return _certificationId;
    }

    @XMLProperty
    public void setChildCertificationIds(List<String> ids) {
        _childCertificationIds = ids;
    }

    @XMLProperty
    public String getCertificationGroupId() {
        return _certificationGroupId;
    }

    public void setCertificationGroupId(String certificationGroupId) {
        _certificationGroupId = certificationGroupId;
    }

    /**
     * The IDs of the archived child certifications of the top-level
     * certification in this archive. Since child certifications are
     * archived within their parents, this list is flattened and stored
     * when the archive is created so the archive that
     * holds a child certification can be searched for.
     */
    public List<String> getChildCertificationIds() {
        return _childCertificationIds;
    }

    @XMLProperty(xmlname="creator")
    public void setCreatorName(String s) {
        _creatorName = s;
    }

    /**
     * The name of the user that created this certification.
     * Note that this is not necessarily the same as the owner, it
     * will typically be a manager of the owner.
     */
    public String getCreatorName() {
        return _creatorName;
    }

    @XMLProperty(xmlname="owner")
    public void setOwnerName(String s) {
        _ownerName = s;
    }

    /**
     * The name of the user that currently owns this certification.
     * This will be set by the creator when the certification process
     * is started, the owner can be changed through delegation.
     */
    public String getOwnerName() {
        return _ownerName;
    }

    @XMLProperty
    public void setSigned(Date d) {
        _signed = d;
    }

    /**
     * The date the certification process was completed.
     */
    public Date getSigned() {
        return _signed;
    }

    /**
    * @return Expiration date (due date) for this certification.
    */
    public Date getExpiration() {
        return _expiration;
    }

    /**
     * @param expiration Expiration date (due date) for this certification.
     */
    @XMLProperty
    public void setExpiration(Date expiration) {
        _expiration = expiration;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setComments(String s) {
        _comments = s;
    }

    /**
     * Optional comments on the certification process.
     */
    public String getComments() {
        return _comments;
    }

    // TODO: This is going to appear as ugly escaped text
    // in XML, may want to decompress it for XML representation?

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setArchive(String s) {
        _archive = s;
    }

    /**
     * XML blob containing serialized Certification.
     */
    public String getArchive() {
        return _archive;
    }

    /**
     * Set the Certification to archive. This should only be called when
     * initially constructing the archive. Subsequently, if an archived
     * Certification needs to be saved into the archive use
     * rezipCertification(XMLReferenceResolver, Certification).
     * 
     * @param  cert  The Certification to archive.
     */
    public void setArchive(Certification cert) throws GeneralException {

        if (cert == null) {
            _archive = null;
        }
        else {
            cert.inlineChildCertifications();

            _archive = cert.toXml();
            _certificationId = cert.getId();
            _childCertificationIds = getChildCertificationIds(cert.getCertifications());
            _creatorName = cert.getCreator();
            _ownerName = (null != cert.getOwner()) ? cert.getOwner().getName() : null;
            _signed = cert.getSigned();
            _expiration = cert.getExpiration();
            _comments = cert.getComments();
            for(SignOffHistory soh : cert.getSignOffs()) {
                if(soh.isElectronicSign()) {
                    //We have found an electronic signature
                    //Set the flag on certification archive so we do not purge
                    setImmutable(true);
                    break;
                }
            }
        }
    }

    /**
     * Get a Set containing all ID's of the given certifications and their
     * child certifications.
     */
    private static List<String> getChildCertificationIds(List<Certification> certs) {

        List<String> ids = new ArrayList<String>();
        if (null != certs) {
            for (Certification cert : certs) {
                ids.add(cert.getId());
                ids.addAll(getChildCertificationIds(cert.getCertifications()));
            }
        }
        return ids;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Accessors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Decompress this archive and retrieve the Certification with the given ID.
     * If the given ID is null, this returns the top-level certification from
     * this archive.
     * 
     * @param  r       The resolver to use.
     * @param  certId  The ID of the certification to retrieve from the archive.
     */
    public Certification decompress(XMLReferenceResolver r, String certId) 
        throws GeneralException {
        
        Certification cert = null;
        if (_archive != null) {
            XMLObjectFactory factory = XMLObjectFactory.getInstance();
            Object obj = factory.parseXml(r, _archive, false);

            if (obj instanceof Certification) {

                // If there is no ID given, return the top-level certification.
                // Otherwise, dig the appropriate certification out of the
                // certification hierarchy.
                // might want to cache this?
                if (null == certId) {
                    cert = (Certification) obj;
                }
                else {
                    cert = ((Certification) obj).getCertification(certId);
                }
            }
            else 
                throw new GeneralException("Wrong object class in certification archive!");
        }
        
        return cert;
    }

    /**
     * Store the given Certification back in this archive. Use this (rather
     * than setArchive(Certification)) to update a Certification in an archive
     * so that child certifications are stored correctly.
     * 
     * @param  r     The resolver to use.
     * @param  cert  The Certification to save back into this archive.
     */
    public void rezipCertification(XMLReferenceResolver r, Certification cert)
        throws GeneralException {

        if (null != cert) {

            // If this is the top-level certification in the archive, just store
            // the whole thing.
            if ((null != _certificationId) && _certificationId.equals(cert.getId())) {
                _archive = cert.toXml();
            }
            else if (null != _archive) {
                // This is a child certification.  Set it in the appropriate
                // place in the archive.
                Certification topLevelCert = decompress(r, null);
                Certification matchingCert =
                    topLevelCert.getCertification(cert.getId());

                if (null == matchingCert) {
                    throw new GeneralException("Certification not found in archive: " + cert);
                }

                Certification parent = matchingCert.getParent();
                if (null == parent) {
                    throw new GeneralException("Expected a parent certification for " + matchingCert);
                }

                // Replace the certification in the children list.
                List<Certification> childCerts = parent.getCertifications();
                int idx = findCertification(childCerts, matchingCert);
                childCerts.set(idx, cert);

                // Now, store the top-level certification as the archive again.
                _archive = topLevelCert.toXml();
            }
            else {
                throw new GeneralException("Existing archive required to rezip.");
            }
        }
    }

    /**
     * Find the index of the given Certification in the list of Certifications.
     */
    private int findCertification(List<Certification> certs, Certification cert)
        throws GeneralException {

        for (int i=0; i<certs.size(); i++) {
            if (cert.equals(certs.get(i))) {
                return i;
            }
        }

        throw new GeneralException("Could not find certification in list: " + cert);
    }

}
