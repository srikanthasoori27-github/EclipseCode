/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.UIConfig;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.web.certification.CertificationUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * @author patrick.jeong
 */
public class CertificationRevocationService extends BaseListService<BaseListServiceContext> {

    private Certification certification;

    /**
     * ListResourceColumnSelector implementation that adds additional projection columns
     */
    private static final BaseListResourceColumnSelector COLUMN_SELECTOR =
            new BaseListResourceColumnSelector(UIConfig.CERTIFICATION_REVOCATION_DETAIL_TABLE_COLUMNS) {

                /**
                 * Extend getProjectionColumns() to add a few more columns that are needed.
                 */
                public List<String> getProjectionColumns() throws GeneralException {
                    List<String> columns = new ArrayList<>(super.getProjectionColumns());

                    /* Projection columns needed for calculated columns */
                    addColumnToProjectionList("type", columns);
                    addColumnToProjectionList("exceptionEntitlements", columns);
                    addColumnToProjectionList("policyViolation", columns);
                    addColumnToProjectionList("violationSummary", columns);
                    addColumnToProjectionList("bundle", columns);

                    return columns;
                }
            };

    /**
     * Constructor
     *
     * @param context SailPointContext
     * @param listServiceContext BaseListServiceContext
     */
    public CertificationRevocationService(Certification certification, SailPointContext context,
                                          BaseListServiceContext listServiceContext) {
        super(context, listServiceContext, COLUMN_SELECTOR);
        this.certification = certification;
    }

    /**
     * Get revocations for certification.
     *
     * @return ListResult containing the certification revocations
     * @throws GeneralException
     */
    public ListResult getCertificationRevocations() throws GeneralException {

        // Setup the sorting and paging for query options. Sorting and paging context is in the listServiceContext and
        // columnSelector
        QueryOptions queryOptions = super.createQueryOptions();
        queryOptions.add(Filter.eq("action.remediationKickedOff", true));
        queryOptions.add(Filter.eq("parent.certification", certification));

        int count = countResults(CertificationItem.class, queryOptions);

        List<Map<String, Object>> results = getResults(CertificationItem.class, queryOptions);

        // Convert results to DTOs
        List<CertificationRevocationDTO> revocationDTOs = new ArrayList<>();

        for (Map<String, Object> result : results) {
            CertificationRevocationDTO revocationDTO = createDTO(result);
            revocationDTOs.add(revocationDTO);
        }

        return new ListResult(revocationDTOs, count);
    }

    /**
     *
     * @param resultMap
     * @return
     * @throws GeneralException
     */
    public CertificationRevocationDTO createDTO(Map<String, Object> resultMap) throws GeneralException {
        CertificationRevocationDTO revocationDTO = new CertificationRevocationDTO();

        revocationDTO.setStatus(getRemediationStatus(resultMap));

        revocationDTO.setIdentityName(getIdentityDisplayName(resultMap));

        CertificationUtil.calculateDescription(resultMap, listServiceContext.getLocale());
        revocationDTO.setTargetDisplayName((String)resultMap.get("IIQ_revoked"));

        revocationDTO.setDetails((String)resultMap.get("action-description"));

        // Calculate the remediation action
        revocationDTO.setRequestType(getRemediationAction(resultMap));

        String requester = WebUtil.getDisplayNameForName("Identity", (String)resultMap.get("action-actorName"));
        revocationDTO.setRequester(requester);

        String owner = WebUtil.getDisplayNameForName("Identity", (String)resultMap.get("action-ownerName"));
        revocationDTO.setOwner(owner);

        revocationDTO.setExpiration(getRevocationExpiration());

        return revocationDTO;
    }

    /**
     * Get the phase end date
     * @return localized expiration date
     */
    private String getRevocationExpiration() {
        Date phaseEnd = certification.calculatePhaseEndDate(certification.getPhase());
        String localizedDate = Internationalizer.getLocalizedDate(phaseEnd, listServiceContext.getLocale(),
                listServiceContext.getUserTimeZone());

        return localizedDate;
    }

    /**
     * Get the remediation action string
     *
     * @param resultMap
     * @return remediation action localized string
     */
    private String getRemediationAction(Map<String, Object> resultMap) {
        CertificationAction.RemediationAction remAction = (CertificationAction.RemediationAction)resultMap.get("action-remediationAction");
        Message actionMessage = new Message(remAction == null ? "" : remAction.getMessageKey());
        return actionMessage.getLocalizedMessage(listServiceContext.getLocale(), listServiceContext.getUserTimeZone());
    }

    /**
     * Get the remediation status string
     *
     * @param resultMap
     * @return localized remediation status string
     */
    private String getRemediationStatus(Map<String, Object> resultMap) {
        // convert the remediation completed boolean to a useful text
        Message msg;

        if (((Boolean)resultMap.get("action-remediationCompleted")).booleanValue()) {
            msg = new Message(MessageKeys.LABEL_CLOSED);
        }
        else {
            msg = new Message(MessageKeys.LABEL_OPEN);
        }

        return msg.getLocalizedMessage(listServiceContext.getLocale(), listServiceContext.getUserTimeZone());
    }

    /**
     * Get the certification display target name
     *
     * @param resultMap
     * @return target identity display name
     */
    private String getIdentityDisplayName(Map<String, Object> resultMap) throws GeneralException {
        String targetProperty = this.certification.getType().isObjectType() ? "targetName" :
                "parent-targetName";

        return WebUtil.getDisplayNameForName("Identity", (String)resultMap.get(targetProperty));
    }
}
