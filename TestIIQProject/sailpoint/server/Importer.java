/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class to perform bulk imports of objects
 * from an XML file.  Not currently considered
 * part of the "public api" but if it doesn't do anything
 * dangerous should expose it.
 * Author: Jeff
 */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.ImportAction;
import sailpoint.object.JasperTemplate;
import sailpoint.object.SailPointObject;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLReferenceResolver;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class Importer implements XMLReferenceResolver {

    //////////////////////////////////////////////////////////////////////
    //
    // Monitor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Interface that may be implemented by something that wants
     * to be notified as the export progresses.
     */
    public interface Monitor {

        public void report(SailPointObject obj);
        public void includingFile(String fileName);
        public void mergingObject(SailPointObject obj);
        public void executing(ImportExecutor executor);
        public void info(String msg);
        public void warn(String msg);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The XML tag used to wrap multiple SailPointObjects.
     */
    public static final String EL_SAILPOINT = BrandingServiceFactory.getService().getXmlHeaderElement();

    /**
     * The XML tag used for unwrapped Jasper reports.
     */
    public static final String EL_JASPER_REPORT = "jasperReport";

    /**
     * The XML tag used for special import actions.
     */
    public static final String EL_ACTION = "ImportAction";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(Importer.class);

    /**
     * Context to resolve references and save objects.
     */
    SailPointContext _context;

    /**
     * Context to resolve references and save objects.
     */
    XMLReferenceResolver _xmlResolver;

    /**
     * Factory object for special parsing.
     */
    XMLObjectFactory _factory;

    /**
     * Optional object that wants to be notified as things progress.
     */
    Monitor _monitor;

    /**
     * Optional Class that (when specified) will cause only objects that
     * are of this type (or a subclass) of this type to get imported.
     */
    Class<? extends SailPointObject> _classToImport;

    /**
     * Enables auto-creation of unresolved references.
     */
    boolean _autoCreate = true;

    /**
     * Enables errors on unresolved references.
     */
    boolean _strictReferences;

    /**
     * Ignores ids in the import file.
     */
    boolean _scrubIds;

    /**
     * Import Bundles with Role Propagation capability.
     */
    boolean _isRolePropEnabled = false;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Importer(XMLReferenceResolver resolver) {
        _xmlResolver = resolver;
    }
    
    public Importer(XMLReferenceResolver resolver, Monitor mon) {
        _xmlResolver = resolver;
        _monitor = mon;
    }

    public Importer(SailPointContext con) {
        _context = con;
    }

    public Importer(SailPointContext con, Monitor mon) {
        _context = con;
        _monitor = mon;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Setters
    //
    //////////////////////////////////////////////////////////////////////

    public void setClassToImport(Class<? extends SailPointObject> clazz) {
        _classToImport = clazz;
    }

    public void setAutoCreate(boolean b) {
        _autoCreate = b;
    }

    public void setStrictReferences(boolean b) {
        _strictReferences = b;
    }

    public void setScrubIds(boolean b) {
        _scrubIds = b;
    }

    public void setContext(SailPointContext ctx) {
        _context = ctx;
    }
    
    public void setXMLReferenceResolver(XMLReferenceResolver r) {
        _xmlResolver = r;
    }

    public void setRolePropEnabled(boolean b) {
        _isRolePropEnabled = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // XMLReferenceResolver
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Usually a SailPointContext is passed in as the XMLReferenceResolver
     * but here we implement it ourselves so we can provide some additional
     * features, like auto-creation of referenced objects or strict reference
     * error reporting.
     * 
     * This has to be able to throw exceptions, but the interface doesn't
     * allow it.
     */
    public Object getReferencedObject(String className, String id, String name)
        throws GeneralException {

        Object obj = null;
        
        // Ignore autocreate if the id and name are both null
        // this can happen when objects that don't have names
        // are referenced and the parent object is exported
        // with the -clean option.  One example is UIPreferences
        if (id == null && name == null)
            return null;

        // Delegate to the assigned resolver if there is one.
        if (null != _xmlResolver) {
            obj = _xmlResolver.getReferencedObject(className, id, name);
        }
        else {
            // let the context do the usual lookup
            obj = _context.getReferencedObject(className, id, name);

            if (obj == null && Util.isNotNullOrEmpty(name)) {
                Class clazz = null;
                try {
                    clazz = Class.forName(className);
                    if (!AbstractSailPointContext.supportsLookupByName(clazz)) {
                        // Handle the outlier case (for backward-compatibility) where: we are
                        // importing and have a <Reference class="cls" name="objname" />
                        // to an already existing object by name alone, but the class declares that it
                        // doesn't support lookup by name.  Go ahead and try to resolve it by name.
                        obj = _context.getObjectByName(clazz, name);
                    }
                }
                catch (ClassNotFoundException e) {
                    throw new GeneralException(e);
                }
            }
        }

        if (obj == null) {
            if (_autoCreate) {
                // Supporting this means that SailPointObject subclasses
                // must not have any non-null constraints.  If that 
                // becomes necessary for some reason, we'll need a way
                // to detect that here, and promote this to an error.

                // sigh, have to duplicate a lot of the class lookup
                // logic from AbstractSailPointFactory

                if (className == null || className.length() == 0)
                    throw new GeneralException("Missing class name");

                // convenience for hand written files
                if (className.indexOf(".") < 0)
                    className = "sailpoint.object." + className;

                try {
                    


                    Class c = Class.forName(className);
                    SailPointObject spo = (SailPointObject)c.newInstance();
                    spo.setId(id);
                    spo.setName(name);  
                    // Have to call import rather than save so we can
                    // have pre-defined ids.  Ugh, while I think calling
                    // importObject is the right thing I'm fighting a bunch
                    // of Hibernate obscurities right now so leave it the
                    // old way until the current round of failures is
                    // sorted out - jsl
                    // import seems to work, as long as transaction gets committed -rap
                    _context.importObject(spo);
//                    _context.saveObject(spo);
                    _context.commitTransaction();
                    obj = spo;
                }
                catch (ClassNotFoundException e) {
                    // bad class name
                    throw new GeneralException(e);
                }
                catch (InstantiationException e) {
                    // no-arg constructor was not defined
                    throw new GeneralException(e);
                }
                catch (IllegalAccessException e) {
                    // no-arg constructor was not public
                    throw new GeneralException(e);
                }
                catch (ClassCastException e) {
                    // not a SailPointObject
                    throw new GeneralException(e);
                }
            }
            else if (_strictReferences) {
                StringBuffer b = new StringBuffer();
                b.append("Reference to unknown object: ");
                // this must exist by now
                b.append(className);
                b.append(" ");
                // but these are optional
                if (name != null)
                    b.append(name);
                if (id != null) {
                    b.append("(");
                    b.append(id);
                    b.append(")");
                }
                throw new GeneralException(b.toString());
            }
        }

        return obj;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Importing
    //
    //////////////////////////////////////////////////////////////////////

    public List<ImportCommand> getCommands(String xml) throws GeneralException {
        return importXml(xml, false, null);
    }

    public void importXml(String xml) throws GeneralException {
        importXml(xml, true, null);
    }

    public void importXml(String xml, ImportExecutor.Context context)
        throws GeneralException {
        importXml(xml, true, context);
    }

    /**
     * Import one or more objects defined as an XML string.
     * Currently we assume that the caller will commit the transaction.
     */
    private List<ImportCommand> importXml(String xml, boolean executeCommands,
                                          ImportExecutor.Context context)
        throws GeneralException {

        List<ImportCommand> cmds = new ArrayList<ImportCommand>();
        _factory = XMLObjectFactory.getInstance();

        if (xml != null) {

            log.info("Beginning import");

            // SailPointObject will call XMLObjectFactory that injects
            // a dtd string built at runtime.  We'll do the same to make
            // sure we get the same DTD, but I would much rather have
            // a concrete DTD file!!
            // KLUDGE: If this contains a jasperReport, disable the DTD
            // insertion and let it resolve normally, we should hook
            // the resolver and redirect into our classpath.
            String dtd = null;
            if (xml.indexOf("DOCTYPE jasperReport") < 0) {
                dtd = _factory.getDTD();
            }

            // Look for the jasper schema definition. If found, don't bother validating since
            // we only support DTDs. Let the report writer be responsible to ensure that
            // their jasper jrxml documents are valid.
            boolean hasJasperSchema = xml.indexOf("xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"") > -1;

            Element root = null;
            if (hasJasperSchema){
                root = XmlUtil.parse(xml);
            } else {
                root = XmlUtil.parse(xml, dtd, true);
            }

            if (root != null) {

                // Check for jasperReport elements so we can handle
                // them right from the report editor and put them in our object.
                String tagName = root.getTagName();

                if (EL_JASPER_REPORT.compareTo(tagName) == 0 ) {
                    JasperTemplate template = new JasperTemplate();
                    // this will validate the xml 
                    template.setDesignXml(xml); 

                    ImportCommand cmd = new ImportCommand.Save(null, tagName, template, _classToImport);
                    cmds.add(cmd);
                    execute(cmd, executeCommands, context);
                } 
                else if (EL_SAILPOINT.equals(tagName)) {
                    for (Element child = XmlUtil.getChildElement(root) ; 
                         child != null ;
                         child = XmlUtil.getNextElement(child)) {

                        tagName = child.getTagName();
                        log.info("Parsing " + tagName);
                        
                        if (EL_ACTION.equals(tagName)) {
                            Object o = _factory.parseElement(this, child);
                            if (o instanceof ImportAction)
                                cmds.add(processAction((ImportAction)o, executeCommands, context));
                            else {
                                // what the heck is this?
                                log.error("Invalid action tag");
                            }
                        }
                        else {
                            // it must be a SailPointObject
                            scrub(child);
                            SailPointObject o = (SailPointObject)
                                SailPointObject.parseXml(this, child);
                            ImportCommand cmd = null;
                            // If Role Propagation is enabled, store RoleChangeEvents with this role.
                            if (_isRolePropEnabled && o instanceof Bundle) {
                                cmd = new ImportCommand.SaveRoleChangeEvents(null, tagName, o, _classToImport);
                            } else {
                                cmd = new ImportCommand.Save(null, tagName, o, _classToImport);
                            }
                            cmds.add(cmd);
                            execute(cmd, executeCommands, context);
                        }
                    }
                }
                else {
                    // expected to be a single SailPointObject
                    log.info("Parsing " + root.getTagName());
                    scrub(root);
                    SailPointObject o = (SailPointObject)
                        SailPointObject.parseXml(this, root);
                    ImportCommand cmd = null;
                    // If Role Propagation is enabled, store RoleChangeEvents with this role.
                    if (_isRolePropEnabled && o instanceof Bundle) {
                        cmd = new ImportCommand.SaveRoleChangeEvents(null, tagName, o, _classToImport);
                    } else {
                        cmd = new ImportCommand.Save(null, tagName, o, _classToImport);
                    }
                    cmds.add(cmd);
                    execute(cmd, executeCommands, context);
                }
            }
        }

        return cmds;
    }

    /**
     * Remove things from the DOM tree before parsing.
     * This was added to remove id="..." attributes because we frequently
     * need to import things that were exported from other dbs and importing
     * foreign ids can cause all sorts of problems, mostly if you have
     * child objects with ids.
     *
     * It is difficult to hook this in during parsing because there isn't
     * a good way to pass state down to AnnotationSerializer which would need
     * a list of attributes to ignore.  Instead we'll preprocess the DOM 
     * tree before parsing.
     */
    private void scrub(Element e) {
        
        if (_scrubIds)
            scrub(e, "id");
    }

    /**
     * Scrub one attribute from the DOM tree.
     */
    private void scrub(Element e, String name) {

        e.removeAttribute(name);

        for (Element child = XmlUtil.getChildElement(e) ; 
             child != null ;
             child = XmlUtil.getNextElement(child))
            scrub(child);
    }

    /**
     * Execute the given ImportCommand if execute is true.  If supplied, use the
     * given Context otherwise use a Context that uses the fields on this
     * Importer.
     */
    private void execute(ImportCommand cmd, boolean execute,
                         ImportCommand.Context context)
        throws GeneralException {

        if (!execute) 
            return;

        if (null == context) {
            context = new ImportCommand.Context() {
                public Connection getConnection() throws GeneralException {
                    return _context.getJdbcConnection();
                }
    
                public SailPointContext getContext() {
                    return _context;
                }
    
                public Monitor getMonitor() {
                    return _monitor;
                }
            };
        }

        cmd.execute(context);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    // Obviously more general than it needs to be but trying to leave
    // things open for future expansion.
    //
    //////////////////////////////////////////////////////////////////////

    private ImportCommand processAction(ImportAction action, boolean execute,
                                        ImportExecutor.Context context) 
        throws GeneralException {

        ImportCommand cmd = null;

        String name = action.getName();
        if (ImportAction.MERGE.equals(name)) {
            cmd = new ImportCommand.Merge(action.getSystemVersion(), action);
        } else if (ImportAction.MERGE_IF_NULL.equals(name)) {
            cmd = new ImportCommand.MergeIfNull(action.getSystemVersion(), action);
        } else if (ImportAction.INCLUDE.equals(name)) {
            cmd = new ImportCommand.Include(action.getSystemVersion(), action, this);
        }
        else if (ImportAction.EXECUTE.equals(name)) {
            cmd = new ImportCommand.Execute(action.getSystemVersion(), action);
        }
        else if (ImportAction.LOG_CONFIG.equals(name)) {
            cmd = new ImportCommand.LogConfig(action.getSystemVersion(), action);
        } 
        else if (ImportAction.MERGE_CONNECTOR_REGISTRY.equals(name) ) {
            cmd = new ImportCommand.ConnectorRegistryMerger(action.getSystemVersion(), action);
        }
        else if (ImportAction.INSTALL_PLUGIN.equals(name)) {
            cmd = new ImportCommand.InstallPlugin(action.getSystemVersion(), action);
        }
        else {
            _monitor.info("Unknown import action: " + name);
            log.error("Unknown import action: " + name);
        }

        if (null != cmd) {
            execute(cmd, execute, context);
        }

        return cmd;
    }
}
