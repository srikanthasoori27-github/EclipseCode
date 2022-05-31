/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Date;
import java.util.Comparator;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.connector.DelimitedFileConnector;
import sailpoint.connector.ConnectorException;


public class ApplicationDelimitedDataSource extends ApplicationDataSource {
  
  /*
   * This is a sort of struct to hold file information.  Each app will have
   * one of these associated with it.
   */
  private class FileInfo {
    public String fileName;
    public Long lastModified;
    public Long fileSize; 
    public boolean exists = false;
  }
  
  /*
   * _appMapsFile is a map where the application is associated with its
   * FileInfo object.  This will be used for sorting the report and for easily
   * retrieving file information that has previously been sniffed out.
   */
  private static HashMap<String, FileInfo> _appMapsFile = new HashMap<String,FileInfo>();
  
  private static final Log log = 
    LogFactory.getLog(ApplicationDelimitedDataSource.class);
  
  public ApplicationDelimitedDataSource(List<Filter> filters, Locale locale,
      TimeZone timezone) {
    super(filters, locale, timezone);
    setScope(Application.class);
  }

  public ApplicationDelimitedDataSource(List<Filter> filters,
      List<Filter> subFilters, Locale locale, TimeZone timezone) {
    super(filters, subFilters, locale, timezone);
  }
  
  /*
   * This prepares the data set for assimilation by the report.
   * Since we're attempting to sort the report by the file timestamp, which
   * is unsaved and unknown at the time of the query, we have to create our
   * own list, get file information, then sort by that file information,
   * and finally feed that list back to the report.
   */
  @Override
  public void internalPrepare() throws GeneralException {
    String appName = "unknown";
    String fileName;
    try {
      
      List<Application> apps = getContext().getObjects(Application.class, qo);
      
      for(Iterator<Application> it = apps.iterator(); it.hasNext();) {
        Application app = it.next();
        appName = app.getName();
        fileName = null;
        
        // Filter out applications that are not local files.
        if("local".equals(app.getStringAttributeValue("filetransport"))) {
          
          DelimitedFileConnector dfc = new DelimitedFileConnector(app);
          
          try {
              Schema accountSchema = app.getSchema("account");
              if (accountSchema != null) {
                  fileName = dfc.getFileName(accountSchema);
              }
          } catch (ConnectorException ce) {
              log.debug("Exception thrown while acquiring schema or file name from application " 
                  + appName + ". " + "Exception [" + ce.getMessage() + "].");
              // bug #5146: At this point, either there is no schema or there is no file name.
              // Either way, it's as if the filename is not defined, so we continue with fileName = null.
          }
          
          if (fileName != null) {
            File file = getFileHandle(fileName);
            
            FileInfo info = new FileInfo();
            info.fileName = fileName;
            info.fileSize = file.length();
            info.lastModified = file.lastModified();
            info.exists = file.exists();
            
            // In the hashmap, associate the application id with its file information.
            _appMapsFile.put(app.getId(), info);
          } else {
            _appMapsFile.put(app.getId(), new FileInfo());
          }
        } else {
          it.remove();
        }
      }
      
      // Sort the list using a custom comparator.
      Collections.sort(apps, new SortListByMapValue());
      
      // Feed the list to the report.
      _objects = apps.iterator();
      
    } catch (GeneralException ge){
      log.error("Exception thrown while gathering file information from application " + appName + ". " 
          + "Exception [" + ge.getMessage() + "].");
    } 
  }
  
  /* (non-Javadoc)
   * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
   */
  public Object getFieldValue(JRField jrField) throws JRException {

    String fieldName = jrField.getName();
    Object value = null;
  
    if (fieldName.equals("fileName")) {
      value = _appMapsFile.get(_object.getId()).fileName;
              
    } else if (fieldName.equals("fileDate")) {
      FileInfo info = _appMapsFile.get(_object.getId());
      
      if (info.exists) {
        value = new Date(info.lastModified);
      }
      
    } else if (fieldName.equals("fileSize")) {
      FileInfo info = _appMapsFile.get(_object.getId());
      
      if (info.exists) {
        java.text.DecimalFormat thousandths = new java.text.DecimalFormat();
        thousandths.setMaximumFractionDigits(3);
        value = thousandths.format(info.fileSize/1000000F);
      } else {
          value = getMessage(MessageKeys.REPT_APP_DELIMITED_MISSING_FILE);
      }
      
    } else if (fieldName.equals("refreshDate")) {
      value = _object.getDateAttributeValue("acctAggregationStart");
      
    } else if (fieldName.equals("daysOld")) {
      FileInfo info = _appMapsFile.get(_object.getId());
      
      if (info.exists) {
        java.text.DecimalFormat tenths = new java.text.DecimalFormat();
        tenths.setMaximumFractionDigits(1);
        Date now = new Date();
        value = tenths.format((now.getTime() - info.lastModified) / (double)(24*60*60*1000));
      } else {
        value = getMessage(MessageKeys.REPT_APP_DELIMITED_MISSING_FILE);
      }
    } 

    if(value==null)
      value = super.getFieldValue(jrField);
    return value;
  }


  private File getFileHandle(String fileName) throws GeneralException {
    // sniff the file see if it's relative
    File file = new File(fileName);
    if ( ( !file.isAbsolute() ) && ( !file.exists() ) ) {
        // see if we can append sphome and find it
        String appHome = Util.getApplicationHome();
        if ( appHome != null ) {
            file = new File(appHome + File.separator + fileName);
            if ( !file.exists() ) 
                file = new File(fileName);
        }
    }
    return file;
  }
  
  private class SortListByMapValue implements Comparator<Application> {
    
    /*
     * (non-Javadoc)
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     */
    public int compare(Application a1, Application a2) {
      FileInfo a1Info = _appMapsFile.get(a1.getId());
      FileInfo a2Info = _appMapsFile.get(a2.getId());
      
      // We want to sort by the timestamp of the file that is associated with the application.
      // Also, we're putting non-existing and filesize == 0 applications at the top of the list.
      
      if(!a1Info.exists || a1Info.fileSize == 0) {
        return -1;
      } else if (!a2Info.exists || a2Info.fileSize ==0) {
        return 1; 
      } else if (a1Info.lastModified == a2Info.lastModified) {
        return 0;
      } else if (a1Info.lastModified > a2Info.lastModified) {
        return 1;
      } else return -1;
    }
  }

}
