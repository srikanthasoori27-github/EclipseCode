/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * This provides a way to define Filters whose property names
 * may reference several application account links or the identity
 * cube attribute.   The scope of the property is defined by a prefix
 * convention to avoid complicating the Filter model.
 *
 *     Filter.eq("scope:costCenter", "42");
 *
 * Property names without a prefix are assumed to be identity 
 * attributes.  This is used in combination with Matchmaker
 * to do complex matching of Identity objects and their associated
 * Links.
 *
 * Author: Jeff
 * 
 * TODO: I don't like decorating with actual unique ids, may want
 * something more abstract and readable.  The problem then is maintaining
 * the dictionary of abstract names and actual objects.
 *
 */

package sailpoint.object;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.tools.xml.SerializationMode;

/**
 * Used within <code>IdentitySelector</code> to define filters
 * whose property names might reference attributes from several
 * application accounts as well as attributes in the IdentityIQ
 * identity cube.  
 * 
 * The scope of the property is defined by a prefix
 * convention to avoid complicating the Filter model.
 *
 *     Filter.eq("1:costCenter", "42");
 *
 * Property names without a prefix are assumed to be identity 
 * attributes. This is used in combination with Matchmaker
 * to do complex matching of Identity objects and their associated
 * Links.
 *
 * Prefixes are usually numbers that are then used as indexes
 * into the <code>applications</code> list.
 *
 * Prefixes can also be application names, but this is uncommon
 * since names might contain spaces and this is not allowed
 * in the string representation of filters.
 */
@XMLClass
public class CompoundFilter extends AbstractXmlObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(CompoundFilter.class);

    /**
     * Usual XML preamble we glue onto manually entered XML.
     */
    private static final String dtdFilename = BrandingServiceFactory.getService().getDtdFilename();
    public static final String XML_PREAMBLE_FILTER = 
        "<?xml version='1.0' encoding='UTF-8'?>\n<!DOCTYPE Filter PUBLIC '" + dtdFilename + "' '" + dtdFilename + "'>\n";

    public static final String XML_PREAMBLE_COMPOUND = 
        "<?xml version='1.0' encoding='UTF-8'?>\n<!DOCTYPE CompoundFilter PUBLIC '" + dtdFilename + "' '" + dtdFilename + "'>\n";

    /**
     * A list of applications that can be referenced in the filter.
     * This is mostly for documentation when reading the XML since
     * the prefixes are unique identifiers.
     */
    List<Application> _applications;

    /**
     * The Filter with mixed properties.
     */
    Filter _filter;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public CompoundFilter() {
    }

    /**
     * A list of applications that can be referenced in the filter.
     * Numeric prefixes in the property name are indexes into this list.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Application> getApplications() {
        return _applications;
    }

    public void setApplications(List<Application> apps) {
        _applications = apps;
    }

    /**
     * A filter with a mixture of application and IdentityIQ identity
     * property references.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Filter getFilter() {
        return _filter;
    }

    public void setFilter(Filter f) {
        _filter = f;
    }

    /**
     * This is used only for IdentitySelector evaluation. Applications have
     * a lot of stuff attached to them but we only need the name.
     */
    public void load() {
        if (_applications != null) {
            for (Application app : _applications)
                app.getName();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Render the string representation of the filter for editing
     * in a text area.
     *
     * @ignore
     * Sigh, I wanted to use the FilterRenderer but it can't deal
     * with application prefixes containing spaces and we don't
     * have a syntax for defining the application list.  Something
     * like this may be possible:
     *
     *   $1 E-Order Management System
     *   1:memberOf.containsAll({"foo"})
     *
     * OR
     *
     *   "E-Order Management System":memberOf.containsAll(...
     * 
     * But these are going to be hard to get passed the parser
     * which assumes Java conventions for identifiers.
     */
    public String render() {
        String src = null;
        if (_filter != null || 
            (_applications != null && _applications.size() > 0)) {

            //FilterRenderer fr = new FilterRenderer(_filter);
            //src = fr.render();

            // in theory rendering can throw but catch and ignore
            // so we can get the thing into the editor and fixed
            try {
                src = this.toXml();
                // strip off the XML for editing
                int start = src.indexOf("<Compound");
                if (start > 0)
                    src = src.substring(start);
            }
            catch (Throwable t) {
                log.error(t.getMessage(), t);
            }
       }
        return src;
    }

    /**
     * Parse the string representation of a filter, throwing exceptions
     * if syntax errors are encountered. This does not modify the
     * CompoundFilter.
     */
    public void parse(XMLReferenceResolver res, String src) 
        throws GeneralException {

        parseUpdate(res, src, false);
    }

    /**
     * Parse the string representation of a filter and modify 
     * this CompoundFilter if the syntax is valid.
     */
    public void update(XMLReferenceResolver res, String src) 
        throws GeneralException {

        parseUpdate(res, src, true);
    }
    
    /**
     * Internal method for parsing and updating.
     */
    private void parseUpdate(XMLReferenceResolver res, String src, boolean update)
        throws GeneralException {

        Object o = null;

        // if we could render with FilterRenderer then we would
        // use this after filtering out the application declarations
        //Filter f = Filter.compile(src);

        if (src != null) {
            src = src.trim();
            if (src.indexOf("<!DOCTYPE") < 0) {
                // hmm, this is ugly could wrap it all in 
                // a <sailpoint> instead
                if (src.startsWith("<Filter"))
                    src = XML_PREAMBLE_FILTER + src;
                else
                    src = XML_PREAMBLE_COMPOUND + src;
            }

            XMLObjectFactory f = XMLObjectFactory.getInstance();
            o = f.parseXml(res, src, true);
        }

        if (update) {
            if (o == null) {
                _filter = null;
                _applications = null;
            }
            else if (o instanceof Filter) {
                _filter = (Filter)o;
                _applications = null;
            }
            else if (o instanceof CompoundFilter) {
                CompoundFilter neu = (CompoundFilter)o;
                _filter = neu.getFilter();
                _applications = neu.getApplications();
            }
        }
    }

    /**
     * Get an application given an index.
     */ 
    public Application getApplication(int index) {
        Application found = null;
        if (_applications != null && index >= 0 && index < _applications.size())
            found = _applications.get(index);
        return found;
    }

}
