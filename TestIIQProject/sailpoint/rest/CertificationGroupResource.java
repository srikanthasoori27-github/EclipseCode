/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.WebResourceAuthorizer;
import sailpoint.integration.RequestResult;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.web.messages.MessageKeys;
/**
 * @author: jonathan.bryant@sailpoint.com
 */
public class CertificationGroupResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(CertificationGroupResource.class);

    private String certificationGroupId;

    public CertificationGroupResource(String groupId, BaseResource parent) {
        super(parent);
        this.certificationGroupId = groupId;
    }

    @Path("forward")
    @POST
    public RequestResult forward(@FormParam("newOwner") String newOwner) throws GeneralException {
    	
        authorize(new WebResourceAuthorizer("monitor/scheduleCertifications/viewAndEditCertifications.jsf"));

        RequestResult result = new RequestResult();

        try {
            CertificationGroup group = getContext().getObjectById(CertificationGroup.class, certificationGroupId);
            Identity owner = getContext().getObjectById(Identity.class, newOwner);
            if(owner != null){
                group.setOwner(owner);
                getContext().saveObject(group);
                getContext().commitTransaction();
            } else {
                result.addError("Failed loading new owner identity object.");
            }
        } catch (Throwable e) {
            log.error(e);
            result.setStatus("error");
            result.addError(Internationalizer.getMessage(MessageKeys.ERR_FATAL_SYSTEM, getLocale()));
        }

        return result;
    }

}
