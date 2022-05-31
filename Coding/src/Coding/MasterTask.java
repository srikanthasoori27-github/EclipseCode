package Coding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Custom;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class MasterTask {
	
	SailPointContext context;
	public String POCDEX_DAILY_AGG_FILE_PREFIX;
	public String POCDEX_MONTHLY_AGG_FILE_PREFIX;
	public String POCDEX_ARCHIVAL_FOLDER_PATH;
	public String POCDEX_AGG_FOLDER_PATH;
	public String POCDEX_IDENTITY_APP_NAME;
	public String POCDEX_TARGET_APP_NAME;
	public String POCDEX_DAILY_AGG_STATUS_FLAG;
	public String POCDEX_MONTHLY_AGG_STATUS_FLAG;
	public String POCDEX_DAILY_AGG_STATUS_FAILURE_EMAIL_TEMPLATE;
	public String POCDEX_DAILY_AGG_EMAIL_FAILURE_NOTIFY_START_TIME;
	public String POCDEX_DAILY_AGG_EMAIL_FAILURE_NOTIFY_END_TIME;
	public String POCDEX_MONTHLY_AGG_EMAIL_FAILURE_NOTIFY_START_TIME;
	public String POCDEX_MONTHLY_AGG_EMAIL_FAILURE_NOTIFY_END_TIME;
	public String POCDEX_MONTHLY_AGG_STATUS_FAILURE_EMAIL_TEMPLATE;
	public String POCDEX_AGG_DEFAULT_STATUS_FLAG="FAILURE";
	public String POCDEX_AGG_SUCCESS_STATUS_FLAG="SUCCESS";
	public String POCDEX_AGG_TASK_NAME="SUCCESS";
	public Custom customObj=null;
	
	public void init()
	{
		try {
			 customObj = context.getObjectByName(Custom.class,"Govtech-Custom-ErrorCodes Mapping");
			if(customObj!=null && customObj.getAttributes()!=null)
			{
				POCDEX_DAILY_AGG_FILE_PREFIX=(String) customObj.get("POCDEX_DAILY_AGG_FILE_PREFIX");
				POCDEX_MONTHLY_AGG_FILE_PREFIX= (String) customObj.get("POCDEX_MONTHLY_AGG_FILE_PREFIX");
				POCDEX_ARCHIVAL_FOLDER_PATH= (String) customObj.get("POCDEX_ARCHIVAL_FOLDER_PATH");
				POCDEX_AGG_FOLDER_PATH = (String) customObj.get("POCDEX_AGG_FOLDER_PATH");
				POCDEX_IDENTITY_APP_NAME= (String) customObj.get("POCDEX_IDENTITY_APP_NAME");
				POCDEX_TARGET_APP_NAME= (String) customObj.get("POCDEX_TARGET_APP_NAME");
				POCDEX_DAILY_AGG_STATUS_FAILURE_EMAIL_TEMPLATE= (String) customObj.get("POCDEX_DAILY_AGG_STATUS_FAILURE_EMAIL_TEMPLATE");
				POCDEX_MONTHLY_AGG_STATUS_FAILURE_EMAIL_TEMPLATE= (String) customObj.get("POCDEX_MONTHLY_AGG_STATUS_FAILURE_EMAIL_TEMPLATE");
			}
		} catch (GeneralException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String getAggregationStatus(String flagName) throws GeneralException
	{
		String status=POCDEX_AGG_DEFAULT_STATUS_FLAG;
		String todaysDate= Util.dateToString(new Date(), "dd-MM-yyyy");
		if(customObj!=null && customObj.getAttributes()!=null)
		{
			Map dailyStatusMap = (Map) customObj.get(flagName);
			if(dailyStatusMap!=null && dailyStatusMap.get(todaysDate)!=null)
			{
				status=(String) dailyStatusMap.get(todaysDate);
			}
			else
			{
				setAggregationStatus(flagName,status);
			}
		}
		return status;
	}
	
	public void setAggregationStatus(String flagName,String flagValue) throws GeneralException
	{
		
		Custom customObj = context.getObjectByName(Custom.class,"Govtech-Custom-ErrorCodes Mapping");
		String todaysDate= Util.dateToString(new Date(),"dd-MM-yyyy");
		if(customObj!=null && customObj.getAttributes()!=null)
		{
			Map dailyStatusMap = dailyStatusMap = new HashMap();
			dailyStatusMap.put(todaysDate, flagValue);
			customObj.put(flagName, dailyStatusMap);
			context.saveObject(customObj);
			context.commitTransaction();
			
		}
		
	}
	
	public void updateApplicationFilePath(String absolutePath,String pocdexIdentityAppName,String pocdexTargetAppName) throws GeneralException
	{
		
		Application pocdexIdentityApp = context.getObjectByName(Application.class,"pocdexIdentityAppName");
		Application pocdexTargetApp = context.getObjectByName(Application.class,"pocdexTargetAppName");
		
		if(pocdexIdentityApp!=null)
		{
			pocdexIdentityApp.setAttribute("file", absolutePath);
		}
		if(pocdexTargetApp!=null)
		{
			pocdexTargetApp.setAttribute("file", absolutePath);
		}
		
		context.saveObject(pocdexTargetApp);
		context.saveObject(pocdexIdentityApp);
		context.commitTransaction();
		
	}
	
	 public void sendEmail(String emailTemplateName,List recpients) throws GeneralException {
	       
	            String from = null;
	            String cc = null;
	            String bcc = null;
	            String subject = null;
	            String body = null;
	            EmailOptions options = new EmailOptions();
	            options.setTo(recpients);
	            EmailTemplate template = context.getObjectByName(EmailTemplate.class, emailTemplateName);
	            template.setFrom(from);
	            template.setSubject(subject);
	            template.setBody(body);
	            template.setCc(cc);
	            template.setBcc(bcc);
	            context.sendEmailNotification(template, options);
	        
	    }	
	 
	 public boolean isFileExists(String path, String fileNamePrefix)
	 {
		 String fileName = generateFileName(fileNamePrefix);
		 File file = new File(path+fileName);
		 return file.exists();
	 }
	 
	 public String generateFileName(String fileNamePrefix)
	 {
		 String todaysDate= Util.dateToString(new Date(), "ddMMyyyy");
		 String fileName = fileNamePrefix+"_"+todaysDate+".csv";
		 return fileName;
	 }
	 
	
	 
	 public void archiveAndDeleteFile(String sourcePath,String fileName,String destinationFolderPath) throws Exception
	 {
		 	String sourceFile = sourcePath+"//"+fileName;
		 	String hhmmssFormat=Util.dateToString(new Date(),"HHmmss");
		 	String archivalFileName = destinationFolderPath+"//"+fileName.replace(".csv","_")+hhmmssFormat+"_compressed.zip";
	        FileOutputStream fos = new FileOutputStream(archivalFileName);
	        ZipOutputStream zipOut = new ZipOutputStream(fos);
	        File fileToZip = new File(sourceFile);
	        FileInputStream fis = new FileInputStream(fileToZip);
	        ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
	        zipOut.putNextEntry(zipEntry);
	        byte[] bytes = new byte[1024];
	        int length;
	        while((length = fis.read(bytes)) >= 0) {
	            zipOut.write(bytes, 0, length);
	        }
	        zipOut.close();
	        fis.close();
	        fos.close();
	        fileToZip.delete();
	 }
	 
	 //checks if the current time is in between two times and will send an email notification
	 public boolean isTimetoSendEmail(String startTime, String EndTime)
	 {
		 try {
			    
			    Date time1 = new SimpleDateFormat("HH:mm:ss").parse(startTime);
			    Calendar calendar1 = Calendar.getInstance();
			    calendar1.setTime(time1);
			    calendar1.add(Calendar.DATE, 1);
			    Date time2 = new SimpleDateFormat("HH:mm:ss").parse(EndTime);
			    Calendar calendar2 = Calendar.getInstance();
			    calendar2.setTime(time2);
			    calendar2.add(Calendar.DATE, 1);
			    String currentTime= Util.dateToString(new Date(),"HH:mm:ss");
			    Date d = new SimpleDateFormat("HH:mm:ss").parse(currentTime);
			    Calendar calendar3 = Calendar.getInstance();
			    calendar3.setTime(d);
			    calendar3.add(Calendar.DATE, 1);
			    Date x = calendar3.getTime();
			    if (x.after(calendar1.getTime()) && x.before(calendar2.getTime())) {
			        return true;
			    }
			} catch (ParseException e) {
			    e.printStackTrace();
			}
		return false;
		 
	 }
	 
	 public boolean runTask()
	 {
		       TaskManager tm = new TaskManager(context);
		       boolean isSuccessful = false;
	            //tm.setLauncher(result.getOwner().getName());
	            TaskResult res = null;
	            String resName = null;
	            TaskDefinition task = context.getObjectByName(TaskDefinition.class,POCDEX_AGG_TASK_NAME);
	            Attributes moreArgs = new Attributes();
	            List certificationGroups = new ArrayList();
	            certificationGroups.add("c0a812027b851064817baa97f72f2661");
	          
	            moreArgs.put("certificationGroups",certificationGroups);
	            if (task == null) {
	                
	                throw new GeneralException("Missing Task Definition: name[" + POCDEX_AGG_TASK_NAME + "]");
	            }

	            task.setArgument("certificationGroups",certificationGroups);
	            try {
	                res = tm.runWithResult(task, moreArgs);
	                resName = res.getName();


	                int iterations = 0;
	                int threadSleep = 5000;
	                
	               
	                while (iterations>500) {
	                    
	                    Thread.sleep(threadSleep);
	                    context.decache();
	                    res = context.getObjectById(TaskResult.class, res.getId());
	                    
	                    if (res == null) {
	                        throw new GeneralException("TaskResult evaporated!: " + resName);
	                    }
	    
	                    if (res.isComplete())
	                    {
	                    	isSuccessful= true;
	                        break;
	                       
	                    }
	           
	                    iterations++;
	                }
	              return isSuccessful;
	            } catch (GeneralException ge) {
	                log.error(ge);
	                return isSuccessful;
	               
	            }
	        
	 }

}
