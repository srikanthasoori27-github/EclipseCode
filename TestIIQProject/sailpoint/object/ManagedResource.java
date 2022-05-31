/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * 
 * A class used to hold information about resources managed
 * by provisioning system.
 *
 * Author: Jeff
 *
 * This was originally inside IntegrationConfig but was promoted so we
 * could use this in the Application model as well.
 *
 * The main thing this provides is a way to list which IIQ Applications
 * are managed by a provisioning system.  This is used during plan 
 * compilation to partition plans among provisioning systems.
 * 
 * The model also provides mapping betweeen the resource and attribute
 * names we want to see in IIQ and the names required in the provisioning
 * plans sent down to the provisioning system.  This is used less often,
 * but for systems like OIM that have obscure capital/underscore names
 * it gives us a way to hide them.
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;

/**
 * Class used to define name mappings for resources.
 * The attribute map is keyed by IdentityIQ attribute name and the value
 * is the target attribute name.
 *
 * A list of these will be maintained within the IntegrationConfig,
 * search structures are built at runtime.
 */
@XMLClass
public class ManagedResource {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The IdentityIQ application corresponding to the remote resource.
     */
    Application _application;

    /**
     * The name of this resource in the IDM system.
     * If not set it is assumed to be the same as the Application name.
     */
    String _name;

    /**
     * The name of the attribute in this resource that represents
     * the account id. This is optional and used only in cases where
     * the Application definitions are bootstrapped from a 
     * provisioning system that can give an aggregate feed of
     * "composite" identities. If _application is set and it has
     * an identity attribute set in the account schema this is ignored.
     * 
     * @ignore
     * I don't especially like this, but it's easier than managing
     * the configuration on the OIM side.
     */
    String _identityAttribute;

    /**
     * Attribute transformations.
     * If an attribute is not found in this list, the name
     * and value are passed through unmodified.
     */
    List<ResourceAttribute> _attributes;

    /**
     * Account operation transformations. This can be used
     * to convert operations of one type to another type -
     * for example converting deletes to disables. If an
     * operation is not in this list it is not transformed.
     */
    List<OperationTransformation> _operationTransformations;
        
    Map<Operation,OperationTransformation> _operationTransformationCache;

    Map<String,ResourceAttribute> _localAttributeCache;
    Map<String,ResourceAttribute> _remoteAttributeCache;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ManagedResource() {
    }

    public void load() {
        if (_application != null) {
            // NOTE WELL: Do NOT call Application.load here.
            // It is normal for there to be a circular reference between
            // a bootstrapped managed Application and the IDM system
            // application.  The bootstrapped application will reference
            // the IDM system through the proxy relationship, and the IDM
            // system may reference the bootstrapped application in it's
            // ManagedResource list.  We have to break the load cycle.
            // 
            // I don't like this, consider just downgrading this to 
            // a String application name?
            _application.getName();
        }
    }

