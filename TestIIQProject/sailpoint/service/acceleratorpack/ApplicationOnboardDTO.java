/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.acceleratorpack;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.service.ApplicationDTO;
import sailpoint.web.trigger.IdentityProcessingThresholdDTO;

public class ApplicationOnboardDTO {

    private static Log _log = LogFactory.getLog(ApplicationOnboardDTO.class);

    /**
     * Represents the RapidSetup-specific fields in an Application
     */
    ApplicationDTO applicationDTO;

    /**
     * Represents the RapidSetup Configuration values
     */
    Attributes<String,Object> configAttributes;

    Map<String, IdentityProcessingThresholdDTO> thresholdMap;

    /**
     * Whether the values in this DTO can be modified by the user.
     * Right now we send either app-specific settings OR global settings.
     * If we ever need to send both in one DTO, we may need to break this
     * out into two separate properties.
     */
    boolean editable;

    public ApplicationOnboardDTO() { }

    public ApplicationOnboardDTO(ApplicationDTO appDto, Attributes<String,Object> config) {
        this.applicationDTO = appDto;
        this.configAttributes = config;
    }

    public ApplicationDTO getApplicationDTO() {
        return applicationDTO;
    }

    public void setApplicationDTO(ApplicationDTO appDTO) {
        this.applicationDTO = appDTO;
    }

    public Attributes<String, Object> getConfigAttributes() {
        return configAttributes;
    }

    public void setConfigAttributes(Attributes<String, Object> config) {
        this.configAttributes = config;
    }

    public boolean isEditable() {
        return this.editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

	public Map<String, IdentityProcessingThresholdDTO> getThresholdMap() {
		return thresholdMap;
	}

	public void setThresholdMap(Map<String, IdentityProcessingThresholdDTO> thresholdMap) {
		this.thresholdMap = thresholdMap;
	}

}
