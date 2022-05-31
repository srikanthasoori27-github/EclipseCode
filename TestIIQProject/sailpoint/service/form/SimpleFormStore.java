/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.form;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.Authorizer;
import sailpoint.object.Form;
import sailpoint.service.form.renderer.FormDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple form store that retrieve form objects from the database.  Used by the REST service to
 * return forms by name to the client
 *
 * @author: peter.holcomb
 */
public class SimpleFormStore extends BaseFormStore {

    private static final Log log = LogFactory.getLog(SimpleFormStore.class);

    String formName;

    private static final String ARG_FORM_NAME = "formName";

    public SimpleFormStore(UserContext userContext, String formName) {
        super(userContext);
        this.formName = formName;
    }

    public SimpleFormStore(UserContext userContext, Map<String, Object> state) {
        super(userContext);

        if (state.containsKey(ARG_FORM_NAME)) {
            formName = (String) state.get(ARG_FORM_NAME);
        }
    }

    /**
     * Retrieve the form from the context via name
     *
     * @return Form The form that is stored in the database with the given form name
     */
    public Form retrieveMasterForm() throws GeneralException {
        if (this.masterForm == null && !Util.isNullOrEmpty(this.formName)) {
            this.masterForm = getContext().getObjectByName(Form.class, this.formName);
            if (this.masterForm == null) {
                throw new ObjectNotFoundException(Form.class, formName);
            }
        }
        return this.masterForm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FormDTO retrieveFormDTO() throws GeneralException {
        this.retrieveExpandedForm();

        if (this.masterForm == null) {
            throw new ObjectNotFoundException(Form.class, formName);
        }

        return getFormRenderer(null).createDTO();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getFormBeanState() {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put(ARG_FORM_NAME, formName);

        return args;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Authorizer getAuthorizer(boolean isRead) throws GeneralException {
        return new AllowAllAuthorizer();
    }

}
