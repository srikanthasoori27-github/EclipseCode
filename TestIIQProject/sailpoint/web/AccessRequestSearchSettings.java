package sailpoint.web;

import sailpoint.object.Identity;

public class AccessRequestSearchSettings {

    private String requestType;
    private String requestId;
    private String requestPriority;
    private long startDate;
    private long endDate;
    private Identity requester;
    private Identity requestee;
    private String groups;
    private String externalTicketId;
    private String requestStatus;

    public AccessRequestSearchSettings() {
    	
    }

	public String getRequestStatus() {
		return requestStatus;
	}

	public void setRequestStatus(String requestStatus) {
		this.requestStatus = requestStatus;
	}

	public String getRequestType() {
		return requestType;
	}

	public void setRequestType(String requestType) {
		this.requestType = requestType;
	}

	public String getRequestPriority() {
		return requestPriority;
	}

	public void setRequestPriority(String requestPriority) {
		this.requestPriority = requestPriority;
	}

	public long getStartDate() {
		return startDate;
	}

	public void setStartDate(long startDate) {
		this.startDate = startDate;
	}

	public long getEndDate() {
		return endDate;
	}

	public void setEndDate(long endDate) {
		this.endDate = endDate;
	}

	public Identity getRequester() {
		return requester;
	}

	public void setRequester(Identity requester) {
		this.requester = requester;
	}

	public Identity getRequestee() {
		return requestee;
	}

	public void setRequestee(Identity requestee) {
		this.requestee = requestee;
	}

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public void setGroups(String groups) {
		this.groups = groups;
	}

	public String getGroups() {
		return this.groups;
	}

    public String getExternalTicketId() {
        return externalTicketId;
    }

    public void setExternalTicketId(String externalTicketId) {
        this.externalTicketId = externalTicketId;
    }
}
