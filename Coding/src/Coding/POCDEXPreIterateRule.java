package Coding;
import java.util.*;
import java.util.regex.Pattern;

import sailpoint.connector.DelimitedFileConnector;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.tools.Util;
import sailpoint.object.Application;
import sailpoint.object.Custom;
import sailpoint.object.EmailFileAttachment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import sailpoint.object.EmailOptions;
	import sailpoint.object.EmailTemplate;


public class POCDEXPreIterateRule {
	
	
	Application application;
	SailPointContext context;
	
	public int getHeaderRecordCount(String recordHeader)
	{
		
			int headerCount=0;
			String splitBy = "|";
		    if(Util.isNotNullOrEmpty(recordHeader))
		    {
		    	String[] headerColumns= recordHeader.split(Pattern.quote(splitBy));
		    	if(headerColumns.length==2)
		    	{
		    		 headerCount = Util.otoi(headerColumns[1]);
		    	}
		    }
		return headerCount;
	}
	public void doValidations()
	{
	  String splitBy = "|";
	  System.out.println("Starting Data Validation");
	  System.out.println("Inside the preiterate rule ");
	  String filepath=(String)application.getAttributeValue("file");
	  File file = new File(filepath);
	  TaskManager managerTask = new TaskManager(context);
	  TaskDefinition taskDef = context.getObjectByName(TaskDefinition.class,"POCDEX Validations Aggregation Task");
	  int errTolerance;
	  if(taskDef.getString("maxErrorThreshold")!=null){
	  errTolerance = Integer.parseInt(taskDef.getString("maxErrorThreshold"));
	  }

	  System.out.println("the errTolerance is "+errTolerance);
	  	if(file!=null && errTolerance!=0){
	    BufferedReader br = new BufferedReader(new FileReader(file));
	    String record = br.readLine();
	    List columnList = null;
	    System.out.println("Column List: "+columnList);
	    String []columns = record.split(Pattern.quote(splitBy));
	    columnList = Arrays.asList(columns);
	    int columnSize = columnList.size();
	    
	    System.out.println("Column Size: "+columnSize);
	    int count = 0;
	    
	    Custom custom = context.getObject(Custom.class, "Govtech-Custom-ErrorCodes Mapping");
	    Map errorCodes = custom.get("POCDEX_ERROR_CODES");
	    String inputFileName = file.getName();
	    String outputFileName = inputFileName.substring(0, inputFileName.indexOf(".")) + "_ErrorLog.csv";
	    String outputFileDirectory = custom.get("POCDEX_ERROR_OUTPUT");
	    BufferedWriter outputWriter = new BufferedWriter(new FileWriter(outputFileDirectory+outputFileName,true));
	    try {
	    outputWriter.write("POCDEX UID|ID Type|ID No|First Name|Last Name|Email Address|Alias|Position ID|New Position ID"
	                       +"|Owner Agency Code|Present Agency Code|Primary Position Indicator|Employee Type|TIVO Indicator"
	                       +"|Position Start Date|Position End Date|Department ID|Department Name|Title|RO UID|RO Name"
	                       +"|RO Email|Additional Email Addresses|Reason for error\n");
	    
	    while((record=br.readLine())!=null){
	        if(record != null && record.trim().length() > 0){
	          String []records = record.split(Pattern.quote(splitBy));
	          List recordList = Arrays.asList(records);
	          
	          //Build Map
	          HashMap map = (HashMap) DelimitedFileConnector.defaultBuildMap(columnList,recordList);
	          System.out.println("Map: "+map);
	          
	          //Initiate columns
	          String pocdexUID								= map.get(columns[0]);
	          String email										= map.get(columns[5]);
	          String positionID								= map.get(columns[7]);
	          String ownerAgencyCode					= map.get(columns[9]);
	          String presentAgencyCode				= map.get(columns[10]);
	          String primaryPositionIndicator	= map.get(columns[11]);
	          String roUID										= map.get(columns[19]);
	          System.out.println("Testing");
	          //Check Attributes
	          //Validate POCDEX UID
	          if(!isNull(pocdexUID)){
	            System.out.println("Govtech-Rule-POCDEXDataValidation - POCDEX UID is null");
	            count++;
	            outputWriter.write(record + "|" + errorCodes.get("ERROR_01") + "\n");            
	            if(count >=errTolerance) break;
	          }
	          //Validate Position ID
	          if(isNull(positionID)){
	            System.out.println("Govtech-Rule-POCDEXDataValidation - Position ID not found for user "+pocdexUID);
	            count++;
	            outputWriter.write(record + "|" + "Position ID " + errorCodes.get("ERROR_02") + "\n");
	            if(count >=errTolerance) break;
	            }
	          //Validate Owner Agency Code
	          if(isNull(ownerAgencyCode)){
	            System.out.println("Govtech-Rule-POCDEXDataValidation - Owner Agency Code not found for user "+pocdexUID);
	            count++;
	            outputWriter.write(record + "|" + "Owner Agency Code " + errorCodes.get("ERROR_02") + "\n");
	            if(count >=errTolerance) break;
	            }
	          //Validate Present Agency Code
	          if(isNull(presentAgencyCode)){
	            System.out.println("Govtech-Rule-POCDEXDataValidation - Present Agency Code not found for user "+pocdexUID);
	            count++;
	            outputWriter.write(record + "|" + "Present Agency Code " + errorCodes.get("ERROR_02") + "\n");
	            if(count >=errTolerance) break;
	            }
	          
	          //Validate Primary Position Indicator
	          if(isNull(primaryPositionIndicator)){
	            System.out.println("Govtech-Rule-POCDEXDataValidation - Primary Position Indicator not found for user "+pocdexUID);
	            count++;
	            outputWriter.write(record + "|" + "Primary Position Indicator " + errorCodes.get("ERROR_02") + "\n");
	            if(count >=errTolerance) break;
	            }
	          //Validate RO UID
	          if(isNull(roUID)){
	            System.out.println("Govtech-Rule-POCDEXDataValidation - RO POCDEX UID not found for user "+pocdexUID);
	            count++;  
	            outputWriter.write(record + "|" + "RO POCDEX UID " + errorCodes.get("ERROR_02") + "\n");
	                if(count >=errTolerance) break;
	            }
	          if(isNull(email)){
	            System.out.println("Govtech-Rule-POCDEXDataValidation - Email not found for user "+pocdexUID);
	            count++;
	            outputWriter.write(record + "|" + "Email " + errorCodes.get("ERROR_02") + "\n");
	           
	                if(count >=errTolerance) break;
	            }
	          
	          //Validate Email
	          if(email.contains(" ") || !validEmail(email)){
	            System.out.println("Govtech-Rule-POCDEXDataValidation - Email validation failed for user "+pocdexUID);
	            count++;
	            outputWriter.write(record + "|" + errorCodes.get("ERROR_03") + "\n");
	           
	            if(count >=errTolerance) break;
	          }
	          
	       } 
	    }
	    } catch(Exception e) {
	      return e.getMessage();
	      System.out.println("Govtech-Rule-POCDEXDataValidation - Exception while running preiterate rule" + e.getMessage());
	      
	    } finally {
	      
	    outputWriter.flush();
	    outputWriter.close();
	      
	    }
	    
	     
	    if(count>=errTolerance){
	      TaskResult result = context.getObject(TaskResult.class,"Govtech-POCDEX Periodic Account Aggregation Task");
				managerTask.terminate(result);
	    }
	      
	      
	      try {
	        System.out.println("the count value in preiterate rule is "+count);
	      if(count>0)
	      {
	      List recepients = new ArrayList();
	      recepients.add("kx00755355@techmahindra.com");
	        Map mailArgs = new HashMap();
	        		mailArgs.put("fileName",outputFileDirectory+outputFileName);
		            EmailOptions options = new EmailOptions();
		            options.addAttachment(new EmailFileAttachment());
		            options.setTo(recepients,mailArgs);
		            EmailTemplate template = context.getObjectByName(EmailTemplate.class, "Govtech-EmailTemplate-POCDEX Error Validation Notification");
		            context.sendEmailNotification(template, options);
		            Util.listToCsv(columnList);
		            new FileWriter();
	      }
	       
					
	      } catch(Exception e) {
	        throw new Exception("The headerCount in the file does not match with the RecordCount in the file");	
	    	  System.out.println("Govtech-Rule-POCDEXDataValidation - Exception while sending email in preiterate rule" + e.getMessage());
	      }
	      return;
	    	}
	  	}
}

}
