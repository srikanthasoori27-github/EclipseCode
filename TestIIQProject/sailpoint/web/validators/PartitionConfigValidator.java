package sailpoint.web.validators;

import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.validator.Validator;
import javax.faces.validator.ValidatorException;

public class PartitionConfigValidator implements Validator {

    public void validate(FacesContext context, UIComponent component, Object value)
            throws ValidatorException
    {

        // If partitioning is enabled, lossLimit must be less than the objectsPerPartition

        int lossLimit = Util.otoi(value);
        if (lossLimit > 0) {
            UIInput input = (UIInput)component.getAttributes().get("enablePartitioning");
            if (input != null) {
                boolean enablePartitioning = Util.otob(input.getValue());
                if (enablePartitioning) {
                    input = (UIInput)component.getAttributes().get("objectsPerPartition");
                    if (input != null) {
                        Object objectsPerPartionComponentObj = input.getValue();
                        int objectsPerPartition = Util.otoi(objectsPerPartionComponentObj);
                        if (objectsPerPartition > 0 && objectsPerPartition <= lossLimit) {
                            Message summary = new Message(MessageKeys.INVALID_FIELD_INPUT);
                            Message msgDetail = new Message(MessageKeys.LOSSLIMIT_GE_OBJECTSPERPARTITION);
                            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, summary.getLocalizedMessage(), msgDetail.getLocalizedMessage());
                            throw new ValidatorException(message);
                        }
                    }
                }
            }
        }
    }
}
