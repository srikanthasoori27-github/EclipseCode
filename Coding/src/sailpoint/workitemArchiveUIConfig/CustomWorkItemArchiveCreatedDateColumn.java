package sailpoint.workitemArchiveUIConfig;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.view.DefaultColumn;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.Map; 

public class CustomWorkItemArchiveCreatedDateColumn extends DefaultColumn {     
    
	private static Log log = LogFactory.getLog(CustomWorkItemArchiveCreatedDateColumn.class);
   private static final String COL_TARGET_ID = "targetId";     
    
   public Object getValue(Map<String, Object> row) throws GeneralException {         
     
	System.out.println("The row values are "+row);
	log.debug("The row values are "+row);
    String identityId = (String)row.get(COL_TARGET_ID);         
    Identity identity = this.getSailPointContext().getObjectById(Identity.class, 
          identityId);        
  
   
   return Util.dateToString(new Date(),
           "M/d/y H:m:s z");
   }
}