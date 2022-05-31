/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.module;

import sailpoint.object.ColumnConfig;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ModuleStatusDTO extends ModuleDTO {

    List<HostStatusDTO> statuses;

    public ModuleStatusDTO(String appName) {
        setName(appName);
    }

    public ModuleStatusDTO(Map<String,Object> app, List<ColumnConfig> cols, List<String> additionalColumns) {
        super(app, cols, additionalColumns);
    }

    public List<HostStatusDTO> getStatuses() { return statuses; }
    public void setStatuses(List<HostStatusDTO> l) { this.statuses = l; }

    public static class HostStatusDTO {
        // Name of the Server
        String server;

        // Status of the Host
        Boolean status;

        // Date of last status check
        Date ping;

        // True if there is an outstanding Request to get
        Boolean outstandingRequest;

        // Name of the module
        String moduleName;

        String exceptionMessage;

        public HostStatusDTO(String serverName, Boolean status, Date lastRefresh, String appName ) {
            this.server = serverName;
            this.status = status;
            this.ping = lastRefresh;
            this.moduleName = appName;
        }

        public String getServer() {
            return server;
        }

        public void setServer(String server) {
            this.server = server;
        }

        public Boolean getStatus() {
            return status;
        }

        public void setStatus(Boolean status) {
            this.status = status;
        }

        public Date getPing() {
            return ping;
        }

        public void setPing(Date ping) {
            this.ping = ping;
        }

        public Boolean getOutstandingRequest() { return outstandingRequest; }

        public void setOutstandingRequest(Boolean b) { this.outstandingRequest = b; }

        public String getModuleName() { return moduleName; }

        public void setModuleName(String s) { this.moduleName = s; }

        public String getExceptionMessage() { return exceptionMessage; }

        public void setExceptionMessage(String s) { this.exceptionMessage = s; }
    }


}


