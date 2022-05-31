/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 *
 * A utility to export ManagedAttribute objects from a csv file.
 * See ManagedAttributeImporter for more on the syntax of this file.
 * 
 * Author: Jeff
 * 
 * 
 */

package sailpoint.api;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.server.SailPointConsole.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Rfc4180CsvBuilder;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

public class ManagedAttributeExporter {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(ManagedAttributeExporter.class);

    SailPointContext _context;
    Monitor _monitor;
    PrintWriter _writer;

    /**
     * The list of applications whose MAs will be exported.
     * If this is null all MAs are exported.
     */
    List<Application> _applications;

    /**
     * The list of localized descriptions to export.
     * If this list is null then we will do an object property
     * export.  If this is set we do a description export.
     * Usually there will only be one language the he list.
     */
    List<String> _languages;

    /**
     * Flag that may be set from the outside to terminate the export.
     * For the usual case where we're streaming to a file this won't be
     * set but if we need to wrap this in a TaskExecutor someday we'll
     * need to be able to stop it.
     */
    boolean _terminate;

    //
    // Runtime fields
    //

    /**
     * Set internally if we determine that we need to include a type column.
     * We try to suppress this if all rows will be Entitlement.
     */
    boolean _includeType;

    /**
     * Set internally if we determine that we need to include an application
     * name column.  We try to suppress this if we're only exporting
     * for one app.
     */
    boolean _includeApplication;

    /**
     * Set if we're doing a description export.
     */
    boolean _doDescriptions;

    /**
     * The list of extended attribute definitions from the ObjectConfig.
     */
    List<ObjectAttribute> _extended;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ManagedAttributeExporter(SailPointContext con) {
        _context = con;
    }

    public void setApplications(List<Application> apps) {
        _applications = apps;
    }
    
    public void setLanguages(List<String> langs) {
        _languages = langs;
    }

    public void addApplication(Application app) {
        if (app != null) {
            if (_applications == null)
                _applications = new ArrayList<Application>();
            _applications.add(app);
        }
    }

    public void addLanguage(String lang) {
        if (lang != null) {
            if (_languages == null)
                _languages = new ArrayList<String>();
            _languages.add(lang);
        }
    }

    public void setMonitor(Monitor m) {
        _monitor = m;
    }

    public void setTerminate(boolean b) {
        _terminate = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Export
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * If the language list is empty this will do an object export of the
     * selected applications.  If the language list is set it will do a 
     * description export.
     */
    public void export(PrintWriter writer)
        throws GeneralException {

        _writer = writer;

        // emit the header comments and set the inclusion flags
        emitHeader();

        if (_applications != null) {
            // do these by application so they come out grouped without
            // having to pay for an orderBy
            for (Application app : _applications) {
                export(app);
                _context.decache();
                if (_terminate)
                    break;
             }
        }
        else {
            // if we have a lot of apps the result can be massive and we
            // can't risk a lenghty ordering, iterate by Application
            List<String> props = new ArrayList();
            props.add("id");
            Iterator<Object[]> result = _context.search(Application.class, null, props);
            while (result.hasNext()) {
                String id = (String)(result.next()[0]);
                Application app = _context.getObjectById(Application.class, id);
                export(app);
                _context.decache();
                if (_terminate)
                    break;
            }
        }
    }

    /**
     * Export the MAs for one Application.
     */
    private void export(Application app) 
        throws GeneralException {

        // query for MA ids
        List<String> props = new ArrayList<String>();
        props.add("id");

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application", app));

        // only necessary if we have more than one MA, 
        // this could be expensive!!
        ops.addOrdering("attribute", true);
        ops.setScopeResults(true);
                
        Iterator<Object[]> result = _context.search(ManagedAttribute.class, ops, props);
        export(result);
    }

    /**
     * Determine what columns we're going to export and emit
     * the header comments.  Suppress columns if we can.
     */
    private void emitHeader() throws GeneralException {

        List<String> columns = new ArrayList();

        _includeType = isTypeNeeded();
        _includeApplication = (_applications == null || _applications.size() == 0 || _applications.size() > 1);
        _doDescriptions = (_languages != null && _languages.size() > 0);
        _extended = null;

        if (_includeType)
            columns.add("type");

        if (_includeApplication)
            columns.add("application");

        // could suppress attribute if all the applications only have
        // one attribute with the same name
        columns.add("attribute");
        columns.add("value");
        columns.add("displayName");

        if (_doDescriptions) {
            columns.addAll(_languages);
        }
        else {
            columns.add("owner");
            columns.add("requestable");
            columns.add("classifications");
            
            ObjectConfig config = _context.getObjectByName(ObjectConfig.class, ObjectConfig.MANAGED_ATTRIBUTE);
            if (config != null) {
                _extended = config.getObjectAttributes();
                if (_extended != null) {
                    for (ObjectAttribute att : _extended)
                        columns.add(att.getName());
                }
            }
        }


        String header = Util.listToCsv(columns);
        if (log.isInfoEnabled())
            log.info("Column header: " + header);

        _writer.print("# ");
        _writer.println(Util.listToCsv(columns));

        // type implicitily defaults to Entitlement
        if (!_includeApplication) {
            Application app = _applications.get(0);
            _writer.printf("# application=%s\n", app.getName());
        }
    }

    /**
     * Determine if we need to emit a type column for this export.  This
     * is true if any of the applications being exported have MAs that
     * have type Permission or AccountGroups. This is relatively rare so reduce clutter
     * in the export file by supressing the column.
     *
     * This assumes that the type column is indexed.  Check performance
     * on this carefully we can't afford a lot of up front wait time or
     * else the stream will close.
     */
    private boolean isTypeNeeded() 
        throws GeneralException {

        boolean needed = false;

        if (_applications == null) {
            if (log.isInfoEnabled())
                log.info("Exporting ManagedAttributes for all Applications");
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.ne("type", ManagedAttribute.Type.Entitlement.name()));
            int count = _context.countObjects(ManagedAttribute.class, ops);
            needed = count > 0;
        }
        else {
            for (Application app : _applications) {
                if (log.isInfoEnabled())
                    log.info("Exporting ManagedAttributes for Application " + app.getName());

                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("application", app));
                ops.add(Filter.ne("type", ManagedAttribute.Type.Entitlement.name()));
                int count = _context.countObjects(ManagedAttribute.class, ops);
                if (count > 0) {
                    needed = true;
                    break;
                }
            }
        }

