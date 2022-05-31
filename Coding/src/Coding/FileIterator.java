package Coding;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class FileIterator {
	
	public static void main(String args[])
	{
		
		try {
			listf("/Users/srikanth.asoori/Desktop/Projects/GOVTECH/CAM_SFTP") ;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	


	 
	  static String INPUTDIRBASE=config.get("inputFilePath");
	  static String OUTPUTDIRBASE=config.get("outputFilePath");
	  


	    public static List listf(String inputdirectoryName) throws IOException {
	      File directory = new File(inputdirectoryName);
	      List<File> resultList = new ArrayList<File>();

	      List readFilesList = new ArrayList();
	      // get all the files from a directory
	      File[] fList = directory.listFiles();
	      resultList.addAll(Arrays.asList(fList));
	      int i=0;
	      for (File file : fList) {
	        System.out.println(i++ +" : " +file.getAbsolutePath());

	        // I will go back to the present folder
	        // And will read all the files in the folder
	        //// check if the files are ending with Json or not
	        if (file.isFile() && !file.isHidden() && !readFilesList.contains(file.getAbsolutePath())) {


	          File parentFolder = new File(file.getParent());
	          File[] tempFilesList = parentFolder.listFiles();
	          System.out.println("tempFilesList is "+tempFilesList);
	          String data= "";
	          
	          org.json.JSONObject jsonbody = new org.json.JSONObject();
	          JSONArray array = new JSONArray();
	          
	          for(File tempFile :tempFilesList)
	          {
	            if(!tempFile.isHidden() && tempFile.getName().endsWith(".json"))
	            {
	              System.out.println("tempFile.getAbsolutePath() "+tempFile.getAbsolutePath());
	              
	              JSONObject jsonObj = new JSONObject(readFromFile(tempFile.getAbsolutePath()));
	              array.put(jsonObj);
	              readFilesList.add(tempFile.getAbsolutePath());

	            }
	          }
	          jsonbody.put("data",array);
	          String strep="\\\\";

	          String parentpath = file.getParent().replaceAll(strep, "/");

	          String outputpathfinal = parentpath.replaceAll(INPUTDIRBASE, OUTPUTDIRBASE);       

	          System.out.println("outputfinalpath: "+ outputpathfinal );
	          WriteToFile(outputpathfinal,"finaloutput.json", jsonbody.toString(), false);


	          // Iterate the other files in the present folder
	          //System.out.println(file.getAbsolutePath());
	        } else if (file.isDirectory()) {
	          //System.out.println("is directory");
	          resultList.addAll(listf(file.getAbsolutePath()));
	        }
	      }
	      System.out.println(readFilesList);
	      return resultList;
	    }


	    public static void WriteToFile(String filePath,String fileName, String content, boolean append) {

	      try {
	        if (fileName == null)
	          throw new Exception("File Name not specified");
	        File directory = new File(filePath);
	        if (! directory.exists()){
		        directory.mkdirs();
		    }
	        fileName=filePath+"/"+fileName;
	        Writer writer = new BufferedWriter(new FileWriter(fileName, append));

	        writer.write(content);
	        writer.flush();
	        writer.close();
	      } catch (Exception ex) {
	        ex.printStackTrace();
	        // System.out.println("error in writing file", ex);
	      }
	    }

	    public static String readFromFile(String fileName) {


	      StringBuffer sb = new StringBuffer();

	      try {
	        if (fileName == null)
	          throw new Exception("File Name not specified");

	        BufferedReader br = new BufferedReader(new FileReader(fileName));
	        String line = null;

	        while ((line = br.readLine()) != null) {

	          sb.append(line);
	          

	        }

	        br.close();
	      } catch (Exception ex) {
	        // System.out.println("error in writing file", ex);
	      }


	      return sb.toString();
	    }
	    
	   

}
