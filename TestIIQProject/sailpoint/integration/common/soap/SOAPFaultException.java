/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.integration.common.soap;

import javax.xml.namespace.QName;
import javax.xml.soap.Detail;

/**
 * Thrown when SOAP Fault is detected in SOAP response. 
 * Java 11 removed 'SOAPFaultException' class, hence to support SIM on Java 11, this class is
 * added.
 */
public class SOAPFaultException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private QName faultcode;
    private String faultstring;
    private Detail detail;

    /**
     * Construct a new SOAPFaultException.
     *
     * @param faultcode
     *            the QName
     * @param faultstring
     *            the fault string
     * @param detail
     *            fault detail
     */
    public SOAPFaultException(QName faultcode, String faultstring,
                              Detail detail) {
        super(faultstring);

        this.faultcode = faultcode;
        this.faultstring = faultstring;
        this.detail = detail;
    }

    public QName getFaultCode() {
        return this.faultcode;
    }

    public String getFaultString() {
        return this.faultstring;
    }

    public Detail getDetail() {
        return this.detail;
    }
}