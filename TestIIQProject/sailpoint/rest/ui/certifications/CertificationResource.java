/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.certifications;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectAlreadyLockedException;
import sailpoint.api.certification.CertificationDecisioner;
import sailpoint.api.certification.CertificationDecisioner.Decision;
import sailpoint.api.certification.CertificationDecisioner.DecisionResults;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.Util;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.ElectronicSignature;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.certification.CertificationConfigDTO;
import sailpoint.service.certification.CertificationDTO;
import sailpoint.service.certification.CertificationService;
import sailpoint.service.certification.IdentityCertEntityListFilterContext;
import sailpoint.service.certification.ObjectCertEntityListFilterContext;
import sailpoint.service.certification.ObjectCertItemListFilterContext;
import sailpoint.service.certification.IdentityCertItemListFilterContext;
import sailpoint.service.listfilter.ListFilterContext;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ModifyImmutableException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.certification.CertificationUtil;
import sailpoint.web.messages.MessageKeys;

/**
 * Sub-resource for certification
 *
 * @author patrick.jeong
 */
public class CertificationResource extends BaseResource {

    private static Log log = LogFactory.getLog(CertificationResource.class);
    private String certificationId;

    private static final String DECISIONS = "decisions";
    private static final String ENTITY_DECISION = "entityDecision";

    /**
     * Constructor.
     *
     * @param parent BaseResource
     * @param certificationId ID of the certification. 
     * @throws GeneralException
     */
    public CertificationResource(BaseResource parent, String certificationId) throws GeneralException {
        super(parent);

        if (certificationId == null) {
            throw new InvalidParameterException("certificationId");
        }

        this.certificationId = certificationId;
    }

    /**
     * Get certification dto
     *
     * @return ObjectResult containing the CertificationDTO
     * @throws GeneralException
     */
    @GET
    public ObjectResult getCertification() throws GeneralException {
        return new CertificationService(loadCertification(), this).getCertificationDTO();
    }

    /**
     * Get a CertificationConfigDTO for the cert, with some UI configuration details.
     * @return CertificationConfigDTO
     * @throws GeneralException
     */
    @GET
    @Path("config")
    public CertificationConfigDTO getCertificationConfig() throws GeneralException {
        return new CertificationService(loadCertification(), this).getConfig();
    }

    /**
     * Get the CertificationItemListResource for listing certification items
     * @return CertificationItemListResource
     * @throws GeneralException
     */
    @Path("items")
    public CertificationItemListResource getCertificationItems() throws GeneralException {
        return new CertificationItemListResource(this, loadCertification(), null);
    }

    /**
     * Get the CertificationEntityListResource for certification entities
     * @return CertificationEntityListResource
     * @throws GeneralException
     */
    @Path("entities")
    public CertificationEntityListResource getCertificationEntities() throws GeneralException {
        return new CertificationEntityListResource(this, loadCertification());
    }

    /**
     * Save decisions on the certification
     * @param request Map holding 'decisions' with array of CertificationDecisioner.Decision objects
     * @return ObjectResult containing the CertificationDTO
     */
    @POST
    @Path("decisions")
    public ObjectResult saveDecisions(Map<String, Object> request) throws GeneralException {

        Certification certification = loadCertification();
        CertificationService certificationService = new CertificationService(certification, this);
        if (certification == null) {
            throw new GeneralException("Certification is null");
        }
        if (request == null || !request.containsKey(DECISIONS)) {
            throw new InvalidParameterException("decisions");
        }

        if (!CertificationUtil.isEditable(this, certification)) {
            if (log.isWarnEnabled()) {
                log.warn("User " + this.getLoggedInUserName() + " attempted to modify a read only certification: " + certification.getName());
            }
            throw new ModifyImmutableException(certification,
                    new Message(Message.Type.Error, MessageKeys.EXCEPTION_IMMUTABLE));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisionMaps = (List<Map<String, Object>>)request.get(DECISIONS);
        if (Util.isEmpty(decisionMaps)) {
            throw new InvalidParameterException("decisions");
        }
        List<Decision> decisions = new ArrayList<Decision>();
        for (Map<String, Object> decisionMap: decisionMaps) {
            ListFilterContext filterContext = getListFilterContext(certification, decisionMap);
            ListFilterService listFilterService =
                    new ListFilterService(getContext(), getLocale(), filterContext);
            Decision decision = new Decision(decisionMap, listFilterService, certification.getType());
            //check for any missing information and update the decision object
            decisions.add(certificationService.checkDecision(decision));
        }

        CertificationDecisioner decisioner = new CertificationDecisioner(getContext(), certificationId, getLoggedInUser());
        // we do not want the warnings for any invalidItems built
        // warnings will be generated client-side based on the attributes map for the ObjectResult
        decisioner.setBuildWarningsForInvalidItems(false);
        DecisionResults decisionResult = decisioner.decide(decisions, true);

        ObjectResult certificationObjectResult = this.getCertification();
        ObjectResult objectResult = new ObjectResult(
                (CertificationDTO) certificationObjectResult.getObject(),
                decisionResult.getStatus(),
                null,
                decisionResult.getWarnings(),
                decisionResult.getErrors());

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("invalidItemsCount", Util.size(decisionResult.getInvalidItems()));
        objectResult.setAttributes(attributes);

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) certificationObjectResult.getWarnings();
        if (warnings != null) {
            for (String warning : warnings) {
                objectResult.addWarning(warning);
            }
        }
        if (decisionResult.isTimedOut()) {
            throw new ObjectAlreadyLockedException(new Message(MessageKeys.CERT_LOCKED_SAVE_FAILURE));
        }
        return objectResult;
    }

