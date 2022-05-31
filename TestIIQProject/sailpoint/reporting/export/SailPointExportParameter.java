/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */


package sailpoint.reporting.export;

import net.sf.jasperreports.engine.JRExporterParameter;


public class SailPointExportParameter extends JRExporterParameter {

    public static final SailPointExportParameter PAGE_HANDLER = 
        new SailPointExportParameter("pageHandler");

    public SailPointExportParameter(String name) {
        super(name);
    }
}
