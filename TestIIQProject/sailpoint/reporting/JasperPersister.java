/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import net.sf.jasperreports.engine.JRExporter;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRPdfExporterParameter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.PersistedFile;
import sailpoint.persistence.PersistedFileOutputStream;
import sailpoint.reporting.export.PageHandler;
import sailpoint.tools.GeneralException;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class JasperPersister {

    private SailPointContext context;
    private JasperPrint print;
    private PageHandler pageHandler;

    public JasperPersister(SailPointContext context, JasperPrint print,
                           PageHandler pageHandler) {
        this.context = context;
        this.print = print;
        this.pageHandler = pageHandler;
    }

    public void persist(PersistedFile file, Attributes<String, Object> options) throws GeneralException{

        PersistedFileOutputStream stream = new PersistedFileOutputStream(context, file);

        JasperExport.OutputType type = file.isPdf() ? JasperExport.OutputType.PDF :
                JasperExport.OutputType.CSV;

        try {
            JRExporter exporter = getExporter(type, options);
            exporter.setParameter(JRPdfExporterParameter.OUTPUT_STREAM, stream);
            exporter.exportReport();
        } catch (Exception e) {
            throw new GeneralException(e);
        }
    }

    private JRExporter getExporter(JasperExport.OutputType outputType, Attributes<String,Object> options){
       JasperExport fact = new JasperExport();
       return fact.getExporter(print, outputType, options, pageHandler);
    }

}
