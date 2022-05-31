/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * Configuration for a Certification phase.
 */
@XMLClass
public class CertificationPhaseConfig extends CertificationStateConfig<Certification.Phase> {


    /**
     * Default constructor.
     */
    public CertificationPhaseConfig() {
        super();
    }

    /**
     * Constructor.
     */
    public CertificationPhaseConfig(Certification.Phase phase, boolean enabled,
                                    Duration duration, NotificationConfig notifConfig) {
        super(phase, enabled, duration, notifConfig);
    }


    @XMLProperty
    public Certification.Phase getPhase() {
        return super.getState();
    }

    public void setPhase(Certification.Phase phase) {
        super.setState(phase);
    }
}
