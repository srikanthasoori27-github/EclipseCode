/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import sailpoint.object.*;
import sailpoint.tools.GeneralException;

import java.util.Iterator;
import java.util.List;


/**
 * A CertificationContext provides an interface that can be used by the
 * Certificationer when rendering certifications to determine the certification
 * owners, which identities are to be certified, which information on each
 * identity is to be certified, etc...
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public interface CertificationContext {


    /**
     * Create a new certification object, initializing a number of certification properties.
     * @param requestor Identity who should be specified as certification creator
     * @return Initialized Certification object
     */
    public Certification initializeCertification(Identity requestor) throws GeneralException;

    /**
     * Return the owners for the certification that is being generated.
     * 
     * @return The owners for the certification that is being generated.
     */
    public List<Identity> getOwners() throws GeneralException;

    /**
     * Return an iterator over all of the identities that are to be added to
     * the certification that is being generated.
     * 
     * @return An iterator over all of the identities that are to be added to
     *         the certification that is being generated.
     */
    public Iterator<? extends AbstractCertifiableEntity> getPopulation()
        throws GeneralException;

    /**
     * Return whether the given entity is in the population for this
     * certification.  This should return true if the iterator returned by
     * getPopulation() will contain the given entity.  This should provide a
     * faster way to check for containment than iterating over the full
     * population.
     * 
     * @param  entity  The AbstractCertifiableEntity to look for.
     * 
     * @return True if the given entity is in the population for this
     *         certification, false otherwise.
     */
    public boolean inPopulation(AbstractCertifiableEntity entity)
        throws GeneralException;
    
    /**
     * Return a list of all Certifiable items on the given identity that should
     * be included in the certification being generated.
     * 
     * @param  entity  The entity for which the Certifiable items are being
     *                   retrieved.
     * 
     * @return A list of all Certifiable items on the given identity that should
     *         be included in the certification being generated.
     */
    public List<Certifiable> getCertifiables(AbstractCertifiableEntity entity)
        throws GeneralException;

    /**
     * Create a CertificationEntity from the given AbstractCertifiableEntity.
     * This can return null if the entity has nothing to certify.
     * 
     * @param  cert    The Certification for which to create the entity.
     * @param  entity  The certifiable entity from which to create the entity.
     * 
     * @return The CertificationEntity, or null if there is nothing to certify
     *         for the given entity.
     */
    CertificationEntity createCertificationEntity(Certification cert, AbstractCertifiableEntity entity)
        throws GeneralException;
    
    /**
     * Lets others know if this entity should be excluded from the cert
     * @param cert The Certification in which to check for exclusion
     * @param entity The Certifiable entity from which to check for exclusion
     * @return true or false
     * @throws GeneralException
     */
    boolean isExcluded(Certification cert, AbstractCertifiableEntity entity)
        throws GeneralException;

    /**
     * Return a list of CertificationContexts that will generate subordinate
     * certifications for the given identity being included in the report
     * (eg - in a manager certification with subordinate generation enabled,
     * this would return a context that would generate a certification with all
     * users that report to the given identity.)
     * 
     * @param  entity  The entity for which to return a subordinate context.
     * 
     * @return A list of CertificationContexts that will generate subordinate
     *         certifications for the given identity being included in the
     *         report.
     */
    public List<CertificationContext> getSubordinateContexts(AbstractCertifiableEntity entity)
        throws GeneralException;

    /**
     * Store the information in this context on the Certification.
     * 
     * @param  cert  The Certification in which to save the context info.
     */
    public void storeContext(Certification cert) throws GeneralException;


    public void setCertificationGroups(List<CertificationGroup> groups);

    /**
     * Returns the list of CertificationGroups which should be assigned to
     * the Certification created by this context.
     */
    public List<CertificationGroup> getCertificationGroups();

    /**
     * Generates certification short name.
     */
    public String generateShortName() throws GeneralException;
    
    /**
     * Generates certification nam.
     */
    public String generateName() throws GeneralException;
    
    /**
     * Note: This is exposed for differencing.
     *
     * @return True if the certifications will contain policy violations.
     */
    public boolean isIncludePolicyViolations();

    /**
     * Return the type of the certification being generated if there is one.
     *
     * @return The type of the certification being generated, or null if the
     *         certification being generated does not have a type.
     */
    public Certification.Type getType();

}
