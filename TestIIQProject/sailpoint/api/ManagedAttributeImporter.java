/* (c) Copyright 2010 SailPoint Technologies, Inc., All 
/**
 *
 * A utility to import ManagedAttribute objects from a csv file.
 * This is an expansion of the file parser formerly found in Explanator.
 * 
 * Author: Jeff
 * 
 * IMPORT/EXPORT FILES
 *
 * The file format allows a variable number of columns and will 
 * be self describing with header comments.  A file may contain the complete
 * set of ManagedAttribute properties or it may be focused to contain only
 * descriptions for one locale.  It is up to the user to design an appropriate
 * column set for their needs.  The available columns are:
 * 
 *   type
 *    - "entitlement" or "permission"
 * 
 *   application
 *    - Application object name
 * 
 *   attribute
 *    - attribute name or Permission target
 *
 *   value
 *    - attribute value, not used for permissions
 * 
 *   displayName
 *    - alternate name to display instead of the value,
 *      not used for permissions
 *       
 *   owner
 *    - Identity name
 * 
 *   requestable
 *    - true or false  
 * 
 *   extended
 *    - the name of any declared extended attribute may be used
 *      as a column name
 *  
 *   locale
 *    - the name of any declared locale may be used as a column name
 * 
 * 
 * The structure of the file is defined by a header comment that must
 * be the first line of the file:
 * 
 *      # col1, col2, col3, ...
 * 
 * for example:
 * 
 *      # application, attribute, value, displayName, owner, requestable
 * 
 *      # application, attribute, value, somethingCustom
 *
 *      # application, attribute, value, En_US
 * 
 * If a type column is omitted the type is assumed to be entitlement.
 * 
 * A default value for any column may be specified on lines after the first one.
 * If the file contains only lines related to one application you do
 * not need to duplicate the application name in a column, instead give
 * it a default:
 * 
 *      # attribute, value, displayName, owner, requestable
 *      # application=Active Directory
 *      ...
 *
 * 
 * While will allow full flexibility in import files, we will only export
 * in two formats: object files and description files.  The type of export
 * and export options will be selected in a popup dialog.  Besides the
 * type you may also select one or more applications, and if exporting
 * descriptions one or more languages.  An object file will contain 
 * these columns:
 *   
 *   type, application, attribute, value, displayName, owner, requestable
 * 
 * Plus columns for all extended attributes.  It will not contain the
 * localized descriptions.  We default the columns if possible so if
 * only one Application is selected and that application doesn't support
 * permissions the type, application, and attribute columns will be omitted.
 * 
 * A description export file will have these columns:
 * 
 *   type, application, attribute, value
 * 
 * Plus one column for each selected locale.  Again we will default
 * columns if we can so type will usually be omitted and application
 * and attribute will be ommitted if you are only exporting for
 * one application.  
 * 
 * For the selected locales the column header will be the standard
 * locale identifier.  It is possible to have more than one in the file:
 * 
 *   # value, En_US, En_GB, Fr_FR
 *   # application=Active Directory
 *   # attribute=memberOf
 * 
 * I doubt this would happen in practice though since descriptions tend 
 * to be long and will not look good if several of them are 
 * in the same row.
 * 
 * Column values will be surrounded with quotes only when necessary.
 * This will be necessary if the value contains comma, double
 * quote, or line break characters.  Examples:
 * 
 *   # value, En_US
 *   # application=Some App
 *   # attribute=groups
 * 
 *   Marketing, A fine group of people.
 *   Engineering, "A group of raconteurs, scallywags, and  rogues."
 *   Sales, "An ""interesting"" group of people."
 *
 * PARSING ALGORITHM
 *
 * For each data line, there are two phases.  In phase one we isolate
 * the type, application, attribute, scheamObjectType and value properties.
 * All of these are required to uniquely identify an MA.  Then we find an MA
 * with matching properties or create a new one.
 *
 * In the second phase we iterate over Column descriptors that describe
 * how to store the values of columns other than the 5 from phase 1 in
 * the MA.  Finally we save the MA.
 * 
 */

package sailpoint.api;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.primefaces.model.UploadedFile;

