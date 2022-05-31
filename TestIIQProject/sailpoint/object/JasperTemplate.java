/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.io.ByteArrayInputStream;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JRXmlWriter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The definition of a JasperTemplate. This is the object where 
 * JasperReport objects can be stored off in the repository. The design
 * is the xml format, but the JasperReport object is the actual 
 * representation of a compiled report. The main difference between a 
 * report and a report design is that reports are already compiled 
 * and validated, so many characteristics are read only.
 * <p>
 */
@XMLClass
public class JasperTemplate extends SailPointObject implements Cloneable {
    private static final long serialVersionUID = 1L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The xml string representing the design of the report. This is
     * what will be stored in the repository and possibly edited 
     * via the web at some point.
     */
    private String _reportXml;

    /**
     * Compiled version of the report, this is not stored and it is 
     * only compiled when requested.
     */
    private JasperReport _report;

  
    /**
     * The actual design object
     */
    private JasperDesign _design;

    /**
     * Construct a new empty JasperTemplate. 
     */
    public JasperTemplate() {
        super();
        _report = null;
        _reportXml = null;
    }

    /**
     * Construct a new JasperTemplate with the containing JasperReport
     * object.
     */
    public JasperTemplate(JasperReport report) {
        this();
        setReport(report);
    }

    @XMLProperty
    public String getName() {
        if ( _name == null ) {
            if ( _design == null ) {
                loadDesign();
            } 
            if ( _design != null ) {
                _name = _design.getName(); 
            }
        } 
        return _name;
    }

    /**
     * Returns the JasperReport object for this Template.
     * <p>
     * If only the xml is available, the report will be compiled and
     * returned.
     */
    public JasperReport getReport() throws GeneralException {
        if ( _report == null ) {
            compile();
        }
        return  _report;
    }

    /**
     * Compile the report, which will validate the xml. This method
     * also names the object based on the name of the compiled object.
     */
    public void compile() throws GeneralException {
        if ( _reportXml != null ) {
            try {
                byte[] byteArray = _reportXml.getBytes("UTF-8"); 
                ByteArrayInputStream baos = new ByteArrayInputStream(byteArray);
                _report = JasperCompileManager.compileReport(baos);
            } catch ( Exception e) {
                _report = null;
                throw new GeneralException(e);
            }
        }
    } 

    /**
     * Set the report xml object. The xml will not be updated until 
     * this object is serialized and getDesignXml is called.
     */
    public void setReport(JasperReport report) {
        _report = report;
    }

    /**
     * Returns a string which is the xml representation of this object.
     * <p>
     * If the reportXml field is empty, but there is a JasperReport  
     * object, serialize the JasperReport to xml.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getDesignXml() {
       if ( _reportXml == null ) {
           if ( _report != null ) 
               _reportXml = JRXmlWriter.writeReport(_report, "UTF-8");
       }
       return _reportXml;
    }
    
    public JasperDesign getDesign() {
        if (_design == null) {
            _design = loadDesign();
        }
        return _design;
    }
    
    public JasperDesign getDesign(boolean forceLoad) {
        if (_design == null || forceLoad) {
            _design = loadDesign();
        }
        return _design;
    }

    /**
     * Set the design xml for this object.
     */
    public void setDesignXml(String xml) {
        _reportXml = xml;
        if ( xml != null ) {
            loadDesign();
            if (_design != null)
                _name = _design.getName();
        }
    }

    private JasperDesign loadDesign() {
        try {
            byte[] byteArray = _reportXml.getBytes("UTF-8"); 
            ByteArrayInputStream baos = new ByteArrayInputStream(byteArray);
            _design = JRXmlLoader.load(baos);
            // set name for kicks
            _name = _design.getName();
        } catch(Exception e ) {
                System.out.println("Exception importing: " + e.toString());
        }
        return _design;
    
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Clone this object.
     * <p>
     * For the Cloneable interface.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
