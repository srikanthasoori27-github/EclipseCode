/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.WebResourceAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.object.Tag;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * @author: jonathan.bryant@sailpoint.com
 */
@Path("certificationGroups")
public class CertificationGroupListResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(CertificationGroupResource.class);

    private String name;
    private String owner;
    private String completionMin;
    private String completionMax;
    private String createdMin;
    private String createdMax;
    private List<String> tags;
    private boolean filterEmpty;

    @Override
    protected String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : "certificationsTableColumns";
    }

    @Override
    protected void processForm(MultivaluedMap<String, String> form){
        super.processForm(form);
        name = getSingleFormValue(form, "name");
        owner = getSingleFormValue(form, "owner");
        completionMin = getSingleFormValue(form, "completionMin");
        completionMax = getSingleFormValue(form, "completionMax");
        createdMin = getSingleFormValue(form, "createdMin");
        createdMax = getSingleFormValue(form, "createdMax");
        filterEmpty = Util.atob(getSingleFormValue(form, "filterEmpty"));
        
        if (form.containsKey("tags")){
            tags = form.get("tags");
            if (tags != null && !tags.isEmpty()){
                tags.remove("");
            }
        }
        
        if (sortBy == null || "".equals(sortBy)){
            sortBy = "created";
            sortDirection = "DESC";
        }

    }

    @Override
    protected QueryOptions getQueryOptions(String columnsKey) throws GeneralException {
        QueryOptions ops = super.getQueryOptions(columnsKey);
        ops.setScopeResults(true);

         ops.add(Filter.ne("status", CertificationGroup.Status.Archived));
         ops.add(Filter.ne("status", CertificationGroup.Status.Canceling));

        if (!Util.isNullOrEmpty(name)){
            ops.add(Filter.ignoreCase(Filter.like("name", name, Filter.MatchMode.START)));
        }
        
        if (filterEmpty){
            ops.add(Filter.ne("status", CertificationGroup.Status.Pending));
            ops.add(Filter.gt("totalCertifications", 0));
        }

        if (!Util.isNullOrEmpty(owner)){
            ops.add(Filter.or(Filter.eq("owner.id", owner), Filter.eq("owner.name", owner)));
        }

        Date minCreateDate = parseDateRange(createdMin, true);
        if (minCreateDate != null){
            ops.add(Filter.gt("created", minCreateDate));
        }

        Date maxCreateDate = parseDateRange(createdMax, false);
        if (maxCreateDate != null){
            ops.add(Filter.lt("created", maxCreateDate));
        }

        Integer minCompletion = Util.atoi(completionMin);
        if (minCompletion != 0){
            ops.add(Filter.ge("percentComplete", minCompletion));
        }

        Integer maxCompletion = Util.atoi(completionMax);
        if (maxCompletion != 0){
            ops.add(Filter.le("percentComplete", maxCompletion));
        }

        /** Need to fake out the sort so that "Nothing to Certify ends up above 100% or below it */
        if(ops.getOrderings()!=null) {
            Ordering ordering = null;
            for(Ordering o : ops.getOrderings()) {
                if(o.getColumn().equals("percentComplete")) {
                    ordering = o;
                }
            }
            if(ordering!=null) {
                ops.addOrdering("totalCertifications", !ordering.isAscending());
            }
        }
        
        if (tags != null && !tags.isEmpty()){
            ops.add(Filter.containsAll("definition.tags.id", tags));
        }
        
        return ops;
    }

    @Path("{groupId}")
    public CertificationGroupResource getCertificationGroup(@PathParam("groupId") String groupId){
        return new CertificationGroupResource(groupId, this);
    }

    @POST
    public ListResult list(MultivaluedMap<String, String> form) throws GeneralException {
    	
        //This endpoint is used in viewAndEditCertifications as well as Compliance Dashboard
	    authorize(CompoundAuthorizer.or(new WebResourceAuthorizer("monitor/scheduleCertifications/viewAndEditCertifications.jsf"), new CapabilityAuthorizer("CertificationAdministrator")));

        processForm(form);

        List<CertificationGroupDTO> dtos = new ArrayList<CertificationGroupListResource.CertificationGroupDTO>();
        int count = 0;
        
        try {
            String columnkey = getColumnKey();
            QueryOptions ops = this.getQueryOptions(columnkey);
            count = getContext().countObjects(CertificationGroup.class, ops);
            Iterator<CertificationGroup> iter = getContext().search(CertificationGroup.class, ops);
            if (iter!=null){
                while(iter.hasNext()){
                    CertificationGroup certGroup = iter.next();
                    dtos.add(buildDTO(certGroup));
                }
            }
        } catch (GeneralException e) {
            log.error(e);
        }

        return new ListResult(dtos, count);
    }

    private CertificationGroupDTO buildDTO(CertificationGroup certGroup) throws GeneralException {
        CertificationGroupDTO dto = new CertificationGroupDTO();
        
        dto.setId(certGroup.getId());
        dto.setName(certGroup.getName());
        dto.setOwnerDisplayName(certGroup.getOwner() != null ? certGroup.getOwner().getDisplayName() : "");
        
        CertificationGroup.Status status = certGroup.getStatus();
        Message statusMsg = new Message(status.getMessageKey());
        dto.setStatus(statusMsg.getLocalizedMessage(getLocale(), getUserTimeZone()));
        
        int totalCerts = certGroup.getTotalCertifications();
        Message percentComplete = null;
        
        if (CertificationGroup.Status.Pending.equals(status)) {
            percentComplete = new Message(status.getMessageKey());    
        } else if ( totalCerts == 0) {
            percentComplete = new Message(MessageKeys.CERT_PERCENT_NO_ITEMS);
        } else {
            int percentCompleteNum = certGroup.getPercentComplete();
            int completeCerts = certGroup.getCompletedCertifications();
            
            percentComplete = new Message(MessageKeys.PERCENT_COMPLETE_WITH_COUNT,
                    percentCompleteNum, completeCerts, totalCerts);
        }
        
        dto.setPercentComplete(percentComplete.getLocalizedMessage(getLocale(), getUserTimeZone()));
        
        dto.setCreated(certGroup.getCreated());
        
        if (certGroup.getDefinition().getTags() != null) {
            List<String> certTags = new ArrayList<String>();
            for (Tag tag : certGroup.getDefinition().getTags()) {
                certTags.add(tag.getName());
            }
            dto.setTags(certTags);
        }

        return dto;
    }
    
    public class CertificationGroupDTO {
        public String id;
        public String name;
        public String ownerDisplayName;
        public String status;
        public String percentComplete;
        public Date created;
        public List<String> tags;

        public CertificationGroupDTO() {}

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getOwnerDisplayName() {
            return ownerDisplayName;
        }

        public void setOwnerDisplayName(String ownerDisplayName) {
            this.ownerDisplayName = ownerDisplayName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getPercentComplete() {
            return percentComplete;
        }

        public void setPercentComplete(String percentComplete) {
            this.percentComplete = percentComplete;
        }

        public Date getCreated() {
            return created;
        }

        public void setCreated(Date created) {
            this.created = created;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }
}