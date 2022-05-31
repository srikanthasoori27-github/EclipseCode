/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.object.AbstractCertificationItem.ContinuousState;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * Configuration for a Certification continuous state.
 */
@XMLClass
public class ContinuousStateConfig
    extends CertificationStateConfig<ContinuousState> {


    /**
     * Default constructor.
     */
    public ContinuousStateConfig() {
        super();
    }

    /**
     * Constructor.
     */
    public ContinuousStateConfig(ContinuousState state, boolean enabled,
                                              Duration duration, NotificationConfig notifConfig) {
        super(state, enabled, duration, notifConfig);
    }


    @XMLProperty
    public ContinuousState getContinuousState() {
        return super.getState();
    }

    public void setContinuousState(ContinuousState state) {
        super.setState(state);
    }
}
