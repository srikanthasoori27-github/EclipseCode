/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction.RemediationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.certification.CertificationUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


/**
 * A sub-resource to return the completed revocation items in a cert.
 *
 * @author <a href="mailto:derry.cannon@sailpoint.com">Derry Cannon</a>
 */
public class CertificationRevocationResource extends BaseListResource {
    private static final Log log = LogFactory.getLog(CertificationRevocationResource.class);

    private static final String REVOCATIONS_TABLE_COLUMNS = "certificationRemediationTableColumns";

    // Description is a calculated property, so we will sort by the
    // contributing columns
    private static final String REVOCATION_DESC_SORT_COLUMNS =
            "type, bundle, exceptionApplication, exceptionAttributeName, exceptionAttributeValue, exceptionPermissionTarget, exceptionPermissionRight, violationSummary";

    private String certificationId;
    private CertificationResource parent;


    /**
     * Default constructor
     */
    public CertificationRevocationResource() {
    }


    /**
     * Sub-resource constructor.
     *
     * @throws GeneralException
     */
    public CertificationRevocationResource(String certificationId, CertificationResource parent)
            throws GeneralException {
        super(parent);
        this.certificationId = certificationId;
        this.parent = parent;
    }


    /**
     * Return the revoked items for this certification.
     *
     * @return A ListResult with details about the revoked items.
     */
    @GET
    public RequestResult getCompletedRevocations() throws GeneralException {
        parent.authCertification(certificationId);

        QueryOptions qo = this.getQueryOptions();

        List<Map<String, Object>> results = getResults(REVOCATIONS_TABLE_COLUMNS,
                CertificationItem.class, qo);

        return new ListResult(results, countResults(CertificationItem.class, qo));
    }


    /**
     * Build the query options for returning the cert revocations.
     *
     * @return QueryOptions object
     * @throws GeneralException
     */
    @Override
    protected QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.getQueryOptions(REVOCATIONS_TABLE_COLUMNS);
        qo.add(Filter.eq("action.remediationKickedOff", true));
        qo.add(Filter.eq("parent.certification", getCertification()));

        boolean containsOrdering = false;
        Ordering revoked = null;
        if ((qo.getOrderings() != null) && (!qo.getOrderings().isEmpty())) {
            List<Ordering> orderings = qo.getOrderings();
            for (Ordering ordering : orderings) {
                // Add a default sort by identity id to make the sorting of values
                // that are the same or empty consistent. Need to ensure that the 
                // ordering doesn't already exist for MSSQL.
                if (ordering.getColumn().equals("parent.identity"))
                    containsOrdering = true;

                // replace the revoked column with its component columns,
                // but make sure to preserve the sort direction
                if (ordering.getColumn().equals("IIQ_revoked"))
                    revoked = ordering;
            }
        }

        if (!containsOrdering)
            qo.addOrdering(qo.getOrderings().size(), "parent.identity", true);

        if (revoked != null) {
            // qo.getOrderings().remove() refuses to work for some reason?
            List<Ordering> orderings = qo.getOrderings();
            orderings.remove(revoked);
            List<String> cols = Util.csvToList(REVOCATION_DESC_SORT_COLUMNS);
            for (String col : cols) {
                orderings.add(new Ordering(col, revoked.isAscending()));
            }

            qo.setOrderings(orderings);
        }

        return qo;
    }

    /**
     * An override that specifies a list of projection columns that is
     * different from the list of columns that would be returned by the
     * UIConfig for the given columnsKey.  This lets us leverage the work
     * being done in the superclass without being tied exclusively to
     * the UIConfig.
     *
     * @param columnsKey Key to the table config
     * @return Modified list of column names
     * @throws GeneralException
     */
    @Override
    protected List<String> getProjectionColumns(String columnsKey) throws GeneralException {
        List<String> cols = super.getProjectionColumns(columnsKey);
        cols.addAll(getSupplimentalProjectionColumns());

        return cols;
    }

    /**
     * There are a handful of columns we need from the db that
     * are not part of the UIConfig.
     *
     * @return
     * @throws GeneralException
     */
    private List<String> getSupplimentalProjectionColumns() throws GeneralException {
        List<String> cols = new ArrayList<String>();

        cols.add("type");
        cols.add("exceptionEntitlements");
        cols.add("policyViolation");
        cols.add("bundle");
        cols.add("parent.certification.id");

        return cols;
    }

    /**
     * Converts the row returned from the db to the format needed by the
     * data store on the UI side.
     */
    @Override
    public Map<String, Object> convertRow(Object[] row, List<String> cols, String columnsKey)
            throws GeneralException {
        Map<String, Object> map = super.convertRow(row, cols, columnsKey);

        // we pushed a handful of extra columns onto the projection list,
        // so now we have to manually pull them off the row of results
        for (int i = 0; i < row.length; i++) {
            if (!map.containsKey(cols.get(i)))
                map.put(cols.get(i), row[i]);
        }

        // convert the remediation completed boolean to a useful text
        Message msg;
        if (((Boolean) map.get("action.remediationCompleted")).booleanValue())
            msg = new Message(MessageKeys.LABEL_CLOSED);
        else
            msg = new Message(MessageKeys.LABEL_OPEN);

        map.put("action.remediationCompleted", msg.getLocalizedMessage(getLocale(), null));

        // convert the names of the recipient, requestor and target 
        // to their displayable names
        map.put("action.ownerName", WebUtil.getDisplayNameForName("Identity",
                (String) map.get("action.ownerName")));
        map.put("action.actorName", WebUtil.getDisplayNameForName("Identity",
                (String) map.get("action.actorName")));

        String target = null;
        if (getCertification().getType() == Certification.Type.DataOwner) {
            target = (String) map.get("targetName");
        } else {
            target = (String) map.get("parent.targetName");
        }
        map.put("parent.targetName", WebUtil.getDisplayNameForName("Identity", target));

        // i18n for the type of remediation action
        RemediationAction remAction = (RemediationAction) map.get("action.remediationAction");
        msg = new Message(remAction == null ? "" : remAction.getMessageKey());
        map.put("action.remediationAction", msg.getLocalizedMessage(getLocale(), null));

        // calculate the description of the revoked stuff
        CertificationUtil.calculateDescription(map, getLocale());

        // calculate the expiration date of the cert's current phase
        Certification cert = getContext().getObjectById(Certification.class,
                (String) map.get("parent.certification.id"));
        Date phaseEnd = cert.calculatePhaseEndDate(cert.getPhase());
        String localizedDate =
                Internationalizer.getLocalizedDate(phaseEnd, getLocale(), getUserTimeZone());
        map.put("parent.certification.created", localizedDate);

        cleanMap(map);

        return map;
    }

    /**
     * Remove any data from the map that isn't needed by the caller
     * of the service.  In particular, the entitlement snapshot
     * creates problems if you try to send it over the wire.
     *
     * @param map Map to clean
     * @throws GeneralException
     */
    private void cleanMap(Map<String, Object> map) throws GeneralException {
        for (String col : getSupplimentalProjectionColumns()) {
            map.remove(col);
        }
    }

    /**
     * Return the cert we're operating on.
     */
    private Certification getCertification() throws GeneralException {
        return getContext().getObjectById(Certification.class, this.certificationId);
    }
}
