package sailpoint.rest;

import java.io.StringWriter;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.mining.ItRoleMiningTemplate;
import sailpoint.web.mining.ItRoleMiningTemplateManager;

/**
 * IT Role Mining REST Service Bean
 * @author justin.williams
 */
@Path("roleMiningTemplate")
public class ItRoleMiningTemplateService extends BaseResource {
    
    /**
     * Schedules a new instance of the specified template to execute immediately 
     * @param templateId The ID of the template to schedule an instance of
     * @return JSON String of ItRoleMiningTemplateServie.Response
     */
    @POST
    @Path("execute")
    public String executeTemplate( @FormParam("templateId") String templateId ) throws GeneralException {
    	authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));
    	
        ItRoleMiningTemplateManager manager = null;
        /* Think positive */
        boolean success = true;
        Message message = new Message( MessageKeys.IT_ROLE_MINING_TEMPLATE_SCHEDULE_SUCCESSFUL );
        try {
            manager = new ItRoleMiningTemplateManager( getContext(), getLocale(), getLoggedInUserName() );
        } catch ( Throwable e ) {
            success = false;
            message = new Message( MessageKeys.IT_ROLE_MINING_TEMPLATE_SCHEDULE_NO_TEMPLATE_MANAGER );
        }
        if( success ) {
            ItRoleMiningTemplate template = null;
            try {
                template = manager.getTemplate( templateId );
            } catch ( GeneralException e ) {
                success = false;
                message = new Message( MessageKeys.IT_ROLE_MINING_TEMPLATE_SCHEDULE_NO_TEMPLATE );
            }
            if( template != null ) {
                try {
                    manager.scheduleTemplate( template );
                } catch ( GeneralException e ) {
                    success = false;
                    message = new Message( MessageKeys.IT_ROLE_MINING_TEMPLATE_SCHEDULE_UNABLE_TO_SCHEDULE );
                }
            } else {
                success = false;
                message = new Message( MessageKeys.IT_ROLE_MINING_TEMPLATE_SCHEDULE_NO_TEMPLATE_WITH_ID, templateId );
            }
        }
        Response response = new Response();
        response.setSuccess( success );
        response.setMessage( message.getLocalizedMessage() );
        return response.toJsonString();
    }
    
    private static final class Response {
        
        public boolean getSuccess() {
            return success;
        }
        
        public void setSuccess( boolean success ) {
            this.success = success;
        }
        
        public String getMessage() {
            if( message == null ) {
                message = "";
            }
            return message;
        }
        
        public void setMessage( String message ) {
            this.message = message;
        }
        
        public String toJsonString() {
            StringWriter stringWriter = new StringWriter();
            JSONWriter jsonWriter = new JSONWriter( stringWriter );
            
            try {
                jsonWriter.object();
                jsonWriter.key( SUCCESS );
                jsonWriter.value( getSuccess() );
                jsonWriter.key( MESSAGE );
                jsonWriter.value( getMessage() );
                jsonWriter.endObject();
            } catch ( JSONException e ) {
                return "{\"" + SUCCESS + "\":false,\"" + MESSAGE + "\":\"Unable to write JSON response\"}";
            }
            return stringWriter.toString();
        }
        private static final String SUCCESS = "success";
        private static final String MESSAGE = "message";
        private boolean success;
        private String message;
    }
}
