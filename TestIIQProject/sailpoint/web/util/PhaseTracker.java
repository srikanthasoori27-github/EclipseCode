/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Based on code in _Core_JavaServer_Faces_ by David Geary and Cay Horstmann
 *
 * To use this, add the following to your faces configuration file:
 *
 *   <lifecycle>
 *     <phase-listener>sailpoint.web.util.PhaseTracker</phase-listener>
 *   </lifecycle>
 */
package sailpoint.web.util;

import java.util.Iterator;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.event.PhaseEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 */
public class PhaseTracker implements PhaseListener {

    private static final long serialVersionUID = 1L;
    private static Log log = LogFactory.getLog(PhaseTracker.class);

    private static final String PHASE_PARAM =
                                           "sailpoint.web.phaseTracker.phase";

    private static String _phase = null;

    /**
     *
     * @param newValue
     */
    public void setPhase(String phase) {
        _phase = phase;
    }

    /**
     *
     */
    public PhaseId getPhaseId() {

        PhaseId phaseId = PhaseId.ANY_PHASE;

        if ( _phase == null ) {
            FacesContext context = FacesContext.getCurrentInstance();
            if (context == null)
                return phaseId;
            _phase = (String) context.getExternalContext().
                                                getInitParameter(PHASE_PARAM);
        }

        if (_phase != null) {
            if ("RESTORE_VIEW".equals(_phase))
                phaseId = PhaseId.RESTORE_VIEW;
            else if ("APPLY_REQUEST_VALUES".equals(_phase))
                phaseId = PhaseId.APPLY_REQUEST_VALUES;
            else if ("PROCESS_VALIDATIONS".equals(_phase))
                phaseId = PhaseId.PROCESS_VALIDATIONS;
            else if ("UPDATE_MODEL_VALUES".equals(_phase))
                phaseId = PhaseId.UPDATE_MODEL_VALUES;
            else if ("INVOKE_APPLICATION".equals(_phase))
                phaseId = PhaseId.INVOKE_APPLICATION;
            else if ("RENDER_RESPONSE".equals(_phase))
                phaseId = PhaseId.RENDER_RESPONSE;
            else if ("ANY_PHASE".equals(_phase))
                phaseId = PhaseId.ANY_PHASE;
        }

        return phaseId;
    }  // getPhaseId

    /**
     *
     */
    public StringBuffer printUIComponentTree(StringBuffer buf,
                                         UIComponent uic, int indent) {
        FacesContext c = FacesContext.getCurrentInstance();
        if ( buf == null ) buf = new StringBuffer();
        if ( uic == null ) return buf;

        for ( int i = 0; i < indent; i++ ) {
            buf.append(' ');
        }

        buf.append(uic.getClass().getName() +
                        "(" + uic.getClientId(c) + "): " +
                        uic.getFamily() + " " + uic.getRendererType() + "\n");

        Iterator kids = uic.getFacetsAndChildren();
        while ( kids.hasNext() ) {
            UIComponent kid = (UIComponent)kids.next();
            if ( kid != null )
                printUIComponentTree(buf, kid, indent+2);
        }

        return buf;
    }  // printUIComponentTree(StringBuffer, uic, indent)

    /**
     *
     */
    public void beforePhase(PhaseEvent e) {
        if (log.isInfoEnabled()) {
            log.info("BEFORE " + e.getPhaseId());
            UIComponent uic = FacesContext.getCurrentInstance().getViewRoot();
            log.info("UI Component Tree\n" +
                                   printUIComponentTree(null, uic, 0).toString());
        }
    }

    public void afterPhase(PhaseEvent e) {
        if (log.isInfoEnabled()) {
            log.info("AFTER " + e.getPhaseId());
        }
    }
}  // class PhaseTracker
