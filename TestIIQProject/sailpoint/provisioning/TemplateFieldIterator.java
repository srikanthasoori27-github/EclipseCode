package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.FormItem;
import sailpoint.object.FormRef;
import sailpoint.object.Resolver;
import sailpoint.object.Template;
import sailpoint.object.Form.Section;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Iterates through the list of fields in a template. If the template references a form
 * it resolves the form and retrieves its respective FormItem objects and extracts the fields
 * within the form items incorporating them within a list and returning the iterator to the list
 * @author alevi.d'costa
 */
public class TemplateFieldIterator implements Iterator<Field> {

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Template whose fields to be resolved and retrieved
     */
    private Template _template;
    /**
     * Resolver to resolve the form references
     */
    private Resolver _resolver;
    /**
     * Fields retrieved from form references
     */
    private List<Field> _fields;
    /**
     * Iterator on the list of fields
     */
    private Iterator<Field> _currentIterator;

    private static Log log = LogFactory.getLog(TemplateCompiler.class);
    
    public TemplateFieldIterator(Template template, Resolver resolver) {
        _template = template;
        _resolver = resolver;
        _fields = new ArrayList<Field>();
    }

    /**
     * Has more fields?
     */
    @Override
    public boolean hasNext() {
        if(_fields.isEmpty()) {
            try {
                if(_template != null) {
                    gatherFields(_template);
                }
            } catch (GeneralException e) {
                throw new RuntimeException("Failed to retrieve fields from template.", e);
            } 
            _currentIterator = _fields.iterator();
        }
        return _currentIterator.hasNext();
    }

    /**
     * Return the next field
     */
    @Override
    public Field next() {
        return _currentIterator.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * Gathers fields of a template
     * @param template template whose fields are to be retrieved
     * @throws GeneralException
     */
    private void gatherFields(Template template)
        throws GeneralException {
        try {
            gatherFields(template.getFormItems(), _fields);
        } catch (GeneralException e) {
            throw e;
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
    private void gatherFields(List<FormItem> items, List<Field> result)
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
                       boolean useName = false;
                      String refVar = ((FormRef)item).getId();
                      if(refVar == null) {
                          refVar = ((FormRef) item).getName();
                          useName = true;
                      }

                      Form form = useName ? _resolver.getObjectByName(Form.class, refVar) : _resolver.getObjectById(Form.class, refVar);
                      if (!Util.isEmpty(form.getSections())) {
                          gatherFields(new ArrayList<FormItem>(form.getSections()), result);
                      }
                  }
            }
        }
    }

}
