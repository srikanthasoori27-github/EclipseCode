/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.Date;
import java.util.List;


/**
 * Configuration for a Certification state.
 */
@XMLClass
public abstract class CertificationStateConfig <S extends Comparable> {

    private S state;
    private boolean enabled;
    private Duration duration;
    private NotificationConfig notificationConfig;


    /**
     * Default constructor.
     */
    public CertificationStateConfig() {}

    /**
     * Constructor.
     */
    public CertificationStateConfig(S state, boolean enabled, Duration duration,
                                    NotificationConfig notificationConfig) {
        this.state = state;
        this.enabled = enabled;
        this.duration = duration;
        this.notificationConfig = notificationConfig;
    }

    /**
     * Return the state that this config corresponds to. Note that this is not
     * an XMLProperty because the serializer cannot determine the type. XML
     * serialization is done through a getter/setter pair on the subclass.
     */
    public S getState() {
        return this.state;
    }

    /**
     * Set the state this this config corresponds to. See note in getState().
     */
    public void setState(S state) {
        this.state = state;
    }
    
    @XMLProperty
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @XMLProperty
    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public NotificationConfig getNotificationConfig() {
        return notificationConfig;
    }

    public void setNotificationConfig(NotificationConfig notificationConfig) {
        this.notificationConfig = notificationConfig;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the CertificationStateConfig for the given state.
     */
    public static <T extends CertificationStateConfig> T getStateConfig(Object state, List<T> configs) {

        if ((null != state) && (null != configs)) {
            for (T config : configs) {
                if (state.equals(config.getState())) {
                    return config;
                }
            }
        }

        return null;
    }

    /**
     * Return whether the given state is enabled according to the given list
     * containing the state config.
     */
    public static boolean isEnabled(Object state, List<? extends CertificationStateConfig> configs) {

        CertificationStateConfig stateConfig = getStateConfig(state, configs);
        return ((null != stateConfig) && stateConfig.isEnabled());
    }

    /**
     * Return the enabled state that comes before the given current state in the
     * list of configs, or no if there is no previous enabled state.
     */
    public static <S extends Comparable> S getPreviousState(List<? extends CertificationStateConfig<S>> configs, S current) {

        S previous = null;

        if ((null != current) && (null != configs)) {
            // Keep going until we find the current.  The previous is the last
            // enabled state.
            for (CertificationStateConfig<S> config : configs) {

                // Stop when we find the current state.
                if (current.equals(config.getState())) {
                    break;
                }

                // Haven't found the current state yet, store this as the
                // previous state if it is enabled.
                if (config.isEnabled()) {
                    previous = config.getState();
                }
            }
        }

        return previous;
    }
    
    /**
     * Get the next enabled state (or the endState if there are no more states).
     * If the current state is null, return the initialState.
     *
     * @return The next enabled state, or endState if there are no more states.
     */
    public static <S extends Comparable> S getNextState(List<? extends CertificationStateConfig<S>> configs,
                                                        S current, S initialState, S endState) {

        S next = null;

        // Null means that we have not been activated yet.
        if (null == current) {
            next = initialState;
        }
        else if (null != configs) {
            boolean pastCurrentState = false;

            // Non-null current state.  Scroll to the next enabled state in the
            // state configuration.
            for (CertificationStateConfig<S> config : configs) {
                if (pastCurrentState && config.isEnabled()) {
                    next = config.getState();
                    break;
                }

                if (current.equals(config.getState())) {
                    pastCurrentState = true;
                }

                // Set to null in case we never find a next state.
                next = null;
            }
        }

        // If we still haven't got a state, we're at the end.
        if (null == next) {
            next = endState;
        }

        return next;
    }

    /**
     * Calculate the end date for the given state assuming that the first state
     * starts at the given start date.
     */
    public static <S extends Comparable> Date calculateStateEndDate(
                                                  S state,
                                                  List<? extends CertificationStateConfig<S>> configs,
                                                  Date startDate) {

        return calculateStateEndDate(null, state, configs, startDate);
    }

    /**
     * Calculate the duration starting at the given start state through the end
     * of the given end state (inclusive). If a start state is not given,
     * start at the first state. If the end state is not given, finish at
     * the last state in the config list.
     *
     */
    public static <S extends Comparable> Date calculateStateEndDate(
                                                  S startState, S endState,
                                                  List<? extends CertificationStateConfig<S>> configs,
                                                  Date startDate) {
        Date endDate = null;

        if ((null != configs) && !configs.isEmpty()) {
            for (CertificationStateConfig<S> config : configs) {
                // Check that the current config is enabled and is between the
                // start and end states (inclusive) if given.
                if (config.isEnabled() &&
                    (null != config.getDuration()) &&
                    ((null == startState) || (config.getState().compareTo(startState) >= 0)) &&
                    ((null == endState) || (config.getState().compareTo(endState) <= 0))) {

                    if (null == endDate) {
                        endDate = startDate;
                    }
                    endDate = config.getDuration().addTo(endDate);
                }
            }
        }

        return endDate;
    }
}
