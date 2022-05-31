/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.identity;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.Identity;
import sailpoint.object.Resolver;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.tools.GeneralException;

import java.util.Date;
import java.util.Map;

public class ForwardingInfoDTO {
    private static final Log log = LogFactory.getLog(ForwardingInfoDTO.class);

    /**
     * The date that the forwarding should start (in the future)
     */
    private Date startDate;

    /**
     * The date that the forwarding should end
     */
    private Date endDate;

    /**
     * The identity that we should forward to
     */
    private IdentitySummaryDTO forwardUser;

    public ForwardingInfoDTO() {}

    /**
     * Constructor used from the REST endpoint to pass a data map and instantiate the model
     * @param data
     */
    public ForwardingInfoDTO(Map<String, Object> data) {
        Long startDateTime = (Long) data.get("startDate");
        if (startDateTime != null) {
            startDate = new Date(startDateTime);
        }

        Long endDateTime = (Long) data.get("endDate");
        if (endDateTime != null) {
            endDate = new Date(endDateTime);
        }
        Map<String, Object> forwardUserMap = (Map) data.get("forwardUser");
        if (forwardUserMap != null) {
            this.forwardUser = new IdentitySummaryDTO(forwardUserMap);
        }
    }

    /**
     * Constructor with the identity and a sailpoint context
     *
     * @param identity The identity that we are looking at
     * @param resolver A sailpoint context
     * @throws GeneralException
     */
    public ForwardingInfoDTO(Identity identity, Resolver resolver) throws GeneralException {
        this.startDate = (Date) identity.getPreference(Identity.PRF_FORWARD_START_DATE);
        this.endDate = (Date) identity.getPreference(Identity.PRF_FORWARD_END_DATE);
        String name = (String) identity.getPreference(Identity.PRF_FORWARD);
        if (name != null) {
            Identity forwardingUser = resolver.getObjectByName(Identity.class, name);
            if (forwardingUser != null) {
                this.forwardUser = new IdentitySummaryDTO(forwardingUser);
            }
        }
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public IdentitySummaryDTO getForwardUser() {
        return forwardUser;
    }

    public void setForwardUser(IdentitySummaryDTO forwardUser) {
        this.forwardUser = forwardUser;
    }
}
