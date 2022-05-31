/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.certification.CertificationDecisioner;
import sailpoint.api.certification.CertificationDecisioner.Decision;
import sailpoint.api.certification.CertificationDecisioner.DecisionResults;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.view.DecisionSummary;
import sailpoint.web.view.DecisionSummaryFactory;


/**
 * A sub-resource for an certification.
 *
 * @author <a href="peter.holcomb@sailpoint.com">Peter Holcomb</a>
 */
@Path("certification")
public class CertificationResource extends BaseResource {

    private static final Log log = LogFactory.getLog(CertificationResource.class);

    @QueryParam("workItemId") protected String workItemId;

    /** Sub Resource Methods **/
    @Path("item/{itemId}")
    public CertificationItemResource getItem(@PathParam("itemId") String itemId)
    throws GeneralException {
        return new CertificationItemResource(itemId, workItemId, this);
    }

    /**
     * Expects a list of Decision objects and creates the various CertificationActions for them.
     */
    @POST
    @Path("{certificationId}/decisions")
    public RequestResult makeDecisions(@FormParam("decisions") String decisionsJSON, @PathParam("certificationId") String certificationId) throws GeneralException {

    	authCertification(certificationId);

        if (decisionsJSON != null && !decisionsJSON.equals("[]")) {
            List<Decision> decisions = JsonHelper.listFromJson(Decision.class, decisionsJSON);
            CertificationDecisioner decisioner = new CertificationDecisioner(getContext(), certificationId, getLoggedInUser());
            decisioner.setBuildWarningsForInvalidItems(true);
            DecisionResults decisionResult = decisioner.decide(decisions);
            RequestResult result = new ObjectResult(decisionResult);
            if (decisionResult.isTimedOut()) {
                result.setStatus(RequestResult.STATUS_RETRY);
            } else {
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }
            
            return result;
        }

        return null;
    }

    /**
     * Returns decision details for a bulk entity action. The decision details include
     * things like the default description, default rememediation, etc.
     */
    @GET
    @Path("{certificationId}/bulkdecision/{entityId}/{action}")
    public RequestResult getBulkEntityDecisionDetails(@PathParam("certificationId") String certificationId,
                                                      @PathParam("action") String action,
                                                      @PathParam("entityId") String id) throws GeneralException {

        CertificationEntity entity = getContext().getObjectById(CertificationEntity.class, id);
        authCertificationEntity(certificationId, entity);
        
        CertificationAction.Status status = CertificationAction.Status.valueOf(action);

        DecisionSummaryFactory summaryFactory = new DecisionSummaryFactory(getContext(), getLoggedInUser(),
                getLocale(), getUserTimeZone());

        DecisionSummary summary = summaryFactory.calculateSummary(entity, status);
        return new ObjectResult(summary);
    }

     /**
     * Returns decision details for a bulk action. The decision details include
     * things like the default description, default rememediation, etc.
     */
    @GET
    @Path("{certificationId}/bulkdecision/{action}")
    public RequestResult getBulkDecisionDetails(@PathParam("certificationId") String certificationId,
                                                @PathParam("action") String action) throws GeneralException{

    	authCertification(certificationId);
    	
        CertificationAction.Status status = CertificationAction.Status.valueOf(action);


        Iterator<Object[]> query = getContext().search(Certification.class,
                new QueryOptions(Filter.eq("id", certificationId)),
                Arrays.asList("type"));

        Certification.Type certType = null;
        if (query.hasNext())
            certType = (Certification.Type) query.next()[0];

        if (certType == null)
            throw new GeneralException("Error getting bulk decision details. " +
                    "Could not find certification id:" + certificationId);

        DecisionSummaryFactory summaryFactory = new DecisionSummaryFactory(getContext(), getLoggedInUser(),
            getLocale(), getUserTimeZone());
        DecisionSummary summary = summaryFactory.calculateBulkSummary(certType, status);
        return new ObjectResult(summary);
    }
    
    /**
     * Return the sub-resource that will handle revocations for this cert.
     * @throws GeneralException 
     */
    @Path("{certificationId}/revocations")
    public CertificationRevocationResource getCompletedRevocations(@PathParam("certificationId") String certificationId) 
        throws GeneralException {
        return new CertificationRevocationResource(certificationId, this);
    }
    
    

    // ------------------------------------------------------------------
    //
    //  Private Methods
    //
    // ------------------------------------------------------------------

    protected void authCertification(String id) throws GeneralException {
        Certification cert = getContext().getObjectById(Certification.class, id);
        authorize(new CertificationAuthorizer(cert, workItemId));
    }

    private void authCertificationEntity(String certId, CertificationEntity entity) throws GeneralException {
        Certification cert = getContext().getObjectById(Certification.class, certId);
        authorize(new CertificationAuthorizer(cert, workItemId));

        if (entity == null || entity.getCertification() == null || !Util.nullSafeEq(certId, entity.getCertification().getId())) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.CERT_UNAUTHORIZED_ACCESS_EXCEPTION));
        }
    }
}
