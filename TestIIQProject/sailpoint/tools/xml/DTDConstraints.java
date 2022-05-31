/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A memory representation for various constraints that need
 * to be included in the DTD generated from a serializer.
 * Used only within the XMLSerializer.generateDTD methods.
 *
 * Author: Rob, comments by Jeff
 */

package sailpoint.tools.xml;

import java.util.ArrayList;
import java.util.List;

abstract class DTDConstraints
{
   public static enum Operation
   {
       OR,
       ORDERED_LIST,
       ZERO_OR_ONE,
       ZERO_OR_MORE,
       ONE_OR_MORE
   }
   
   abstract static public class ElementConstraint
   {       
   }
   
   static public class ElementConstraints
   {
       private ElementConstraint _elementConstraint;
       private List<AttributeConstraint> _attributeConstraints;

       public ElementConstraints(ElementConstraint elementConstraint)
       {
           _elementConstraint = elementConstraint;
           _attributeConstraints = new ArrayList<AttributeConstraint>();
       }
       public void addAttributeConstraint(AttributeConstraint con)
       {
           _attributeConstraints.add(con);
       }
       
       public ElementConstraint getElementConstraint()
       {
           return _elementConstraint;
       }
       public List<AttributeConstraint> getAttributeConstraints()
       {
           return _attributeConstraints;
       }
   }
   
   public static class AttributeConstraint
   {
       private boolean _required;
       private String _name;
       private List<String> _enumeratedValues;

       public AttributeConstraint(String name, boolean required, List<String> enumeratedValues)
       {
           _name             = name;
           _required         = required;
           _enumeratedValues = enumeratedValues;
       }
       public boolean isRequired()
       {
           return _required;
       }
       public String getName()
       {
           return _name;
       }
       public List<String> getEnumeratedValues()
       {
           return _enumeratedValues;
       }
       
   }
   
   static public class EmptyConstraint extends ElementConstraint
   {
       public boolean equals( Object o )
       {
           return o instanceof EmptyConstraint;
       }
   }
   
   static public class PCDataConstraint extends ElementConstraint
   {
       public boolean equals( Object o )
       {
           return o instanceof PCDataConstraint;
       }
   }

   abstract static public class SubElementConstraint extends ElementConstraint
   {
       
   }
   
   public static class ConstraintNode extends SubElementConstraint
   {
       
       private Operation _operation;
       private List<SubElementConstraint> _children;
       
       public ConstraintNode(Operation operation)
       {
           _operation = operation;
           _children  = new ArrayList<SubElementConstraint>();
       }
       
       public ConstraintNode(Operation operation, SubElementConstraint child)
       {
           this(operation);
           addChild(child);
       }
       
       public Operation getOperation()
       {
           return _operation;
       }
       
       public List<SubElementConstraint> getChildren()
       {
           return _children;
       }
       
       public void addChild(SubElementConstraint child)
       {
           _children.add(child);
       }
       
       
   }
   
   public static class ElementNameConstraint extends SubElementConstraint
   {
       private String _elementName;
       public ElementNameConstraint(String elementName)
       {
           _elementName = elementName;
       }
       public String getElementName()
       {
           return _elementName;
       }
       
       
   }
   
   
}
