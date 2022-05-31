/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Our one implementation of the DTDBuilder interface.
 * Convert element definitions represented with DTDConstraint objects
 * into a textual DTD .
 * 
 * One of these will be created and used by XMLObjectFactory after all the
 * serializers have been registered and compiled.
 * 
 * TODO: Need better formatting!
 * 
 * Author: Rob, comments by Jeff
 */

package sailpoint.tools.xml;

import sailpoint.tools.xml.DTDConstraints.AttributeConstraint;
import sailpoint.tools.xml.DTDConstraints.ConstraintNode;
import sailpoint.tools.xml.DTDConstraints.ElementConstraint;
import sailpoint.tools.xml.DTDConstraints.ElementConstraints;
import sailpoint.tools.xml.DTDConstraints.ElementNameConstraint;
import sailpoint.tools.xml.DTDConstraints.EmptyConstraint;
import sailpoint.tools.xml.DTDConstraints.PCDataConstraint;
import sailpoint.tools.xml.DTDConstraints.SubElementConstraint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DTDBuilderImpl implements DTDBuilder
{
    ////////////////////////////////////////////////////////////////////// 
    //
    // Fields
    //
    ////////////////////////////////////////////////////////////////////// 
    
    /**
     * Map to keep track of element definitions added to the DTD
     * to detect conflicts.
     */
    private Map<String,String> _elementDefinitions = 
        new HashMap<String,String>();

    /**
     * Buffer we accumulate the DTD text into.
     */
    private StringBuilder _buf = new StringBuilder();
    
    ////////////////////////////////////////////////////////////////////// 
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////// 

    public DTDBuilderImpl()
    {
    }
    
    ////////////////////////////////////////////////////////////////////// 
    //
    // DTDBuilder Implementation
    //
    ////////////////////////////////////////////////////////////////////// 

    public boolean defineElement(String name, ElementConstraints cons)
    {
        String definition = createDefinition(name, cons);
        
        String previousDefinition = _elementDefinitions.get(name);
        if ( previousDefinition == null )
        {
            _buf.append(definition);
            _elementDefinitions.put(name, definition);
            return true;
        }
        else
        {
            if ( previousDefinition.equals(definition) )
            {
                return false;
            }
            else
            {
                throw new ConfigurationException(
                "Conflicting definition for element "+name+":\n"+
                previousDefinition+"=====================\n"+
                definition);
            }
        }
    }
    
    public String getDTD()
    {
        return _buf.toString();
    }
    
    ////////////////////////////////////////////////////////////////////// 
    //
    // Internal Rendering
    //
    ////////////////////////////////////////////////////////////////////// 

    private static String buildConstraint(SubElementConstraint constraint)
    {
        if ( constraint instanceof ElementNameConstraint )
        {
            ElementNameConstraint el = (ElementNameConstraint)constraint;
            return el.getElementName();
        }
        else
        {
            ConstraintNode node = (ConstraintNode)constraint;
            List<String> childStrings = new ArrayList<String>();
            for (SubElementConstraint child : node.getChildren())
            {
                String childString = buildConstraint(child);
                if (childString.length() > 0)
                {
                    childStrings.add(childString);
                }
            }
            if (childStrings.size() == 0)
            {
                return "";
            }
            switch (node.getOperation())
            {
                case OR:
                    return buildListConstraint(childStrings, "|");
                case ORDERED_LIST:
                    return buildListConstraint(childStrings, ",");
                case ZERO_OR_ONE:
                    return "("+childStrings.get(0)+")?";
                case ZERO_OR_MORE:
                    return "("+childStrings.get(0)+")*";
                case ONE_OR_MORE:
                    return "("+childStrings.get(0)+")+";
                default:
                    throw new RuntimeException("Missing case: "+node.getOperation());
            }
        }
    }
    
    private static String buildListConstraint(List<String> childStrings, String sep)
    {
        if (childStrings.size() == 1)
        {
            return childStrings.get(0);
        }
        StringBuilder buf = new StringBuilder();
        String currentSep = "";
        for (String childString:childStrings)
        {
            buf.append(currentSep);
            currentSep = sep;
            buf.append(childString);
        }
        return buf.toString();
    }

    private static String createDefinition(String elementName, ElementConstraints cons)
    {
        StringBuilder buf = new StringBuilder();
        ElementConstraint elCons = cons.getElementConstraint();
        String elConsStr;
        if (elCons instanceof PCDataConstraint)
        {
            elConsStr = "(#PCDATA)";
        }
        else if (elCons instanceof EmptyConstraint)
        {
            elConsStr = "EMPTY";
        }
        else 
        {
            elConsStr = buildConstraint((SubElementConstraint)elCons);
            if ( elConsStr.length() == 0 )
            {
                elConsStr = "EMPTY";
            }
            else
            {
                elConsStr = "("+elConsStr+")";
            }
        }
        buf.append("<!ELEMENT "+elementName+" "+elConsStr+">\n");
        if (cons.getAttributeConstraints().size() > 0)
        {
            buf.append("<!ATTLIST "+elementName+"\n");
            for (AttributeConstraint attCons : cons.getAttributeConstraints())
            {
                String attributeName = attCons.getName();
                boolean required     = attCons.isRequired();
                buf.append("  "+attributeName);
                List<String> enumerated = attCons.getEnumeratedValues();
                if ( enumerated != null && enumerated.size() > 0 )
                {
                    buf.append(" (");
                    String sep = "";
                    for (String enumval : enumerated)
                    {
                        buf.append(sep);
                        buf.append(enumval);
                        sep = " | ";
                    }
                    buf.append(") ");
                }
                else
                {
                    buf.append(" CDATA ");
                }
                
                if (required)
                {
                    buf.append("#REQUIRED");
                }
                else
                {
                    buf.append("#IMPLIED");
                }
                buf.append("\n");
            }
            buf.append(">\n");
        }
        return buf.toString();
    }

}
