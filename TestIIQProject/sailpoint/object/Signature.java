/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object describing the arguments and return value of an abstract function.
 * This is used by TaskDefinition, RequestDefinition, and Rule to provide
 * metadata for human documetation and auto-generation of forms in the UI.
 *
 * These are not currently autonomous objects, though that may be
 * interesting if we get to a point where we want to share large
 * signatures.
 *
 * Author: Jeff
 *
 * Waveset folks will notice that this and Argument are beginning
 * to smell very "form like".  
 */

package sailpoint.object;

import java.util.Iterator;
import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class Signature extends AbstractXmlObject implements Cloneable
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * 
     */
    private static final long serialVersionUID = -6399649405143852389L;
    
    String _name;
    String _description;
    String _returnType;

    List<Argument> _arguments;
    List<Argument> _returns;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Signature() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setName(String s) {
        _name = s;
    }

    public String getName() {
        return _name;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setDescription(String s) {
        _description = s;
    }

    public String getDescription() {
        return _description;
    }

    @XMLProperty
    public void setReturnType(String s) {
        _returnType = s;
    }

    public String getReturnType() {
        return _returnType;
    }

    // sigh, wanted to use <Arguments> but that conflicts
    // with a few other classes where <Arguments> wraps a Map
    @XMLProperty(mode=SerializationMode.LIST,xmlname="Inputs")
    public List<Argument> getArguments() {
        return _arguments;
    }

    public void setArguments(List<Argument> args) {
        _arguments = args;
    }

    public boolean hasArgument(String name){

        if (name != null && _arguments != null){
            for(Argument arg : _arguments){
                if (name.equals(arg.getName()))
                    return true;
            }
        }

        return false;
    }

    public boolean removeArgument(String name){
        boolean found = false;
        if (hasArgument(name)){
            Iterator<Argument> iter = _arguments.iterator();
            while(iter.hasNext()){
                Argument arg = iter.next();
                if (name.equals(arg.getName())){
                    iter.remove();
                    found = true;
                    break;
                }
            }
        }

        return found;
    }

    @XMLProperty(mode=SerializationMode.LIST,xmlname="Returns")
    public List<Argument> getReturns() {
        return _returns;
    }

    public void setReturns(List<Argument> args) {
        _returns = args;
    }

    /**
     * @ignore
     * Kludge for hibernate, see TaskDefinition.getEffectiveSignature.
     * Return true if this signature doesn't actually contain anything.
     */
    public boolean isEmpty() {

        return (_name == null && 
                _description == null &&
                _returnType == null && 
                (_arguments == null || _arguments.size() == 0) &&
                (_returns == null || _returns.size() == 0));
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Performance experiment to fully load the Rule so the cache can be cleared.
     */
    public void load() {

        getName();

        if (_arguments != null) {
            for (Argument a : _arguments)
              a.getName();
        }

        if (_returns != null) {
            for (Argument a : _returns)
              a.getName();
        }
    }

    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        
        if (getClass() != obj.getClass())
            return false;
        
        final Signature other = (Signature) obj;        
        if (_name == null) {
            if (other._name != null)
                return false;
        } else if (!_name.equals(other._name))
            return false;
        
        if (_description == null) {
            if (other._description != null)
                return false;
        } else if (!_description.equals(other._description))
            return false;
        
        if (_returnType == null) {
            if (other._returnType != null)
                return false;
        } else if (!_returnType.equals(other._returnType))
            return false;
        
        if (_arguments == null) {
            if (other._arguments != null)
                return false;
        } else if (!_arguments.equals(other._arguments))
            return false;
        
        if (_returns == null) {
            if (other._returns != null)
                return false;
        } else if (!_returns.equals(other._returns))
            return false;
        
        return true;
    }
}
