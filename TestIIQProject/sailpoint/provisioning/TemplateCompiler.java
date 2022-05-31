/**
 * A class used internally by PlanCompiler to process Templates,
 * aka. "provisioning policies" from roles and applications.
 *
 * Author: Jeff
 *
 * Factored out of PlanCompiler in 5.5 because it was getting complicated
 * and we need to maintain a lot of temporary state to handle field 
 * dependencies.
 *
 * Currently this is very dependent on PlanCompiler but we may want something
 * like this for fields outside of templates?  If so think about moving
 * some of this to Formicator.
 *
 * The template compiler accomplishes two major tasks:
 *
 *     - Conversion of a Template/Field model into AttributeRequests
 *       that can be assimilated into a plan.
 *
 *     - Accumulation of a Question list for fields that cannot calculate
 *       static values.
 *
 * The compiler must be used to process all templates in a provisioning 
 * project so that a single list of Questions can be accumulated without
 * duplications.
 * 
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.DynamicValuator;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.DynamicValue;
import sailpoint.object.ExpansionItem;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.Form.Section;
import sailpoint.object.FormItem;
import sailpoint.object.FormRef;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.Question;
import sailpoint.object.Schema;
import sailpoint.object.Template;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.Question.Type;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.transformer.LinkTransformer;

public class TemplateCompiler {

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    private static Log log = LogFactory.getLog(TemplateCompiler.class);

    //
    // Inputs
    //

    /**
     * The parent compiler.
     */
    PlanCompiler _comp;

    /**
     * The always helpful context.
     */
    SailPointContext _context;

    /**
     * The Identity we're operating on.
     */
    Identity _identity;
    
    /**
     * The ManagedAttribute we're compiling for, in this case _identity
     * is null.
     */
    ManagedAttribute _managedAttribute;

    /**
     * The project we're operating on.
     * Currently this is necessary because that's where the Question
     * lists live.  Since questions are only related to templates
     * think about moving that down here.
     */
    ProvisioningProject _project;
    
    /**
     * The plan our request came from so we can be sure
     * we have unique sections in case of multiple
     * requests for the same application
     */
    ProvisioningPlan _plan;
    
    /**
     * Link to allow us to name sections with something useful like display name
     */
    Link _link;
    
    /**
     * Flag to indicate if we need to add the native identity to the field qname
     */
    boolean _needsNativeIdentity;

    //
    // Transient fields used in template compilation and 
    // field dependency checking
    // 

    /**
     * For role template compilation, the operation being applied to 
     * the role.  This will effect the "polarity" of the requests added
     * to the plan.  If the role is being added the expansions will
     * also use op=Add, if the role is being removed the expansions
     * will use op=Remove
     */
    Operation _operation;

    /**
     * Role with the template we're compiling.
     */
    Bundle _role;

    /**
     * Application with the template we're compiling.
     * Both this and _role cannot be non-null at the same time.
     */
    Application _application;
    
    /**
     * The native identity that we are attempting to modify.  If this is
     * not present (==null), then we will not attempt to look for an
     * existing link.
     */
    String _nativeIdentity;

    /**
     * The template we're compiling.
     */
    Template _template;

    /**
     * The AbstractRequest from the ProvisioninPlan we leave
     * results in.
     */
    AbstractRequest _request;

    /**
     * Map of all fields from _template for fast lookup.
     * These are *copies* of the template fields that we can modify.
     */
    Map<String,Field> _fields;

    /**
     * Fields that have been visited at least once during compilation.
     */
    Map<String,Field> _visited;

    /**
     * Fields currently on the dependency stack.  This is used for
     * dependency cycle detection.
     */
    Map<String,Field> _stack;

    /**
     * The resulting dependency ordered field list.
     */
    List<Field> _ordered;

    /**
     * Fields we decided to defer until questions were asked.
     */
    List<Field> _deferred;

    /**
     * The identity attribute for the application if we're expanding
     * an application template.
     */
    String _identityAttribute;

    /**
     * Values of fields we accumulate after dependency checking.
     */
    Map<String,Object> _values;

    /**
     * Questions we added during the compilation of one template.
     * This is reset each time a template is compiled.  The full
     * list of accumulated questions is in the project.
     */
    List<Question> _questions;

    private boolean _isCreate;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public TemplateCompiler(PlanCompiler comp) {

        _comp = comp;

        // can be null in rare cases where we need a few independent utilities
        if (_comp != null) {
            _context = _comp.getContext();
            _identity = _comp.getIdentity();
            _project = _comp.getProject();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // External Interface
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Compile a role template.
     */
    public ProvisioningPlan compile(Bundle role, Template tmp, Operation op) 
        throws GeneralException {

        // TODO: Reset state
        _operation = op;

        
        ProvisioningPlan plan = null;

        List<Field> fields = tmp.getFields(_context);
        if (fields != null) {
            
            plan = new ProvisioningPlan();
            AccountRequest account = new AccountRequest();
            plan.add(account);

            account.setApplication(getApplicationName(tmp));

            convertTemplate(role, null, tmp, account);
        }

        return plan;
    }

    /**
     * Compile an application template.
     * Here we're working into an existing AccountRequest.
     *
     * !! TODO
     * In theory a role could have supplied values for attributes that
     * are also in the creation template.  In that case who wins?  
     * Because we're processing creation templates last, they will but
     * it feels like the role should be able to override something?  
     * If so then the value behavior needs to be SET_IF_NULL rather
     * than REPLACE.
     * 
     */
    public void compile(Application app, Template tmp, AbstractRequest req)
        throws GeneralException {

        compile(app, tmp, req, null, null, false);
    }
    
    /**
     * Compile an application template.
     * Here we're working into an existing AccountRequest.
     *
     * !! TODO
     * In theory a role could have supplied values for attributes that
     * are also in the creation template.  In that case who wins?  
     * Because we're processing creation templates last, they will but
     * it feels like the role should be able to override something?  
     * If so then the value behavior needs to be SET_IF_NULL rather
     * than REPLACE.
     * 
     */
    public void compile(Application app, Template tmp, AbstractRequest req, ProvisioningPlan plan, Link link, boolean needsNativeIdentity)
        throws GeneralException {

        // We're used for both role and app templates.  We need to be
        // sensitive to the add/remove operation when expanding role templates
        // but app templates go through unmodified.
        _operation = null;
        
        _plan = plan;
        _link = link;
        _needsNativeIdentity = needsNativeIdentity;
        // kludge: should have a better way to pass this in
        if (req instanceof ObjectRequest)
            _managedAttribute = ManagedAttributer.get(_context, (ObjectRequest)req, app);

        if (tmp != null)
            convertTemplate(null, app, tmp, req);
    }

    /**
     * Save any remaining template compilation results on the project.
     * Since we were modifying the project's question and question history
     * lists as we went we don't have anything more to add here, but
     * we can detect whether we have deferred fields that won't
     * have dependencies satisfied?  This shouldn't happen.
     */
    public void saveResults() throws GeneralException {

        if (_deferred != null) {
            for (Field f : _deferred) {
                // todo: store the "question name" on the field
                // so we don't have to keep recalculating it...

                // this won't work since it has an unqualified name
                /*
                if (_project.getQuestion(f.getName()) == null) {
                    log.error("Dependent field has no question!?");
                }
                */
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the name of the application associated with a template.
     * This is used for scoping the field names within the template.
     * 
     * The new convention for this is an Application reference, but support
     * the older "purview" property until the template editor is changed.
     * 
     * !! what about instances?  It may be better to leave the purview
     * string and let it be a qualified app/instance name like we use
     * in the cert model.  Or have another _instance property.
     */
    private String getApplicationName(Template src) 
        throws GeneralException {

        String name = null;
 
        Application app = src.getApplication();
        if (app != null)
            name = app.getName();
        else {
            name = src.getPurview();
            // sigh, template editor stores ids here but
            // sometimes they're names
            if (ObjectUtil.isUniqueId(name)) {
                app = _context.getObjectById(Application.class, name);
                if (app != null)
                    name = app.getName();
            }
        }
           
        return name;
    }
    
    /**
     * Returns the resolved Application associated with a template.
     */
    public Application getApplication(Template src)
        throws GeneralException {

        Application app = src.getApplication();
        if (app == null)  {

            String purv = src.getPurview();
            if (purv != null)  {
                app = _comp.getApplication(purv);
                if (app == null)
                    log.warn("Unknown application: " + purv);
            }
        }

        return app;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Template Compilation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convert template fields into attribute or permission requests.
     *
     * This can be used both when converting templates stored on the
     * role and creation templates stored on an application.
     * If this is a creation template, recognize the field "accountId"
     * as a way to change the native identity of the account.
     * 
     * !!TODO: What about instances?
     *
     * The "role" argument is passed if we're expanding a role template.
     * The "app" argument is passed if we're expanding an application
     * create template.
     *
     * If we're expanding a role template the resulting AttributeRequest must
     * use _operation so it matches what is being done with the role
     * (e.g. Remove or Retain).
     * 
     * If we're expanding an application template, the operations are
     * left as is since this will only be called if we're provisioning the role.
     * _operation must be set to null.
     * 
     * For each field we first look to see if there is an answered
     * Question object from a prior form interaction, if so the answer
     * is used instead of the corresponding Link attribute.  For
     * application creation templates there will be no Link anyway.
     * 
     * If we can't find a value for a field and it is marked required
     * a new Question object is created and added to the project.
     * 
     * During role template expansion, Questions are generated only if
     * _operation is Set or Add.  For Remove and Revoke we're taking things
     * away so it doesn't matter if they're missing some required account 
     * attributes.
     *
     * For Retain we could go two ways.  We can either suppress the generation
     * of Questions, or treat this like a "reconciliation" and ask for the
     * missing things.  Currently we suppress under the assumption that
     * the form was filled out when the role was Added and the provisioning
     * request may still be in progress so we don't want to keep presenting the
     * form every time they add or remove a different role.  If you want to 
     * do a full reconciliation (don't add/remove roles, just add missing things)
     * this currently has to be done by putting Add requests for all the
     * currently assigned roles in the master plan.  This will prevent them
     * from being treated as Retains so we'll generate the Questions.
     * 
     * TODO: if we have to generate a question we may
     * need to suppress the simplification of the AccountRequest
     * since it may have nothing in it until after the
     * questions are answered.  This would either require a flag
     * in the AccountRequest or we could just put dummy
     * AttributeRequests in it that will be recompiled later.
     * Currently the entire simplification phase is disabled if there
     * are questions.
     *
     * TODO: Need to think more about the Remove and Retain cases.  If the
     * fields use scripts to calculate values, these could produce things 
     * to remove that aren't intended.  We will pass _operation into the
     * script in case they need to be sensitive to this.
     * 
     * UPDATE: Field dependencies in 5.5 adds a new layer of complication
     * to this.
     */
    private void convertTemplate(Bundle role, Application app, 
                                 Template tmp, AbstractRequest req)
    
        throws GeneralException {

        _role = role;
        _application = app;
        _template = tmp;
        _request = req;
        _nativeIdentity = req.getNativeIdentity();
        
        // set isCreate for role compilation
        // quick check on the operation
        _isCreate = _request != null && _request.getOp() == ObjectOperation.Create;
        if (!_isCreate) {
            // If we don't already have an application, then dig it out of the template
            Application application = _application != null ? _application : _template.getApplication();
            // check the project for provisioning targets
            String roleName = null;
            if (_role != null) {
                roleName = _role.getName();
            }
            AccountSelection accountSelection = _project.getAccountSelection(application, roleName, null);
            if (accountSelection == null) {
                // if accountSelection is null, either because we never had a role or passing a role
                // didn't yield results, try w/o the role
                accountSelection = _project.getAccountSelection(application, null, null);
            }
            _isCreate = accountSelection != null && accountSelection.isDoCreate();
        }

        // Determine the name of the attribute that is considered
        // the "native identity" for an account
        _identityAttribute = getIdentityAttribute(app, tmp);

        // Make the field values available as top-level symbols
        // in the script rather than having to dig them out
        // of the ProvisioningPlan
        _values = new HashMap<String,Object>();
        
        // This accumulates the Questions needed for this template
        _questions = null;

        // reorder the fields by dependencies
        // this also sets up _fields and other state
        //TODO: Evaluate accountId first -rap
        List<Field> fields = orderFields(_template.getFields(_context));

        // remove excluded fields
        excludeFields(fields);

        if (fields != null && fields.size() > 0) {
            for (Field field : fields) {
                if (null != _link) {
                    field.setAttribute("linkId", _link.getId());
                }
                // Make sure to load the field so a decache later
                // in the workflow doesn't cause an exception
                field.load();

                boolean wait = false;
                List<String> dependencies = field.getDependencyList();
                if (dependencies != null) {
                    for (String depname : dependencies) {
                        Field dep = _fields.get(depname);
                        // will have already traced invalid dependencies
                        if (dep != null) {
                            Object depvalue = _values.get(dep.getName());
                            if (depvalue == null) {
                                // static fields wait
                                if (!field.isDynamic()) {
                                    if (_deferred == null)
                                        _deferred = new ArrayList<Field>();
                                    _deferred.add(field);
                                    wait = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!wait)
                    convertField(field);
            }
        }

        // Add dependency info for the dynamic fields added for this template.
        // We wait until all fields have been processed so we have a better
        // chance of having all of the dependencies available.
        if (_questions != null) {
            for (Question q : _questions)
                addDynamicFieldDependencies(q);
        }

    }
    
    /**
     * Reorder the field list so that fields that have dependencies
     * are placed after the fields they depend on. If cycles are found they
     * will be broken.  
     *
     * This also copies the Field objects so that we can modify them.
     */
    private List<Field> orderFields(List<Field> src) 
        throws GeneralException {
        
        // initialize traversal state
        _fields = new HashMap<String,Field>();
        _visited = new HashMap<String,Field>();
        _stack = new HashMap<String,Field>();
        _ordered = new ArrayList<Field>();

        if (src != null) {
            // first populate the field lookup table
            for (Field field : src) {
                String name = field.getName();
                if (name != null) {
                    // copy the field so we can modify it
                    // note that passing a Resolver is important in case
                    // the DynamicValues contain Rule references
                    Field copy = (Field)field.deepCopy(_context);
                    _fields.put(name, copy);
                }
            }

            //Find accountId, if applicable and put first (besides dependencies)
            //Questions/fields now have the concept of nativeId to associate to certain requests, if nativeId is not
            //populated first, this falls on its face -rap

            // then order them
            for (Field field : src) {
                String name = field.getName();
                if (name != null) {
                    // have we seen it before chasing dependencies?
                    if (_visited.get(name) == null) {
                        // operate on the copy 
                        Field copy = _fields.get(name);
                        chaseDependencies(copy);
                    }
                }
            }
        }

        return _ordered;
    }

    /**
     * Recursively walk the dependency hierarchy for a field.
     * As we find dependency fields they will be marked as required
     * so we force a Question for them if they can't calculate a value.
     */
    private void chaseDependencies(Field field) {

        _stack.put(field.getName(), field);

        String depspec = field.getDependencies();
        if (depspec != null) {
            List<String> deplist = Util.csvToList(depspec);
            if (deplist != null) {
                for (String name : deplist) {
                    Field depfield = _fields.get(name);
                    if (depfield == null) {
                        log.error("Unresolved field dependency: " + name);
                        // nothing we can do about it now so go ahead
                        // and process it
                    }
                    else {
                        // having a dependency automatically makes
                        // this field required
                        if (!depfield.isRequired()) {
                            log.info("Promoting field " + depfield.getName() + 
                                     " to required due to dependency from " + 
                                     field.getName());
                            depfield.setRequired(true);
                        }

                        if (_visited.get(name) != null) {
                            // already processed this 
                        }
                        else if (_stack.get(name) != null) {
                            // cycles in the dependency tree, have to break
                            log.error("Cycle in field dependencies: " + 
                                      field.getName() + " depends on " + 
                                      name);
                        }
                        else {
                            chaseDependencies(depfield);
                        }
                    }

                }
            }
        }

        _visited.put(field.getName(), field);
        _ordered.add(field);
        _stack.remove(field.getName());
    }

    /**
     * Remove fields from the list if they have an exclusion rule that
     * evaluates to true.  This is a hacky solution for #14396 where
     * we need a way to keep fields out of the compiled form in a 
     * few special cases.  The "hidden" option isn't enough because these
     * are often marked required='true' and if they're excluded that overrides
     * required.  There are several levels this could be done including Formicator.
     * But it gets more complicated the deeper you go.  This gets the job
     * done for 14396 but need to think more about this concept and how 
     * it releates to other field options.  Note that a consequence of doing
     * it out here is that you cannot rely on other field values, only the
     * Form and Field models.
     *
     * KLUDGE: This is also where we'll add the "operation" argument since
     * that's what 14396 needs to test.  We're operating on a clone by now
     * so we won't damage the original template.
     */
    private void excludeFields(List<Field> fields) 
        throws GeneralException {

        if (fields != null) {
            // set up the args to pass to the exclusion rule
            Map<String,Object> args = new HashMap<String,Object>();

            // add the script args passed in from the workflow
            Attributes<String,Object> scriptArgs = _comp.getScriptArgs();
            if (scriptArgs != null)
                args.putAll(scriptArgs);
            
            // then the reserved names
            args.put("identity", _identity);
            args.put("accountRequest", _request);
            args.put("role", _role);
            args.put("application", _application);
            args.put("template", _template);

            ListIterator<Field> it = fields.listIterator();
            while (it.hasNext()) {
                Field field = it.next();
                args.put("field", field);

                // Create/Update/Delete is obvious because there are different
                // policies.  non-Modify is the problem because we run the
                // Modify policy for op=Enable, op=Disable etc.
                ObjectOperation op = _request.getOp();
                if (op != null && op != ObjectOperation.Create && op != ObjectOperation.Modify)
                    field.setAttribute(Field.ATT_OPERATION, op.toString());

                Object value = field.getAttribute(Field.ATT_IGNORED);
                if (value != null) {
                    DynamicValuator dv = new DynamicValuator(value.toString());
                    if (Util.otob(dv.evaluate(_context, args)))
                        it.remove();
                }
            }
        }
    }

    /**
     * Process one template field.
     * We first look for previously answered Question objects that will
     * supply the field value.  Then we try to evaluate the field's
     * value script.  If nothing produces a value and the field
     * is marked required we generate a new Question.
     */
    private void convertField(Field field)
        throws GeneralException {

        // An informational field?  Normally don't have these in templates.
        if (field == null || field.getName() == null) {
            return;
        }

        // If this field has an app dependency we don't want to show it in the form.
        if(field.getAppDependency() != null) {
            return;
        }

        String source = _request.getApplicationName();
        Question.Type type = Question.Type.Application;
        if(_role != null) {
            source = _role.getName();
            type = Question.Type.Role;
        }
        String target = null;
        
        if(_needsNativeIdentity) {
            target = _request.getNativeIdentity();
        }
        String attributeName = field.getName();

        Question question = _project.getQuestion(type, source, target, attributeName);
        if (question != null) {
            // we've already encountered this field and promoted
            // it to a question
            return;
        }

        // Check if there is an expanded value already in the project.
        Object value = getExpansionValue(field);
        
        // bug 18136 - we should only dump into here if the field is not a multi-valued field,
        // otherwise the value gleamed may be one we just need to merge to
        if (null != value && !(value instanceof List)) {
            // We have a value but review is required.  Show it but don't allow
            // editing.  Also, give some help to indicate what's up.
            if (field.isReviewRequired()) {
                field.setReadOnly(true);
                // TODO: It would be nice to show some i-help on this field
                // that indicates that the value is being set by the system
                // and cannot be overridden.  If we were to use the helpKey
                // we would need to localize and append our own message.
                // We don't have a Locale here, though.  Instead we could set
                // a flag on the field that the Formicator uses to add this
                // info.
            }
            else {
                // We have a value and review is not required, so bail.
                return;
            }
        }
        
        // look for a previous answer
        question = _project.getAnsweredQuestion(type, source, target, attributeName);

        // If we have an expansion value, it is authoritative so use it.
        // Otherwise try to get it from a previous answer or expand the field.
        // Unless it's a list, in which case we may have more to add to it
        if (null == value || value instanceof List) {
            Object newValue = null;
            if (question == null)
                newValue = getFieldValue(field);
            else
                newValue = question.getAnswer();
            if (newValue != null && value == null) {
                // just set it
                value = newValue;
            } else if (newValue != null && value instanceof List) {
                // add it, if not present already
                List listValue = (List)value;
                if (newValue instanceof List) {
                    // merge our new list into our existing list
                    for (Object newValueObject : (List)newValue) {
                        if (!listValue.contains(newValueObject)) {
                            listValue.add(newValueObject);
                        }
                    }
                } else {
                    // new value isn't a list, add
                    if (!listValue.contains(newValue)) {
                        listValue.add(newValue);
                    }
                }
            } // only other logical 'else' is if newValue is null; leave it all alone if so
        }

        // If we still haven't come up with a value check if one was hard-coded in the master plan.  If so, go ahead and apply it.
        if (value == null) {
            Application app = (null != _application) ? _application : getApplication(_template);
            value = _project.getLinkAttribute(app.getName(), _nativeIdentity, null, field.getName(), _request, _identity, true);
        }

        // UI might leave empty strings here if nothing
        // was entered, collapse to null, I guess we should
        // trim too, I can't think of a reason why you
        // would need to add/set/remove spaces?
        // what about empty lists?
        if (value instanceof String && 
            ((String)value).trim().length() == 0)
            value = null;

        // save it in the argument map for the scripts, do this
        // even if null so the scripts don't' have to mess with
        // unbound checking
         _values.put(field.getName(), value);


        // NOTE: prior to 6.0 we suppressed questions unless
        // we were creating new accounts, this made policies
        // practically useless since they override the profiles
        // for provisioning but they don't end up doing anything.
        // Assume for now tht all templates on a role are update
        // templates, we can type them later.

        // Determine whether we're going to prompt for this field
        // Ignore if this is Remove or Revoke since it doesn't
        // matter if there are missing fields.
        boolean askingQuestion = false;
        if (isQuestionableOperation()) {

            if (field.isRequired() && value == null) {
                // keep asking until they get it right
                // if the answered Question has a null value,
                // do we need a way to let it be an authoritative
                // null or always keep asking?
                askingQuestion = true;
            }
            else if (field.isReviewRequired()) {
                // This one is kind of a kludge, the question goes
                // out in the first form but once it has been 
                // answered we don't keep asking.  Otherwise
                // we'd never stop generating forms.  Need to 
                // rethink this, it might be more useful
                // to say "review required as long as there 
                // are other missing required things to ask for?"
                if (question == null || !question.isShown())
                    askingQuestion = true;
            }

            if (askingQuestion) {
                if (question != null) {
                    // transfer from the history back to the
                    // current list
                    _project.removeAnsweredQuestion(question);
                }
                else {
                    // make a new one
                    question = newQuestion(field);
                    // if we're here because of review Required, make
                    // sure the review value gets into the cloned field
                    question.setAnswer(value);
                    // If this is a dynamic field we need this here too
                    // to prevent it from "sticking"
                    // jsl - I HATE having so much form logic here
                    // that has to be consistent with Formicator,
                    // need to move at least some of this assembly
                    // code over there.
                    if (field.isDynamic())
                        question.setPreviousValue(value);
                }

                // add it to the active list of the project
                _project.addQuestion(question);

                // remember the questions we added for this template
                // for later adjustment
                if (_questions == null)
                    _questions = new ArrayList<Question>();
                _questions.add(question);
            }
        }

        // if we're not asking any more take whatever we have and
        // turn it into a request
        if (!askingQuestion)
            addFieldResult(field, value, question);
    }

    /**
     * Get that target Link for a request.
     * This is called in several places, should just calculate
     * this upfront and leave it in a field?
     */
    private Link getTargetLink() throws GeneralException {

        Link link = null;

        if (_identity != null) {
            Application app = _application;
            if (app == null && _role != null)
                app = getApplication(_template);

            if (app != null) {
                String nid = _request.getNativeIdentity();
                if (nid != null)
                    link = _identity.getLink(app, nid);

                // safe to fall back to this?
                if (link == null)
                    link = _identity.getLink(app);
            }
        }

        return link;
    }

    /**
     * Return the values from the provisioning project for this field if they
     * were expanded.
     */
    @SuppressWarnings("unchecked")
    private Object getExpansionValue(Field field) throws GeneralException {
        Object value = null;
        String fieldName = field.getName();
        
        Application app = (null != _application) ? _application : getApplication(_template);
        
        if (_identity != null) {
            if (null == app) {
                // no application scope, treated as an identity attribute
                value = _project.getIdentityAttribute(fieldName, _identity);
            }
            else {
                // find a link, these are mutually exclusive. Pass 'isCreate' to indicate if only the project should be evaluated
                value = _project.getLinkAttribute(app.getName(), _nativeIdentity, null, fieldName, _request, _identity, false, isCreate());
                
                if (!(value instanceof List)) {
                    // the value we got isn't a list; should it be?  Yes, if the attribute is marked
                    // as multi-valued.  This way our caller will merge values instead of overwrite
                    
                    // will this be used for group provisioning?
                    Schema acctSchema = app.getSchema("account");
                    if (acctSchema != null) {
                        AttributeDefinition attributeDef = acctSchema.getAttributeDefinition(fieldName);
                        if (attributeDef != null && attributeDef.isMulti()) {
                            List valueList = new ArrayList();
                            if (value != null) {
                                valueList.add(value);
                            }
                            value = valueList;
                        }
                    }
                }
            }
        }
        else if (_managedAttribute != null) {
            value = _project.getObjectAttribute(app.getName(), fieldName, _managedAttribute);
        }

        // If we have a value, check that it was from an expansion.
        if (null != value) {
            if (value instanceof List) {
                // Only return the values that were expanded.
                List<Object> expanded = new ArrayList<Object>();
                for (Object current : (List) value) {
                    ExpansionItem expansion =
                        _project.getExpansionItem(app.getName(), fieldName, current);
                    if (null != expansion) {
                        // Expanded ... add it to return list.
                        expanded.add(current);
                    }
                }

                // Return the expanded subset, or null if empty.
                value = (!expanded.isEmpty()) ? expanded : null;
            }
            else {
                // iiqtc-6 - Expansion items for fields with single values should be calculated
                // the old way since the native identity is almost always null.
                ExpansionItem expansion = null;
                if (field.isMulti()) {
                    // This should return an ExpansionItem related to our nativeIdentity, if
                    // one is provided and / or the expansion item has a native identity or not
                    ExpansionItem preLoad = new ExpansionItem(app.getName(), null, _nativeIdentity, fieldName, value, null, null, null);
                    expansion = _project.getExpansionItem(preLoad, true);
                } else {
                    expansion = _project.getExpansionItem(app.getName(), fieldName, value);
                }

                if (null == expansion) {
                    // Not expanded ... comes from elsewhere.  Return null.
                    value = null;
                }
            }
        }

        return value;
    }

    /**
     * Add a calculated field value or answered question to the plan.
     */
    private void addFieldResult(Field field, Object value, Question question)
        throws GeneralException {

        String fieldName = field.getName();
        Operation op = getFieldOperation(field);

        // find the link we're going to provision to
        // will be null if this is an ObjectRequest
        Link targetLink = getTargetLink();

        // Determine whether to ignore null values.
        // Account creation templates with optional fields can
        // result in Questions with null values, we want to keep
        // these out of the provisioning plan so we don't 
        // submit requests we don't need.  

        if (value == null && (op != Operation.Set || 
                              (targetLink == null && 
                               _managedAttribute == null))) {

            // ADD/REMOVE of null is always meaningless
            // SET of null is meaningful only if the account
            // already exists
            if (log.isDebugEnabled())
                log.debug("Ignoring null field: " + fieldName);
        }
        else if (field.isPermission()) {

            PermissionRequest perm = new PermissionRequest();
            perm.setTarget(fieldName);
            perm.setOp(op);
                        
            if (value instanceof List)
                perm.setRightsList((List<String>)value);
            else if (value != null)
                perm.setRights(value.toString());

            _request.add(perm);
        }
        else if (fieldName.equals(_identityAttribute) ||
                 fieldName.equals(Field.ACCOUNT_ID)) {

            // Field representing the native account must be 
            // stored on the AccountRequest rather than an 
            // AttributeRequest.  The usual convention is to 
            // use the name designated in the schema as the
            // identity attribute, but in case you need to use
            // the field with more than one app you can use
            // the universal reserved name "accountId"

            if (_operation != null)
                log.warn("Ignoring accountId field role template");
                    
            else if (_request.getOp() != ObjectOperation.Create)
                log.warn("Ignoring accountId field in non-Create request");

            else if (value != null) {
                // these will be given the identity name by default 
                // so don't warn in that case
                // djs : changes this to a debug since its
                // fairly common
                String current = _request.getNativeIdentity();
                if (current != null && _identity != null && 
                    !current.equals(_identity.getName()))
                    log.debug("Replacing accountId field in Create request");
                _request.setNativeIdentity(value.toString());
                _nativeIdentity = value.toString();
                //Update the question to have the nativeId
                if (question != null) {
                    question.setFutureTarget(value.toString());
                }
            }
        }
        else {
            AttributeRequest att = new AttributeRequest();
            att.setName(fieldName);
            att.setOp(op);
            //Bug 19008 We strip out type information from the field when setting the request.
            //Let's try a little harder to preserve that type information
            //to prevent the plan compiler from filtering this out
            //due to a false-positive mismatch in PlanCompiler.filterRemove
            //TODO handle other types if there are issues - dates maybe?
            if(!(Field.TYPE_BOOLEAN.equals(field.getType())))
                att.setValue(value);
            else
                att.setValue(Util.otob(value));
            // bug#15808, flag things that came from secret fields
            // so we know to keep them out of the IdentityRequest
            if (Field.TYPE_SECRET.equals(field.getType())) 
                att.put(ProvisioningPlan.ARG_SECRET, "true");
            _request.add(att);
        }
    }

    /**
     * Return the name of the attribute in the Schema for accounts
     * that is considered to represent the native account identity.
     * When we find fields with this name, the value needs to 
     * be stored in the AccountRequest.nativeIdentity rather than
     * as an AttributeRequest.
     */
    private String getIdentityAttribute(Application app, Template tmp) 
        throws GeneralException {

        String attname = null;

        if (app == null) {
            // we must be dealing with a role template, this is unusual
            // since we shouldn't be defining accountId policy in a role,
            // but we can follow the purview and locate the app
            app = getApplication(tmp);
        }
        
        if (app != null) {
            Schema schema = app.getAccountSchema();
            if (schema != null)
                attname = schema.getIdentityAttribute();
        }

        return attname;
    }

    /**
     * Return true if it is permissible to generate a Question to
     * request or review a value for a template field.  This is 
     * sensitive to the _operation being used with the associated role.
     * If _operation is null it means we're expanding an application
     * creation template so questions are always allowed.
     *
     * For Remove and Revoke we're trying to take the role away so unresolved
     * fields don't matter.
     *
     * For Retain we're just trying to keep things we already have so don't
     * but the user by generating a form that they usually have already answered
     * when the role was first assigned.  See further commentary in 
     * expandRoleEntitlements.
     */
    private boolean isQuestionableOperation() {

        return (_operation == null || 
                _operation == Operation.Set ||
                _operation == Operation.Add);
    }

    /**
     * Determine the Operation to apply to the value for a template field.
     * If we're expanding a role template with Remove, Revoke, or Retain we use
     * that operation.  
     *
     * If we're expanding a role with Add or Set, the operation is determined
     * by the isAuthoritative property of the field.  If true we use Set
     * otherwise Add.  I'm not sure the authoritative option is a good idea,
     * it may conflict with other roles.
     * 
     * If we're expanding an application creation template (_operation is null)
     * use Add or Set depending on the authoritative flag.  Also check
     * the schema, if this is a single valued attribute it must always
     * use Set, or else if there is an overlap between the application
     * and role policies you end up with a List that the connector
     * isn't expecting.
     */
    private Operation getFieldOperation(Field field) 
        throws GeneralException {

        Operation op = _operation;

        if (op == null || op == Operation.Set || op == Operation.Add) {

            boolean singleValued = false;
            Application app = (null != _application) ? _application : getApplication(_template);
            if (app != null) {
                Schema schema = app.getAccountSchema();
                if (schema != null) {
                    AttributeDefinition def = schema.getAttributeDefinition(field.getName());
                    // Need to include otherAttributes as SET ops so we are in sync with the LinkTransformer attribute requests
                    // and don't get duplicates.
                    List<String> otherAttributes = LinkTransformer.getOtherAttributeNames(schema);
                    singleValued = ((def != null && !def.isMulti()) || (def == null && otherAttributes.contains(field.getName())));
                }
            }

            if (field.isAuthoritative() || singleValued)
                op = Operation.Set;
            else
                op = Operation.Add;
        }

        return op;
    }

    /**
     * Helper for convertTemplate, determine the value for a field
     * using either the static default value, the result of a script, 
     * or the result of a rule.  Note that this does not examine
     * answered Question objects, the caller must do that.
     *
     * The default treatment of a field value is REPLACE/MERGE
     * where single-valued attributes are replaced and multi-valued
     * attributes are merged.  If you want to change this, for
     * example to get REPLACE-IF-NULL, then you need to mark
     * the field as authoritative, write a value script or rule
     * and check the "current" value passed into the script.
     */
    private Object getFieldValue(Field field)
        throws GeneralException {

        Object value = null;

        // look for a pre-populated value if this is the identity attribute
        // which should only be specified in a app create policy
        if (isIdentityAttribute(field) && _request != null) {
            // first try the nativeIdentity on the account request then
            // look through the attribute requests
            if (!Util.isNullOrEmpty(_request.getNativeIdentity())) {
                value = _request.getNativeIdentity();
                //use every opportunity to set / updatge the native identity
                if (_nativeIdentity == null) {
                    _nativeIdentity = _request.getNativeIdentity();
                }
            } else {
                AttributeRequest attrRequest = _request.getAttributeRequest(field.getName());
                if (attrRequest != null && attrRequest.getValue() != null) {
                   value = attrRequest.getValue();
                }
            }
        }

        if (value == null) {
            DynamicValue valuedef = field.getValueDefinition();
            if (valuedef != null) {

                log.debug("Evaluating field dynamic value ");

                DynamicValuator valuator = new DynamicValuator(valuedef);

                // This is relatively expensive, build out an arg map only
                // if we need one
                Map<String,Object> args = _values;
                if (valuator.needsArgs()) {

                    // make a full copy so we don't keep corrupting _values
                    args = new HashMap<String,Object>();

                    // Find the current value for the corresponding
                    // identity/account attribute field to pass into the script.
                    // Among other things this can be used to implement a
                    // non-destructive default value.
                    Object current = null;

                    Application templateApp = getApplication(_template);
                    Link link = null;

                    if (_identity != null) {
                        link = getTargetLink();
                        if (_application == null && templateApp == null) {
                            // no application scope, treated as an identity attribute
                            current = _identity.getAttribute(field.getName());
                        }
                        else if (link != null) {
                            current = link.getAttribute(field.getName());
                        }
                    }

                    // add the script args passed in from the workflow
                    Attributes<String,Object> scriptArgs = _comp.getScriptArgs();
                    if (scriptArgs != null)
                        args.putAll(scriptArgs);

                    // then the field values we've processed so far
                    if (_values != null)
                        args.putAll(_values);

                    // finally the reserved names
                    args.put("identity", _identity);
                    args.put("link", link);
                    args.put("group", _managedAttribute);
                    args.put("project", _project);
                    args.put("accountRequest", _request);
                    args.put("objectRequest", _request);
                    args.put("role", _role);
                    args.put("application", _application);
                    args.put("template", _template);
                    args.put("field", field);
                    args.put("current", current);

                    // pass the operation being applied to the role
                    // if this is a creation template always pass Add?
                    Operation op = _operation;
                    if (op == null) {
                        // assume it's Add since we'll only do this to create
                        // something new
                        op = Operation.Add;
                    }
                    else if (op == Operation.Revoke) {
                        // simplify this so the script only has one thing to test
                        op = Operation.Remove;
                    }

                    args.put("operation", op);
                }

                try {
                    value = valuator.evaluate(_context, args);
                    //use every opportunity to set / updatge the native identity
                    if (isIdentityAttribute(field) && value != null && value instanceof String) {
                        _nativeIdentity = (String)value;
                    }
                } catch (GeneralException e) {
                	log.error("There was a problem evaluating the field named " + field.getName() + " with value definition :" + field.getValueDefinition().toXml());
                	throw e;
                }
            }
        }

        return value;
    }

    /**
     * Determines if the field is for the identity attribute.
     * @param field The field.
     * @return True if identity attribute, false otherwise.
     */
    private boolean isIdentityAttribute(Field field) {
        return field != null && field.getName().equals(_identityAttribute);
    }
    
    /**
     * Flag to indicate that this compilation is for an account creation. It's a special use case
     * for role template compilation where we don't have the nativeIdentity for the future
     * account. This prevents us from evaluating information on a potentially existing
     * Link which would otherwise be irrelevant for our purposes.
     * @return true if we were able to determine this to be an account creation
     */
    private boolean isCreate() {
        return _isCreate;
    }

    /**
     * Given a static value from the Field model, collapse empty
     * strings to null so they aren't considered to be satisfactions
     * of a required field.
     */
    private Object nullify(Object obj) {
        if (obj instanceof String) {
            String str = ((String)obj).trim();
            if (str.length() == 0)
                obj = null;
        }
        return obj;
    }

    /**
     * Generate a unique field name that is HTML safe taking into
     * consideration a project that has multiple account requests
     * for the same application
     */ 
    private String generateFieldName(Field field) 
        throws GeneralException {
        
        String qname = null;
        String fieldName = field.getName();

        if (fieldName == null) {
            log.error("I've been through the desert on a Field with no name");
        }
        else {
            String prefix = null;

            if(null != _project) {
                prefix = "Question:" + Integer.toString(_project.getTotalQuestionCount());
            }

            if (prefix == null) {
                // would this happen?
                qname = fieldName;
            }
            else {
                // TODO: make sure we can use colon as a delimiter
                qname = field.qualifyName(prefix, fieldName);
            }
        }

        return qname;
    }

    /**
     * Make a Question object to include in the project.
     * We require that the Field object have been cloned above so 
     * we can modify it.
     */
    private Question newQuestion(Field field)
        throws GeneralException {

        // save the owner definition before we start cleaning up the field
        // derive the owner for the question 
        DynamicValue ownerdef = field.getOwnerDefinition();
        if (ownerdef == null || ownerdef.isEmpty()) {
            ownerdef = _template.getOwnerDefinition();
        }
        
        String attributeName = field.getName();

        // try not to let system code depend on the qualified name convention
        // use the source information below instead
        field.setName(generateFieldName(field));
        
        // give the field information about where it came from
        field.setTemplate(_template.getName());
        if (_role != null)
            field.setRole(_role.getName());
        
        String applicationName = null;

        // app  name will always be set, either from the app that owns
        // the template or from the app referenced in the role template
        if (_application != null) {
            applicationName = _application.getName();
        }
        else {
            applicationName = getApplicationName(_template);
        }
        
        field.setApplication(applicationName);

        // Remove things we don't need to pass into the work item
        // that can clutter up the XML.
        field.setDescription(null);
        field.setDefaultValue(null);
                                
        // we'll evaluate "ownerdef" above
        // clear this so Formicator doesn't see it later 
        // when it assembles the presentation Form
        field.setOwnerDefinition(null);
        
        // If the value scripts were set the result was null, remove them so
        // Formicator doesn't try to run them again, unless this is 
        // declared as a dynamic field
        if (!field.isDynamic()) {
            field.setScript(null);
            field.setRule(null);
        }

        // we leave the allowedValues and validation scripts behind
        // allowedValue is done in Formicator
        // !! Why?  should we evaluate allowedValues now!?

        // KLUDGE: When combining account creation templates and
        // role templates it looks better if the account creation 
        // fields show up first.  So we don't have to manually
        // set field priorities default fields from a creation
        // template to 10.  As long as role fields don't set a
        // priority this will make them appear first.
        if (_application != null) {
            //bug#16470 -- Only apply default priority if not defined.
            if (field.getPriority() == 0) {
                field.setPriority(10);
            }
        }

        if (Util.trimnull(field.getSection()) == null) {
            field.setSection(getFieldSectionName(field));
        }

        Question q = new Question(field);

        // derive the owner for the question 
        if (ownerdef != null) {
            Map<String,Object> args = new HashMap<String,Object>();

            // add the script args passed in from the workflow
            Attributes<String,Object> scriptArgs = _comp.getScriptArgs();
            if (scriptArgs != null)
                args.putAll(scriptArgs);

            // then the previous field values
            if (_values != null)
                args.putAll(_values);
            
            // then the reserved names
            args.put("identity", _identity);
            args.put("accountRequest", _request);
            args.put("role", _role);
            args.put("application", _application);
            args.put("template", _template);
            args.put("field", field);

            DynamicValuator valuator = new DynamicValuator(ownerdef);
            Object value = valuator.evaluate(_context, args);

            String owner = null;
            if (value instanceof Identity) {
                owner = ((Identity)value).getName();
            }
            else if (value != null) {
                owner = resolveOwner(value.toString());
            }

            q.setOwner(owner);
        }
        
        if(_role != null) {
            q.setSource(_role.getName());
            q.setType(Question.Type.Role);
        } else {
            
            if(Util.isNotNullOrEmpty(applicationName)) {
                q.setSource(applicationName);
                q.setType(Type.Application);
            }
        }
        
        //Only set the target on the question when we are sure we will not have a change
        //once a question that can set native identity gets answered / generated for a create
        if(Util.isNotNullOrEmpty(_nativeIdentity)  && _needsNativeIdentity) {
            q.setTarget(_nativeIdentity);
        }
        
        if(Util.isNotNullOrEmpty(attributeName)) {
            q.setAttributeName(attributeName);
        }
        

        return q;
    }

    /**
     * Auto generate a section for a form field using the application name.
     * If there are multiple app account requests for the same app,
     * append the name with the link name for non-Create requests and
     * with _request nativeIdentity when there are multiple Create requests.
     * @param field
     * @return the section field name
     */
    private String getFieldSectionName(Field field) {

        String sectionName = field.getApplication();

        if(_plan != null) {
            List<AccountRequest> appAccountRequests = _plan.getAccountRequests(field.getApplication());
            if (appAccountRequests != null && appAccountRequests.size() > 1) {
                if (ProvisioningPlan.ObjectOperation.Create.equals(_request.getOp())) {
                    int appCreateReqs = 0;
                    for (AccountRequest req : Util.iterate(appAccountRequests)) {
                        if (ProvisioningPlan.ObjectOperation.Create.equals(req.getOp()))
                            appCreateReqs++;
                    }
                    if (_request.getNativeIdentity() != null && appCreateReqs > 1) {
                        sectionName = field.getApplication() + " : " + _request.getNativeIdentity();
                    }
                } else {
                    if (_link != null && Util.isNotNullOrEmpty(_link.getDisplayName())) {
                        sectionName = field.getApplication() + " : " + _link.getDisplayName();
                    }
                }
            }
        }

        return sectionName;
    }

    /**
     * Helper for newQuestion, given the value returned from
     * the owner DynamicValue, look for some special values that
     * represent indirect references to owners.
     */
    private String resolveOwner(String value) 
        throws GeneralException {

        String owner = null;

        if (Template.OWNER_PARENT.equals(value)) {
            // whatever the parent object is, role or application
            // this is not currently set by the template editor
            Identity idowner = null;
            if (_role != null)
                idowner = _role.getOwner();
            else if (_application != null)
                idowner = _application.getOwner();

            if (idowner != null)
                owner = idowner.getName();
            else {
                // a problem?  
                log.warn("Template parent has no owner!");
            }
        }
        else if (Template.OWNER_ROLE.equals(value)) {
            if (_role == null) {
                // can't use this mode when expanding app templates
                log.warn("Application template can't use " + 
                         Template.OWNER_ROLE);
            }
            else {
                Identity idowner = _role.getOwner();
                if (idowner == null)
                    log.warn("Role has no owner: " + _role.getName());
                else
                    owner = idowner.getName();
            }
        }
        else if (Template.OWNER_APPLICATION.equals(value)) {
            Application app = _application;
            if (app == null) {
                // when this is used in a role template, it means
                // the application assigned to the template
                app = getApplication(_template);
            }
            if (app == null)
                log.warn("Unable to determine application");
            else {
                Identity idowner = app.getOwner();
                if (idowner == null)
                    log.warn("Application has no owner: " + app.getName());
                else
                    owner = idowner.getName();
            }
        }
        else if (Field.OWNER_REQUESTER.equals(value)) {
            if (_project != null) {
                owner = _project.getRequester();
            }
        }
        else {
            // must be a literal name
            owner = value;
        }

        return owner;
    }

    /**
     * Given a Question we're about to add to the project, gather
     * values for dependency fields and save them within the question.
     * This is necessary for dynamic fields that may have their value
     * and allowedValues scripts evaluated later by FormBean.  Since
     * that will be outside the PlanCompiler/TemplateCompiler we won't have
     * easy access to the calculated field values which at the moment are
     * in the _values map.
     *
     * This is only relevant for dynamic fields.
     * We do this for both new questions and previously answered questions
     * in case the values for the static hidden fields changes during
     * form interaction.
     *
     * NOTE: It is important that the Question be for the template that
     * is currently being compiled, we can't simply iterate over everything
     * in the Project._questions list because that can contain fields from
     * different roles or applications and the names of the dependencies
     * could overlap.
     */
    private void addDynamicFieldDependencies(Question q) {
        
        Map<String,Object> args = null;
        Field f = q.getField();
        if (f.isDynamic()) {
            List<String> dependencies = f.getDependencyList();
            if (dependencies != null) {
                args = new HashMap<String,Object>();
                for (String depname : dependencies) {
                    // TODO: If we generated a Question for this
                    // field we don't need to include it here because
                    // the rendered field value will be in the post data.
                    // It's okay to include them as long as FormBean
                    // gives priority to posted fields, it just adds clutter.

                    // Don't really need to do the field lookup or filter
                    // invalid dependencies, just leave null in the map.
                    args.put(depname, _values.get(depname));
                }
            }
        }

        f.setScriptArguments(args);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Unit tests
    //
    //////////////////////////////////////////////////////////////////////

    public static void println(Object o) {
        System.out.println(o);
    }

    public static void main(String [] args) {
        try {
            if (args.length < 1)
                println("TemplateCompiler <filename>");
            else {
                String xml = Util.readFile(args[0]);
                XMLObjectFactory xof = XMLObjectFactory.getInstance();
                Object o = xof.parseXml(null, xml, true);
                if (!(o instanceof Template))
                    println("Test file does not contain a Template");
                else {
                    Template tmp = (Template)o;

                    TemplateCompiler tc = new TemplateCompiler(null);
                    List<Field> ordered = tc.orderFields(tmp.getFields());
                    if (ordered == null || ordered.size() == 0)
                        println("No ordered felds");
                    else {
                        println("Ordered Fields:");
                        for (Field f : ordered) {
                            println(f.getName());
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            log.error(t);
        }
    }
    
    /**
     * Given a list of form items, resolves it to either a Field,Section or FormRef
     * Recursively invokes itself to return a list of fields held by a section within
     * a form referenced by the FormRef.
     * @param items List of FormItems
     * @param result List of Fields
     * @return
     * @throws GeneralException
     */
    public void gatherFields(List<FormItem> items, List<Field> result)
        throws GeneralException {
            if(!Util.isEmpty(items) && result != null) {
                for (FormItem item : items) {
                       if (item instanceof Field) {
                             result.add((Field)item);
                       }
                      else if (item instanceof Section) {
                             gatherFields(((Section)item).getItems(), result);
                      }
                      else if (item instanceof FormRef) {
                          Form form = _context.getObjectById(Form.class, ((FormRef)item).getId());
                          // jsl - need to support sectionless forms, use the
                          // FieldIterator or change the logic

                          gatherFields(new ArrayList<FormItem>(form.getSections()), result);
                      }
              }
            }
    }

}

