/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.service.webservice;

import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Map;

public interface ConnectionManagerConfigProvider<T extends ConnectionManagerConfigProvider.ConnectionManagerConfiguration> {
    T getConfiguration();

    class ConnectionManagerConfiguration {
        protected Configuration configuration = null;

        public ConnectionManagerConfiguration(Configuration config) {
            this.configuration = config;
        }

        public boolean isConfigured() {
            try {
                validateConfiguration();
            } catch (GeneralException ex) {
                return false;
            }
            return true;
        }

        public void validateConfiguration() throws GeneralException {

            if (this.configuration == null) {
                throw new GeneralException("No Configuration for Connectionmanager found");
            }

            if (Util.isNullOrEmpty(getHostname())) {
                throw new GeneralException("Missing value for " + Configuration.WEB_SERVICE_CONFIG_HOSTNAME);
            }

        }

        public static final int READ_TIMEOUT_DEFAULT  = 60;    // 60 secs

        public int getReadTimeout() {
            int timeoutSecs = this.configuration.getInt(Configuration.WEB_SERVICE_CONFIG_READ_TIMEOUT_SECS);
            if (timeoutSecs <= 0) {
                timeoutSecs = READ_TIMEOUT_DEFAULT;
            }
            return timeoutSecs * 1000;
        }

        public static final int CONNECT_TIMEOUT_DEFAULT = 10;  // 10 secs

        public int getConnectTimeout() {
            int timeoutSecs = this.configuration.getInt(Configuration.WEB_SERVICE_CONFIG_CONNECT_TIMEOUT_SECS);
            if (timeoutSecs <= 0) {
                timeoutSecs = CONNECT_TIMEOUT_DEFAULT;
            }
            return timeoutSecs * 1000;
        }

        public long getIdleConnectionRefreshInterval() {
            return this.configuration.getLong(
                    Configuration.WEB_SERVICE_CONFIG_IDLE_CONNECTION_REFRESH_INTERVAL);
        }

       public String getHostname() {
            return this.configuration.getString(Configuration.WEB_SERVICE_CONFIG_HOSTNAME);
        }

        public boolean configHasChanged(ConnectionManagerConfiguration lastKnownConfiguration) {
            if ((lastKnownConfiguration == null) || (lastKnownConfiguration.configuration == null)){
                return (this.configuration != null);
            } else {
                if (this.configuration == null) {
                    // if current is not null, and new is null there's been a change
                    return true;
                }
            }

            return attributesHaveChanged(lastKnownConfiguration.configuration.getAttributes(),
                    this.configuration.getAttributes());
        }

        private boolean attributesHaveChanged(
                Map<String, Object> currentAttributes, Map<String, Object> newlyReadAttributes) {

            if (currentAttributes == null && newlyReadAttributes == null) {
                return false;
            }
            else if (currentAttributes != null && newlyReadAttributes == null) {
                return true;
            }
            else if (currentAttributes == null) {
                return true;
            }


            // if the count of current attributes is not the same as the count of new attributes there's been a change
            if(currentAttributes.size() != newlyReadAttributes.size()) {
                return true;
            }

            // compare each attribute value
            for(String attributeName : currentAttributes.keySet()) {
                if(newlyReadAttributes.containsKey(attributeName)) {
                    Object currentValue = currentAttributes.get(attributeName);
                    Object newlyReadValue = newlyReadAttributes.get(attributeName);
                    if(currentValue == null) {
                        if(newlyReadValue != null) {
                            // if current value is null, and new value is not, there's been a change
                            return true;
                        }
                    } else {
                        if(!currentValue.equals(newlyReadValue)) {
                            // if current value is not equal to the new value there's been a change
                            return true;
                        }
                    }
                } else {
                    return true;
                }
            }

            return false;
        }

    }
}