import sailpoint.api.monitor.ImmediateMonitor;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Classification;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectClassification;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Source;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.RFC4180LineIterator;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * The ManagedAttributer provides utility methods for searching,
 * reading, and creating ManagedAttribute objects.  It is also used
 * by the Identitizer to promote attribute values from links into
 * ManagedAttributes.
 */
public class ManagedAttributeImporter {

    //////////////////////////////////////////////////////////////////////
    //
    // Column
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A little structure describing file columns, built from
     * the column declaration in the header comments.
     * These will only be built for the non "identity" columns:
     * type, application, attribute, and value.
     *
     * One of these will also be built for any default attributes
     * we find. In this case the value will be set and the position 
     * will be -1.
     */
    public class Column {

        /**
         * Name of the column.
         */
        String name;    

        /**
         * The position in the csv.
         */
        int number = -1;

        /**
         * Default value.
         */
        String defaultValue;

        /**
         * A Method on the ManagedAttributeImporter class responsible
         * for setting the column value into a ManagedAttribute object.
         */
        Method setter;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(ManagedAttributeImporter.class);

    /**
     * Names of our fixed columns.
     */
    private static final String COL_TYPE = "type";
    private static final String COL_APPLICATION = "application";
    private static final String COL_ATTRIBUTE = "attribute";
    private static final String COL_VALUE = "value";
    private static final String COL_DISPLAY = "displayName";
    private static final String COL_OWNER = "owner";
    private static final String COL_REQUESTABLE = "requestable";
    private static final String COL_CLASSIFICATIONS = "classifications";

    /**
     * Everyone loves context.
     */
    private SailPointContext _context;

    /**
     * Optional monitor used by the console.
     */
    ImmediateMonitor _monitor;

    /**
     * Columnn numbers for the five identity properties, parsed
     * from the header comments. -1 means they are not in the 
     * columns and must have defaults defined.
     */
    int _typeColumn;
    int _applicationColumn;
    int _attributeColumn;
    int _valueColumn;

    /**
     * Set when we have parsed enough column infomration
     * to proceed.
     */
    boolean _ready;

    /**
     * Columns in this file parsed from the header comments.
     */
    List<Column> _columns;

    /**
     * The number of columns we expect to see in data lines.
     */
    int _expectedColumns;

    /**
     * Column default values parsed from the header comments
     * It never makes sense to default value and displayName.
     */
    String _defaultType = ManagedAttribute.Type.Entitlement.name();
    Application _defaultApplication;
    Application _lastApplication;
    String _defaultAttribute;

    /**
     * Number of lines we've parsed.
     */
    int _lines;

    /**
     * Number of comment lines we've parsed.
     */
    int _comments;
    
    /**
     * Number of errors we've hit.
     */
    int _errors;

    /**
     * True to parse the file but not persist anything.
     */
    boolean _test;


    //////////////////////////////////////////////////////////////////////
    //
    // Import
    //
    //////////////////////////////////////////////////////////////////////

    public ManagedAttributeImporter(SailPointContext con) {
        _context = con;
    }

    public void setMonitor(ImmediateMonitor monitor) {
        _monitor = monitor;
    }
    
    public ImmediateMonitor getMonitor() {
        return _monitor;
    }

    public void setTestMode(boolean b) {
        _test = b;
    }

    /**
     * Import ManagedAttribute properties from a file specified 
     * in the IIQ console.
     *
     * See file format comments at the top of the file.
     */
    public int importFile(File importFile)
        throws IOException, GeneralException {

        if (log.isTraceEnabled()) {
            log.trace("Importing explanations via console:");
            log.trace("    file: " + importFile.getName());
        }

        BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(importFile), "UTF8"));

