/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Configuration object that is used to drive unstructured collection 
 * from PE2 applications site collections. This is used to hold context info of
 * TSS,ACF2,RACF and LINUX applications.
 */
@XMLClass
public class PE2SiteConfig implements Cloneable {

 
    /**
     * Account to access PE2 targets
     */
    private String _user;

    /**
     * Password of user
     */
    private String _password;
    
    /**
     * Name of target like /home/cm
     */
	
	private String _targetName;
	

    /**
     * Target type 
     */
	
	private String _targetType;	
	
	/**
     * Looking for inputs 
     */
	private String _type;
	
	/**
     * Looking for inputs 
     */
	private String _generic;
	
	/**
     * Looking for inputs 
     */
	private String _unit;
	
	/**
     * Looking for inputs 
     */
	private String _volume;

    public PE2SiteConfig() {    
        _user = null;
        _password = null;
        _targetName = null;
        _targetType = null;
        _generic = null;
        _type = null;
        _unit = null;
        _volume = null;
    }

    @XMLProperty
    public String getTargetName() {
        return _targetName;
    }

    public void setTargetName(String targetName) {
        _targetName = targetName;
    }
    
    @XMLProperty
    public String getTargetType() {
        return _targetType;
    }

    public void setTargetType(String targetType) {
        _targetType = targetType;
    }

    @XMLProperty
    public String getUser() {
        return _user;
    }

    public void setUser(String user ) {
        _user = user;
    }

    @XMLProperty
    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        _password = password;
    }
    
	@XMLProperty
	public String getType() {
		return _type;
	}

	public void setType(String _type) {
		this._type = _type;
	}

	@XMLProperty
	public String getGeneric() {
		return _generic;
	}

	public void setGeneric(String _generic) {
		this._generic = _generic;
	}

	@XMLProperty
	public String getUnit() {
		return _unit;
	}

	public void setUnit(String _unit) {
		this._unit = _unit;
	}

	@XMLProperty
	public String getVolume() {
		return _volume;
	}

	public void setVolume(String _volume) {
		this._volume = _volume;
	}

	public Object clone() {
        Object buddy =null;
        try {
            buddy = super.clone();
        } catch (CloneNotSupportedException cnfe) { }
        return buddy;
    }
}
