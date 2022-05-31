package Coding;

import java.util.Collection;
import java.util.List;

import sp.poc.filter.CharResponseWrapper;
import sp.poc.filter.ResponseFilter;

public class TestClass {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Boolean s =new Boolean("false");
		Boolean b = new Boolean("false");
		System.out.println( isFieldValueUpdated(s,b));
		
		ResponseFilter rs = new ResponseFilter();
		
	}
	public static boolean isFieldValueUpdated(Object oldVal, Object val){
		System.out.println("Enter isFieldValueUpdated with oldVal: " + oldVal + " and newVal: " + val +"  testing "+val.equals(oldVal));
			
			boolean isUpdate = false;
			// TODO:  MIGHT NEED TO HANDLE IF NULLING OUT A VALUE
			if (val != null && oldVal != null &&val instanceof List && oldVal instanceof List){
							System.out.println("Inside 1");
				if (!((List) val).containsAll( (Collection) oldVal) || !((List) oldVal).containsAll((Collection) val)){
					System.out.println("Inside 2");
					isUpdate = true;
				}
			} else {
				if (val != null && !(val+"").trim().equals("") && (oldVal == null || !val.equals(oldVal))){
					isUpdate = true;
					System.out.println("Inside 3");
				} else if ((val == null || val == "") && oldVal != null){
					System.out.println("Inside 4");
					isUpdate = true;
				}
			}
			if((val instanceof List && ((String) val).isEmpty() && oldVal == null)){
				isUpdate = false;
			}
			
			System.out.println("Exit isFieldValueUpdated: " + isUpdate);
			return isUpdate;
		}
	}


