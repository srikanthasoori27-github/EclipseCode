/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.api.IncrementalObjectIterator;

import java.util.*;

/**
 * Datasource for CertificationSignOffReport
 */
public class CertificationSignoffReportDataSource extends BaseCertificationDataSource{

    private static final Log log = LogFactory.getLog(CertificationSignoffReportDataSource.class);

    private List<String> applications;
    private List<String> managers;
    private List<String> groups;
    private List<String> signers;

    private static final int CACHE_MAX_SIZE = 10;

    private int cacheTracker;


    public CertificationSignoffReportDataSource(Locale locale, TimeZone timezone) {
        super(locale, timezone);
        setScope(Certification.class);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for certification entries");

        List<Filter> certTypeFilters = new ArrayList<Filter>();

        if (applications != null && !applications.isEmpty())
            certTypeFilters.add(Filter.in("applicationId", applications));

        if (managers != null && !managers.isEmpty())
            certTypeFilters.add(Filter.in("manager", managers));

        List<Reference> groupDefs = getGroupDefinitions(groups);
        if (!groupDefs.isEmpty())
            certTypeFilters.add(Filter.in("groupDefinition", groupDefs));
        
        qo = new QueryOptions(getUserSpecifiedFilters(""));
        if (!certTypeFilters.isEmpty())
            qo.add(Filter.or(certTypeFilters));

        if (signers != null && !signers.isEmpty())
            qo.add(Filter.in("signOffHistory.signerId", signers));

        qo.add(Filter.notnull("signed"));

        _objects = new IncrementalObjectIterator<Certification>(getContext(), Certification.class, qo);
    }

    @Override
    public boolean internalNext() throws JRException {

        if (_objects == null || !_objects.hasNext())
            return false;

        if (cacheTracker % CACHE_MAX_SIZE == 0){
           try {
               getContext().decache();
           } catch (GeneralException e) {
               log.error("Error clearing cache.", e);
               throw new JRException(e);
           }
        }

        _object = (SailPointObject)_objects.next();
        cacheTracker++;

        return true;
    }


    @Override
    public Object getFieldValue(JRField jrField) throws JRException {

        Certification cert = (Certification)_object;
        if (cert == null)
            return null;

        if ("owner".equals(jrField.getName())){
            try {
                return Message.info(MessageKeys.MSG_PLAIN_TEXT, getCertifiers()).getLocalizedMessage(getLocale(), getTimezone());
            } catch (GeneralException e) {
                log.error(e);
                throw new JRException(e);
            }
        } else if ("signers".equals(jrField.getName())){
            List<String> signers = new ArrayList<String>();
            List<SignOffHistory> history = cert.getSignOffHistory();
            if (history != null && !history.isEmpty()){
                for(SignOffHistory histItem : history){
                    Identity signer = null;
                    try {
                        signer = histItem.getSigner(getContext());
                    } catch (GeneralException e) {
                        log.error(e);
                    }
                    if (signer != null)
                        signers.add(signer.getDisplayableName());
                    else
                        signers.add(histItem.getSignerName());
                }
            }

            return Message.info(MessageKeys.MSG_PLAIN_TEXT, signers).getLocalizedMessage(getLocale(), getTimezone());
        } else if ("tags".equals(jrField.getName())){
            if (cert.getTags() != null){
                List<String> tags = new ArrayList<String>();
                for(Tag tag : cert.getTags()){
                    tags.add(tag.getName());
                }

                return Message.info(MessageKeys.MSG_PLAIN_TEXT, tags).getLocalizedMessage(getLocale(), getTimezone());
            } else {
                return "";
            }
        } else if ("signerFirstName".equals(jrField.getName())){
            Identity identity = getSigner();
            return identity != null ? identity.getFirstname() : "";
        } else if ("signerLastName".equals(jrField.getName())){
            Identity identity = getSigner();
            return identity != null ? identity.getLastname() : "";
        }

        return super.getFieldValue(jrField);
    }

    /**
     * Returns the first signer for the current certification.
     * @return
     */
    private Identity getSigner(){

        Certification cert = (Certification)_object;
        if (cert == null || cert.getSignOffHistory() == null)
            return null;

        List<SignOffHistory> history = cert.getSignOffHistory();
        if (history.size() > 0){
            try {
                return history.get(0).getSigner(getContext());
            } catch (GeneralException e) {
                log.error(e);
            }
        }

        return null;
    }

    /**
     * Returns list of displayable names for the certifiers of the current certification object.
     * @return
     * @throws GeneralException
     */
    private List<String> getCertifiers() throws GeneralException{

        Certification cert = (Certification)_object;

        List<String> certifiers = new ArrayList<String>();
        if (cert == null || cert.getCertifiers() == null)
            return certifiers;

        QueryOptions ops = new QueryOptions(Filter.in("name", cert.getCertifiers()));
        Iterator<Identity> identities = getContext().search(Identity.class, ops);
        while (identities.hasNext()) {
            Identity identity =  identities.next();
            certifiers.add(identity.getDisplayableName());
        }

        return certifiers;
    }

    /**
     * Converts group names in references so we can query certifications by groupDefinition
     * @param groupNames
     * @return
     * @throws GeneralException
     */
    private List<Reference> getGroupDefinitions(List<String> groupNames) throws GeneralException{
        List<Reference> definitionRefs = new ArrayList<Reference>();
        if (groupNames != null && !groupNames.isEmpty()) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.in("name", groupNames));

            Iterator<Object[]> defs = getContext().search(GroupDefinition.class,
                    qo, Arrays.asList("id", "name"));

            if (defs != null){
                while (defs.hasNext()) {
                    Object[] def =  defs.next();
                    String id = def[0] != null ? def[0].toString() : null;
                    String name =  def[1] != null ? def[1].toString() : null;
                    definitionRefs.add(new Reference(GroupDefinition.class.getName(), id, name));
                }
            }
        }
        return definitionRefs;
    }

    public List<String> getApplications() {
        return applications;
    }

    public void setApplications(List<String> applications) {
        this.applications = applications;
    }

    public List<String> getManagers() {
        return managers;
    }

    public void setManagers(List<String> managers) {
        this.managers = managers;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public List<String> getSigners() {
        return signers;
    }

    public void setSigners(List<String> signers) {
        this.signers = signers;
    }
}
