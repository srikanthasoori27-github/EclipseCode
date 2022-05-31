/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.rule;
/**
 * Application Onboarding Service
 * @author rohit.gupta
 *
 */
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.Form.Section;
import sailpoint.object.Form.Type;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
public class SelfServiceRuleLibrary {
	 private static Log selServiceLogger = LogFactory.getLog("rapidapponboarding.rules");
	/**
	 * Append Password Name from Create Provisioning Policy
	 * to the Email Text
	 * @param context
	 * @param text
	 * @param appName
	 * @throws GeneralException 
	 */
	 public static String appendPasswordtoRehireJoinerEmails(SailPointContext context,String text, String appName) throws GeneralException
	 {
		 LogEnablement.isLogDebugEnabled(selServiceLogger,"Start appendPasswordtoRehireJoinerEmails..");
		 LogEnablement.isLogDebugEnabled(selServiceLogger,"appName.."+appName);
		 String newText=null;
	      boolean matchFound=false;
		 if(text!=null && appName!=null)
		 {
			 Application app=context.getObjectByName(Application.class, appName);
			 List<Form> provisioningForms = app.getProvisioningForms();
	        if(provisioningForms!=null && provisioningForms.size()>0)
	        {
		        for (Form provisioningForm:provisioningForms)
		        {
		           if(provisioningForm.getType()!=null && provisioningForm.getType().equals(Type.Create) &&  provisioningForm.getObjectType()!=null
		           &&  provisioningForm.getObjectType().equals("account") && provisioningForm.getSections()!=null 
		          )
		           {
			           List<Section> sections = provisioningForm.getSections();
			           if(sections!=null && sections.size()>0)
			           {
			           	for (Section section:sections)
			           	{
			           	     LogEnablement.isLogDebugEnabled(selServiceLogger,"section..");
				             if(section.getFields()!=null && section.getFields().size()>0)
				             {
				               List<Field> fields = section.getFields();
				               LogEnablement.isLogDebugEnabled(selServiceLogger,"fields..");
				               if(fields!=null && fields.size()>0)
				               {
					               for (Field field:fields)
					               {
						               if( field.getName()!=null && field.getType()!=null && field.getType().equalsIgnoreCase("secret"))
						               {
						              		matchFound=true;
						              		if(!text.contains("$"+field.getName()))
						              		{
						              			newText=text+" "+"$"+field.getName();
						              		}
						              		else
						              		{
						              			newText=text;
						              		}
						              		break;
						               }
					               }
				               }
				             }
			             }
			           }
		           }
		        }
	        }
		 }
		 LogEnablement.isLogDebugEnabled(selServiceLogger,"text.."+text);
		 LogEnablement.isLogDebugEnabled(selServiceLogger,"newText.."+newText);
		 if(!matchFound)
		 {
			 return null;
		 }
		 if(newText==null || newText.length()<text.length())
		 {
			 LogEnablement.isLogDebugEnabled(selServiceLogger,"End appendPasswordtoRehireJoinerEmails..");
			 return null;
		 }
		 LogEnablement.isLogDebugEnabled(selServiceLogger,"End appendPasswordtoRehireJoinerEmails..");
		 return newText;
	 }
}
