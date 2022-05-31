/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import java.util.Date;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.CertifiableDescriptor;
import sailpoint.object.CertificationLink;
import sailpoint.object.MitigationExpiration;
import sailpoint.object.PolicyViolation;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
/**
 * Mitigation Expiration Lifecycle Events
 * @author rohit.gupta
 *
 */
public class MitigationExpirationsRuleLibrary {
	private static final String COMMENTS = "MOVER EVENT PROCESSED";
	private static Log mitigationLogger = LogFactory.getLog("rapidapponboarding.rules");
	public static boolean isEligibleForRemdiation(SailPointContext context,
			sailpoint.object.Identity previousIdentity,
			sailpoint.object.Identity newIdentity) throws GeneralException {
		String identityName=null;
		if(newIdentity!=null)
		{
			identityName=newIdentity.getName();
		}
		LogEnablement.isLogDebugEnabled(mitigationLogger,"Enter isEligibleForRemdiation.."+identityName);
		boolean flag = false;
		LogEnablement.isLogDebugEnabled(mitigationLogger,"...Check for  Mitigations.."+identityName);
		List<MitigationExpiration> meList = newIdentity
				.getMitigationExpirations();
		if (meList == null || meList.size() == 0) {
			LogEnablement.isLogDebugEnabled(mitigationLogger,"...Mitigations not found..."+identityName);
			flag = false;
		} else {
			LogEnablement.isLogDebugEnabled(mitigationLogger,"...Mitigations found...."+identityName);
			Date now = new Date();
			for (MitigationExpiration exp : meList) {
				// Handled if the expiration has past and this hasn't been acted
				// upon.
				String comments = exp.getComments();
				CertifiableDescriptor cd = exp.getCertifiableDescriptor();
				CertificationLink clink = exp.getCertificationLink();
				LogEnablement.isLogDebugEnabled(mitigationLogger,"...clink " + clink);
				LogEnablement.isLogDebugEnabled(mitigationLogger,"...comments " + comments);
				PolicyViolation pv = cd.getPolicyViolation();
				LogEnablement.isLogDebugEnabled(mitigationLogger,"...pv " + pv);
				// Make sure it is not a policy violation mitigation allowed
				// within certification or done directly on Policy Violation
				// Make sure it is not processed and it has a certification
				// link, which indicates it is from Mover certification
				// Make sure there are no comments OR comments not equal to
				// MOVER EVENT PROCESSED
				if (null == pv
						&& null != exp
						&& null != exp.getExpiration()
						&& null != clink
						&& (exp.getExpiration().compareTo(now) < 0)
						&& ((comments == null || comments.length() <= 0) || (comments != null && !comments
								.equalsIgnoreCase(MitigationExpirationsRuleLibrary.COMMENTS)))) {
					LogEnablement.isLogDebugEnabled(mitigationLogger,"...Expiration Is Less than Today's Date= "
							+ exp.getExpiration());
					LogEnablement.isLogDebugEnabled(mitigationLogger,"...No Comments on Mitigations Yet");
					// We need to make sure these mitigation expirations are
					// from Mover certification
					// We will do this provisioning plan
					flag = true;
				} else if (null != exp && null != exp.getExpiration()
						&& (exp.getExpiration().compareTo(now) < 0)
						&& comments != null
						&& comments.equalsIgnoreCase(MitigationExpirationsRuleLibrary.COMMENTS)) {
					LogEnablement.isLogDebugEnabled(mitigationLogger,"...Mitigations are processed");
					// Action Could be from someone else
					flag = false;
				} else if (null != exp && null != exp.getExpiration()
						&& exp.getExpiration().compareTo(now) >= 0) {
					LogEnablement.isLogDebugEnabled(mitigationLogger,"...Mitigations Not Expired, We are not ready to process yet");
					flag = false;
				} else if (null != exp && null == exp.getExpiration()) {
					LogEnablement.isLogDebugEnabled(mitigationLogger,"...Mitigations are in endless expiration state");
					flag = false;
				} else {
					LogEnablement.isLogDebugEnabled(mitigationLogger,"...No Mitigations");
					flag = false;
				}
			}
		}
		LogEnablement.isLogDebugEnabled(mitigationLogger,"Exit isEligibleForRemdiation: " + flag+"...."+identityName);
		return flag;
	}
}
