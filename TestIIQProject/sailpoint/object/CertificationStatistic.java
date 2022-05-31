/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author peter.holcomb
 */

package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.api.certification.CertificationStatCounter;

/**
 * Used to capture statistics about all of the open certifications that are currently owned/related
 * to a SailPointObject such as an identity/application/group definition.  
 * See sailpoint.api.CertificationService for ways in which these are built and sent to the UI.
 */
public class CertificationStatistic extends AbstractXmlObject {

	private int total;
	private int completed;
	private int delegated;
	private int overdue;
	private int remediationsStarted;
	private int remediationsCompleted;
	
	
	public int getCompleted() {
		return completed;
	}
	
	public void setCompleted(int completed) {
		this.completed = completed;
	}
	
	public int getDelegated() {
		return delegated;
	}
	
	public void setDelegated(int delegated) {
		this.delegated = delegated;
	}
	
	public int getOverdue() {
		return overdue;
	}
	
	public void setOverdue(int overdue) {
		this.overdue = overdue;
	}	
	
	public int getRemediationsCompleted() {
		return remediationsCompleted;
	}
	public void setRemediationsCompleted(int remediationsCompleted) {
		this.remediationsCompleted = remediationsCompleted;
	}
	
	public int getRemediationsStarted() {
		return remediationsStarted;
	}
	
	public void setRemediationsStarted(int remediationsStarted) {
		this.remediationsStarted = remediationsStarted;
	}
	
	public int getTotal() {
		return total;
	}
	
	public void setTotal(int total) {
		this.total = total;
	}
	
	/** Calculated Fields **/
	public int getPercentComplete() {
		return CertificationStatCounter.calculatePercentComplete(getCompleted(), getTotal());
	}
	
	public int getRemaining() {
		return getTotal() - getOverdue() - getCompleted();
	}
	
	
}