        return needed;
    }

    /**
     * Inner exporter that iterates over a search result.
     */
    private void export(Iterator<Object[]> result) 
        throws GeneralException {

        StringBuilder buf = new StringBuilder();
        int cacheAge = 0;

        while (result.hasNext() && !_terminate) {
            Object[] row = result.next();
            String id = (String)row[0];
            ManagedAttribute ma = _context.getObjectById(ManagedAttribute.class, id);

            buf.setLength(0);
            boolean needsComma = false;

            // type
            if (_includeType) {
                String type = ma.getType();
                if (type == null)
                    type = ManagedAttribute.Type.Entitlement.name();
                buf.append(type);
                needsComma = true;
            }

            // application
            if (_includeApplication) {
                if (needsComma) buf.append(",");
                Application app = ma.getApplication();
                if (app != null)
                    buf.append(escape(app.getName()));
                else {
                    // can't happen
                    log.error("Encountererd ManagedAttribute without Application");
                }
                needsComma = true;
            }
            
            // attribute
            if (needsComma) buf.append(",");
            buf.append(escape(ma.getAttribute()));

            // value
            buf.append(",");
            buf.append(escape(ma.getValue()));

            // displayName
            buf.append(",");
            buf.append(escape(ma.getDisplayName()));

            if (_doDescriptions) {
                if (_languages != null) {
                    for (String lang : _languages) {
                        String desc = ma.getDescription(lang, false);
                        // we have to have a column no matter what
                        // may want a special value to represent missing?
                        buf.append(",");
                        buf.append(escape(desc));
                    }
                }
            }
            else {

                // owner
                buf.append(",");
                Identity owner = ma.getOwner();
                if (owner != null)
                    buf.append(escape(owner.getName()));
                
                // requestable, could use 1/0 to save space
                buf.append(",");
                if (ma.isRequestable())
                    buf.append("true");
                else
                    buf.append("false");

                // classifications
                buf.append(",");
                buf.append(WebUtil.buildCSVField(Util.listToCsv(ma.getClassificationNames())));
                
                if (_extended != null) {
                    for (ObjectAttribute ext : _extended) {
                        buf.append(",");
                        Object value = ma.getAttribute(ext.getName());
                        if (value != null) {
                            // We don't have extended Identity attributes yet
                            // so only Date needs special treatment.
                            String etype = ext.getType();
                            if (ObjectAttribute.TYPE_DATE.equals(etype)) {
                                if (value instanceof Date) {
                                    long utime = ((Date)value).getTime();
                                    buf.append(utime);
                                }
                                else {
                                    // Not supposed to happen, assume it's a utime
                                    buf.append(escape(value.toString()));
                                }
                            } else if (ObjectAttribute.TYPE_IDENTITY.equals(etype)){
                                //use identity name for any ideneity reference
                                Identity referenceIdentity = ObjectUtil.getIdentityOrWorkgroup(_context, value.toString());
                                if(referenceIdentity != null) {
                                    buf.append(referenceIdentity.getName());
                                } else {
                                    buf.append(value.toString());
                                }
                            }
                            else {
                                buf.append(escape(value.toString()));
                            }
                        }
                    }
                }
            }

            _writer.println(buf.toString());

            _context.decache(ma);
            cacheAge++;
            if (cacheAge > 100) {
                _context.decache();
                cacheAge = 0;
            }
        }
    }

    /**
     * Escape a value so it can be included nicely in a csv column.
     */
    private String escape(String src) {
        // Escape formula injection
        String result = Rfc4180CsvBuilder.escapeFormulaInjection(src);
        if (src != null && 
            (src.indexOf("\"") >= 0 ||   
             src.indexOf(",") >= 0 ||
             src.indexOf("\n") >= 0)) {

            result = WebUtil.buildCSVField(src);
        }

        // prevent "null" from being a column value
        if (result == null) result = "";

        return result;
    }

}
