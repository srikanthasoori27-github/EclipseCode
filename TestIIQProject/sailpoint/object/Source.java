/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Constant definitions for "sources", used in several places in the
 * model to record the origin of something.
 *
 * Author: Kelly, Jeff
 *
 * This was originally designed to be passed as a task argument for
 * aggregation and refresh tasks for use in the audit log.  
 * The sources common to all usages are: UI, LCM ARM, and WebService.
 *
 * But we had several other places where a similar source concept was
 * needed and the values were being duplicated with slight name differences.
 * 
 *    RoleAssignment
 *      - records how a role assignment was made includes the ones
 *        above plus "Rule"
 * 
 *    ProvisioningPlan
 *      - for remediation plans, passes information about the certification
 *        or policy violation being remediated
 * 
 * These were merged into the Source enumeration so we could have one place
 * to manage source names and maintain usage comments.  There are two things
 * that are "funny" about this:
 *
 *   - Not all sources are valid for a given use.  For example, you would
 *     never see PolicyViolation as a source for a role assignment.
 *
 *   - Sources are usually represented as Strings rather than enumeration
 *     values.  This makes them easier to pass around in maps and allows
 *     custom sources to be defined if necessary.
 *
 * The first one isn't so bad, defining multiple sources with inheritance
 * or cross referencing is messy for something this simple.
 *
 * The second one argues against this being an enum at all, but it's nice
 * to have a place to hang message keys, conversion methods, etc. 
 *
 * NOTE: We are likely going to need a more complex class like SourceInfo
 * to combine the source constant with other information.  For example
 * with WebService we may want the name of the customer application that
 * called the web service, for Task the name of the TaskDefinition
 * or TaskResult, etc.
 *
 * ProvisioningPlan is already doing this with ARG_SOURCE_ID and 
 * ARG_SOURCE_NAME.
 *
 */

package sailpoint.object;

import java.util.Locale;
import java.util.TimeZone;

import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.xml.XMLClass;
import sailpoint.web.messages.MessageKeys;


/**
 * Constant definitions for "sources", used in several places in the
 * model to record the origin of something.
 */
@XMLClass(xmlname = "SourceType")
public enum Source implements Localizable {

    /**
     * Default value indicating that the source was never set.
     */
    Unknown(MessageKeys.SOURCE_UNKNOWN),

    /**
     * Indicates that the source was the main IdentityIQ user interface.
     */
    UI(MessageKeys.SOURCE_UI),

    /**
     * @deprecated Indicates that the source was ARM. This source is deprecated
     * but might still appear in older logs.
     */
    @Deprecated
    ARM(MessageKeys.SOURCE_ARM),

    /**
     * Indicates that source was LCM.
     */
    LCM(MessageKeys.SOURCE_LCM),

    /**
     * Indicates that source was LCM.
     */
    Batch(MessageKeys.SOURCE_BATCH),

    /**
     * Indicates that source was PAM.
     */
    PAM(MessageKeys.SOURCE_PAM),

    /**
     * Indicates that source was RapidSetup.
     */
    RapidSetup(MessageKeys.SOURCE_RAPIDSETUP),

    /**
     * Indicates that the source was one of the IIQ web service interfaces.
     * A distinction is not being made between SPML, REST, SOAP, etc. 
     * This one is likely to need additional information like 
     * the name of the customer application that is calling the service. 
     */
    WebService(MessageKeys.SOURCE_WEB_SERVICE),

    /**
     * Indicates that the source was a background task.
     */
    Task(MessageKeys.SOURCE_TASK),

    /**
     * Indicates that the source was the aggregation
     * process. Initially added for IdentityEntitlements
     * found during aggregation.
     */
    Aggregation(MessageKeys.SOURCE_AGGREGATION),

    /**
     * Indicates that the source was the target aggregation
     * process.  
     */
    TargetAggregation(MessageKeys.SOURCE_TARGET_AGGREGATION),

    /**
     * Indicates that the source was a workflow.
     */
    Workflow(MessageKeys.SOURCE_WORKFLOW),

    /**
     * Indicates that the source was a Rule
     * This is currently valid only for role assignment sources and
     * indicates that the role was assigned automatically through
     * a rule.
     */
    Rule(MessageKeys.SOURCE_RULE),

    /**
     * Indicates that the source was a Certification.
     * This is used only in provisioning plans generated to handle
     * remediations.
     */
    Certification(MessageKeys.SOURCE_CERT),

    /**
     * Indicates that the source was the remediation of a policy violation.
     * This is used only in provisioning plans generated to handle
     * remediations.
     */
    PolicyViolation(MessageKeys.SOURCE_VIOLATION),

    /**
     * Indicates that the source was set in a role change.
     */
    RoleChangePropagation(MessageKeys.SOURCE_ROLE_CHANGE_PROPAGATION),

    /**
     * Indicates that the source was set during account group editing.
     */
    GroupManagement(MessageKeys.SOURCE_GROUP_MANAGEMENT),

    /**
     * Indicates that the source was set during an identity refresh.
     */
    IdentityRefresh(MessageKeys.SOURCE_IDENTITY_REFRESH);


    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    private String messageKey;
    
    private Source(String messageKey) {
        this.messageKey = messageKey;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public String getLocalizedMessage() {
        return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
    }

    public String getLocalizedMessage(Locale locale, TimeZone timezone) {
        Message msg = new Message(this.messageKey);
        return msg.getLocalizedMessage(locale, timezone);
    }
    
    /**
     * Utility to derive the Source value from a string.
     * This converter is relaxed about case sensitivity
     * so older RoleAssignment sources that normally
     * used all lower case can be auto-upgraded.
     *
     * This will return null and not throw if the string is not the 
     * name of one of the built-in sources. This indicates 
     * that this is a custom source.
     */
    public static Source fromString(String str) {

        Source src = null;

        if (str != null) {
            src = fromStringQuietly(str);
            if (src == null)
                src = fromStringQuietly(str.toUpperCase());

            if (src == null) {
                // old RoleAssignment mappings
                if (str.equals("cert"))
                    src = Certification;
                else if (str.equals("rule"))
                    src = Rule;
            }
        }

        return src;
    }

    private static Source fromStringQuietly(String str) {
        Source src = null;
        try {
            src = Source.valueOf(str);
        }
        catch (java.lang.IllegalArgumentException e) {
        }
        return src;
    }


}
