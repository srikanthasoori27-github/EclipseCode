/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Source;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.server.Auditor;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
/**
 * ProvisioningRequest can be used for any ticketing integration
 *
 * @author rohit.gupta
 *
 */
public class AuditRuleLibrary {
	private static Log auditLogger = LogFactory
			.getLog("rapidapponboarding.rules");
	/**
	 * List of Request Types
	 */
	// LifeCycleEvents - Start
	private static final String ATTRIBUTE_SYNCHRONIZATION_FEATURE = "ATTRIBUTE SYNCHRONIZATION FEATURE";
	private static final String MANAGER_CERTIFICATION_ALLOW_EXCEPTION_FEATURE = "MANAGER CERTIFICATION ALLOW EXCEPTION FEATURE";
	private static final String NATIVE_CHANGE_DETECTION_FEATURE = "NATIVE CHANGE DETECTION FEATURE";
	private static final String SER_LINK_FEATURE = "SER LINK FEATURE";
	// LifeCycleEvents - End
	// Interactive Request - Start
	private static final String CART_REQUEST_FEATURE = "CART REQUEST FEATURE";
	private static final String MANAGE_ACCOUNTS_FEATURE = "MANAGE ACCOUNTS FEATURE";
	private static final String CREATE_IDENTITY_FEATURE = "CREATE IDENTITY FEATURE";
	private static final String EDIT_IDENTITY_FEATURE = "EDIT IDENTITY FEATURE";
	private static final String CHANGE_PASSWORD_FEATURE = "CHANGE PASSWORD FEATURE";
	private static final String MANAGE_PASSWORDS_FEATURE = "MANAGE PASSWORDS FEATURE";
	private static final String REGISTRATION_FEATURE = "REGISTRATION FEATURE";
	private static final String FIREFIGHTER_FEATURE ="FIREFIGHTER FEATURE";
	// Interactive Request - End
	// Operational Request - Start
	private static final String SER_UNLINK_FEATURE = "SER UNLINK FEATURE";
	private static final String RECOVERY_TOOL_FEATURE = "RECOVERY TOOL FEATURE";
	private static final String IMMEDIATE_TERMINATION_FEATURE = "IMMEDIATE TERMINATION FEATURE";
	// Operational Request - End
	// BackGround Request - Start
	private static final String PASSWORD_INTERCEPTOR_FEATURE = "PASSWORD INTERCEPTOR FEATURE";
	private static final String PRIVILEGED_SERVICE_PASSWORD_EXPIRATION_FEATURE = "PRIVILEGED SERVICE PASSWORD EXPIRATION FEATURE";
	private static final String REQUEST_MANAGER_FEATURE = "REQUEST MANAGER FEATURE";
	// BackGround Request - End
	// Webservice Request - Start
	private static final String EXTERNAL_FEATURE = "EXTERNAL FEATURE";
	// Webservice Request - End
	// Audit Events Start
	private static final String AUDIT_LIFECYCLE_EVENT = "roadLifeCycleEventRequest";
	private static final String AUDIT_LIFECYCLE_CERTIFICATION_EVENT = "roadCertificationLifeCycleEventRequest";
	private static final String AUDIT_INTERACTIVE = "roadInteractiveRequest";
	private static final String AUDIT_OPERATIONAL = "roadOperationalRequest";
	private static final String AUDIT_BACKGROUNDSERVICE = "roadBackGroundServiceRequest";
	private static final String AUDIT_ROLEENTASSIGNMENT = "roadRoleEntitlementAssignmentsRequest";
	private static final String AUDIT_POLICYVIOLATON = "roadPolicyViolationDeprovisioningRequest";
	private static final String AUDIT_CERTREVOCATION = "roadCertificationRemediation";
	private static final String AUDIT_WEBSERVICE = "roadWebservicesRequest";
	/**
	 * List of flow values
	 */
	/**
	 * private static final String ROLES_REQUEST = "AccessRequest"; private
	 * static final String ACCOUNTS_REQUEST = "AccountsRequest"; private static
	 * final String EXPIRE_PASSWORD = "ExpirePassword"; private static final
	 * String FORGOT_PASSWORD = "ForgotPassword"; private static final String
	 * PASSWORDS_REQUEST = "PasswordsRequest"; private static final String
	 * IDENTITY_CREATE_REQUEST = "IdentityCreateRequest"; private static final
	 * String IDENTITY_EDIT_REQUEST = "IdentityEditRequest"; private static
	 * final String RESGISTRATION = "Registration"; private static final String
	 * EXTERNAL = "External"; private static final String LIFECYCLE =
	 * "Lifecycle"; private static final String RECOVERY = "Recovery"; private
	 * static final String INTERCEPTOR = "Interceptor"; private static final
	 * String LEAVER_X_DAYS = "LeaverXDays"; private static final String
	 * PASSWORD_EXPIRATION = "PasswordExpiration"; private static final String
	 * FORM_ROLE = "FormRole"; private static final String
	 * SERVICE_ACCOUNT_OWNERS = "ServiceAccountOwners";
	 */
	/**
	 * Audit Accelerator Pack Events For Provisioning Projects Internal IIQ Provisioning,
	 * and Ticket Provisioning
	 *
	 * @param context
	 * @param plan
	 * @param planResult
	 * @param application
	 * @param auditEvent
	 * @param identityName
	 * @param certificactionEventType
	 * @param certName
	 * @throws Exception
	 */
	public static void logRoadAuditEvents(SailPointContext context,
			ProvisioningProject provisioningProject,
			ProvisioningResult planResult, Object application,
			String identityName, String certificactionEventType,
			String certName, String launcher) throws Exception {
		LogEnablement.isLogDebugEnabled(auditLogger,"Start logRoadAuditEvents...");
		String uniqueId = Util.uuid();
		LogEnablement.isLogDebugEnabled(auditLogger,"Start uniqueId..."+uniqueId);
		ProvisioningProject clonedProject = null;
		if (provisioningProject != null) {
			LogEnablement.isLogDebugEnabled(auditLogger,"Deep Copy Start...");
			clonedProject = (ProvisioningProject) provisioningProject
					.deepCopy(context);
			LogEnablement.isLogDebugEnabled(auditLogger,"Deep Copy End...");
			List<ProvisioningPlan> plans = clonedProject.getPlans();
			if (identityName == null)
			{
				LogEnablement.isLogDebugEnabled(auditLogger,"Get Identity Start...");
				identityName = clonedProject.getIdentity();
				LogEnablement.isLogDebugEnabled(auditLogger,"Get Identity End...");
			}
			LogEnablement.isLogDebugEnabled(auditLogger,"Start identityName..."+identityName);
			ObjectUtil.scrubPasswords(clonedProject);
			LogEnablement.isLogDebugEnabled(auditLogger,"Start scrubPasswords...");
			if (plans != null) {
				for (ProvisioningPlan plan : plans) {
					if (plan != null) {
						String targetIntegration = plan.getTargetIntegration();
						LogEnablement.isLogDebugEnabled(auditLogger,"targetIntegration "
								+ targetIntegration);
						boolean isPlanTicketItegration = false;
						boolean isDirectConnectorApplication = false;
						boolean isManual = false;
						boolean isIIQ = false;
						if (targetIntegration != null) {
							isDirectConnectorApplication = ROADUtil
									.isDirectConnectorApplication(context,
											targetIntegration);
							LogEnablement.isLogDebugEnabled(auditLogger,"isDirectConnectorApplication "
									+ isDirectConnectorApplication);
						}
						if (isDirectConnectorApplication) {
							// This needs to be logged in after provisioning
							// rule
							continue;
						}
						if (targetIntegration != null) {
							isPlanTicketItegration = ROADUtil
									.isTicketIntegrationPlan(context,
											targetIntegration);
							LogEnablement.isLogDebugEnabled(auditLogger,"isPlanTicketItegration "
									+ isPlanTicketItegration);
						}
						List<AccountRequest> accountRequests = plan
								.getAccountRequests();
						if (accountRequests != null && !isPlanTicketItegration) {
							for (AccountRequest accountRequest : accountRequests) {
								String appName = accountRequest
										.getApplicationName();
								LogEnablement.isLogDebugEnabled(auditLogger,"appName " + appName);
								if (appName != null
										&& appName
												.equalsIgnoreCase(ProvisioningPlan.APP_IIQ)
										|| appName
												.equalsIgnoreCase(ProvisioningPlan.IIQ_APPLICATION_NAME)
										|| appName
												.equalsIgnoreCase(ProvisioningPlan.APP_IDM)) {
									LogEnablement.isLogDebugEnabled(auditLogger,"isIIQ ");
									break;
								} else if (appName != null) {
									isManual = ROADUtil
											.isManualWorkItemApplication(
													context, appName);
									LogEnablement.isLogDebugEnabled(auditLogger,"isManual " + isManual);
									break;
								}
							}
						}
						if (isIIQ || isManual || isPlanTicketItegration) {
							logRoadAuditEvents(context, plan, planResult,
									application, identityName,
									certificactionEventType, certName,
									launcher, false, uniqueId);
						}
					}
				}
			}
		}
	}
	/**
	 * Audit Accelerator Pack Events For Provisioning Plan
	 *
	 * @param context
	 * @param plan
	 * @param planResult
	 * @param application
	 * @param auditEvent
	 * @param identityName
	 * @param certificactionEventType
	 * @param certName
	 * @param scrubPlan
	 * @param uniqueId
	 * @throws Exception
	 */
	public static void logRoadAuditEvents(SailPointContext context,
			ProvisioningPlan plan, ProvisioningResult planResult,
			Object application, String identityName,
			String certificactionEventType, String certName, String launcher,
			boolean scrubPlan, String uniqueId) throws Exception {
		LogEnablement.isLogDebugEnabled(auditLogger,"Enter: logRoadAuditEvents");
		String auditEvent = null;
		if (plan == null && planResult == null && application == null
				&& identityName != null && context != null) {
			if (certificactionEventType != null
					&& (certificactionEventType
							.equalsIgnoreCase(MoverRuleLibrary.MOVERFEATURE) || certificactionEventType
							.equalsIgnoreCase(AuditRuleLibrary.NATIVE_CHANGE_DETECTION_FEATURE))) {
				if (launcher != null
						&& (launcher.equalsIgnoreCase("Scheduler") || launcher
								.equalsIgnoreCase("RequestHandler"))) {
					auditEvent = AuditRuleLibrary.AUDIT_LIFECYCLE_CERTIFICATION_EVENT;
				} else if (launcher != null) {
					auditEvent = AuditRuleLibrary.AUDIT_OPERATIONAL;
				} else if (launcher == null) {
					auditEvent = AuditRuleLibrary.AUDIT_LIFECYCLE_CERTIFICATION_EVENT;
				}
				if (Auditor.isEnabled(auditEvent)) {
					LogEnablement.isLogDebugEnabled(auditLogger,"Auditor Enabled");
					AuditEvent event = new AuditEvent();
					// Set Audit Action same as Audit Event Name
					String action = auditEvent;
					event.setAction(action);
					// Set Instance
					if (certificactionEventType != null
							&& certificactionEventType.length() > 0) {
						event.setInstance(certificactionEventType);
					}
					// Set Target
					if (identityName != null) {
						event.setTarget(identityName);
					}
					// Set Certification Name
					if (certName != null) {
						event.setAttribute("CertificationName", certName);
					}
					if (launcher != null) {
						event.setSource(launcher);
					}
					LogEnablement.isLogDebugEnabled(auditLogger,"Logging: Audit Event For Certs");
					LogEnablement.isLogDebugEnabled(auditLogger,"Log Audit Start");
					Auditor.log(event);
					LogEnablement.isLogDebugEnabled(auditLogger,"Log Audit End");
					context.commitTransaction();
				}
			}
		} else {
			// Let's scrub passwords first
			ProvisioningPlan clonedPlan = null;
			if (scrubPlan) {
				LogEnablement.isLogDebugEnabled(auditLogger,"Scrub Plan Start");
				clonedPlan = (ProvisioningPlan) plan.deepCopy(context);
				ObjectUtil.scrubPasswords(clonedPlan);
				LogEnablement.isLogDebugEnabled(auditLogger,"Scrub Plan End");
			} else {
				// Project is already scrubed
				clonedPlan = plan;
			}
			StringBuilder allProvisioningErrors = new StringBuilder();
			StringBuilder allProvisioningWarnings = new StringBuilder();
			StringBuilder allProvisioningTicketIds = new StringBuilder();
			StringBuilder allProvisioningStatuses = new StringBuilder();
			String planTargetIntegration = null;
			String planTicketId = null;
			String planError = null;
			String planResultStatus = null;
			AuditEvent event = null;
			// Find Requestor, Identity Request Id, Source, Requestor, and
			// RequestType- Start
			String source = null;
			String identityRequestId = null;
			String requester = null;
			String requestType = null;
			String flow = null;
			if (clonedPlan != null
					&& clonedPlan instanceof sailpoint.object.ProvisioningPlan) {
				Attributes attributes = ((sailpoint.object.ProvisioningPlan) clonedPlan)
						.getArguments();
				if (attributes != null) {
					identityRequestId = (String) attributes
							.get("identityRequestId");
					requestType = (String) attributes.get("requestType");
					requester = (String) attributes.get("requester");
					source = (String) attributes.get("source");
					flow = (String) attributes.get("flow");
                    if (launcher == null || launcher.length() < 0) {
						launcher = (String) attributes.get("launcher");
                    }

                    if (requestType != null) {
                        if (("RequestHandler".equalsIgnoreCase(requester) || "Scheduler".equalsIgnoreCase(requester) ||
                            ("RequestHandler".equalsIgnoreCase(launcher) || "Scheduler".equalsIgnoreCase(launcher))) &&
                             (JoinerRuleLibrary.JOINERFEATURE.equalsIgnoreCase(requestType) ||
                              JoinerRuleLibrary.JOINERLOAFEATURE.equalsIgnoreCase(requestType) ||
                              JoinerRuleLibrary.JOINERLTDFEATURE.equalsIgnoreCase(requestType) ||
                              JoinerRuleLibrary.JOINERREHIREFEATURE.equalsIgnoreCase(requestType) ||
                              LeaverRuleLibrary.LEAVERFEATURE.equalsIgnoreCase(requestType) ||
                              LeaverRuleLibrary.LOAFEATURE.equalsIgnoreCase(requestType) ||
                              LeaverRuleLibrary.LTDFEATURE.equalsIgnoreCase(requestType) ||
                              ReverseLeaverRuleLibrary.REVERSELEAVERFEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.ATTRIBUTE_SYNCHRONIZATION_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.MANAGER_CERTIFICATION_ALLOW_EXCEPTION_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.NATIVE_CHANGE_DETECTION_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.SER_LINK_FEATURE.equalsIgnoreCase(requestType))) {
                                 auditEvent = AuditRuleLibrary.AUDIT_LIFECYCLE_EVENT;
                        } else if (AuditRuleLibrary.CART_REQUEST_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.MANAGE_ACCOUNTS_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.CREATE_IDENTITY_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.EDIT_IDENTITY_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.CHANGE_PASSWORD_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.MANAGE_PASSWORDS_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.REGISTRATION_FEATURE.equalsIgnoreCase(requestType)  ||
                              AuditRuleLibrary.FIREFIGHTER_FEATURE.equalsIgnoreCase(requestType)) {
                                 auditEvent = AuditRuleLibrary.AUDIT_INTERACTIVE;
                        } else if (AuditRuleLibrary.RECOVERY_TOOL_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.SER_UNLINK_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.IMMEDIATE_TERMINATION_FEATURE.equalsIgnoreCase(requestType) ||
                              JoinerRuleLibrary.JOINERLOAFEATURE.equalsIgnoreCase(requestType) ||
                              JoinerRuleLibrary.JOINERLTDFEATURE.equalsIgnoreCase(requestType) ||
                              JoinerRuleLibrary.JOINERREHIREFEATURE.equalsIgnoreCase(requestType) ||
                              LeaverRuleLibrary.LEAVERFEATURE.equalsIgnoreCase(requestType) ||
                              LeaverRuleLibrary.LTDFEATURE.equalsIgnoreCase(requestType) ||
                              ReverseLeaverRuleLibrary.REVERSELEAVERFEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.ATTRIBUTE_SYNCHRONIZATION_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.MANAGER_CERTIFICATION_ALLOW_EXCEPTION_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.NATIVE_CHANGE_DETECTION_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.SER_LINK_FEATURE.equalsIgnoreCase(requestType)) {
                                 auditEvent = AuditRuleLibrary.AUDIT_OPERATIONAL;
                        } else if (AuditRuleLibrary.PASSWORD_INTERCEPTOR_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.PRIVILEGED_SERVICE_PASSWORD_EXPIRATION_FEATURE.equalsIgnoreCase(requestType) ||
                              AuditRuleLibrary.REQUEST_MANAGER_FEATURE.equalsIgnoreCase(requestType)) {
                                 auditEvent = AuditRuleLibrary.AUDIT_BACKGROUNDSERVICE;
                        } else if (AuditRuleLibrary.EXTERNAL_FEATURE.equalsIgnoreCase(requestType)) {
                            auditEvent = AuditRuleLibrary.AUDIT_WEBSERVICE;
                        }
                    } else if (source != null) {
                        if (source.equalsIgnoreCase(Source.Certification.toString())) {
                            auditEvent = AuditRuleLibrary.AUDIT_CERTREVOCATION;
                        } else if (source.equalsIgnoreCase(Source.IdentityRefresh.toString())) {
                            // Role Assignments
                            // Entitlement Assignments
                            auditEvent = AuditRuleLibrary.AUDIT_ROLEENTASSIGNMENT;
                        } else if (source.equalsIgnoreCase(Source.PolicyViolation.toString())) {
                            auditEvent = AuditRuleLibrary.AUDIT_POLICYVIOLATON;
                        } else if (source.equalsIgnoreCase(Source.Rule.toString())) {
                            // Role Assignments
                            // Entitlement Assignments
                            auditEvent = AuditRuleLibrary.AUDIT_ROLEENTASSIGNMENT;
                        } else if (source.equalsIgnoreCase(Source.Task.toString())) {
                            // BackGround Service
                            auditEvent = AuditRuleLibrary.AUDIT_BACKGROUNDSERVICE;
                        }
                    }
                }
            }
			// Find Requestor, Identity Request Id, Source, Requestor, and
			// RequestType- End
			if (clonedPlan != null && Auditor.isEnabled(auditEvent)) {
				/**
				 * Find Plan Target Integration Start
				 */
				LogEnablement.isLogDebugEnabled(auditLogger,"Audit Enabled");
				planTargetIntegration = ((sailpoint.object.ProvisioningPlan) clonedPlan)
						.getTargetIntegration();
				/**
				 * Find Plan Target Integration End
				 */
				/**
				 * Find Plan Provisioning Result Start
				 */
				if (planResult == null) {
					LogEnablement.isLogDebugEnabled(auditLogger,"Plan Result");
					planResult = ((sailpoint.object.ProvisioningPlan) clonedPlan)
							.getResult();
					if (planResult != null && planResult.getStatus() != null) {
						planResultStatus = planResult.getStatus().toString();
					}
				}
				/**
				 * Find Plan Provisioning Result End
				 */
				/**
				 * Find Plan Ticket Id Start
				 */
				if (planResult != null) {
					planTicketId = planResult.getRequestID();
				}
				/**
				 * Find Plan Ticket Id End
				 */
				/**
				 * Find Plan Provisioning Result Error Start
				 */
				if (planResult != null && planResult.getErrors() != null) {
					planError = planResult.getErrors().toString();
				}
				/**
				 * Find Plan Provisioning Result Error End
				 */
				// Find Errors , Warnings, Ticket Id's, and Result Status -
				// Start
				if (planResult != null && planResult.getErrors() != null) {
					List<Message> planErrors = planResult.getErrors();
					if (planErrors != null) {
						for (Message message : planErrors) {
							if (allProvisioningErrors.length() > 0) {
								allProvisioningErrors.append(", ");
							}
							allProvisioningErrors.append(message);
						}
					}
				}
				if (planResult != null && planResult.getWarnings() != null) {
					List<Message> planWarnings = planResult.getWarnings();
					if (planWarnings != null) {
						for (Message message : planWarnings) {
							if (allProvisioningWarnings.length() > 0) {
								allProvisioningWarnings.append(", ");
							}
							allProvisioningWarnings.append(message);
						}
					}
				}
				if (planResult != null && planResult.getRequestID() != null) {
					if (allProvisioningTicketIds.length() > 0) {
						allProvisioningTicketIds.append(", ");
					}
					allProvisioningTicketIds.append(planResult.getRequestID());
				}
				if (planResult != null && planResult.getStatus() != null) {
					if (allProvisioningStatuses.length() > 0) {
						allProvisioningStatuses.append(", ");
					}
					allProvisioningStatuses.append(planResult.getStatus());
				}
				List accountsRequests = ((sailpoint.object.ProvisioningPlan) clonedPlan)
						.getAccountRequests();
				for (Object o : accountsRequests) {
					if (o instanceof ProvisioningPlan.AccountRequest) {
						ProvisioningResult accountRequestResult = ((ProvisioningPlan.AccountRequest) o)
								.getResult();
						if (accountRequestResult != null
								&& accountRequestResult.getErrors() != null) {
							List<Message> actRequestErrors = accountRequestResult
									.getErrors();
							if (actRequestErrors != null) {
								for (Message message : actRequestErrors) {
									if (allProvisioningErrors.length() > 0) {
										allProvisioningErrors.append(", ");
									}
									allProvisioningErrors.append(message);
								}
							}
						}
						if (accountRequestResult != null
								&& accountRequestResult.getWarnings() != null) {
							List<Message> actRequestWarnings = accountRequestResult
									.getWarnings();
							if (actRequestWarnings != null) {
								for (Message message : actRequestWarnings) {
									if (allProvisioningWarnings.length() > 0) {
										allProvisioningWarnings.append(", ");
									}
									allProvisioningWarnings.append(message);
								}
							}
						}
						if (accountRequestResult != null
								&& accountRequestResult.getRequestID() != null) {
							if (allProvisioningTicketIds.length() > 0) {
								allProvisioningTicketIds.append(", ");
							}
							allProvisioningTicketIds
									.append(accountRequestResult.getRequestID());
						}
						if (accountRequestResult != null
								&& accountRequestResult.getStatus() != null) {
							if (allProvisioningStatuses.length() > 0) {
								allProvisioningStatuses.append(", ");
							}
							allProvisioningStatuses.append(accountRequestResult
									.getStatus());
						}
						List<AttributeRequest> attrRequests = ((ProvisioningPlan.AccountRequest) o)
								.getAttributeRequests();
						if (attrRequests != null && attrRequests.size() > 0) {
							for (AttributeRequest attrRequest : attrRequests) {
								ProvisioningResult attrResult = attrRequest
										.getResult();
								if (attrResult != null
										&& attrResult.getErrors() != null) {
									List<Message> attrErrors = attrResult
											.getErrors();
									if (attrErrors != null) {
										for (Message message : attrErrors) {
											if (allProvisioningErrors.length() > 0) {
												allProvisioningErrors
														.append(", ");
											}
											allProvisioningErrors
													.append(message);
										}
									}
								}
								if (attrResult != null
										&& attrResult.getWarnings() != null) {
									List<Message> attrRequestWarnings = attrResult
											.getWarnings();
									if (attrRequestWarnings != null) {
										for (Message message : attrRequestWarnings) {
											if (allProvisioningWarnings
													.length() > 0) {
												allProvisioningWarnings
														.append(", ");
											}
											allProvisioningWarnings
													.append(message);
										}
									}
								}
								if (attrResult != null
										&& attrResult.getRequestID() != null) {
									if (allProvisioningTicketIds.length() > 0) {
										allProvisioningTicketIds.append(", ");
									}
									allProvisioningTicketIds.append(attrResult
											.getRequestID());
								}
								if (attrResult != null
										&& attrResult.getStatus() != null) {
									if (allProvisioningStatuses.length() > 0) {
										allProvisioningStatuses.append(", ");
									}
									allProvisioningStatuses.append(attrResult
											.getStatus());
								}
							}
						}
					}
				}
				// Find Errors, Ticket Id's. and Result Status - End
				// Find Requestor - Start
				List requesters = new ArrayList();
				if (requester != null && requester.length() >= 0) {
					requesters.add(requester);
				} else {
					List<Identity> requestersfromPlan = ((sailpoint.object.ProvisioningPlan) clonedPlan)
							.getRequesters();
					if (requestersfromPlan != null) {
						for (Identity req : requestersfromPlan) {
							if (req != null)
								requesters.add(req.getName());
						}
					}
				}
				// Find Requestor - End
				// Find Target Identity - Start
				String targetIdentity = null;
				if (identityName != null && identityName.length() > 0) {
					targetIdentity = identityName;
				}
				if (targetIdentity == null) {
					Identity targetIdentityOb = ((ProvisioningPlan) clonedPlan)
							.getIdentity();
					if (targetIdentityOb != null) {
						targetIdentity = targetIdentityOb.getName();
					}
				}
				if (targetIdentity == null) {
					if (((ProvisioningPlan) clonedPlan).getNativeIdentity() != null) {
						targetIdentity = ((ProvisioningPlan) clonedPlan)
								.getNativeIdentity();
					}
				}
				// Find Target Identity - End
				List accounts = ((sailpoint.object.ProvisioningPlan) clonedPlan)
						.getAccountRequests();
				if (accounts != null) {
					for (Object o : accounts) {
						// Check to make sure this is an account request and not
						// something else
						if (o instanceof ProvisioningPlan.AccountRequest
								|| o instanceof sailpoint.integration.ProvisioningPlan.AccountRequest) {
							// Create New Audit Event for Each Account Request
							event = new AuditEvent();
							// Set Interface to tie all account requests within
							// a provisioning project
							if (uniqueId != null) {
								event.setInterface(uniqueId);
							}
							LogEnablement.isLogDebugEnabled(auditLogger,"Set Unique Id");
							// Set Audit Action same as Audit Event Name
							String action = auditEvent;
							event.setAction(action);
							// Set Plan Target Integration as String 1
							if (planTargetIntegration != null
									&& planTargetIntegration.length() > 0) {
								event.setString1("Target Integration:"
										+ planTargetIntegration);
							}
							// Set Plan Result Status as String 2
							if (planResultStatus != null
									&& planResultStatus.length() > 0) {
								event.setString2(planResultStatus);
							} else if (allProvisioningStatuses != null) {
								event.setString2(allProvisioningStatuses
										.toString());
							}
							// Set Plan Ticket Id as String 3
							if (planTicketId != null
									&& planTicketId.length() > 0) {
								event.setString3(planTicketId);
							}
							// Set Request Id as String 4
							if (identityRequestId != null
									&& identityRequestId.length() > 0) {
								event.setString4(identityRequestId);
							}
							// Set Plan Error From as an "Error" Attribute
							if (planError != null && planError.length() > 0) {
								event.setAttribute("Error", planError);
							}
							// Set Application Name
							if (application != null
									&& application instanceof sailpoint.object.Application) {
								event.setApplication(((Application) application)
										.getName());
							} else if (application != null) {
								event.setApplication((String) application);
							}
							// Set Target
							if (targetIdentity != null) {
								event.setTarget(targetIdentity);
							}
							// Set Instance
							if (requestType != null && requestType.length() > 0) {
								event.setInstance(requestType);
							}
							// Set Source
							if (source != null && source.length() > 0) {
								event.setAttribute("Source", source);
							}
							// Set Flow
							if (flow != null && flow.length() > 0) {
								event.setAttribute("Flow", flow);
							}
							// Set Requester
							if (requesters != null && requesters.size() > 0) {
								event.setSource((String) requesters.get(0));
							}
							// Set All Errors
							if (allProvisioningErrors != null
									&& allProvisioningErrors.length() > 0) {
								event.setAttribute("csvErrors",
										allProvisioningErrors.toString());
							}
							// Set All Warnings
							if (allProvisioningWarnings != null
									&& allProvisioningWarnings.length() > 0) {
								event.setAttribute("csvWarnings",
										allProvisioningWarnings.toString());
							}
							// Set All Ticket Ids
							if (allProvisioningTicketIds != null
									&& allProvisioningTicketIds.length() > 0) {
								event.setAttribute("csvTicketIds",
										allProvisioningTicketIds.toString());
							}
							// Set All Statuses
							if (allProvisioningStatuses != null
									&& allProvisioningStatuses.length() > 0) {
								event.setAttribute("csvStatuses",
										allProvisioningStatuses.toString());
							}
							Object accountRequest = null;
							accountRequest = o;
							// Set Operation
							if (((AccountRequest) accountRequest)
									.getOperation() != null) {
								String operation = ((AccountRequest) accountRequest)
										.getOperation().toString();
								if (operation != null && operation.length() > 0) {
									event.setAttribute("Operation", operation);
								}
								// Multiple Account Requests within a
								// provisioning
								if (application == null
										&& ((AccountRequest) accountRequest)
												.getApplicationName() != null) {
									event.setApplication(((AccountRequest) accountRequest)
											.getApplicationName());
								}
							}
							// Set Account Name
							if (accountRequest != null
									&& ((AccountRequest) accountRequest)
											.getNativeIdentity() != null) {
								event.setAccountName(((AccountRequest) accountRequest)
										.getNativeIdentity());
							}
							if (accountRequest != null) {
								List<AttributeRequest> attributeRequests = ((AccountRequest) accountRequest)
										.getAttributeRequests();
								if (attributeRequests != null && attributeRequests.size() > 0) {
								StringBuilder auditAttrRequests = new StringBuilder();
								for (AttributeRequest attrRequest : attributeRequests) {
									if (auditAttrRequests.length() > 0) {
										auditAttrRequests.append("||");
									}
									Operation attrOp = attrRequest
											.getOperation();
									if (attrOp != null) {
										String op = attrOp.toString();
										if (op != null) {
											auditAttrRequests.append(op);
											auditAttrRequests.append(", ");
										}
									}
									String attrName = attrRequest.getName();
									if (attrName != null) {
										auditAttrRequests.append(attrName);
										auditAttrRequests.append(", ");
									}
									Object attrValue = attrRequest.getValue();
									if (attrValue != null) {
										auditAttrRequests.append(attrValue);
									} else {
										auditAttrRequests
												.append("Empty or Scrubed");
									}
									if (auditAttrRequests != null
											&& auditAttrRequests.length() > 0) {
										event.setAttribute(
												"csvAttributeRequests",
												auditAttrRequests.toString());
									}
								}
								}
							}
							// Log Audit Event
							if (event != null) {
								LogEnablement.isLogDebugEnabled(auditLogger,"Log Audit");
								Auditor.log(event);
							}
						}
					}// End of casting Object to accountRequest
				}// end of account request null check
			}// end of plan and application null check
			if (event != null && context != null) {
				context.commitTransaction();
			}
		}
		LogEnablement.isLogDebugEnabled(auditLogger,"Exit: logRoadAuditEvents");
	}
}