    /**
     * The name of this resource in the IDM system.
     * If not set it is assumed to be the same as the Application name.
     */
    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String s) {
        _name = s;
    }

    @XMLProperty
    public String getIdentityAttribute() {
        return _identityAttribute;
    }

    public void setIdentityAttribute(String s) {
        _identityAttribute = s;
    }

    /**
     * The IIQ application corresponding to the remote resource.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application app) {
        _application = app;
    }

    /**
     * Attribute transformations.
     * If an attribute is not found in this list, the name
     * and value are passed through unmodified.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<ResourceAttribute> getResourceAttributes() {
        return _attributes;
    }

    public void setResourceAttributes(List<ResourceAttribute> atts) {
        _attributes = atts;
        _localAttributeCache = null;
        _remoteAttributeCache = null;
    }
    
    public void add(ResourceAttribute att) {
        if (att != null) {
            if (_attributes == null)
                _attributes = new ArrayList<ResourceAttribute>();
            _attributes.add(att);
            _localAttributeCache = null;
            _remoteAttributeCache = null;
        }
    }

    /**
     * @exclude
     * Kludge to support the older qualified attribute list.
     * @deprecated use {@link #getResourceAttributes()}
     */
    @XMLProperty(mode=SerializationMode.LIST,xmlname="ResourceAttributes")
    public List<ResourceAttribute> getXmlResourceAttributes() {
        return null;
    }

    /**
     * @exclude
     * Kludge to support the older qualified attribute list.
     * @deprecated use {@link #setResourceAttributes(java.util.List)} 
     */
    public void setXmlResourceAttributes(List<ResourceAttribute> atts) {
        setResourceAttributes(atts);
    }

    /**
     * Return true if there are any resource attributes defined.
     * This is used in cases where the absence of any attribute definitions
     * means to pass through all the raw attributes without modification. 
     */
    public boolean hasResourceAttributes() {
        return (_attributes != null && _attributes.size() > 0);
    }

    /**
     * Pseudo property for convenience.
     */
    public String getLocalName() {
        return (_application != null) ? _application.getName() : null;
    }

        
    public String getResourceName() {
        return (_name != null) ? _name : getLocalName();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Resource Name Mapping
    //
    //////////////////////////////////////////////////////////////////////

    public ResourceAttribute getResourceAttribute(String localName) {
        if (_localAttributeCache == null) {
            _localAttributeCache = new HashMap<String,ResourceAttribute>();
            if (_attributes != null) {
                for (ResourceAttribute att : _attributes) {
                    String name = att.getLocalName();
                    if (name != null)
                        _localAttributeCache.put(name, att);
                    // else, a modeling error
                }
            }
        }
        return _localAttributeCache.get(localName);
    }

    public String getResourceAttributeName(String localName) {

        String targetName = localName;
        ResourceAttribute mapping = getResourceAttribute(localName);
        if (mapping != null) {
            String alt = mapping.getName();
            if (alt != null)
                targetName = alt;
        }
        return targetName;
    }

    /**
     * Lookup a ResourceAttribute using the native name.
     */
    public ResourceAttribute getRemoteResourceAttribute(String name) {
        if (_remoteAttributeCache == null) {
            _remoteAttributeCache = new HashMap<String,ResourceAttribute>();
            if (_attributes != null) {
                for (ResourceAttribute att : _attributes) {
                    String rname = att.getName();
                    if (rname != null)
                        _remoteAttributeCache.put(rname, att);
                }
            }
        }
        return _remoteAttributeCache.get(name);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Operation Transformations
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Account operation transformations.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<OperationTransformation> getOperationTransformations() {
        return _operationTransformations;
    }

    public void setOperationTransformations(List<OperationTransformation> ops) {
        _operationTransformations = ops;
        _operationTransformationCache = null;
    }

    /**
     * Add an account operation transformation.
     */
    public void addOperationTransformation(Operation from, Operation to) {
        if (null == _operationTransformations) {
            _operationTransformations = new ArrayList<OperationTransformation>();
        }

        boolean found = false;
        for (OperationTransformation trans : _operationTransformations) {
            if (from.equals(trans.getSource())) {
                trans.setDestination(to);
                found = true;
            }
        }

        if (!found) {
            _operationTransformations.add(new OperationTransformation(from, to));
        }

        _operationTransformationCache = null;
    }

    /**
     * Return the transformed operation according to the operation
     * transformation configuration on this ManagedResource. If there is not
     * a transformation for the requested operation, the original operation
     * is returned.
     * 
     * @param  op  The Operation to transform.
     */
    public Operation transformOperation(Operation op) {

        Operation newOp = null;
        if (null != op) {
            if (null == _operationTransformationCache) {
                _operationTransformationCache = new HashMap<Operation,OperationTransformation>();
                if (null != _operationTransformations) {
                    for (OperationTransformation trans : _operationTransformations) {
                        _operationTransformationCache.put(trans.getSource(), trans);
                    }
                }
            }

            OperationTransformation trans = _operationTransformationCache.get(op);
            if (null != trans) {
                newOp = trans.getDestination();
            }
        }
            
        return (null != newOp) ? newOp : op;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // OperationalTransformation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Mapping from one account operation to another account operation.
     */
    @XMLClass
    public static class OperationTransformation {

        private Operation _source;
        private Operation _destination;

        public OperationTransformation() {}

        public OperationTransformation(Operation source, Operation dest) {
            _source = source;
            _destination = dest;
        }

        @XMLProperty
        public Operation getSource() {
            return _source;
        }
        
        public void setSource(Operation source) {
            _source = source;
        }
        
        @XMLProperty
        public Operation getDestination() {
            return _destination;
        }
        
        public void setDestination(Operation destination) {
            _destination = destination;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // ResourceAttribute
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Class used to represent one name mapping between 
     * an external attributes and an IdentityIQ application attribute.
     */
    @XMLClass
    public static class ResourceAttribute {

        /**
         * The name of the attribute in the IDM system.
         */
        String _name;

        /**
         * The name of the attribute in the IdentityIQ Application.
         */
        String _localName;

        // TODO: transformation rule?

        public ResourceAttribute() {
        }

        public ResourceAttribute(String name, String localName) {
            _name = name;
            _localName = localName;
        }

        /**
         * The name of the attribute in the IDM system.
         */
        @XMLProperty
        public String getName() {
            return _name;
        }

        public void setName(String s) {
            _name = s;
        }

        /**
         * The name of the attribute in IdentityIQ Application.
         */
        @XMLProperty
        public String getLocalName() {
            return _localName;
        }

        public void setLocalName(String s) {
            _localName = s;
        }


    }

}