        return processImportFile(br);
    }

    /**
     * Import ManagedAttribute properties from a file uploaded via the UI.
     */
    public int importUpload(UploadedFile importFile)
        throws IOException, GeneralException {

        if (log.isTraceEnabled()) {
            log.trace("Importing explanations via UI:");
            log.trace("    file: " + importFile.getFileName());
        }

        byte[] fileBytes = new byte[(int) importFile.getSize()];
        fileBytes = importFile.getContents();

        BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(fileBytes), "UTF8"));

        return processImportFile(br);
    }

    /**
     * Emit an error message.
     * These are traced at info level even though they are errors to prevent
     * excessive clutter in the log if they've got a fundamentally bad file.
     */
    private void error(Message msg) {
        
        if (_monitor != null)
            _monitor.error(msg);

        else if (log.isErrorEnabled())
            log.error(msg);
    }
    
    /**
     * Emit a warning message.
     * These are traced at info level even though they are errors to prevent
     * excessive clutter in the log if they've got a fundamentally bad file.
     */
    private void warn(Message msg) {
        
        if (_monitor != null)
            _monitor.warn(msg);

        else if (log.isWarnEnabled())
            log.warn(msg);
    }

    /**
     * Does the work of processing the import file, parsing the lines into
     * tokens, validating each line, creating ManagedAttribute objects
     * using the  tokens and saving the explanations to the db.
     */
    private int processImportFile(BufferedReader br)
        throws IOException, GeneralException {

        if (br == null) {
            Message errorMsg = new Message(MessageKeys.EXPLANATION_IMPORT_FILE_CONTENTS_UNAVAILABLE);
            error(errorMsg);
            throw new GeneralException(errorMsg);
        }

        _lines = 0;
        _errors = 0;
        _comments = 0;

        _ready = false;
        _typeColumn = -1;
        _applicationColumn = -1;
        _attributeColumn = -1;
        _valueColumn = -1;
        _columns = null;
        _expectedColumns = 0;
        _defaultType = ManagedAttribute.Type.Entitlement.name();
        _defaultApplication = null;
        _lastApplication = null;
        _defaultAttribute = null;

        // build the line iterator that can handle CRLF's within a value
        RFC4180LineIterator it = new RFC4180LineIterator(br);

        // build the RFC-compliant parser 
        RFC4180LineParser parser = new RFC4180LineParser(',');
        parser.tolerateMissingColumns(true);

        // for args passed to the column setters
        // Column, String, ManagedAttribute
        Object[] args = new Object[3];

        String line;
        while ((line = it.readLine()) != null) {
            _lines++;

            if (_monitor != null)
                _monitor.info(new Message(MessageKeys.EXPLANATION_IMPORT_IMPORTING, line));

            else if (log.isTraceEnabled())
                log.trace("Importing: " + line);


            // skip comment lines
            if (line.startsWith("#")) {
                parseComment(line);
                continue;
            }

            // parse the line of CSV data
            List<String> data = parser.parseLine(line);

            // blank line - no special care needed
            if (data == null)
                continue;

            // parser does not appear to trim surrounding 
            // whitespace, do that now so the setters don't
            // have to deal with it.
            for (int i = 0 ; i < data.size() ; i++) {
                String s = data.get(i);
                if (s != null) {
                    s = s.trim();
                    if ("".equals(s)) {
                        // replacing empty strings with null.  Error checking below
                        // catches nulls, not empty strings.
                        s = null;
                    }
                    data.set(i, s);
                }
            }

            // must have parsed headers by now
            // TODO: Determine readiness
            if (!_ready) {
                Message errorMsg = new Message(MessageKeys.EXPLANATION_IMPORT_MISSING_COLUMN_DECLARATIONS);
                error(errorMsg);
                throw new GeneralException(errorMsg);
            }

            // if there aren't the expected number of tokens, log and continue
            if (data.size() != _expectedColumns) {
                _errors++;
                error(new Message(Message.Type.Error, MessageKeys.EXPLANATION_IMPORT_INVALID_LINE, _lines, line ));
                error(new Message(Message.Type.Error, MessageKeys.EXPLANATION_IMPORT_EXPECTING_COLUMNS, _expectedColumns));
                continue;
            }
            
            // get the identity properties
            String type = getType(data);
            Application app = getApplication(data);
            String att = getAttribute(data);
            boolean perm = (ManagedAttribute.Type.Permission.name().equals(type));
            String value = getValue(data, perm);

            boolean isValid = true;
            ArrayList<String> invalidProperties = new ArrayList<String>();
            
            if (type == null) {
                invalidProperties.add("type");
                isValid = false;
            }
            
            if (app == null) {
                invalidProperties.add("application");
                isValid = false;
            }

            if (perm && att == null) {
                invalidProperties.add("attribute");
                isValid = false;
            }

            //If Type att is null and type is not an AccountGroup type, throw error.
            if (!perm && att == null && (type.equalsIgnoreCase(ManagedAttribute.Type.Entitlement.name()) ||
                                        type.equalsIgnoreCase(ManagedAttribute.Type.Permission.name())))
            {
                invalidProperties.add("attribute");
                isValid = false;
            }
            
            if (!perm && value == null) {
                invalidProperties.add("value");
            }
            
            if (!isValid) {
                _errors++;
                error(new Message(MessageKeys.EXPLANATION_IMPORT_INVALID_PROPERTIES_LINE, _lines, line));
                error(new Message(MessageKeys.EXPLANATION_IMPORT_INVALID_PROPERTIES, Util.listToCsv(invalidProperties)));
            }
            else {
                // locate the object
                boolean fail = false;
                //If we have an attribute, use (App/Value/Attribute) for lookup. Otherwise, pass objType so we will use
                //(App/Type/Value)
                ManagedAttribute ma = ManagedAttributer.get(_context, app, perm, att, value, att == null ? type : null);
                if (ma == null) {
                    ma = new ManagedAttribute();

                    String matype = type;
                    if (perm) {
                        matype = ManagedAttribute.Type.Permission.name();
                    }
                    //If no type supplied, default to Entitlement
                    // jsl - this is probably wrong post 6.4, we expect type to be the schema name, old
                    // files should be fixed
                    ma.setType(matype != null ? matype : ManagedAttribute.Type.Entitlement.name());
                    ma.setApplication(app);

                    String maAtt = att;
                    //If we have a type but no attribute, look to see if we should set an attribute for this type
                    if(Util.isNullOrEmpty(maAtt) && Util.isNotNullOrEmpty(type)) {
                        AttributeDefinition ad = app.getGroupAttribute(type);
                        if (ad != null) {
                            maAtt = ad.getName();
                        }
                    }
                    ma.setAttribute(maAtt);

                    ma.setValue(value);

                    ma.setHash(ManagedAttributer.getHash(ma));
                    
                    //We do not allow creating AccountGroups via Import. We will always set type to Entitlement/Permission
                    //when creating new MA. Therefore, we need an attribute for the MA to have a unique key.
                    if(maAtt == null && ManagedAttribute.Type.Entitlement.name().equals(ma.getType())) {
                        _errors++;
                        error(new Message(MessageKeys.EXPLANATION_IMPORT_MISSING_ATTRIBUTE_NAME, line));
                        continue;
                    }
                }

                // set the remaining non-identity columns
                for (int i = 0  ; i < _columns.size() ; i++) {
                    Column col = _columns.get(i);
                    String colvalue = null;
                    if (col.number >= 0) {
                        colvalue = data.get(col.number);
                    }
                    args[0] = col;
                    args[1] = colvalue;
                    args[2] = ma;
                    // Returning non-null means failure, will have
                    // already logged.
                    Object result = null;
                    try {
                        if (col.setter != null)
                            result = col.setter.invoke(this, args);
                        else {
                            // should have caught these by now
                            log.error("Column with no setter method! " + col.name);
                        }
                    }
                    catch (Throwable t) {
                        // won't happen
                        log.error(t);
                    }
                            
                    if (result != null) {
                        fail = true;
                        break;
                    }
                }

                if (!fail) {
                    if (_test) {
                        if (_monitor != null)
                            _monitor.info(new Message(ma.toXml()));
                    }
                    else {
                        _context.saveObject(ma);
                        _context.commitTransaction();

                        // keep the Explanator cache updated within this JVM
                        Explanator.refresh(ma);

                        // Decache after refreshing to avoid lazy issues with classifications
                        _context.decache(ma);
                        // !! periodic full Hibernate decache
                    }
                }
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Import complete:");
            log.trace("    total lines processed: " + _lines);
            log.trace("    bad data lines: " + _errors);
            log.trace("    ManagedAttributes created: " + getAttributesCreated());
        }

        return _errors;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Declaration Parsing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Parse a comment line.
     * Here if the line starts with #, ideally we should allow
     * preceeding whitespace.
     *
     * The first line must be the column descriptors.
     * Following lines may have defaults, though defaults
     * are also allowed in the column descriptors.
     *
     *   type=permission, application=AD, ...
     *
     * !! Hey if we need to support default values that contain
     * commas will need to use a fancier parser.
     *
     * If you put a default in a column descriptor, the file must
     * contain that column, but if the value is null the default is
     * used.  Defaults outside the column descriptor are always set
     * unless there is also a column descriptor.  Examples:
     *
     * 1) Force owner to Jeff
     *
     *     # value, En_US
     *     # application=AD
     *     # attribute=MemberOf
     *     # owner=Jeff
     *
     * 2) Set owner to Jeff unless the file overrides it
     *     
     *     # value, En_US, owner
     *     # application=AD
     *     # attribute=MemberOf
     *     # owner=Jeff
     *
     *     foo, Some description,
     *     bar, Some description, Dave
     *
     *  3) same as 2, with default int the column descriptor
     *
     *     # value, En_US, owner=Jeff
     *     # application=AD
     *     # attribute=MemberOf
     *
     *     foo, Some description,
     *     bar, Some description, Dave
     */
    private void parseComment(String line) throws GeneralException {

        // skip #
        line = line.substring(1);
        
        // tokenize by comma
        List<String> tokens = Util.csvToList(line);
        
        if (tokens != null && tokens.size() > 0) {

            // first line is the column declarations
            boolean first = (_columns == null);
            if (first)
                _expectedColumns = tokens.size();

            for (int i = 0 ; i < tokens.size() ; i++) {
                String name = tokens.get(i);
                if (name == null) {
                    log.warn("Skipping blank token: " + line);
                } else {
                    name = name.trim();
                    String dflt = null;
    
                    if (name.length() == 0) {
                        error(new Message(Type.Error, MessageKeys.EXPLANATION_IMPORT_MALFORMED_COLUMN_DECLARATION, line));
                        throw new GeneralException("Malformed column declaration: " + line);
                    }
    
                    if (name.indexOf("=") > 0) {
                        List<String> deftokens = splitDefault(name);
                        name = deftokens.get(0);
                        dflt = deftokens.get(1);
                    }
    
                    // secondary line without a value
                    if (dflt == null && !first) {
                        Message errorMsg = new Message(Type.Error, MessageKeys.EXPLANATION_IMPORT_MALFORMED_COLUMN_DECLARATION, line);
                        error(errorMsg);
                        throw new GeneralException(errorMsg);
                    }
                    
                    // these four are the identity attributes, they
                    // are not optional and not represented as Columns
                    if (name.equals(COL_TYPE)) {
                        if (first) _typeColumn = i;
                        if (dflt != null) {
                            _defaultType = dflt;
                        }
                    }
                    else if (name.equals(COL_APPLICATION)) {
                        if (first) _applicationColumn = i;
                        if (dflt != null) {
                            _defaultApplication = _context.getObjectByName(Application.class, dflt);
                            if (_defaultApplication == null) {
                                Message errorMsg = new Message(Type.Error, MessageKeys.EXPLANATION_IMPORT_INVALID_APPLICATION_NAME, dflt); 
                                error(errorMsg);
                                throw new GeneralException(errorMsg);
                            }
                        }
                    }
                    else if (name.equals(COL_ATTRIBUTE)) {
                        if (first) _attributeColumn = i;
                        _defaultAttribute = dflt;
                    }
                    else if (name.equals(COL_VALUE)) {
                        // this only makes sense on the first line without a default
                        if (!first) {
                            Message errorMsg = new Message(Type.Error, MessageKeys.EXPLANATION_IMPORT_INVALID_DEFAULT_VALUE, line);
                            error(errorMsg);
                            throw new GeneralException(errorMsg);
                        }
                        
                        // could soften this
                        if (dflt != null) {
                            Message errorMsg = new Message(Type.Error, MessageKeys.EXPLANATION_IMPORT_DEFAULT_NOT_ALLOWED, line);
                            error(errorMsg);
                            throw new GeneralException(errorMsg);
                        }
                        
                        _valueColumn = i;
                    } else {
                        Column col = getColumn(name);
                        if (first && col != null) {
                            Message errorMsg = new Message(MessageKeys.EXPLANATION_IMPORT_REDUNDANT_COLUMN, line);
                            error(errorMsg);
                            throw new GeneralException(errorMsg);
                        }
                        
                        if (col == null) {
                            col = new Column();
                            col.name = name;
                            if (first)
                                col.number = i;
                            addColumn(col);
                        }
                        col.defaultValue = dflt;

                        if (name.equals(COL_DISPLAY)) {
                            col.setter = getSetter("setDisplayName");
                        }
                        else if (name.equals(COL_OWNER)) {
                            col.setter = getSetter("setOwner");
                        }
                        else if (name.equals(COL_REQUESTABLE)) {
                            col.setter = getSetter("setRequestable");
                        }
                        else if (name.equals(COL_CLASSIFICATIONS)) {
                            col.setter = getSetter("setClassifications");
                        }
                        else if (isExtended(name)) {
                            ObjectConfig oconfig = ManagedAttribute.getObjectConfig();
                            if (oconfig != null) {
                                ObjectAttribute att = oconfig.getObjectAttribute(name);
                                if (att != null) {
                                    if (ObjectAttribute.TYPE_IDENTITY.equals(att.getType())) {
                                        col.setter = getSetter("setExtendedIdentity");
                                    } else if (ObjectAttribute.TYPE_DATE.equals(att.getType())) {
                                        col.setter = getSetter("setExtendedDate");
                                    } else if (ObjectAttribute.TYPE_BOOLEAN.equals(att.getType())) {
                                        col.setter = getSetter("setExtendedBoolean");
                                    } else {
                                        col.setter = getSetter("setExtended");
                                    }
                                }
                            }
                        }
                        else {
                            // must be a locale identifier
                            // it never makes sense to default these?
                            Configuration config = _context.getConfiguration();
                            List langs = config.getList(Configuration.SUPPORTED_LANGUAGES);
                            if (langs == null || !langs.contains(name)) {
                                Message errorMsg = new Message(MessageKeys.EXPLANATION_IMPORT_NOT_EXTENDED_OR_LOCALE, name);
                                error(errorMsg);
                                throw new GeneralException(errorMsg);
                            }
                            col.setter = getSetter("setDescription");
                        }
                    }
                }
            }
        }

        _comments++;
        
        // set this when we have enough to start
        _ready = ((_defaultType != null || _typeColumn >= 0) &&
                  (_defaultApplication != null || _applicationColumn >= 0) &&
                  ((_defaultAttribute != null || _attributeColumn >= 0) ||
                          _typeColumn >= 0) &&
                  (_valueColumn >= 0));
    }
    
    /**
     * Do the reflection to look up one of our column setter methods.
     */
    private Method getSetter(String name) {

        Method m = null;
        try {
            m = ManagedAttributeImporter.class.getMethod(name, 
                                                         Column.class, 
                                                         String.class, 
                                                         ManagedAttribute.class);
        }
        catch (NoSuchMethodException e) {
            log.error(e);
        }
        catch (SecurityException e) {
            log.error(e);
        }

        return m;
    }

    /**
     * Split a "token=token" string into a list of two strings.
     * NOTE: The big assumption here is that an attribute name cannot
     * have an = character in it.  That's pretty safe but we've never
     * formally said that.  It would only apply to extended attributes since
     * our built-in column names or locale identifiers won't use =.
     */
    private List<String> splitDefault(String line)
        throws GeneralException {

        int psn = line.indexOf("=");
        if (psn <= 0 || psn >= (line.length() - 2)) {
            Message errorMsg = new Message(Type.Error, MessageKeys.EXPLANATION_IMPORT_MALFORMED_DEFAULT_VALUE, line);
            error(errorMsg);
            throw new GeneralException(errorMsg);
        }
            

        String name = line.substring(0, psn).trim().toLowerCase();
        if (name.length() == 0) {
            Message errorMsg = new Message(Type.Error, MessageKeys.EXPLANATION_IMPORT_MALFORMED_DEFAULT_VALUE, line);
            error(errorMsg);
            throw new GeneralException(errorMsg);
        }
        
        String value = line.substring(psn + 1).trim();
        if (value.length() == 0) {
            Message errorMsg = new Message(Type.Error, MessageKeys.EXPLANATION_IMPORT_MALFORMED_DEFAULT_VALUE, line);
            error(errorMsg);
            throw new GeneralException(errorMsg);
        }

        List<String> result = new ArrayList<String>();
        result.add(name);
        result.add(value);
        
        return result;
    }

    /**
     * Look for a Column by name.
     */
    private Column getColumn(String name) {
        Column found = null;
        if (_columns != null) {
            for (Column c : _columns) {
                if (c.name.equals(name)) {
                    found = c;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Add a column to the list.
     */
    private void addColumn(Column col)  {
        if (_columns == null)
            _columns = new ArrayList<Column>();
        _columns.add(col);
    }

    /**
     * Return true if this is the name of an extended attribute.
     */
    private boolean isExtended(String name) {
        boolean extended = false;
        ObjectConfig oconfig = ManagedAttribute.getObjectConfig();
        if (oconfig != null) {
            ObjectAttribute att = oconfig.getObjectAttribute(name);
            extended = (att != null);
        }
        return extended;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Data Line Parsing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the type of the next MA.
     */
    private String getType(List<String> data) {

        String type = _defaultType;
        if (_typeColumn >= 0) {
            String value = data.get(_typeColumn);
            if (value != null) {
                type = value;
            }
        }
        return type;
    }


    /**
     * Get the Application of the next MA.
     */
    private Application getApplication(List<String> data) 
        throws GeneralException {

        Application app = _defaultApplication;
        if (_applicationColumn >= 0) {
            String value = data.get(_applicationColumn);
            if (value != null) {
                if (_lastApplication != null && 
                    value.equals(_lastApplication.getName())) {
                    app = _lastApplication;
                }
                else {
                    app = _context.getObjectByName(Application.class, value);
                    if (app != null)
                        _lastApplication = app;
                    else {
                        Message errorMsg = new Message(Message.Type.Error, MessageKeys.EXPLANATION_IMPORT_INVALID_APPLICATION_NAME, _lines, value); 
                        error(errorMsg);
                        throw new GeneralException(errorMsg);
                    }
                }
            }
        }
        return app;
    }

    /**
     * Get the attribute of the next MA.
     */
    private String getAttribute(List<String> data) {

        String att = _defaultAttribute;
        if (_attributeColumn >= 0) {
            att = data.get(_attributeColumn);
        }
        return att;
    }

    /**
     * Get the value of the next MA.
     * It never makes sense to default these.
     */
    private String getValue(List<String> data, boolean isPermission) {

        String value = (_valueColumn >= 0) ? data.get(_valueColumn) : null;
        if (value == null && !isPermission)
            warn(new Message(Message.Type.Error, MessageKeys.EXPLANATION_IMPORT_MISSING_VALUE, _lines));

        return value;
    }

    /**
     * Column setter method for the display name.
     * Return null for success, failure value is currently undefined.
     * Has to be public for reflection.
     */
    public String setDisplayName(Column col, String value, ManagedAttribute ma) {
        if (value == null) value = col.defaultValue;
        ma.setDisplayName(value);
        return null;
    }

    /**
     * Column setter method for the requestable flag.
     * Has to be public for reflection.
     */
    public String setRequestable(Column col, String value, ManagedAttribute ma) {
        if (value == null) value = col.defaultValue;
        ma.setRequestable(Util.otob(value));
        return null;
    }

    /**
     * Column setter method for classifications.
     * Has to be public for reflection.
     * @throws GeneralException 
     */
    public String setClassifications(Column col, String value, ManagedAttribute ma) throws GeneralException {
        String result = null;

        List<String> names = Util.csvToList(value);

        if (Util.isEmpty(names)) {
            ma.setClassifications(null);
        } else  {
            Map<String, Classification> clsMap = new HashMap<>();
            for (ObjectClassification ocls : Util.safeIterable(ma.getClassifications())) {
                clsMap.put(ocls.getClassification().getName(), ocls.getClassification());
            }
            
            //for add new ones
            for (String clsName : names) {
                if (!clsMap.containsKey(clsName)) {
                    Classification cls = _context.getObjectByName(Classification.class, clsName);
                    if (cls != null) {
                        ma.addClassification(cls, Source.UI.name(), false);
                    }
                    else {
                        error(new Message(Message.Type.Error, MessageKeys.EXPLANATION_IMPORT_UNRESOLVED_CLASSIFICATION_NAME, _lines, clsName));
                        // should this cause the line to fail? probably
                        result = "Unresolved classification";
                    }
                }
            }
            
            //for remove existing that not in the import line
            for (String existName : clsMap.keySet()) {
                if (!names.contains(existName)) {
                    ma.removeClassification(clsMap.get(existName));
                }
            }
        }
        
        return result;
    }

    /**
     * Column setter method for the owner.
     * Has to be public for reflection.
     */
    public String setOwner(Column col, String value, ManagedAttribute ma) 
        throws GeneralException {

        String result = null;

        // since this will be a db hit, we should cache this in the Column!
        String name = value;
        if (name == null) 
            name = col.defaultValue;

        if (name != null) {
            Identity owner = _context.getObjectByName(Identity.class, name);
            if (owner != null)
                ma.setOwner(owner);
            else {
                error(new Message(Message.Type.Error, MessageKeys.EXPLANATION_IMPORT_UNRESOLVED_OWNER_NAME, _lines, name));
                // should this cause the line to fail? probably
                result = "Unresolved owner";
            }
        } else {
            //Since there's a blank value for the owner, we should null out any possible existing values.  IIQETN-4256.
            ma.setOwner(null);
        }
        return result;
    }

    /**
     * Column setter method for an extended attribute.
     * Has to be public for reflection.
     */
    public String setExtended(Column col, String value, ManagedAttribute ma) {
        if (value == null) value = col.defaultValue;
        ma.put(col.name, value);
        return null;
    }

    /**
     * Column setter method for an extended Identity attribute.
     * Has to be public for reflection.
     */
    public String setExtendedIdentity(Column col, String value, ManagedAttribute ma)
        throws GeneralException {

        String result = null;

        String name = value;
        if (name == null) {
            name = col.defaultValue;
       }

        if (name != null) {
            Identity id = _context.getObjectByName(Identity.class, name);
            if (id != null) {
                ma.put(col.name, id.getId());
             } else {
                error(new Message(Message.Type.Error, MessageKeys.EXPLANATION_IMPORT_UNRESOLVED_OWNER_NAME, _lines, name));
                // should this cause the line to fail? probably
                result = "Unresolved owner";
            }
        } else {
            ma.put(col.name, null);
        }
        return result;
    }

    /**
     * Column setter method for an extended Date attribute.
     * Has to be public for reflection.
     */
    public String setExtendedDate(Column col, String value, ManagedAttribute ma)
        throws GeneralException {

        String result = null;

        String utime = value;
        if (utime == null) {
            utime = col.defaultValue;
        }

        if (utime != null) {
            long date = Util.atol(utime);
            if (date != 0) {
                ma.put(col.name, new Date(date));
            } else {
                error(new Message(Message.Type.Error, MessageKeys.EXPLANATION_IMPORT_INVALID_DATE, _lines, utime));
                result = "invalid date";
            }
        } else {
            ma.put(col.name, null);
        }
        return result;
    }

    /**
     * Column setter method for an extended Boolean attribute.
     * Has to be public for reflection.
     */
    public String setExtendedBoolean(Column col, String value, ManagedAttribute ma)
        throws GeneralException {

        String bool = value;
        if (bool == null) {
            bool = col.defaultValue;
        }

        if (bool != null) {
            Boolean newBool = Boolean.parseBoolean(bool);
            ma.put(col.name, newBool);
        } else {
            ma.put(col.name, null);
        }
        return null;
    }

    /**
     * Column setter method for localized descriptions.
     * Assume locale validation was done when the header was parsed,
     * we can trust it at this point.
     * Has to be public for reflection.
     */
    public String setDescription(Column col, String value, ManagedAttribute ma) {
        // note, this will not change the value if it is null
        // do we need that to bulk erase descriptions?
        ma.addDescription(col.name, value);
        return null;
    }
    
    /**
     * @return The number of attributes that this importer created -- used for logging and/or messages
     */
    public int getAttributesCreated() {
        return _lines - _errors - _comments;
    }

}
