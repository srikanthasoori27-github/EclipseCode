/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */


package sailpoint.reporting;

import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.fill.AsynchronousFillHandle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;

/** 
 * 
 * Class that launches a Thread for the filling process of Jasper so
 * we can cancel filling once it has started.
 *
 * The only real job of this class is to make sure we create/release
 * a context for the datasources that'll be part of the report.
 *
 */
public class SailPointAsynchronousFiller extends AsynchronousFillHandle {

    private static Log _log = LogFactory.getLog(SailPointAsynchronousFiller.class);

    public SailPointAsynchronousFiller ( JasperReport jasperReport, Map parameters, JRDataSource dataSource) 
        throws JRException {

        super(jasperReport, parameters, dataSource, null);
    }

    public void startFill() {
        synchronized (lock) {
            if (started) {
                throw new IllegalStateException("Fill already started.");
            }
            started = true;
        }
                
        ReportFiller filler = new ReportFiller();
        if (threadName == null) {
            fillThread = new Thread(filler);
        } else {
            fillThread = new Thread(filler, threadName);
        }
                
        if (priority != null) {
            fillThread.setPriority(priority.intValue());
        }
        fillThread.start();
    }

    protected class ReportFiller implements Runnable {

        SailPointContext _threadContext = null;

        public void run() {
             synchronized (lock) {
                 running = true;
             }

             JasperPrint print = null;
             try {
                 init();
                 if (conn != null) {
                    print = filler.fill(parameters, conn);
                 } else {
                     if (dataSource != null) {
                         print = filler.fill(parameters, dataSource);
                     } else {
                         print = filler.fill(parameters);
                     }
                 }
            } catch (Exception e) {
                synchronized (lock) {
                    if (cancelled) {
                        notifyCancel();
                    } else {
                        notifyError(e);
                    }
                } 
            } finally {
                synchronized (lock) {
                    // release the SailPointContext
                    try {
                        SailPointFactory.releaseContext(_threadContext);
                    } catch (GeneralException e) {
                        if (_log.isWarnEnabled())
                            _log.warn("Failed releasing SailPointContext: "
                                     + e.getLocalizedMessage(), e);
                    }
                    notifyFinish(print);
                    running = false;
                }
            }
        }

        /**
         * Since this class is a Thread we can assume there are no
         * other contexts for this thread and create on at the 
         * begining of the fill
         */
        private void init() throws Exception {
            if ( _log.isDebugEnabled() ) 
                _log.debug("Starting report filling THREAD ["+Thread.currentThread().getId()+"]");
            _threadContext = SailPointFactory.createContext();
        }
    }
}
