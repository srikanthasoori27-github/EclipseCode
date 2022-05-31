/**
 * 
 */
package sailpoint.web.util;

import java.util.Comparator;

import sailpoint.object.ObjectAttribute;

/**
 * @author peter.holcomb
 * Comparator used to sort the input fields for editing/creating an identity
 */
public class IdentityAttributeComparator implements Comparator<ObjectAttribute>{
    /** Order the create identity attributes form in the order of firstname,lastname,email,manager, then any standard atts, then any others **/
    public int compare(ObjectAttribute o1, ObjectAttribute o2) {

        if(o1.getName().equals("firstname"))
            return -1;            
        else if(o2.getName().equals("firstname"))
            return 1;
        
        else if(o1.getName().equals("lastname"))
            return -1;            
        else if(o2.getName().equals("lastname"))
            return 1;
        
        else if(o1.getName().equals("email"))
            return -1; 
        else if(o2.getName().equals("email"))
            return 1;
        
        else if(o1.getName().equals("manager"))
            return -1;   
        else if(o2.getName().equals("manager"))
            return 1;
        
        else if(o1.isStandard() && !o2.isStandard()) {
            return -1;
        } else if(o2.isStandard() && !o1.isStandard()) {
            return 1;
        }
        
        else if(o1.isSystem() && !o2.isSystem()) {
            return -1;
        } else if(o2.isSystem() && !o1.isSystem()) {
            return 1;
        }
        
        else return 0;
    }

}
