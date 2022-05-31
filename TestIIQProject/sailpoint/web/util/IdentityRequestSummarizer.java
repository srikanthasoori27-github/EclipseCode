/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.util.List;
import java.util.Map;

import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.WorkItem;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * 
 * Utility class with a few static methods that can be used
 * to render an IdentityRequest object to HTML. Originally
 * designed so we could easily transform the data encapsulated
 * in an IdentityRequest object into simple html tables for 
 * use in Remedy/SRM ticket field elements that support html.  
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * 
 * TODO: 
 * 
 * Internationalization - allow a local to be passed in
 *
 */
public class IdentityRequestSummarizer {
    
    /**
     * Application mapping for use if we want to map the IIQ application names
     * to other environement specific strings.  This can be added to the
     * options Map handed to each static call and the value should be a 
     * Map<String,Object>.
     * 
     */
    public static String OP_APPLICATION_MAPPING = "applicationMapping";    

    ///////////////////////////////////////////////////////////////////////////
    //
    // HTML Rendering
    // 
    /////////////////////////////////////////////////////////////////////////
    
    /**
     * Summaries the request including all of the items that were 
     * requested and the top level properties store on the IdentityRequest
     * object.
     * 
     * @return string of html including a table
     */
    public static String summarizeItemsHTML(IdentityRequest req, Map<String,Object> options ) {
        
        StringBuffer sb = new StringBuffer();
        sb.append("<![CDATA[");        
        sb.append("<h4>Request Summary</h4>");
        sb.append("<table>");
        addItemRow(sb, "Requested For", req.getTargetDisplayName());
        addItemRow(sb, "Requested Type", req.getType());
        addItemRow(sb, "Requested On", Util.dateToString(req.getCreated()));
        addItemRow(sb, "Requested By",  req.getRequesterDisplayName());
        addItemRow(sb, "Request ID", req.getName());
        
        WorkItem.Level propority = req.getPriority();
        String priority = ( propority != null ) ? propority.toString() : WorkItem.Level.Normal.toString();            
        addItemRow(sb, "Priority",  priority);
        sb.append("</table>");

        // This will build its own indented table
        for ( IdentityRequestItem item : req.getItems() ) {               
            summarizeItemHTML(item, sb, true, true, options);
            sb.append("<br>");
        }
        sb.append("]]>");
        return sb.toString();
    }

    /**
     * Summarize each item in the request and display
     * its approval status.
     * 
     * @return html formatted string of the summary
     */
    public static String summarizeItemApprovalsHTML(IdentityRequest req, Map<String,Object> options ) {
        
        StringBuffer sb = new StringBuffer();       
        sb.append("<![CDATA[");
        sb.append("<h4>Approval Summary</h4>");
        
        List<IdentityRequestItem> items = req.getItems();
        if ( req.getItems() != null ) {
            for ( IdentityRequestItem item : items ) {
                if ( item.isExpansion() )
                    continue;
                
                summarizeItemHTML(item, sb, false, false, options);
                String approvalState = "<p style='color:green'>Approved</p>";
                if ( item.isRejected() ) {
                    approvalState = "<p style='color:red'>Rejected</p>";
                }
                addItemRow(sb, "ApprovalStatus" , approvalState);
                if ( item.isApproved() ) {
                    if ( item.getApproverName() == null )
                        addItemRow(sb, "Approved By", "SELF");
                    else
                        addItemRow(sb, "Approved By", item.getApproverName());
                } else {
                    addItemRow(sb, "Rejected By", item.getApproverName());
                }
                sb.append("</table>");
                sb.append("<br>");
            }
        }
        sb.append("]]>");
        return sb.toString();        
    }
    
