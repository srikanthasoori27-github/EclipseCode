package sailpoint.rest;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.service.LCMConfigService;
import sailpoint.tools.GeneralException;

@Path("velocity")
public class VelocityValidatorResource extends BaseListResource {
    private static final Log log = LogFactory.getLog(VelocityValidatorResource.class);
    
    /**
     * 
     * @param form The form contains a 'template' parameter that specifies an array containing the templates that need to be validated
     * @return String containing the error messages that need to be displayed
     * @throws GeneralException
     */
    @POST
    @Path("validateIdentityFilter")
    public Map<String, Object> validate(MultivaluedMap<String, String> form) throws GeneralException {
    	authorize(new RightAuthorizer(SPRight.FullAccessSystemConfig));
    	
        Map<String, Object> result = new HashMap<String, Object>();
        StringBuffer errorMsg = new StringBuffer();
        
        String templateToValidate = form.getFirst("template");
        String componentId = form.getFirst("componentId");
        if (templateToValidate != null && templateToValidate.trim().length() > 0) {
            LCMConfigService configService = new LCMConfigService(getContext(), getLocale(), getUserTimeZone());
            log.debug("Validating template: " + templateToValidate);
            String currentErrorMsg = configService.isTemplateValid(templateToValidate, getLoggedInUser());
            if (currentErrorMsg != null && currentErrorMsg.trim().length() > 0) {
                errorMsg.append(currentErrorMsg).append("<br>");
                Map<String, String> errorObj = new HashMap<String, String>();
                errorObj.put("componentId", componentId);
                errorObj.put("msg", currentErrorMsg);
                result.put("errorObj", errorObj);
                log.debug("Template was invalid.");
            } else {
                log.debug("Template was valid.");
            }
        }
        
        result.put("isValid", errorMsg.length() == 0);               
        return result;
    }
}