    private ListFilterContext getListFilterContext(Certification certification, Map<String, Object> decisionMap) {
        ListFilterContext filterContext = null;
        // if this is an entity decision, create the entity an filter context
        if(decisionMap.containsKey(ENTITY_DECISION)) {
            boolean entityDecision = (Boolean)decisionMap.get(ENTITY_DECISION);
            if(entityDecision) {
                filterContext = certification.getType().isObjectType() ?
                        new ObjectCertEntityListFilterContext(certification.getId()) :
                        new IdentityCertEntityListFilterContext(certification.getId());
            }
        }

        // if this was not an entity descsion, create an item filter context
        if(filterContext == null) {
            filterContext = certification.getType().isObjectType() ?
                    new ObjectCertItemListFilterContext(certification.getId()) :
                    new IdentityCertItemListFilterContext(certification.getId());
        }

        return filterContext;
    }

    @POST
    @Path("sign")
    public ObjectResult signCertification(Map<String, Object> inputs)
            throws GeneralException, ExpiredPasswordException {
        ElectronicSignature signature = getSignature(inputs);
        CertificationService service = new CertificationService(loadCertification(), this);
        ObjectResult result = service.sign(signature);

        String accountId = signature.getAccountId();
        if (!result.isFailure() && (null != accountId)) {
            super.saveSignatureAccountId(accountId);
        }

        return result;
    }

    /**
     * Rescind a subCertification to the parent
     *
     * @return Response
     * @throws GeneralException
     */
    @POST
    @Path("rescind")
    public Response rescindChildCertification() throws GeneralException {

        CertificationService service = new CertificationService(loadCertification(), this);

        if (service.rescindChildCertification()) {
            return Response.ok().build();
        }

        return Response.serverError().build();
    }

    /**
     * Return certification resource for certification emails
     *
     * @return CertificationEmailResource Resource to handle email actions
     * @throws GeneralException
     */
    @Path("email")
    public CertificationEmailResource getCertificationEmail() throws GeneralException {
        return new CertificationEmailResource(this, loadCertification());
    }

    /**
     * Get the CertificationRevocationListResource for certification entities
     * @return CertificationRevocationListResource
     * @throws GeneralException
     */
    @Path("revocations")
    public CertificationRevocationListResource getCertificationRevocations() throws GeneralException {
        return new CertificationRevocationListResource(this, loadCertification());
    }

    /**
     * Get the basic suggest resource for certification. Needed for remediation modifiable.
     * @return SuggestResource.
     * @throws GeneralException
     */
    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        // Load for auth
        loadCertification();
        SuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext()
                .add(Application.class.getSimpleName())
                .add(ManagedAttribute.class.getSimpleName())
                .add(Identity.class.getSimpleName());

        return new SuggestResource(this, authorizerContext);
    }

    /**
     * Loads the certification object from the id and authorizes the access
     * @return Certification
     * @throws GeneralException
     */
    private Certification loadCertification() throws GeneralException {
        Certification certification = getContext().getObjectById(Certification.class, this.certificationId);
        if (certification == null) {
            throw new ObjectNotFoundException(Certification.class, this.certificationId);
        }

        authorize(new CertificationAuthorizer(certification));
        return certification;
    }
}