    /**
     * 
     * 
     * @return String of html summarizing the request
     */
    public static String summarizeItemProvisioningHTML(IdentityRequest req, Map<String,Object> options ) {
        StringBuffer sb = new StringBuffer();
        
        List<IdentityRequestItem> items = req.getItems();
        if ( items != null  ) {
            sb.append("<![CDATA[");
            sb.append("<h4>Provisioning Summary</h4>");
            for ( IdentityRequestItem item : items ) {      
                if ( item.isRejected() )
                    continue;                
                summarizeItemHTML(item, sb, false, false, options);
                if ( item.getProvisioningEngine() != null )                    
                    addItemRow(sb, "Provisioning Engine" , mapApplication(options, item.getProvisioningEngine()));
                if ( item.getProvisioningState() != null )
                    addItemRow(sb, "Provisioning State" , item.getProvisioningState().toString());
                
                if ( item.getRetries() > 0 )
                    addItemRow(sb, "Retries" , Util.otoa(item.getRetries()));
                sb.append("</table>");
                sb.append("<br>");
            }
            sb.append("]]>");
        }      
        return sb.toString();
    } 
    
    /**
     * Build an HTML table summarizing the results including things
     * like the completion date, status and any errors or warnings
     * that might have been stored.
     * 
     * @return String representing HTML to store in a ticket
     * 
     */
    public static String summarizeCompleteHTML(IdentityRequest req, Map<String,Object> options ) {        
        StringBuffer sb = new StringBuffer();
        
        List<IdentityRequestItem> items = req.getItems();
        if ( items != null  ) {
            sb.append("<![CDATA[");
            sb.append("<h4>Completion Summary</h4>");
            sb.append("<table>");
            
            if ( !req.isTerminated() )
                addItemRow(sb, "Completion Status", req.getCompletionStatus().toString());
        
            if ( req.getEndDate() != null )
                addItemRow(sb, "End Date", Util.dateToString(req.getEndDate()));
            
            List<Message> errors = req.getErrors();
            if ( errors != null ) {
                for ( Message error : errors ) {
                    addItemRow(sb, "Error(s)", error.getLocalizedMessage());
                }
            }       
            List<Message> warnings = req.getWarnings();
            if ( warnings != null ) {
                for ( Message warning : warnings ) {
                    addItemRow(sb, "Warning(s)", warning.getLocalizedMessage());                    
                }
            }    
            sb.append("</table>");
            sb.append("]]>");
        }        
        return sb.toString();
    }     

    protected static void summarizeItemHTML(IdentityRequestItem item, 
                                            StringBuffer sb, 
                                            boolean includeRequestComments, 
                                            boolean endTable,
                                            Map<String,Object> options) {
        
        sb.append("<table style='MARGIN-LEFT: 16px'>");
        if ( item.getRequesterComments() != null && includeRequestComments )
            addItemRow(sb, "Requester Comments(s)", item.getRequesterComments());
        
        String appVal = item.getApplication();        
        addItemRow(sb, "Application", mapApplication(options, appVal));        
        if ( item.getNativeIdentity() != null )
            addItemRow(sb, "Account", item.getNativeIdentity());
                  
        if ( item.getInstance() != null )
            addItemRow(sb, "Instance", item.getInstance());

        addItemRow(sb, "Operation", item.getOperation());
        if ( item.getName() != null )
            addItemRow(sb, "Attribute", item.getName());            
        if ( Util.getString(item.getCsv()) != null )
           addItemRow(sb, "Value(s)", item.getCsv());
        
        if ( endTable )
            sb.append("</table>");
    }
    
    @SuppressWarnings("unchecked")
    private static String mapApplication(Map<String,Object> options, String original) {        
        Map<String,String> appMapping = null;
        if ( options != null ) 
            appMapping = (Map<String,String>)options.get(OP_APPLICATION_MAPPING);
        
        String appVal = null;
        if ( appMapping != null && original != null ) {
            appVal = Util.getString(appMapping, original);
        }        
        if ( appVal == null ) 
            appVal = original;
        
        return appVal;
    }
     
    /**
     * 
     * @param sb
     * @param name
     * @param value
     */
    protected static void addItemRow(StringBuffer sb, String name, String value) {
        sb.append("<tr>");
        sb.append("<td><b>");
        sb.append(name);
        sb.append("</b></td>");        
        sb.append("   <td>");
        sb.append(value);
        sb.append("</td>");
        sb.append("</tr>");
    }
}
