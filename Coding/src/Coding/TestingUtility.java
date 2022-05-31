package Coding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TestingUtility {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
	/*	String movementType ="Termination From Agency A,Agency B";
		String MovementType_Termination_PREFIX ="Termination From ";
		List agencyTerminationList= new ArrayList();
		
		agencyTerminationList.add("Agency A");
		agencyTerminationList.add("Agency B");
		
		String agencyList = movementType.substring(MovementType_Termination_PREFIX.length(), movementType.length());
		System.out.println("agencyList is "+agencyList);
		
		if(movementType.startsWith(MovementType_Termination_PREFIX))
    	{
    		
    		List <String>terminatingAgenciesList=Util.csvToList(agencyList);
    		
    		for(String terminatingAgency: terminatingAgenciesList)
    		{
    			if(!agencyTerminationList.contains(terminatingAgency))
    			{
    				System.out.println("True "+terminatingAgency);
    				break;
    			}
    		}
    		
    		
    	}*/
	}
	
	public static void zipFiles(String inputPath,String outputPath) throws IOException {
		
		Map stats = new HashMap();
		stats.put("abpath", "C:\\IIQFiles\\pocdex.csv");
		stats.put("fileName","abc.csv");
		String abpath= stats.get("abpath").toString();
		String outputfile = abpath.substring(0,abpath.length()-3)+"12-12-2021"+"compressed.zip";
		
        String sourceFile = "test1.txt";
        FileOutputStream fos = new FileOutputStream("compressed.zip");
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
    }

}
