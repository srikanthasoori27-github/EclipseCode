/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class to perform bulk exports of objects to XML
 * Not currently considered part of the "public api" but if 
 * it doesn't do anything dangerous should expose it.
 */

package sailpoint.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import sailpoint.api.SailPointContext;
import sailpoint.object.ClassLists;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.tools.XmlParser;
import sailpoint.tools.XmlTools;
import sailpoint.tools.xml.FileXMLBuilder;
import sailpoint.tools.xml.StringXMLBuilder;

public class Exporter {

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

        public void exportingClass(Class cls);
        public void report(SailPointObject obj);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(Exporter.class);

    /**
     * Context to resolve references and save objects.
     */
    SailPointContext _context;

    /**
     * Optional monitor (typically only used by command line console).
     */
    Monitor _monitor;

    /**
     * Flag to terminate early, assuming we're in a managed thread.
     */
    boolean _terminate;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Exporter(SailPointContext con) {
        _context = con;
    }

    public Exporter(SailPointContext con, Monitor m) {
        _context = con;
        _monitor = m;
    }

    public void setMonitor(Monitor m) {
        _monitor = m;
    }

    public void setTerminate(boolean b) {
        _terminate = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Exporting
    //
    //////////////////////////////////////////////////////////////////////
    
    static class CleanArgs
    {
        public CleanArgs()
        {
        }
        
        private Set<String> _propertiesToClean = new HashSet<String>();

        public Set<String> getPropertiesToClean()
        {
            return _propertiesToClean;
        }
        
        public void setPropertiesToClean(Set<String> toClean)
        {
            _propertiesToClean = toClean;
        }

        public String display()
        {
            return 
                new StringBuilder()
                    .append("PropertiesToClean: " + _propertiesToClean)
                    .toString();
        }
    }
    
    public static class CleanArgsCreator
    {
        private static String[] _defaultPropertiesToClean = {"id", "created", "modified", "lastRefresh", "targetId"};
        
        public CleanArgs create(String val)
        {
            CleanArgs args = new CleanArgs();
            
            int pos = val.indexOf("=");
            if (pos < 0)
            {
                addDefaultProperties(args);
                return args;
            }
            
            for (String component : val.substring(pos+1).split(","))
            {
                args.getPropertiesToClean().add(component);
            }
            
            if (args.getPropertiesToClean().size() == 0)
            {
                addDefaultProperties(args);
            }
            
            return args;
        }
        
        private void addDefaultProperties(CleanArgs args)
        {
            for (String property : _defaultPropertiesToClean)
            {
                args.getPropertiesToClean().add(property);
            }
        }
        
    }
    
    static class ExportArgs
    {
        private boolean _cleanRequested = false; 
        
        public boolean isCleanRequested()
        {
            return _cleanRequested;
        }

        public void setCleanRequested(boolean cleanRequested)
        {
            _cleanRequested = cleanRequested;
        }

        private CleanArgs _cleanArgs = new CleanArgs();
        
        public CleanArgs getCleanArgs()
        {
            return _cleanArgs;
        }

        public void setCleanArgs(CleanArgs cleanArgs)
        {
            _cleanArgs = cleanArgs;
        }

        List<Class> _classes = null;

        public List<Class> getClasses()
        {
            return _classes;
        }

        public void setClasses(List<Class> classes)
        {
            _classes = classes;
        }
        
        private String _fileName;
        
        public String getFileName()
        {
            return _fileName;
        }

        public void setFileName(String fileName)
        {
            _fileName = fileName;
        }
        
        boolean _isParseSuccessful = true;

        public boolean isParseSuccessful()
        {
            return _isParseSuccessful;
        }
        
        public void setParseSuccessful(boolean isParseSuccessful)
        {
            _isParseSuccessful = isParseSuccessful;
        }
        
        boolean _isWorkgroup = false;
        
        /**
         * Returns true if the export command contains the Workgroup object. If no
         * objects are specified this will return false, and all objects (including
         * Workgroups) will be exported.
         * @return if the export command arguments contain the Workgroup object
         */
        public boolean isWorkgroup() {
            return _isWorkgroup;
        }
        
        public void setWorkgroup(boolean isWorkgroup) {
            _isWorkgroup = isWorkgroup;
        }
        
        public ExportArgs()
        {
            
        }

        @Override
        public String toString()
        {
            return new StringBuilder()
                .append("IsParseSuccessful: " +_isParseSuccessful)
                .append(" CleanRequested: " + _cleanRequested)
                .append(" Classes: " + _classes)
                .append(" FileName: " + _fileName)
                .append(" CleanArgs: " + _cleanArgs.display())
                .append(" Workgroup: " + _isWorkgroup)
                .toString();
        }
    }

    static class ExportArgsCreator
    {
        public ExportArgsCreator()
        {
            
        }
        
        public ExportArgs create(final List<String> listArgs, final PrintWriter out)
        {
            ExportArgs args = new ExportArgs();
            
            int nargs = listArgs.size();
            
            if (nargs < 1) 
            {
                printUsage(out);
                args.setParseSuccessful(false);
                return args;
            }

            for (String arg : listArgs)
            {
                if (!args.isCleanRequested() && arg.startsWith("-clean"))
                {
                    args.setCleanRequested(true);
                    args.setCleanArgs(new CleanArgsCreator().create(arg));
                    continue;
                }
                
                if (args.getFileName() == null)
                {
                    args.setFileName(arg);
                    continue;
                }
                
                Class<?> cls = null;
                if (arg.toLowerCase().startsWith(SailPointConsole.WORKGROUP_PREFIX)) {
                    args.setWorkgroup(true);
                } else {
                    cls = SailPointConsole.findClass(arg, out);
                }
                
                if (cls != null)
                {
                    if (args.getClasses() == null)
                    {
                        args.setClasses(new ArrayList<Class>());
                    }
                    args.getClasses().add(cls);
                }
            }
            
            if (args.getFileName() == null)
            {
                printUsage(out);
                args.setParseSuccessful(false);
            }
            
            return args;
        }

        private void printUsage(PrintWriter out)
        {
            out.format("export [-clean[=id,createddate...]] <filename> [<class>...]\n");
        }
        
    }    
    
    static class Workgroup {
        /* This class is only used to print Workgroup in Monitor.exportingClass(). Opting to make a new class
         * rather than alter the interface or pass a SailPointConsole.ExportMonitor to Exporter.
         */
    }

    //tqm: this is the original export code
    public void exportRegular(ExportArgs args) throws GeneralException {

        boolean exportAll = false;
        if (args.getClasses() == null && !args.isWorkgroup()) {
            exportAll = true;
            args.setClasses(new ArrayList<Class>());
            for (int i = 0 ; i < ClassLists.ExportClasses.length ; i++)
                args.getClasses().add(ClassLists.ExportClasses[i]);
        }

        FileXMLBuilder builder = new FileXMLBuilder(args.getFileName());

        for (Class cls : Util.safeIterable(args.getClasses())) {
            if (_monitor != null)
                _monitor.exportingClass(cls);

            exportClass(cls, builder, false);
        }
        // export workgroups also
        if (exportAll || args.isWorkgroup()) {
            if (_monitor != null) {
                _monitor.exportingClass(Workgroup.class);
            }
            exportClass(Identity.class, builder, true);
        }

        builder.close();
        
    }

    public void export(ExportArgs args) throws GeneralException {

        // can't reliably use -clean with Identity catch this early
        if (args.isCleanRequested()) {
            List<Class> classes = args.getClasses();
            if (classes != null) {
                for (Class cls : classes) {
                    if (cls == Identity.class)
                        throw new GeneralException("-clean option not allowed for class Identity");
                }
            }
        }

        if (args.isCleanRequested()) {
            new CleanExporter(args).export();
        } else {
            exportRegular(args);
        }
    }
    
    void exportClass(Class cls, FileXMLBuilder builder, boolean processWorkgroup) throws GeneralException {
        // be careful about overloading the cache, fetch one at a time and
        // clear as we go
    
        List<String> props = new ArrayList<String>();
        props.add("id");
        
        QueryOptions qo = (processWorkgroup) ? new QueryOptions(Filter.eq("workgroup", true)) : null;
        if (qo != null) {
            qo.setCloneResults(true);
        } else {
            qo = new QueryOptions();
            qo.setCloneResults(true);
        }
        Iterator<Object[]> it = _context.search(cls, qo, props);
        while (it.hasNext() && !_terminate) {
            
            String id = (String)(it.next()[0]);
            SailPointObject obj = _context.getObjectById(cls, id);
            if (obj != null) {
                if (_monitor != null)
                    _monitor.report(obj);
                
                new ExportVisitor(_context).visit(obj);
                
                obj.toXml(builder);
                
                _context.decache(obj);
    
                // is this a good idea?
                // oddly enough we get a ResultSet closed error if we do this,
                // why does commitTransaction work in the Aggregator?
                //_context.rollbackTransaction();
            }
        }
    }
    
    public static class Cleaner
    {
        private Collection<String> _propsToClean;
        private boolean _stripHeader = false;
        
        public Cleaner(Collection<String> propsToClean)
        {
        	_propsToClean = propsToClean;
        }
        
        public Cleaner(Collection<String> propsToClean, boolean stripHeader)
        {
            _propsToClean = propsToClean;
            _stripHeader = stripHeader;
        }
        
        public String clean(String val)
        {
            try {
                XmlParser parser = XmlParser.getParser(false);
                Element elementToBeCleaned = parser.parse(val, null);
                String doctype = elementToBeCleaned.getNodeName();
                String header = getHeader(doctype);
                if(log.isDebugEnabled() && _stripHeader) {
                    log.debug("Before: " + XmlTools.getFormattedXmlWithoutHeader(elementToBeCleaned));
                } else {
                    log.debug("Before: " + header + XmlTools.getFormattedXmlWithoutHeader(elementToBeCleaned));
                }
                NodeList children = elementToBeCleaned.getOwnerDocument().getChildNodes();
                for(int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    removeProps(child);
                }

                if(log.isDebugEnabled() && _stripHeader) {
                    log.debug("After: " + XmlTools.getFormattedXmlWithoutHeader(elementToBeCleaned));
                } else {
                    log.debug("After: " + header + XmlTools.getFormattedXmlWithoutHeader(elementToBeCleaned));
                }
                
                if(_stripHeader) {
                    return XmlTools.getFormattedXmlWithoutHeader(elementToBeCleaned) +"\n";
                } else {
                    return header + XmlTools.getFormattedXmlWithoutHeader(elementToBeCleaned);
                }

            } catch (Throwable th) {
                log.error("Unable to parse object!", th);
            }
            
            return "";
        }

        private void removeProps(Node node)
        {
            if(node.hasAttributes()) { //remove the offensive ones.
                Element element = (Element)node; //guaranteed to be an Element if it has attributes
                for(String property : _propsToClean)
                {
                    if(element.hasAttribute(property)) {
                        log.debug("Removing: " + property);
                        element.removeAttribute(property);
                    }
                }
            }
            //Recurse into the children
            NodeList nodesToClean = node.getChildNodes();
            for(int i = 0; i < nodesToClean.getLength(); i++) {
                Node childToClean = nodesToClean.item(i);
                removeProps(childToClean);
            }
        }
        
        private String getHeader(String xmlHeaderElement)
        {
            BrandingService brandingService = BrandingServiceFactory.getService();
            String dtdFilename = brandingService.getDtdFilename();
            return "<?xml version='1.0' encoding='UTF-8'?>\n"
                    + "<!DOCTYPE " + xmlHeaderElement + " PUBLIC \"" + dtdFilename + "\" \"" + dtdFilename + "\">\n";
        }
    }
    
    private class CleanExporter
    {
        private ExportArgs _args;
        private List<Pair<Class<?>, QueryOptions>> classPair = new ArrayList<Pair<Class<?>, QueryOptions>>();
        private PrintWriter _writer;
        private Cleaner _cleaner;

        public CleanExporter(ExportArgs args)
        {
            _args = args;
        }

        public void export() 
            throws GeneralException 
        {
        
            updateClassesToExport();
    
            initializeWriter();
            
            initializeCleaner();
            
            writeHeader();
            
            for (Pair<Class<?>, QueryOptions> pair : classPair) {
                exportClass(pair.getFirst(), pair.getSecond());
            }
    
            writeFooter();
            _writer.close();
            
        }
        
        private void exportClass(Class cls, QueryOptions qo) throws GeneralException
        {
            if (_monitor != null) {
                // as of now the only class that uses non-null QueryOptions is the psuedo-class
                // Workgroup. Print that instead
                Class toMonitor = (qo == null) ? cls : Workgroup.class;
                _monitor.exportingClass(toMonitor);
            }

            List<String> props = new ArrayList<String>();
            props.add("id");
            Iterator<Object[]> it = _context.search(cls, qo, props);
            while (it.hasNext() && !_terminate) {
                String id = (String) (it.next()[0]);
                SailPointObject object = _context.getObjectById(cls, id);
                if (object == null) {
                    continue;
                }

                exportObject(object);
            }
        }
        
        private void exportObject(SailPointObject object)
            throws GeneralException
        {
            if (_monitor != null)
            {
                _monitor.report(object);
            }

            new ExportVisitor(_context).visit(object);

            StringXMLBuilder xmlBuilder = new StringXMLBuilder(null);
            object.toXml(xmlBuilder);

            String val = xmlBuilder.toXML();
            val = _cleaner.clean(val);
            
            _writer.print(val);

            _context.decache(object);
        }
        
        private void updateClassesToExport()
        {
            boolean exportAll = false;
            List<Class> classesToAdd;
            
            if (_args.getClasses() == null && !_args.isWorkgroup()) {
                exportAll = true;
                classesToAdd = Arrays.asList(ClassLists.ExportClasses);
            } else {
                classesToAdd = _args.getClasses();
            }
            
            for (Class clazz : Util.safeIterable(classesToAdd)) {
                classPair.add(new Pair<Class<?>, QueryOptions>(clazz, null));
            }
            
            if (exportAll || _args.isWorkgroup()) {
                QueryOptions qo = new QueryOptions(Filter.eq("workgroup", true));
                classPair.add(new Pair<Class<?>, QueryOptions>(Identity.class, qo));
            }
        }

        private void initializeWriter()
            throws GeneralException
        {
            try 
            {
                _writer = new PrintWriter(Util.findOutputFile(_args.getFileName()), "UTF-8");
            }
            catch (IOException e) 
            {
                throw new GeneralException(e);
            }
        }
        
        private void writeHeader()
        {
            BrandingService brandingService = BrandingServiceFactory.getService();
            String dtdFilename = brandingService.getDtdFilename();
            String xmlHeaderElement = brandingService.getXmlHeaderElement();
            _writer.println("<?xml version='1.0' encoding='UTF-8'?>");
            _writer.println("<!DOCTYPE " + xmlHeaderElement + " PUBLIC \"" + dtdFilename + "\" \"" + dtdFilename + "\">");
            _writer.println("<" + xmlHeaderElement + ">");
        }
        
        private void initializeCleaner()
        {
        	_cleaner = new Cleaner(_args.getCleanArgs().getPropertiesToClean(), true);
        }
        
        private void writeFooter()
        {
            BrandingService brandingService = BrandingServiceFactory.getService();
            String xmlHeaderElement = brandingService.getXmlHeaderElement();
            _writer.println("</" + xmlHeaderElement + ">");
        }
    }
}
