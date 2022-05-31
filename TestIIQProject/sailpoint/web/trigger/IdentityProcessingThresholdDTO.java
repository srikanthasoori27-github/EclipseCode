package sailpoint.web.trigger;

import sailpoint.service.BaseDTO;

public class IdentityProcessingThresholdDTO extends BaseDTO{

	private String identityProcessingThreshold;
	private String identityProcessingThresholdType;
	
	public IdentityProcessingThresholdDTO(){
	}
	
	public IdentityProcessingThresholdDTO(String id, String identityProcessingThreshold, String identityProcessingThresholdType) {
		super();
		this.setId(id);
		this.identityProcessingThreshold = identityProcessingThreshold;
		this.identityProcessingThresholdType = identityProcessingThresholdType;
	}
	
	public String getIdentityProcessingThreshold() {
		return identityProcessingThreshold;
	}
	public void setIdentityProcessingThreshold(String identityProcessingThreshold) {
		this.identityProcessingThreshold = identityProcessingThreshold;
	}
	public String getIdentityProcessingThresholdType() {
		return identityProcessingThresholdType;
	}
	public void setIdentityProcessingThresholdType(String identityProcessingThresholdType) {
		this.identityProcessingThresholdType = identityProcessingThresholdType;
	}
}
