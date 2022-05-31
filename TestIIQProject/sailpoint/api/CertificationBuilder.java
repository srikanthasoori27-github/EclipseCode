/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationPhaseConfig;
import sailpoint.object.Identity;
import sailpoint.object.CertificationGroup;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.api.certification.CertificationNamer;


/**
 * A CertificationBuilder is used to create a list of CertificationContexts to
 * feed to the Certificationer when rendering different types of certifications.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public interface CertificationBuilder {

    /**
     * Performs pre-generation initialization. This may commit.
     */
    public void init() throws GeneralException;

    /**
     * Finishes of certification generation process, updating the CertificationGroups
     * and updating their statistics.
     *
     * @param success  True if the certification generation process completed without
     *                 any errors.
     * @param messages List of messages (usually errors or warnings) to attach to the CertificationGroup
     * @throws GeneralException
     */
    public void finalize(boolean success, List<Message> messages) throws GeneralException;

    /**
     * Return a list of CertificationContexts to use to render certifications.
     *
     * @return Alist of CertificationContexts to use to render certifications.
     */
    public Iterator<CertificationContext> getContexts() throws GeneralException;

    /**
     * Convenience method to return a single CertificationContext from the
     * builder.  If the builder produces more or less than one context this
     * will throw a GeneralException.
     *
     * @return The single CertificationContext from this builder.
     */
    public CertificationContext getContext() throws GeneralException;

    /**
     * Return a reconstructed CertificationContext that was used to generate the
     * given certification.  This should use the information stored by
     * CertificationContext.storeContext(Certification) to get the appropriate
     * information.
     *
     * @param cert The Certification for which to retrieve the context.
     * @return A reconstructed CertificationContext that was used to generate
     *         the given certification
     */
    public CertificationContext getContext(Certification cert)
            throws GeneralException;

    public CertificationNamer getCertificationNamer();

    /**
     * Return a map with results from iterating over the contexts.
     *
     * @return A map with results from iterating over the contexts.
     */
    public Map<String, Object> getResults();


    /**
     * @return Non-null list of warning messages.
     */
    public List<Message> getWarnings();

    /**
     * Add a warning to this certification context.
     *
     * @param msg warning msg to add. Null messages are ignored.
     */
    public void addWarning(Message msg);

    /**
     * Used to create UI messages which describe the type of entity
     * being certified.
     *
     * @param plural Whether or not the entity name should be plural
     * @return The name of the entity type being certified.
     */
    public Message getEntityName(boolean plural);

    /**
     * Used to add attributes that are eventually used
     * in Task results
     *
     * @key attribute key
     * @value attribute value
     */
    public void addResult(String key, String value);

    public void setCertificationGroups(List<CertificationGroup> groups);

    public List<CertificationGroup> getCertificationGroups();

    /**
     * Set a bag of attributes on this builder.  Historically, we've been
     * storing these all individually on the builder but it has become
     * cumbersome to add new properties.  Instead, this map should contain any
     * attribute that needs to be stored on the certification but does not need
     * to be searchable.
     *
     * @param attributes The attributes to set on the builder.
     */
    public void setAttributes(Attributes<String, Object> attributes);

    /**
     * Set the list of the Identities that will be the certifiers for the
     * generated certifications.  If non-null, this overrides the default
     * certifier for the certification type.  If null, the CertificationContext
     * subclass will determine the appropriate certifier(s).
     *
     * @param owners The list of the Identities that will be the certifiers
     *               for the generated certifications.
     */
    public void setOwners(List<Identity> owners);


    public void setPhaseConfig(List<CertificationPhaseConfig> phaseConfig);

    /**
     * Set the ID of the TaskSchedule that is generating the certs.
     *
     * @param  id  The ID of the TaskSchedule that is generating the certs.
     */
    public void setTaskScheduleId(String id);
}
