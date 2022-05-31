package sailpoint.service.form;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.Authorizer;
import sailpoint.object.Form;
import sailpoint.service.SessionStorage;
import sailpoint.service.form.renderer.FormDTO;
import sailpoint.service.form.renderer.FormRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.web.FormBean;
import sailpoint.web.FormHandler;
import sailpoint.web.UserContext;

import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * A base class for form stores and form bean that provides empty functionality for
 * abstract methods that need to be implemented by FormStore and FormBean.
 *
 * @author peter.holcomb
 */
public abstract class BaseFormStore implements FormHandler.FormStore, FormBean {

    /**
     * The log instance.
     */
    private static final Log log = LogFactory.getLog(BaseFormStore.class);

    /**
     * The master form.
     */
    Form masterForm;

    /**
     * The expanded form.
     */
    Form expandedForm;

    /**
     * The user context.
     */
    UserContext userContext;

    /**
     * The form renderer.
     */
    FormRenderer formRenderer;

    /**
     * The session storage. This is necessary for some form stores.
     */
    SessionStorage sessionStorage;

    /**
     * Constructor for BaseFormStore.
     *
     * @param userContext The user context.
     */
    public BaseFormStore(UserContext userContext) {
        this.userContext = userContext;
    }

    /**
     * Gets the time zone.
     *
     * @return The time zone.
     */
    public TimeZone getTimeZone() {
        return userContext.getUserTimeZone();
    }

    /**
     * Gets the locale.
     *
     * @return The locale.
     */
    public Locale getLocale() {
        return userContext.getLocale();
    }

    /**
     * Gets the SailPoint context.
     *
     * @return The context.
     */
    public SailPointContext getContext() {
        return userContext.getContext();
    }

    /**
     * Sets the session storage that is used by the form store.
     *
     * @param sessionStorage The session storage.
     */
    public void setSessionStorage(SessionStorage sessionStorage) {
        this.sessionStorage = sessionStorage;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // FormHandler.FormStore interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeMasterForm(Form form) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeExpandedForm(Form form) {
        this.expandedForm = form;
    }

    /**
     * {@inheritDoc}
     */
    public void clearMasterForm() {
        this.masterForm = null;
    }

    /**
     * {@inheritDoc}
     */
    public void clearExpandedForm() {
        this.expandedForm = null;
    }

    /**
     * {@inheritDoc}
     */
    public abstract Form retrieveMasterForm() throws GeneralException;

    /**
     * Grab the master form and run it through the form handler with the form arguments.  It will
     * come out with sequins
     * @return
     */
    public Form retrieveExpandedForm() throws GeneralException {
        this.retrieveMasterForm();
        if(this.masterForm != null && this.expandedForm == null) {
            FormHandler handler = new FormHandler(userContext.getContext(), this);
            this.expandedForm = handler.initializeForm(this.masterForm, true, this.getFormArguments());
        }
        return this.expandedForm;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // FormBean interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Gets the form renderer. By default, a store only handles one form so the form id
     * parameter is ignored in the default implementation.
     *
     * @param formId The form id.
     * @return The form renderer.
     * @throws GeneralException
     */
    @Override
    public FormRenderer getFormRenderer(String formId) throws GeneralException {
        if (formRenderer == null) {
            formRenderer = new FormRenderer(
                retrieveExpandedForm(), this, getLocale(), getContext(), getTimeZone()
            );
        }

        return formRenderer;
    }

    /**
     * By default, return no arguments.
     *
     * @return The arguments.
     */
    @Override
    public Map<String,Object> getFormArguments() throws GeneralException {
        return null;
    }

    /**
     * By default, return no state.
     *
     * @return The state.
     */
    @Override
    public Map<String,Object> getFormBeanState() {
        return null;
    }

    /**
     * Some form store implementations need to save information between requests. This method
     * should be overridden in these form stores. Not all stores require this functionality,
     * for this reason it is not declared abstract.
     */
    public void save() throws GeneralException { }

    /**
     * Builds the FormDTO from the form.
     *
     * @return The FormDTO.
     * @throws GeneralException
     */
    public abstract FormDTO retrieveFormDTO() throws GeneralException;

    /**
     * Gets the authorizer that can be used to authorize actions on the form.
     *
     * @param isRead True if the request is to read the form, false if any action
     *               is being taken.
     * @return The authorizer.
     * @throws GeneralException
     */
    public abstract Authorizer getAuthorizer(boolean isRead) throws GeneralException;

}
