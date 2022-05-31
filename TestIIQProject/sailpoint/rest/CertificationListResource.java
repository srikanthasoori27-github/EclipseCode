/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationService;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Certification;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationPhaseConfig;
import sailpoint.object.CertificationStateConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.Tag;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
@Path("certifications")
public class CertificationListResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(CertificationListResource.class);


    private List<String> certifiers;
    private String itemCompMin;
    private String itemCompMax;
    private String name;
    private String dueMin;
    private String dueMax;
    private String eSigned;
    private List<String> tags;
    private String certificationGroupId;

    @GET
    public ListResult list(){
        return new ListResult(Collections.EMPTY_LIST, 0) ;
    }

    @Override
    protected void processForm(MultivaluedMap<String, String> form) {
        super.processForm(form);

        certificationGroupId = getSingleFormValue(form, "certGroupId");
        name = getSingleFormValue(form, "name");
        itemCompMin = getSingleFormValue(form, "itemCompMin");
        itemCompMax = getSingleFormValue(form, "itemCompMax");
        dueMin = getSingleFormValue(form, "dueMin");
        dueMax = getSingleFormValue(form, "dueMax");
        eSigned = getSingleFormValue(form, "esigned");
        if (form.containsKey("tags")){
            tags = form.get("tags");
            if (tags != null && !tags.isEmpty()){
                tags.remove("");
            }
        }
        if (form.containsKey("certifiers")){
            certifiers = form.get("certifiers");
         }
    }

    @Path("search")
    @POST
    public ListResult search(MultivaluedMap<String, String> form) throws GeneralException {
    	
    	authorize(new RightAuthorizer(SPRight.ViewGroupCertification, SPRight.FullAccessCertifications, SPRight.FullAccessCertificationSchedule));

        processForm(form);

        List<CertificationDTO> dtos = new ArrayList<CertificationListResource.CertificationDTO>();
        int count = 0;
        try {

            QueryOptions ops = getQueryOptions(colKey);
            if (certificationGroupId != null && !"".equals(certificationGroupId))
                ops.add(Filter.eq("certificationGroups.id", certificationGroupId));

            if (!Util.isNullOrEmpty(name))
                ops.add(Filter.ignoreCase(Filter.like("name", name, Filter.MatchMode.START)));

            Integer minCompletion = Util.atoi(itemCompMin);
            if (minCompletion != 0){
                ops.add(Filter.ge("statistics.itemPercentComplete", minCompletion));
            }

            Integer maxCompletion = Util.atoi(itemCompMax);
            if (maxCompletion != 0){
                ops.add(Filter.le("statistics.itemPercentComplete", maxCompletion));
            }

            Date minDueDate = parseDateRange(dueMin, true);
            if (minDueDate != null){
                ops.add(Filter.gt("expiration", minDueDate));
            }

            Date maxDueDate = parseDateRange(dueMax, false);
            if (maxDueDate != null){
                ops.add(Filter.lt("expiration", maxDueDate));
            }
            
            if(eSigned != null) {
                boolean isEsigned = Boolean.valueOf(eSigned);
                ops.add(Filter.eq("electronicallySigned", isEsigned));
            }

            if (certifiers != null && !"".equals(certifiers)){
                List<String> certifierNames = convertIdsToNames(certifiers);
                List<Filter> filters = new ArrayList<Filter>();
                for (String c : certifierNames){
                    filters.add(Filter.containsAll("certifiers", Arrays.asList(c)));
                }
                if (!filters.isEmpty())
                    ops.add(Filter.or(filters));
            }

            if (tags != null && !tags.isEmpty()){
                ops.add(Filter.containsAll("tags.id", tags));
            }

            // Optionally filter empty certs for pending groups
            CertificationGroup group = (certificationGroupId == null) ? null : getContext().getObjectById(CertificationGroup.class, certificationGroupId);
            CertificationService.filterEmptyCerts(getContext(), ops, group);
            
            count = getContext().countObjects(Certification.class, ops);

            Iterator<Certification> iter = getContext().search(Certification.class, ops);
            if (iter!=null){
                while(iter.hasNext()){
                    Certification cert = iter.next();
                    dtos.add(buildDTO(cert));
                }
            }

        } catch (GeneralException e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        }

        return new ListResult(dtos, count);
    }


    private CertificationDTO buildDTO(Certification cert) throws GeneralException {
        CertificationDTO dto = new CertificationDTO();
        dto.setId(cert.getId());
        dto.setDescription(cert.getName());
        dto.setStatistics(cert.getStatistics());

        Message status = new Message(MessageKeys.PERCENT_COMPLETE_WITH_COUNT, cert.getStatistics().getItemPercentComplete(),
                cert.getStatistics().getCompletedItems(),
                cert.getStatistics().getTotalItems());
        dto.setCompletionStatus(status.getLocalizedMessage(getLocale(), getUserTimeZone()));

        if (cert.getExpiration() != null)
            dto.setDue(Internationalizer.getLocalizedDate(cert.getExpiration(), getLocale(), getUserTimeZone()));
        Date startDate = cert.getActivated() != null ? cert.getActivated() : cert.getCreated();
        if (cert.getPhase() != null) {
            Certification.Phase phase = cert.getPhase();
            dto.setPhase(localize(phase.getMessageKey()));
            if (cert.getNextPhaseTransition() != null) {
                dto.setPhaseEnd(Internationalizer.getLocalizedDate(cert.getNextPhaseTransition(), getLocale(), getUserTimeZone()));
            } else if (cert.getPhaseConfig() != null) {
                List<CertificationPhaseConfig> phaseConfig = cert.getPhaseConfig();
                Date phaseEnd = CertificationStateConfig.calculateStateEndDate(phase, phaseConfig, startDate);
                dto.setPhaseEnd(Internationalizer.getLocalizedDate(phaseEnd, getLocale(), getUserTimeZone()));
            }

            // bug 23422 - Set the isStaged boolean on the DTO to indicate 
            // whether the certification is staged. 
            if (phase == Certification.Phase.Staged) {
                dto.setIsStaged(true);
            }
        }
        if (cert.getTags() != null) {
            List<String> certTags = new ArrayList<String>();
            for (Tag tag : cert.getTags()) {
                certTags.add(tag.getName());
            }
            dto.setTags(certTags);
        }
        SailPointContext context = getContext();
        if (cert.getCertifiers() != null) {
            QueryOptions certifierOps = new QueryOptions(Filter.in("name", cert.getCertifiers()));
            certifierOps.add(Filter.or(Filter.eq("workgroup", false), Filter.eq("workgroup", true)));
            Iterator<Object[]> identities = context.search(Identity.class, certifierOps,
                    Arrays.asList("name", "displayName"));
            if (identities != null) {
                List<String> certifierList = new ArrayList<String>();
                while (identities.hasNext()) {
                    Object[] row = identities.next();
                    String certifierName = (String) row[1];
                    if (certifierName == null)
                        certifierName = (String) row[0];
                    certifierList.add(certifierName);
                }
                dto.setCertifiers(certifierList);
            }
        }

        dto.setSigned(cert.getSigned());
        dto.setElectronicallySigned(cert.isElectronicallySigned());
        // set whether or not reassignment is allowed.
        dto.setLimitReassignments(cert.limitCertReassignment(context));

        return dto;
    }

    /**
     * Bug#14066 : customize identity query to show workgroups as well
     * 
     * @param ids
     * @return
     * @throws GeneralException
     */
    public List<String> convertIdsToNames(List<String> ids)
            throws GeneralException {
        List<String> names = new ArrayList<String>();
        List<Boolean> wgflag = Arrays.asList(new Boolean[]{true, false});
        		
        if (ids != null && !ids.isEmpty()){
        	QueryOptions ops = new QueryOptions(Filter.in("id", ids));
        	ops.add(Filter.in(Identity.ATT_WORKGROUP_FLAG, wgflag));
        	
            Iterator<Object[]> results = getContext().search(Identity.class, ops, Arrays.asList("name"));
            if (results != null){
                while (results.hasNext()) {
                    Object[] row =  results.next();
                    names.add((String)row[0]);
                }
            }
        }

        return names;
    }
    
    public class CertificationDTO {

        public String id;
        public String description;
        public List<String> certifiers;
        public String phase;
        public String phaseEnd;
        public String due;
        public List<String> tags;
        public Certification.CertificationStatistics statistics;
        public String completionStatus;
        public Date signed;
        public boolean electronicallySigned;
        public boolean limitReassignments;
        
        // bug 23422 - The isStaged boolean indicates whether this 
        // certification is staged. The phase gets returned as
        // a localized string ready for display in the UI which makes
        // it difficult to determine exactly what phase it's really in.
        public boolean isStaged = false;

        public CertificationDTO() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCompletionStatus() {
            return completionStatus;
        }

        public void setCompletionStatus(String completionStatus) {
            this.completionStatus = completionStatus;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getCertifiers() {
            return certifiers;
        }

        public void setCertifiers(List<String> certifiers) {
            this.certifiers = certifiers;
        }

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public String getPhaseEnd() {
            return phaseEnd;
        }

        public void setPhaseEnd(String phaseEnd) {
            this.phaseEnd = phaseEnd;
        }

        public String getDue() {
            return due;
        }

        public void setDue(String due) {
            this.due = due;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public void reset() {
            statistics.reset();
        }

        public int getItemPercentComplete() {
            return statistics.getItemPercentComplete();
        }

        public int getTotalItems() {
            return statistics.getTotalItems();
        }

        public int getCompletedItems() {
            return statistics.getCompletedItems();
        }

        public void setStatistics(Certification.CertificationStatistics stats) {
            statistics = stats;
        }

        public Date getSigned() {
            return signed;
        }

        public void setSigned(Date signed) {
            this.signed = signed;
        }

        public boolean isElectronicallySigned() {
            return electronicallySigned;
        }

        public void setElectronicallySigned(boolean electronicallySigned) {
            this.electronicallySigned = electronicallySigned;
        }

        // Get reassignment limit decision
        public boolean isLimitReassignments() {
            return limitReassignments;
        }

        // Set reassignment limit decision
        public void setLimitReassignments(boolean limitReassignments) {
            this.limitReassignments = limitReassignments;
        }

        // Get flag which indicates whether this certification is staged
        public boolean isStaged() {
            return isStaged;
        }

        // Set the staged certification flag. 
        public void setIsStaged(boolean isStaged) {
            this.isStaged = isStaged;
        }
    }


}
